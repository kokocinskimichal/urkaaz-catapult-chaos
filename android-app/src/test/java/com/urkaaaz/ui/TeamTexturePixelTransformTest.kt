package com.urkaaaz.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamTexturePixelTransformTest {
    @Test
    fun weightedFrameCacheEvictsLeastRecentlyUsedEntriesAtItsLimit() {
        val cache = WeightedLruCache<String>(maxSize = 3) { it.length }
        cache.put("first", "a")
        cache.put("second", "b")
        cache.get("first")
        cache.put("third", "c")
        cache.put("fourth", "d")

        assertEquals("a", cache.get("first"))
        assertEquals(null, cache.get("second"))
        assertEquals("c", cache.get("third"))
        assertEquals("d", cache.get("fourth"))
    }

    @Test
    fun appliesTiledTextureOnlyThroughMaskAndPreservesAlphaAndShading() {
        val source = intArrayOf(
            0xff204080.toInt(),
            0xff204080.toInt(),
            0x00204080,
            0xff204080.toInt(),
        )
        val mask = intArrayOf(0xffffffff.toInt(), 0x00ffffff, 0xffffffff.toInt(), 0x80ffffff.toInt())
        val texture = intArrayOf(0xffc03030.toInt(), 0xff30c030.toInt())

        val result = TeamTexturePixelTransform.recolor(
            sourcePixels = source,
            maskPixels = mask,
            texturePixels = texture,
            width = 2,
            height = 2,
            textureWidth = 2,
            textureHeight = 1,
        )

        assertNotEquals(source[0], result[0])
        // Fully covered pixel must equal the lit texture colour (0xc0 * lighting), not an overflowed blend.
        val luminance = (0x20 * 299 + 0x40 * 587 + 0x80 * 114) / 1000f / 255f
        val lighting = (0.5f + luminance).coerceIn(0.45f, 1.2f)
        assertEquals((0xc0 * lighting).toDouble(), (result[0] ushr 16 and 0xff).toDouble(), 1.0)
        assertEquals((0x30 * lighting).toDouble(), (result[0] and 0xff).toDouble(), 1.0)
        assertTrue((result[0] ushr 16 and 0xff) > 100)
        assertTrue((result[0] ushr 16 and 0xff) > (result[0] ushr 8 and 0xff) * 2)
        assertEquals(source[1], result[1])
        assertEquals(source[2], result[2])
        assertEquals(source[3] ushr 24, result[3] ushr 24)
        assertNotEquals(source[3], result[3])
    }
}
