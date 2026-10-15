package com.urkaaaz.mapeditor.rendering

import com.urkaaaz.contracts.MapDefinition
import com.urkaaaz.contracts.CatapultPresentationContract
import com.urkaaaz.contracts.WorldDimensions
import com.urkaaaz.contracts.WorldScale
import com.urkaaaz.mapeditor.domain.EditorDocument
import java.awt.Graphics2D
import java.awt.image.BufferedImage

class ViewportTransform(
    var zoom: Double = 0.15,
    var offsetX: Double = 20.0,
    var offsetY: Double = 20.0,
) {
    var autoFit: Boolean = true
        private set

    fun fitTo(viewportWidth: Int, viewportHeight: Int, worldWidth: Float, worldHeight: Float) {
        if (!autoFit) return
        val horizontal = (viewportWidth - 24).coerceAtLeast(1) / worldWidth
        val vertical = (viewportHeight - 24).coerceAtLeast(1) / worldHeight
        zoom = minOf(horizontal, vertical).toDouble()
        offsetX = (viewportWidth - worldWidth * zoom) / 2.0
        offsetY = (viewportHeight - worldHeight * zoom) / 2.0
    }

    fun zoomBy(factor: Double, viewportWidth: Int, viewportHeight: Int) {
        val centerWorld = screenToWorld(viewportWidth / 2.0, viewportHeight / 2.0)
        zoom = (zoom * factor).coerceIn(0.03, 1.0)
        offsetX = viewportWidth / 2.0 - centerWorld.first * zoom
        offsetY = viewportHeight / 2.0 - centerWorld.second * zoom
        autoFit = false
    }

    fun resetFit() {
        autoFit = true
    }

    fun worldToScreen(x: Float, y: Float): Pair<Double, Double> = x * zoom + offsetX to y * zoom + offsetY
    fun screenToWorld(x: Double, y: Double): Pair<Float, Float> = ((x - offsetX) / zoom).toFloat() to ((y - offsetY) / zoom).toFloat()
}

object MapRenderer {
    private const val LEGACY_FORTRESS_RENDER_WIDTH = 280f
    private const val LEGACY_FORTRESS_RENDER_HEIGHT = 210f
    private const val FORTRESS_RENDER_WIDTH = LEGACY_FORTRESS_RENDER_WIDTH * WorldScale.FORTRESS_SCALE
    private const val FORTRESS_RENDER_HEIGHT = LEGACY_FORTRESS_RENDER_HEIGHT * WorldScale.FORTRESS_SCALE
    private const val LEGACY_VIEWPORT_WIDTH = 4_800f
    private const val LEGACY_VIEWPORT_HEIGHT = 2_400f
    private val FORTRESS_WORLD_WIDTH = FORTRESS_RENDER_WIDTH / LEGACY_VIEWPORT_WIDTH * WorldDimensions.WIDTH
    private val FORTRESS_WORLD_HEIGHT = FORTRESS_RENDER_HEIGHT / LEGACY_VIEWPORT_HEIGHT * WorldDimensions.HEIGHT

