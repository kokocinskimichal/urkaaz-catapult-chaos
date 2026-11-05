package com.urkaaaz.screens.campaign

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.widget.ImageView
import com.urkaaaz.ui.AsyncAssetCache
import com.urkaaaz.ui.BitmapFrameSequence
import com.urkaaaz.ui.BossPresentationCatalog
import com.urkaaaz.ui.CampaignMarkerIdleAnimationDefinition
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class CampaignBossMarkerImageView(
    context: Context,
    presentationKey: String,
    animation: CampaignMarkerIdleAnimationDefinition,
    fallbackSprite: String,
) : ImageView(context) {
    private val frames = CampaignBossMarkerFrameStore.sequence(
        context = context,
        presentationKey = presentationKey,
        animation = animation,
    )
    private var animationStartTime = 0L
    private var displayedFrameIndex = -1

    private val frameTicker = object : Runnable {
        override fun run() {
            val elapsed = SystemClock.uptimeMillis() - animationStartTime
            val frameIndex = animation.frameIndex(elapsed)
            frames.bitmapIfReady(frameIndex)?.let { bitmap ->
                if (displayedFrameIndex != frameIndex) {
                    setImageBitmap(bitmap)
                    displayedFrameIndex = frameIndex
                }
            }
            postDelayed(this, FRAME_POLL_INTERVAL_MILLISECONDS)
        }
    }

    init {
        scaleType = ScaleType.FIT_CENTER
        setImageResource(
            CampaignBossMarkerFrameStore.requireDrawableResource(context, fallbackSprite),
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animationStartTime = SystemClock.uptimeMillis()
        displayedFrameIndex = -1
        post(frameTicker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frameTicker)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FRAME_POLL_INTERVAL_MILLISECONDS = 50L
    }
}

internal object CampaignBossMarkerFrameStore {
    private val resourceIdLock = Any()
    private val resourceIds = mutableMapOf<String, Int>()
    private val decoder = ThreadPoolExecutor(
        1,
        1,
        DECODER_KEEP_ALIVE_SECONDS,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(DECODER_QUEUE_CAPACITY),
        ThreadFactory { runnable ->
            Thread(runnable, "CampaignBossMarkerDecoder").apply { isDaemon = true }
        },
    ).apply {
        allowCoreThreadTimeOut(true)
    }
    private val bitmapCache = AsyncAssetCache<Bitmap>(
        maxKilobytes = CACHE_KILOBYTES,
        executor = decoder,
        sizeOf = { (it.allocationByteCount / 1024).coerceAtLeast(1) },
    )

    fun sequence(
        context: Context,
        presentationKey: String,
        animation: CampaignMarkerIdleAnimationDefinition,
    ): BitmapFrameSequence {
        val appContext = context.applicationContext
        return BitmapFrameSequence(
            frameCount = animation.frameCount,
            cache = bitmapCache,
            frameKey = { frameIndex ->
                "drawable:${BossPresentationCatalog.campaignMarkerIdleFrameSpriteName(
                    presentationKey,
                    frameIndex,
                )}"
            },
            decodeFrame = { frameIndex ->
                val spriteName = BossPresentationCatalog.campaignMarkerIdleFrameSpriteName(
                    presentationKey,
                    frameIndex,
                )
                val resourceId = requireDrawableResource(appContext, spriteName)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(appContext.resources, resourceId, bounds)
                check(bounds.outWidth == animation.frameWidth &&
                    bounds.outHeight == animation.frameHeight
                ) {
                    "Campaign marker frame '$spriteName' must be " +
                        "${animation.frameWidth}x${animation.frameHeight}"
                }
                BitmapFactory.decodeResource(
                    appContext.resources,
                    resourceId,
                    BitmapFactory.Options().apply { inSampleSize = DECODE_SAMPLE_SIZE },
                ) ?: error("Could not decode campaign marker frame '$spriteName'")
            },
        )
    }

    fun requireDrawableResource(context: Context, name: String): Int {
        val appContext = context.applicationContext
        val key = "${appContext.packageName}:$name"
        val resourceId = synchronized(resourceIdLock) {
            resourceIds.getOrPut(key) {
                appContext.resources.getIdentifier(name, "drawable", appContext.packageName)
            }
        }
        return resourceId.takeIf { it != 0 }
            ?: error("Missing campaign boss marker drawable '$name'")
    }

    private const val CACHE_KILOBYTES = 2 * 1024
    private const val DECODE_SAMPLE_SIZE = 2
    private const val DECODER_KEEP_ALIVE_SECONDS = 30L
    private const val DECODER_QUEUE_CAPACITY = 16
}
