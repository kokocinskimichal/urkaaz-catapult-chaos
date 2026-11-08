package com.urkaaaz.ui

internal data class UnitDrawableFrameKey(
    val matchId: String?,
    val matchGeneration: Long,
    val entityId: String,
    val unitType: String,
    val teamLabel: String,
)

internal class UnitDrawableFrameHold<T : Any>(
    private val maxSizeBytes: Int = DEFAULT_MAX_SIZE_BYTES,
    private val sizeOf: (T) -> Int = { 1 },
) {
    private val lastReadyFrameByUnit = LinkedHashMap<UnitDrawableFrameKey, T>(0, 0.75f, true)
    private var currentSizeBytes = 0

    init {
        require(maxSizeBytes > 0) { "maxSizeBytes must be positive" }
    }

    fun lastReady(key: UnitDrawableFrameKey): T? = lastReadyFrameByUnit[key]

    fun resolve(key: UnitDrawableFrameKey, currentFrame: T?): T? {
        if (currentFrame != null) {
            lastReadyFrameByUnit.remove(key)?.let { currentSizeBytes -= frameByteSize(it) }
            val currentFrameSizeBytes = frameByteSize(currentFrame)
            if (currentFrameSizeBytes <= maxSizeBytes) {
                lastReadyFrameByUnit[key] = currentFrame
                currentSizeBytes += currentFrameSizeBytes
                evictToSizeLimit()
            }
            return currentFrame
        }
        return lastReadyFrameByUnit[key]
    }

    fun retainOnly(activeUnits: Set<UnitDrawableFrameKey>) {
        val iterator = lastReadyFrameByUnit.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in activeUnits) {
                currentSizeBytes -= frameByteSize(entry.value)
                iterator.remove()
            }
        }
    }

    fun reset() {
        lastReadyFrameByUnit.clear()
        currentSizeBytes = 0
    }

    private fun evictToSizeLimit() {
        val iterator = lastReadyFrameByUnit.entries.iterator()
        while (currentSizeBytes > maxSizeBytes && iterator.hasNext()) {
            val entry = iterator.next()
            currentSizeBytes -= frameByteSize(entry.value)
            iterator.remove()
        }
    }

    private fun frameByteSize(frame: T): Int = sizeOf(frame).coerceAtLeast(1)

    private companion object {
        const val DEFAULT_MAX_SIZE_BYTES = 4 * 1024 * 1024
    }
}
