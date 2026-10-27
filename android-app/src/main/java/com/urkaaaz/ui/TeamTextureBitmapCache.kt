package com.urkaaaz.ui

import android.graphics.Bitmap
import kotlin.math.roundToInt

internal class TeamTextureBitmapCache(maxKilobytes: Int = DEFAULT_CACHE_KILOBYTES) {
    private val cache = WeightedLruCache<Bitmap>(maxKilobytes) {
        (it.allocationByteCount / 1024).coerceAtLeast(1)
    }

    @Synchronized
    fun getOrCreate(
        key: String,
        source: Bitmap,
        mask: Bitmap,
        texture: Bitmap,
    ): Bitmap = cache.get(key) ?: recolor(source, mask, texture).also { cache.put(key, it) }

    private fun recolor(source: Bitmap, mask: Bitmap, texture: Bitmap): Bitmap {
        require(source.width == mask.width && source.height == mask.height) {
            "Team animation mask must match the source frame dimensions"
        }
        require(texture.width > 0 && texture.height > 0) { "Team texture must not be empty" }

        val width = source.width
        val height = source.height
        val sourcePixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        source.getPixels(sourcePixels, 0, width, 0, 0, width, height)
        mask.getPixels(maskPixels, 0, width, 0, 0, width, height)
        val texturePixels = IntArray(texture.width * texture.height)
        texture.getPixels(texturePixels, 0, texture.width, 0, 0, texture.width, texture.height)
        val output = TeamTexturePixelTransform.recolor(
            sourcePixels = sourcePixels,
            maskPixels = maskPixels,
            texturePixels = texturePixels,
            width = width,
            height = height,
            textureWidth = texture.width,
            textureHeight = texture.height,
        )
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    private companion object {
        const val DEFAULT_CACHE_KILOBYTES = 6 * 1024
    }
}

internal class WeightedLruCache<T>(
    private val maxSize: Int,
    private val sizeOf: (T) -> Int,
) {
    private val entries = LinkedHashMap<String, T>(0, 0.75f, true)
    private var currentSize = 0

    init {
        require(maxSize > 0) { "Cache size must be positive" }
    }

    @Synchronized
    fun get(key: String): T? = entries[key]

    @Synchronized
    fun put(key: String, value: T) {
        val valueSize = sizeOf(value)
        require(valueSize > 0) { "Cached values must have a positive size" }
        entries.remove(key)?.let { currentSize -= sizeOf(it) }
        entries[key] = value
        currentSize += valueSize
        while (currentSize > maxSize) {
            val eldest = entries.entries.iterator().next()
            currentSize -= sizeOf(eldest.value)
            entries.remove(eldest.key)
        }
    }
}

internal object TeamTexturePixelTransform {
    fun recolor(
        sourcePixels: IntArray,
        maskPixels: IntArray,
        texturePixels: IntArray,
        width: Int,
        height: Int,
        textureWidth: Int,
        textureHeight: Int,
    ): IntArray {
        require(width > 0 && height > 0 && textureWidth > 0 && textureHeight > 0)
        require(sourcePixels.size == width * height && maskPixels.size == width * height)
        require(texturePixels.size == textureWidth * textureHeight)
        val output = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val original = sourcePixels[index]
                val originalAlpha = original ushr 24
                val coverage = ((maskPixels[index] ushr 24) * originalAlpha) / (255f * 255f)
                if (coverage <= 0f || originalAlpha == 0) {
                    output[index] = original
                    continue
                }

                val originalRed = original ushr 16 and 0xff
                val originalGreen = original ushr 8 and 0xff
                val originalBlue = original and 0xff
                val luminance =
                    (originalRed * 299 + originalGreen * 587 + originalBlue * 114) / 1000f / 255f
                val lighting = (0.5f + luminance).coerceIn(0.45f, 1.2f)
                val fabric = texturePixels[(y % textureHeight) * textureWidth + x % textureWidth]
                val fabricRed = fabric ushr 16 and 0xff
                val fabricGreen = fabric ushr 8 and 0xff
                val fabricBlue = fabric and 0xff
                val fabricLuminance =
                    (fabricRed * 299 + fabricGreen * 587 + fabricBlue * 114) / 1000f
                val red = blend(
                    originalRed,
                    desaturate(fabricRed, fabricLuminance) * lighting,
                    coverage,
                )
                val green = blend(
                    originalGreen,
                    desaturate(fabricGreen, fabricLuminance) * lighting,
                    coverage,
                )
                val blue = blend(
                    originalBlue,
                    desaturate(fabricBlue, fabricLuminance) * lighting,
                    coverage,
                )
                output[index] = (originalAlpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return output
    }

    private fun desaturate(channel: Int, luminance: Float): Float =
        luminance + (channel - luminance) * TEAM_TEXTURE_SATURATION

    private fun blend(original: Int, textured: Float, coverage: Float): Int =
        (original * (1f - coverage) + textured * coverage).roundToInt().coerceIn(0, 255)

    internal const val TEAM_TEXTURE_SATURATION = 0.8f
}