    fun render(
        graphics: Graphics2D,
        document: EditorDocument,
        transform: ViewportTransform,
        visuals: BiomeVisuals? = null,
        showGrid: Boolean = true,
    ) {
        val map = document.map
        val (left, top) = transform.worldToScreen(0f, 0f)
        val width = map.world.width * transform.zoom
        val height = map.world.height * transform.zoom
        graphics.color = java.awt.Color(40, 45, 52)
        graphics.fillRect(left.toInt(), top.toInt(), width.toInt(), height.toInt())
        if (document.phase != com.urkaaaz.mapeditor.domain.EditorPhase.TERRAIN_EDITING) {
            visuals?.background?.let {
                graphics.drawImage(it, left.toInt(), top.toInt(), width.toInt(), height.toInt(), null)
            }
        }
        if (document.phase != com.urkaaaz.mapeditor.domain.EditorPhase.TERRAIN_EDITING) {
            drawSoil(graphics, map, transform, visuals?.soil)
        }
        graphics.color = java.awt.Color(111, 145, 77)
        map.terrain.samples.zipWithNext().forEach { (a, b) ->
            val p1 = transform.worldToScreen(a.x, a.height)
            val p2 = transform.worldToScreen(b.x, b.height)
            graphics.drawLine(p1.first.toInt(), p1.second.toInt(), p2.first.toInt(), p2.second.toInt())
        }
        if (document.phase != com.urkaaaz.mapeditor.domain.EditorPhase.TERRAIN_EDITING) {
            drawCap(graphics, map, transform, visuals?.cap)
        }
        drawFortresses(
            graphics,
            map,
            transform,
            visuals?.leftFortress,
            visuals?.rightFortress,
            visuals?.leftCatapult,
            visuals?.rightCatapult,
        )
        if (showGrid) {
            graphics.color = java.awt.Color(255, 255, 255, 25)
            var x = 0f
            while (x <= map.world.width) {
                val p = transform.worldToScreen(x, 0f)
                graphics.drawLine(p.first.toInt(), top.toInt(), p.first.toInt(), (top + height).toInt())
                x += map.terrain.sampleStep
            }
        }
        graphics.color = java.awt.Color(255, 190, 70)
        map.protectedTerrainZones.forEach {
            val p1 = transform.worldToScreen(it.minX, it.minHeight)
            val p2 = transform.worldToScreen(it.maxX, it.maxHeight)
            graphics.drawRect(p1.first.toInt(), p1.second.toInt(), (p2.first - p1.first).toInt(), (p2.second - p1.second).toInt())
        }
        map.decorations.forEach {
            val p = transform.worldToScreen(it.x, it.y)
            graphics.color = java.awt.Color(90, 210, 120)
            graphics.fillOval((p.first - 8).toInt(), (p.second - 8).toInt(), 16, 16)
            graphics.color = java.awt.Color.WHITE
            graphics.drawString(it.id, (p.first + 10).toInt(), p.second.toInt())
        }
        graphics.color = java.awt.Color(210, 210, 210)
        graphics.drawRect(left.toInt(), top.toInt(), width.toInt(), height.toInt())
    }

    private fun drawSoil(graphics: Graphics2D, map: MapDefinition, transform: ViewportTransform, soil: BufferedImage?) {
        val polygon = java.awt.Polygon()
        map.terrain.samples.forEach {
            val point = transform.worldToScreen(it.x, it.height)
            polygon.addPoint(point.first.toInt(), point.second.toInt())
        }
        polygon.addPoint(transform.worldToScreen(map.world.width.toFloat(), map.world.height.toFloat()).first.toInt(),
            transform.worldToScreen(map.world.width.toFloat(), map.world.height.toFloat()).second.toInt())
        polygon.addPoint(transform.worldToScreen(0f, map.world.height.toFloat()).first.toInt(),
            transform.worldToScreen(0f, map.world.height.toFloat()).second.toInt())
        if (soil == null) {
            graphics.color = java.awt.Color(125, 88, 52)
            graphics.fillPolygon(polygon)
            return
        }
        val clip = graphics.clip
        graphics.clip = polygon
        val left = transform.worldToScreen(0f, 0f)
        val width = (map.world.width * transform.zoom).toInt()
        val height = (map.world.height * transform.zoom).toInt()
        graphics.drawImage(soil, left.first.toInt(), left.second.toInt(), width, height, null)
        graphics.clip = clip
    }

    private fun drawCap(graphics: Graphics2D, map: MapDefinition, transform: ViewportTransform, cap: BufferedImage?) {
        val capHeight = 70f
        map.terrain.samples.zipWithNext().forEach { (a, b) ->
            val x1 = transform.worldToScreen(a.x, 0f).first.toInt()
            val x2 = transform.worldToScreen(b.x, 0f).first.toInt()
            val y1 = transform.worldToScreen(a.x, a.height).second.toInt()
            val y2 = transform.worldToScreen(b.x, b.height).second.toInt()
            if (cap == null) {
                graphics.color = java.awt.Color(111, 145, 77)
                graphics.fillRect(x1, y1, x2 - x1 + 1, (capHeight * transform.zoom).toInt())
            } else {
                graphics.drawImage(cap, x1, y1 - (capHeight * transform.zoom).toInt(),
                    x2 - x1 + 1, (capHeight * transform.zoom).toInt(), null)
            }
        }
    }

