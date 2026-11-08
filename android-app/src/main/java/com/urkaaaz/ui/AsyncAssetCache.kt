package com.urkaaaz.ui

import android.graphics.Bitmap
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException

internal sealed interface AsyncAssetResult<out T> {
    data class Ready<T>(val value: T) : AsyncAssetResult<T>
    data object Pending : AsyncAssetResult<Nothing>
    data object QueueRejected : AsyncAssetResult<Nothing>
    data class Failed(val cause: Exception) : AsyncAssetResult<Nothing>
}

internal enum class BitmapReadStatus {
    READY,
    PENDING,
    CACHE_MISS_AFTER_READY,
    QUEUE_REJECTED,
    MISSING_RESOURCE,
    NO_SEQUENCE,
}

internal data class BitmapRead(
    val bitmap: Bitmap?,
    val assetKey: String?,
    val status: BitmapReadStatus,
)

internal class AsyncAssetCache<T>(
    maxKilobytes: Int,
    private val executor: Executor,
    sizeOf: (T) -> Int,
    private val onAssetReady: () -> Unit = {},
) {
    private val lock = Any()
    private val cache = WeightedLruCache<T>(maxKilobytes, sizeOf)
    private val pendingTasks = mutableMapOf<String, FutureTask<Unit>>()
    private val failures = mutableMapOf<String, Exception>()

    fun request(key: String, load: () -> T): AsyncAssetResult<T> = synchronized(lock) {
        cache.get(key)?.let { return@synchronized AsyncAssetResult.Ready(it) }
        failures[key]?.let { return@synchronized AsyncAssetResult.Failed(it) }
        if (key in pendingTasks) return@synchronized AsyncAssetResult.Pending

        lateinit var task: FutureTask<Unit>
        task = FutureTask {
            try {
                val value = load()
                synchronized(lock) {
                    if (pendingTasks[key] === task && !Thread.currentThread().isInterrupted) {
                        cache.put(key, value)
                    }
                }
            } catch (exception: Exception) {
                synchronized(lock) {
                    if (pendingTasks[key] === task && !Thread.currentThread().isInterrupted) {
                        failures[key] = exception
                    }
                }
            } finally {
                val shouldInvalidate = synchronized(lock) {
                    if (pendingTasks[key] === task) {
                        pendingTasks.remove(key)
                        true
                    } else {
                        false
                    }
                }
                if (shouldInvalidate) onAssetReady()
            }
            Unit
        }
        pendingTasks[key] = task
        try {
            executor.execute(task)
        } catch (_: RejectedExecutionException) {
            pendingTasks.remove(key)
            return@synchronized AsyncAssetResult.QueueRejected
        }
        AsyncAssetResult.Pending
    }

    fun putReady(key: String, value: T) {
        synchronized(lock) {
            pendingTasks.remove(key)?.cancel(true)
            failures.remove(key)
            cache.put(key, value)
        }
    }

    fun clearFailure(key: String) {
        synchronized(lock) {
            failures.remove(key)
        }
    }

    fun cancelPendingLoads() {
        val tasks = synchronized(lock) {
            pendingTasks.values.toList().also { pendingTasks.clear() }
        }
        tasks.forEach { it.cancel(true) }
    }
}

internal class BitmapFrameSequence(
    val frameCount: Int,
    private val cache: AsyncAssetCache<Bitmap>?,
    private val frameKey: (Int) -> String,
    private val decodeFrame: (Int) -> Bitmap,
    private val prefetchAhead: Int = DEFAULT_PREFETCH_AHEAD,
) {
    private val previouslyReadyFrameKeys = mutableSetOf<String>()

    init {
        require(frameCount >= 0) { "frameCount must not be negative" }
        require(prefetchAhead >= 0) { "prefetchAhead must not be negative" }
    }

    fun requestFrame(index: Int): BitmapRead {
        if (frameCount == 0) {
            return BitmapRead(
                bitmap = null,
                assetKey = null,
                status = BitmapReadStatus.NO_SEQUENCE,
            )
        }
        var currentResult: AsyncAssetResult<Bitmap>? = null
        var currentFrameKey: String? = null
        val bitmap = requestFrameBitmap(index) { key, result ->
            currentFrameKey = key
            currentResult = result
            if (result is AsyncAssetResult.Ready) previouslyReadyFrameKeys += key
        }
        val key = requireNotNull(currentFrameKey)
        return when (val result = requireNotNull(currentResult)) {
            is AsyncAssetResult.Ready ->
                BitmapRead(bitmap, key, BitmapReadStatus.READY)
            AsyncAssetResult.Pending ->
                BitmapRead(
                    bitmap = null,
                    assetKey = key,
                    status = if (key in previouslyReadyFrameKeys) {
                        BitmapReadStatus.CACHE_MISS_AFTER_READY
                    } else {
                        BitmapReadStatus.PENDING
                    },
                )
            AsyncAssetResult.QueueRejected ->
                BitmapRead(null, key, BitmapReadStatus.QUEUE_REJECTED)
            is AsyncAssetResult.Failed ->
                error("Failed animation frame '$key' was already surfaced")
        }
    }

    fun bitmapIfReady(index: Int): Bitmap? = requestFrameBitmap(index)

    fun retryFrame(index: Int) {
        if (frameCount == 0) return
        require(index in 0 until frameCount) {
            "Frame index $index is outside 0 until $frameCount"
        }
        requireNotNull(cache).clearFailure(frameKey(index))
    }

    private fun requestFrameBitmap(
        index: Int,
        onCurrentResult: ((String, AsyncAssetResult<Bitmap>) -> Unit)? = null,
    ): Bitmap? {
        if (frameCount == 0) return null
        require(index in 0 until frameCount) {
            "Frame index $index is outside 0 until $frameCount"
        }
        val frameCache = requireNotNull(cache) {
            "A non-empty bitmap sequence must have an asset cache"
        }

        var currentFrame: Bitmap? = null
        val lastPrefetchIndex = (index + prefetchAhead).coerceAtMost(frameCount - 1)
        for (frameIndex in index..lastPrefetchIndex) {
            val key = frameKey(frameIndex)
            val result = frameCache.request(key) { decodeFrame(frameIndex) }
            if (frameIndex == index) {
                when (result) {
                    is AsyncAssetResult.Ready -> currentFrame = result.value
                    AsyncAssetResult.Pending,
                    AsyncAssetResult.QueueRejected,
                    is AsyncAssetResult.Failed -> Unit
                }
                onCurrentResult?.invoke(key, result)
            }
            if (frameIndex == index && result is AsyncAssetResult.Failed) {
                throw IllegalStateException(
                    "Failed to decode animation frame '$key'",
                    result.cause,
                )
            }
        }
        return currentFrame
    }

    companion object {
        private const val DEFAULT_PREFETCH_AHEAD = 2

        fun empty(): BitmapFrameSequence = BitmapFrameSequence(
            frameCount = 0,
            cache = null,
            frameKey = { "" },
            decodeFrame = { error("Empty animation sequence has no frames") },
        )
    }
}
