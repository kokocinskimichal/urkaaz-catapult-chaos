package com.urkaaaz.ui

import android.graphics.Bitmap
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException

internal sealed interface AsyncAssetResult<out T> {
    data class Ready<T>(val value: T) : AsyncAssetResult<T>
    data object Pending : AsyncAssetResult<Nothing>
    data class Failed(val cause: Exception) : AsyncAssetResult<Nothing>
}

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
            return@synchronized AsyncAssetResult.Pending
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
    init {
        require(frameCount >= 0) { "frameCount must not be negative" }
        require(prefetchAhead >= 0) { "prefetchAhead must not be negative" }
    }

    fun bitmapIfReady(index: Int): Bitmap? {
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
            when (val result = frameCache.request(key) { decodeFrame(frameIndex) }) {
                is AsyncAssetResult.Ready -> {
                    if (frameIndex == index) currentFrame = result.value
                }
                AsyncAssetResult.Pending -> Unit
                is AsyncAssetResult.Failed -> {
                    if (frameIndex == index) {
                        throw IllegalStateException(
                            "Failed to decode animation frame '$key'",
                            result.cause,
                        )
                    }
                }
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