    private fun drawFortresses(
        graphics: Graphics2D,
        map: MapDefinition,
        transform: ViewportTransform,
        leftFortress: BufferedImage?,
        rightFortress: BufferedImage?,
        leftCatapult: BufferedImage?,
        rightCatapult: BufferedImage?,
    ) {
        map.fortresses.forEach { fortress ->
            val image = if (fortress.owner == com.urkaaaz.contracts.Team.BLUE) leftFortress else rightFortress
            val center = transform.worldToScreen(fortress.x, fortress.foundationHeight)
            val width = (FORTRESS_WORLD_WIDTH * transform.zoom).toInt()
            val height = (FORTRESS_WORLD_HEIGHT * transform.zoom).toInt()
            if (image != null) {
                graphics.drawImage(image, center.first.toInt() - width / 2, center.second.toInt() - height, width, height, null)
            } else {
                graphics.color = if (fortress.owner == com.urkaaaz.contracts.Team.BLUE) java.awt.Color(65, 105, 180) else java.awt.Color(180, 65, 65)
                graphics.fillRect(center.first.toInt() - width / 2, center.second.toInt() - height, width, height)
            }
        }
        map.fortresses.forEach { fortress ->
            val isBlue = fortress.owner == com.urkaaaz.contracts.Team.BLUE
            val image = if (isBlue) leftCatapult else rightCatapult
            val baseline = transform.worldToScreen(
                fortress.x + if (isBlue) {
                    -CatapultPresentationContract.CATAPULT_CENTER_OFFSET_FROM_FORTRESS
                } else {
                    CatapultPresentationContract.CATAPULT_CENTER_OFFSET_FROM_FORTRESS
                },
                fortress.foundationHeight - CATAPULT_BASELINE_OFFSET_WORLD,
            )
            val width = (CATAPULT_WORLD_WIDTH * transform.zoom).toInt()
            val height = if (image == null) {
                (CATAPULT_WORLD_HEIGHT * transform.zoom).toInt()
            } else {
                (width.toFloat() * image.height / image.width).toInt()
            }
            if (image != null) {
                graphics.drawImage(image, baseline.first.toInt() - width / 2, baseline.second.toInt() - height, width, height, null)
            } else {
                graphics.color = if (isBlue) java.awt.Color(50, 90, 150) else java.awt.Color(150, 50, 50)
                graphics.fillRect(baseline.first.toInt() - width / 2, baseline.second.toInt() - height, width, height)
            }
        }
    }

    private const val CATAPULT_RENDER_WIDTH = WorldScale.CATAPULT_RENDER_WIDTH
    private const val CATAPULT_RENDER_HEIGHT = 95f
    private val CATAPULT_WORLD_WIDTH = CATAPULT_RENDER_WIDTH / LEGACY_VIEWPORT_WIDTH * WorldDimensions.WIDTH
    private val CATAPULT_WORLD_HEIGHT = CATAPULT_RENDER_HEIGHT / LEGACY_VIEWPORT_HEIGHT * WorldDimensions.HEIGHT
    private val CATAPULT_BASELINE_OFFSET_WORLD =
        FORTRESS_RENDER_HEIGHT * 0.40f / LEGACY_VIEWPORT_HEIGHT * WorldDimensions.HEIGHT
}

data class BiomeVisuals(
    val background: BufferedImage?,
    val soil: BufferedImage?,
    val cap: BufferedImage?,
    val leftFortress: BufferedImage?,
    val rightFortress: BufferedImage?,
    val leftCatapult: BufferedImage?,
    val rightCatapult: BufferedImage?,
)
