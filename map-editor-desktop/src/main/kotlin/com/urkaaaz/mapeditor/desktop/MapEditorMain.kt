package com.urkaaaz.mapeditor.desktop

import com.urkaaaz.assets.AssetCatalog
import com.urkaaaz.assets.AssetCategory
import com.urkaaaz.contracts.*
import com.urkaaaz.mapeditor.domain.*
import com.urkaaaz.mapeditor.format.MapDocumentCodec
import com.urkaaaz.mapeditor.rendering.BiomeVisuals
import com.urkaaaz.mapeditor.rendering.MapRenderer
import com.urkaaaz.mapeditor.rendering.ViewportTransform
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GridLayout
import java.awt.event.*
import java.awt.image.BufferedImage
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.random.Random
import kotlin.io.path.Path
import kotlin.math.abs
import kotlin.math.roundToInt

fun main() {
    SwingUtilities.invokeLater { MapEditorFrame().isVisible = true }
}

private class MapEditorFrame : JFrame("Edytor map Urkaaaz") {
    private var history = EditorHistory(EditorDocumentFactory.newMap("untitled", "green_frontier"))
    private var currentPath: Path? = null
    private var selectedObjectId: String? = null
    private var selectedAssetKey: String? = null
    private val assetCatalog = AssetCatalog()
    private val visualCatalog = BiomeVisualCatalog()
    private val canvas = MapCanvas()
    private val status = JLabel()
    private val biomeSelector = JComboBox<String>()
    private val tilePresetSelector = JComboBox<TerrainTilePreset>()
    private val tilePalette = JPanel()
    private val assetPalette = JPanel()
    private val propertiesPanel = JPanel()
    private var updatingBiome = false

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(1280, 760)
        layout = BorderLayout()
        add(toolbar(), BorderLayout.NORTH)
        add(sidebar(), BorderLayout.WEST)
        add(canvas, BorderLayout.CENTER)
        add(properties(), BorderLayout.EAST)
        add(status, BorderLayout.SOUTH)
        biomeSelector.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component =
                super.getListCellRendererComponent(
                    list,
                    if (value is String) biomeLabel(value) else value,
                    index,
                    isSelected,
                    cellHasFocus,
                )
        }
        biomeSelector.addActionListener {
            if (!updatingBiome && biomeSelector.selectedItem is String) {
                val biome = biomeSelector.selectedItem as String
                if (biome != history.current.map.biome && history.current.map.objects.isEmpty()) {
                    history = EditorHistory(EditorDocumentFactory.newMap(history.current.map.levelId, biome))
                    autosave()
                    refresh()
                }
            }
        }
        refresh()
        recoverAutosave()
    }

    private fun toolbar() = JToolBar().apply {
        isFloatable = false
        add(button("Nowa") { newDocument() })
        add(button("Otwórz") { openDocument() })
        add(button("Zapisz szkic") { saveDraft() })
        add(button("Waliduj") { validateDocument() })
        add(button("Eksportuj") { exportDocument() })
        addSeparator()
        add(button("Cofnij") { history.undo(); autosave(); refresh() })
        add(button("Ponów") { history.redo(); autosave(); refresh() })
        addSeparator()
        add(button("Zoom +") { canvas.zoomBy(1.25); canvas.repaint() })
        add(button("Zoom −") { canvas.zoomBy(0.8); canvas.repaint() })
        add(button("Dopasuj") { canvas.resetZoom(); canvas.repaint() })
        addSeparator()
        add(status)
    }

    private fun sidebar() = JPanel(BorderLayout()).apply {
        preferredSize = Dimension(290, 0)
        val controls = JPanel(GridLayout(0, 1, 4, 4))
        controls.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        controls.add(JLabel("Edycja mapy"))
        biomeSelector.preferredSize = Dimension(250, 28)
        controls.add(biomeSelector)
        controls.add(button("Zablokuj teren") { execute(EditorCommand.LockTerrain) })
        controls.add(button("Wygładź teren") { execute(EditorCommand.SmoothTerrain()) })
        controls.add(button("Generuj losowo") {
            execute(EditorCommand.GenerateRandomTerrain(Random.nextLong()))
        })
        controls.add(JLabel("Predefiniowane kafelki terenu:"))
        tilePresetSelector.preferredSize = Dimension(250, 28)
        controls.add(tilePresetSelector)
        tilePalette.layout = GridLayout(0, 1, 2, 2)
        controls.add(tilePalette)
        controls.add(JLabel("Dekoracje (wybierz i kliknij mapę):"))
        assetPalette.layout = GridLayout(0, 1, 2, 2)
        controls.add(assetPalette)
        add(JScrollPane(controls).apply {
            preferredSize = Dimension(290, 500)
            border = BorderFactory.createEmptyBorder()
        }, BorderLayout.NORTH)
        add(JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            text = "Przebieg pracy:\n1. Wybierz biom i edytuj teren\n2. Zablokuj teren\n3. Przeciągnij kafelek na mapę lub wybierz dekorację i kliknij mapę\n4. Edytuj właściwości zaznaczonego obiektu\n5. Waliduj i eksportuj\n\nAutosave: ${autosavePath()}"
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        }, BorderLayout.CENTER)
    }

    private fun properties() = propertiesPanel.apply {
        preferredSize = Dimension(250, 0)
        layout = BorderLayout()
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }

    private fun newDocument() {
        val biome = (biomeSelector.selectedItem as? String)
            ?: assetCatalog.availableBiomes().firstOrNull()
            ?: "green_frontier"
        val mapId = JOptionPane.showInputDialog(this, "Id mapy", "untitled")?.trim().orEmpty()
        if (mapId.isNotBlank()) {
            history = EditorHistory(EditorDocumentFactory.newMap(mapId, biome))
            currentPath = null
            selectedObjectId = null
            autosave()
            refresh()
        }
    }

    private fun openDocument() {
        JFileChooser().also { chooser ->
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                runCatching {
                    history = EditorHistory(MapDocumentCodec.read(chooser.selectedFile.toPath()))
                    currentPath = chooser.selectedFile.toPath()
                    selectedObjectId = null
                    refresh()
                }.onFailure { showError(it.message ?: "Nie można otworzyć mapy") }
            }
        }
    }

    private fun saveDraft() {
        val path = currentPath ?: JFileChooser().let { chooser ->
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
            chooser.selectedFile.toPath()
        }
        runCatching {
            MapDocumentCodec.writeDraft(Path("${path}.draft.json"), history.current)
            currentPath = path
            status.text = "Zapisano szkic: ${path.fileName}.draft.json"
        }.onFailure { showError(it.message ?: "Nie można zapisać szkicu") }
    }

    private fun validateDocument() {
        val result = EditorValidation.validateForEditor(history.current.map)
        if (result.isValid) JOptionPane.showMessageDialog(this, "Mapa jest poprawna.", "Walidacja", JOptionPane.INFORMATION_MESSAGE)
        else showError(result.errors.joinToString("\n") { "${it.path}: ${it.message}" })
    }

    private fun exportDocument() {
        when (val result = history.execute(EditorCommand.MarkReadyToExport)) {
            is EditorResult.Failure -> showResult(result)
            is EditorResult.Success -> {
                refresh()
                JFileChooser().also { chooser ->
                    if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                        runCatching { MapDocumentCodec.writeFinal(chooser.selectedFile.toPath(), history.current) }
                            .onSuccess { status.text = "Wyeksportowano ${chooser.selectedFile.name}" }
                            .onFailure { showError(it.message ?: "Nie można wyeksportować mapy") }
                    }
                }
            }
        }
    }

    private fun execute(command: EditorCommand) {
        val result = history.execute(command)
        if (result is EditorResult.Success && command is EditorCommand.PlaceMapObject) {
            selectedObjectId = result.document.map.objects.lastOrNull()?.id
            selectedAssetKey = null
        }
        showResult(result)
        if (result is EditorResult.Success) autosave()
    }

    private fun showResult(result: EditorResult) {
        if (result is EditorResult.Failure) showError("${result.error.code}: ${result.error.message}")
        refresh()
    }

    private fun showError(message: String) =
        JOptionPane.showMessageDialog(this, message, "Edytor map", JOptionPane.ERROR_MESSAGE)

    private fun refresh() {
        val available = assetCatalog.availableBiomes().toList().ifEmpty { listOf("green_frontier") }
        updatingBiome = true
        biomeSelector.removeAllItems()
        available.forEach(biomeSelector::addItem)
        biomeSelector.selectedItem = history.current.map.biome
        updatingBiome = false
        biomeSelector.isEnabled = history.current.map.objects.isEmpty() && history.current.phase == EditorPhase.TERRAIN_EDITING
        tilePresetSelector.isEnabled = history.current.phase == EditorPhase.TERRAIN_EDITING
        val selectedTilePresetId = (tilePresetSelector.selectedItem as? TerrainTilePreset)?.id ?: "flat"
        tilePresetSelector.removeAllItems()
        TerrainTileCatalog.presets.forEach(tilePresetSelector::addItem)
        tilePresetSelector.selectedItem = TerrainTileCatalog.byId(selectedTilePresetId)
        rebuildTilePalette()
        rebuildPalette()
        rebuildProperties()
        canvas.repaint()
        status.text = "Biom: ${biomeLabel(history.current.map.biome)}  Etap: ${phaseLabel(history.current.phase)}  Wersja: ${history.current.revision}"
    }

    private fun rebuildPalette() {
        assetPalette.removeAll()
        val assets = assetCatalog.assetsForBiome(history.current.map.biome)
            .filter { it.category in setOf(AssetCategory.TREE, AssetCategory.ROCK, AssetCategory.PROP, AssetCategory.RELIC) }
        if (assets.isEmpty()) assetPalette.add(JLabel("Brak assetów dla tego biomu"))
        assets.forEach { asset ->
            assetPalette.add(button(asset.fileName) {
                selectedAssetKey = asset.fileName
                status.text = "Wybrano asset: ${asset.fileName}"
            })
        }
        assetPalette.revalidate()
        assetPalette.repaint()
    }

    private fun rebuildTilePalette() {
        tilePalette.removeAll()
        TerrainTileCatalog.presets.forEach { preset ->
            val tileButton = JButton(preset.displayName)
            tileButton.toolTipText = "Przeciągnij ${preset.displayName} na mapę"
            tileButton.addActionListener {
                tilePresetSelector.selectedItem = preset
                status.text = "Wybrano kafelek: ${preset.displayName}"
            }
            tileButton.transferHandler = object : TransferHandler() {
                override fun createTransferable(component: JComponent) = StringSelection(preset.id)
                override fun getSourceActions(component: JComponent) = COPY
            }
            tileButton.addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseDragged(event: MouseEvent) {
                    tileButton.transferHandler.exportAsDrag(tileButton, event, TransferHandler.COPY)
                }
            })
            tilePalette.add(tileButton)
        }
        tilePalette.revalidate()
        tilePalette.repaint()
    }

    private fun rebuildProperties() {
        propertiesPanel.removeAll()
        val selected = history.current.map.objects.firstOrNull { it.id == selectedObjectId }
        if (selected == null) {
            propertiesPanel.add(JLabel("Nie wybrano obiektu"), BorderLayout.NORTH)
        } else {
            val fields = listOf(
                "X" to JTextField(selected.x.toString()),
                "Y" to JTextField(selected.y.toString()),
                "Scale" to JTextField(selected.scale.toString()),
                "Rotation" to JTextField(selected.rotationDegrees.toString()),
            )
            val form = JPanel(GridLayout(fields.size + 2, 2, 4, 4))
            form.add(JLabel("ID")); form.add(JLabel(selected.id))
            fields.forEach { (label, field) -> form.add(JLabel(label)); form.add(field) }
            form.add(button("Apply") {
                val values = fields.map { it.second.text.toFloatOrNull() }
                if (values.any { it == null }) showError("Właściwości muszą być liczbami")
                else {
                    execute(EditorCommand.MoveMapObject(selected.id, values[0]!!, values[1]!!))
                    execute(EditorCommand.ScaleMapObject(selected.id, values[2]!!))
                    execute(EditorCommand.RotateMapObject(selected.id, values[3]!!))
                }
            })
            form.add(button("Remove") { execute(EditorCommand.RemoveMapObject(selected.id)); selectedObjectId = null })
            propertiesPanel.add(form, BorderLayout.NORTH)
        }
        propertiesPanel.revalidate()
        propertiesPanel.repaint()
    }

    private fun autosave() {
        runCatching { MapDocumentCodec.writeDraft(autosavePath(), history.current) }
            .onFailure { status.text = "Autosave failed: ${it.message}" }
    }

    private fun autosavePath(): Path = Path(
        System.getProperty("user.home"),
        ".urkaaz",
        "map-editor",
        "${history.current.map.levelId}.draft.json",
    )

    private fun recoverAutosave() {
        val path = autosavePath()
        if (!java.nio.file.Files.exists(path)) return
        val recover = JOptionPane.showConfirmDialog(this, "Odzyskać autosave mapy ${history.current.map.levelId}?", "Edytor map", JOptionPane.YES_NO_OPTION)
        if (recover == JOptionPane.YES_OPTION) {
            runCatching { history = EditorHistory(MapDocumentCodec.read(path)); refresh() }
                .onFailure { showError("Odzyskiwanie autosave nie powiodło się: ${it.message}") }
        }
    }

    private fun button(label: String, action: () -> Unit) = JButton(label).apply { addActionListener { action() } }

    private fun biomeLabel(biome: String): String = when (biome) {
        "green_frontier" -> "Zielona Granica"
        "frost" -> "Mroźna Przełęcz"
        "copperwood" -> "Miedziany Las"
        "ashen_march" -> "Popielne Marchie"
        else -> biome
    }

    private fun phaseLabel(phase: EditorPhase): String = when (phase) {
        EditorPhase.TERRAIN_EDITING -> "edycja terenu"
        EditorPhase.TERRAIN_LOCKED -> "teren zablokowany"
        EditorPhase.DECORATION_EDITING -> "edycja dekoracji"
        EditorPhase.READY_TO_EXPORT -> "gotowe do eksportu"
    }

    private inner class MapCanvas : JPanel() {
        private val transform = ViewportTransform()
        private var draggingObjectId: String? = null

        fun zoomBy(factor: Double) {
            transform.zoomBy(factor, width, height)
        }

        fun resetZoom() {
            transform.resetFit()
        }

        init {
            background = java.awt.Color(30, 32, 36)
            transferHandler = object : TransferHandler() {
                override fun canImport(support: TransferSupport): Boolean =
                    support.isDrop && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)

                override fun importData(support: TransferSupport): Boolean {
                    if (!canImport(support)) return false
                    val presetId = support.transferable.getTransferData(
                        java.awt.datatransfer.DataFlavor.stringFlavor,
                    ) as String
                    val point = support.dropLocation as? TransferHandler.DropLocation ?: return false
                    val world = transform.screenToWorld(point.dropPoint.x.toDouble(), point.dropPoint.y.toDouble())
                    placeTile(world.first, world.second, presetId)
                    return true
                }
            }
            val listener = object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    val world = transform.screenToWorld(event.x.toDouble(), event.y.toDouble())
                    if (history.current.phase != EditorPhase.TERRAIN_EDITING) {
                        draggingObjectId = hitObject(world.first, world.second)
                        selectedObjectId = draggingObjectId
                        refresh()
                    }
                }

                override fun mouseDragged(event: MouseEvent) {
                    val id = draggingObjectId ?: return
                    val world = transform.screenToWorld(event.x.toDouble(), event.y.toDouble())
                    val result = history.execute(EditorCommand.MoveMapObject(id, world.first, terrainHeightAt(world.first)))
                    if (result is EditorResult.Success) {
                        autosave()
                        refresh()
                    }
                }

                override fun mouseReleased(event: MouseEvent) {
                    draggingObjectId = null
                }

                override fun mouseClicked(event: MouseEvent) {
                    if (event.button != MouseEvent.BUTTON1) return
                    val world = transform.screenToWorld(event.x.toDouble(), event.y.toDouble())
                    if (history.current.phase == EditorPhase.TERRAIN_EDITING) {
                        placeTile(world.first, world.second, (tilePresetSelector.selectedItem as? TerrainTilePreset)?.id ?: "flat")
                    } else if (selectedObjectId == null && selectedAssetKey != null) {
                        execute(EditorCommand.PlaceMapObject(selectedAssetKey!!, world.first, terrainHeightAt(world.first)))
                    } else {
                        selectedObjectId = hitObject(world.first, world.second)
                        refresh()
                    }
                }
            }
            addMouseListener(listener)
            addMouseMotionListener(listener)
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            transform.fitTo(width, height, history.current.map.worldWidth, history.current.map.worldHeight)
            MapRenderer.render(
                graphics as java.awt.Graphics2D,
                history.current,
                transform,
                visualCatalog.visualsFor(history.current.map.biome),
            )
        }

        private fun placeTile(worldX: Float, worldY: Float, presetId: String) {
            val tileSize = TerrainTilePreset.DEFAULT_TILE_SIZE
            val x = (worldX / tileSize).roundToInt().coerceIn(
                0,
                (history.current.map.worldWidth / tileSize).toInt() - 1,
            )
            val terrainY = terrainHeightAt(x * tileSize)
            val y = (terrainY / tileSize).roundToInt()
            val preset = TerrainTileCatalog.byId(presetId)
            val tile = TerrainTilePlacement(
                tileId = "terrain-${history.current.map.biome}-${preset.id}-$x-$y",
                gridX = x,
                gridY = y,
                geometry = preset.geometry().copy(
                    heightSamples = preset.heightSamples.map { terrainY + it * tileSize },
                ),
            )
            execute(EditorCommand.PlaceTerrainTile(tile))
        }

        private fun hitObject(x: Float, y: Float): String? =
            history.current.map.objects.firstOrNull { abs(it.x - x) <= 100f && abs(it.y - y) <= 160f }?.id
    }

    private class BiomeVisualCatalog(
        private val classLoader: ClassLoader = BiomeVisualCatalog::class.java.classLoader,
    ) {
        fun visualsFor(biome: String): BiomeVisuals {
            val names = when (biome) {
                "green_frontier" -> VisualNames("background_green_frontier", "terrain_green_frontier_soil_00", "terrain_green_frontier_cap_00")
                "frost" -> VisualNames("background_frostbound_pass", "terrain_frostbound_soil_00", "terrain_frostbound_cap_00")
                "copperwood" -> VisualNames("background_copperwood", "terrain_copperwood_soil_00", "terrain_copperwood_cap_00")
                "ashen_march" -> VisualNames("background_ashen_march", "terrain_ashen_march_soil_00", "terrain_ashen_march_cap_00")
                else -> VisualNames("background_green_frontier", "terrain_green_frontier_soil_00", "terrain_green_frontier_cap_00")
            }
            return BiomeVisuals(
                background = load(names.background, "terrain/${biome}/"),
                soil = load(names.soil, "terrain/${biome}/"),
                cap = load(names.cap, "terrain/${biome}/"),
                leftFortress = load("fortress_chapter_01_left", "terrain/"),
                rightFortress = load("fortress_chapter_01_right", "terrain/"),
                leftCatapult = load("catapult_left", "terrain/"),
                rightCatapult = load("catapult_right", "terrain/"),
            )
        }

        private fun load(name: String, directory: String): BufferedImage? =
            classLoader.getResourceAsStream("$directory$name.png")?.use(ImageIO::read)

        private data class VisualNames(val background: String, val soil: String, val cap: String)
    }

    private fun terrainHeightAt(x: Float): Float {
        val samples = history.current.map.terrain.samples
        val left = samples.lastOrNull { it.x <= x } ?: samples.first()
        val right = samples.firstOrNull { it.x >= x } ?: samples.last()
        if (left.x == right.x) return left.height
        val ratio = (x - left.x) / (right.x - left.x)
        return left.height + (right.height - left.height) * ratio
    }
}
