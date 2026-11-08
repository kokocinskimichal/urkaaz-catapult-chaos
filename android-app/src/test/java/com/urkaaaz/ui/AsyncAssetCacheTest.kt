package com.urkaaaz.ui

import android.graphics.Bitmap
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AsyncAssetCacheTest {
    @Test
    fun executorRejectionIsReportedSeparatelyFromPendingDecode() {
        val cache = AsyncAssetCache<String>(
            maxKilobytes = 8,
            executor = Executor { throw RejectedExecutionException("queue is full") },
            sizeOf = String::length,
        )

        assertSame(
            AsyncAssetResult.QueueRejected,
            cache.request("asset") { "bitmap" },
        )
    }

    @Test
    fun identicalMapDecorationRequestsShareOneDecodeAndCacheTheResult() {
        val executor = QueuedExecutor()
        var decodeCount = 0
        var readyCallbackCount = 0
        val cache = AsyncAssetCache<String>(
            maxKilobytes = 8,
            executor = executor,
            sizeOf = String::length,
            onAssetReady = { readyCallbackCount += 1 },
        )

        val assetKey = "drawable:terrain_green_frontier_tree_00"
        assertSame(AsyncAssetResult.Pending, cache.request(assetKey) {
            decodeCount += 1
            "bitmap"
        })
        assertSame(AsyncAssetResult.Pending, cache.request(assetKey) {
            decodeCount += 1
            "duplicate"
        })
        assertEquals(0, decodeCount)

        executor.runNext()

        assertEquals(1, decodeCount)
        assertEquals(1, readyCallbackCount)
        assertEquals(
            AsyncAssetResult.Ready("bitmap"),
            cache.request(assetKey) { error("cached asset was decoded again") },
        )
    }

    @Test
    fun decodeFailureIsReturnedExplicitlyOnSubsequentRequests() {
        val executor = QueuedExecutor()
        val decodeFailure = IllegalStateException("asset decode failed")
        val cache = AsyncAssetCache<String>(
            maxKilobytes = 8,
            executor = executor,
            sizeOf = String::length,
        )

        assertSame(
            AsyncAssetResult.Pending,
            cache.request("broken") { throw decodeFailure },
        )
        executor.runNext()

        val result = cache.request("broken") { error("failed asset was decoded again") }
        assertTrue(result is AsyncAssetResult.Failed)
        assertSame(decodeFailure, (result as AsyncAssetResult.Failed).cause)
    }

    @Test
    fun cancelPendingLoadsAllowsTheSameKeyToBeRequestedAgain() {
        val executor = QueuedExecutor()
        var decodeCount = 0
        val cache = AsyncAssetCache<String>(
            maxKilobytes = 8,
            executor = executor,
            sizeOf = String::length,
        )

        cache.request("tree") {
            decodeCount += 1
            "old"
        }
        cache.cancelPendingLoads()
        assertSame(
            AsyncAssetResult.Pending,
            cache.request("tree") {
                decodeCount += 1
                "new"
            },
        )

        executor.runNext()
        executor.runNext()

        assertEquals(
            AsyncAssetResult.Ready("new"),
            cache.request("tree") { error("cached asset was decoded again") },
        )
        assertEquals(1, decodeCount)
    }

    @Test
    fun weightedLimitEvictsTheLeastRecentlyUsedAsset() {
        val executor = QueuedExecutor()
        var decodeCount = 0
        val cache = AsyncAssetCache<String>(
            maxKilobytes = 2,
            executor = executor,
            sizeOf = String::length,
        )
        cache.putReady("first", "aa")
        cache.putReady("second", "bb")

        assertSame(
            AsyncAssetResult.Pending,
            cache.request("first") {
                decodeCount += 1
                "aa"
            },
        )
        executor.runNext()

        assertEquals(1, decodeCount)
        assertEquals(
            AsyncAssetResult.Ready("aa"),
            cache.request("first") { error("reloaded asset was decoded again") },
        )
        assertSame(
            AsyncAssetResult.Pending,
            cache.request("second") {
                decodeCount += 1
                "bb"
            },
        )
        executor.runNext()
        assertEquals(2, decodeCount)
    }

    @Test
    fun frameSequenceStartsLoadingOnlyWhenAFrameIsRequested() {
        val executor = QueuedExecutor()
        var decodeCount = 0
        val cache = AsyncAssetCache<Bitmap>(
            maxKilobytes = 8,
            executor = executor,
            sizeOf = { 1 },
        )
        val sequence = BitmapFrameSequence(
            frameCount = 1,
            cache = cache,
            frameKey = { "frame:$it" },
            decodeFrame = {
                decodeCount += 1
                error("decode must be deferred to the executor")
            },
            prefetchAhead = 0,
        )

        assertEquals(0, decodeCount)
        assertNull(sequence.bitmapIfReady(0))
        assertEquals(0, decodeCount)
        assertEquals(1, executor.queuedCount)
    }

    @Test
    fun frameReadDistinguishesPendingDecodeFromAFrameEvictedAfterBeingReady() {
        val executor = QueuedExecutor()
        val cache = AsyncAssetCache<Bitmap>(
            maxKilobytes = 1,
            executor = executor,
            sizeOf = { 1 },
        )
        val sequence = BitmapFrameSequence(
            frameCount = 1,
            cache = cache,
            frameKey = { "frame:$it" },
            decodeFrame = { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) },
            prefetchAhead = 0,
        )

        val pendingRead = sequence.requestFrame(0)
        assertEquals(BitmapReadStatus.PENDING, pendingRead.status)
        assertEquals("frame:0", pendingRead.assetKey)

        executor.runNext()
        assertEquals(BitmapReadStatus.READY, sequence.requestFrame(0).status)

        cache.putReady("other-frame", Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

        val evictedRead = sequence.requestFrame(0)
        assertEquals(BitmapReadStatus.CACHE_MISS_AFTER_READY, evictedRead.status)
        assertEquals("frame:0", evictedRead.assetKey)
    }

    @Test
    fun frameSequenceSurfacesDecodeFailureInsteadOfReturningAPlaceholder() {
        val executor = QueuedExecutor()
        val decodeFailure = IllegalStateException("frame decode failed")
        val cache = AsyncAssetCache<Bitmap>(
            maxKilobytes = 8,
            executor = executor,
            sizeOf = { 1 },
        )
        val sequence = BitmapFrameSequence(
            frameCount = 1,
            cache = cache,
            frameKey = { "frame:$it" },
            decodeFrame = { throw decodeFailure },
            prefetchAhead = 0,
        )

        assertNull(sequence.bitmapIfReady(0))
        executor.runNext()

        val surfaced = assertThrows(IllegalStateException::class.java) {
            sequence.bitmapIfReady(0)
        }
        assertSame(decodeFailure, surfaced.cause)
    }

    @Test
    fun frameSequenceCanRetryAfterAnExplicitFailureReset() {
        val executor = QueuedExecutor()
        val decodeFailure = IllegalStateException("temporary frame decode failure")
        val decodedFrame = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        var decodeCount = 0
        val sequence = BitmapFrameSequence(
            frameCount = 1,
            cache = AsyncAssetCache(
                maxKilobytes = 8,
                executor = executor,
                sizeOf = { 1 },
            ),
            frameKey = { "frame:$it" },
            decodeFrame = {
                decodeCount += 1
                if (decodeCount == 1) throw decodeFailure
                decodedFrame
            },
            prefetchAhead = 0,
        )

        assertNull(sequence.bitmapIfReady(0))
        executor.runNext()
        assertThrows(IllegalStateException::class.java) {
            sequence.bitmapIfReady(0)
        }

        sequence.retryFrame(0)
        assertNull(sequence.bitmapIfReady(0))
        executor.runNext()

        assertSame(decodedFrame, sequence.bitmapIfReady(0))
        assertEquals(2, decodeCount)
    }

    private class QueuedExecutor : Executor {
        private val commands = ArrayDeque<Runnable>()
        val queuedCount: Int
            get() = commands.size

        override fun execute(command: Runnable) {
            commands.addLast(command)
        }

        fun runNext() {
            commands.removeFirst().run()
        }
    }
}
