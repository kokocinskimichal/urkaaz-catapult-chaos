# Urkaaaz web map editor

Local React/Vite editor for the existing `MapDefinition` JSON contract. The
Canvas is a world-space view of the 7200 × 4800 map; terrain is edited by
dragging its contour nodes and exported through the shared campaign map JSON
contract. Locking terrain disables further contour edits and reveals the
terrain cap.

```sh
npm install
npm run dev
npm run test
npm run build
```

Drafts are autosaved in `localStorage` under a map-editor-specific key. Draft
autosave never replaces an exported file. Import strictly rejects unsupported
fields and schema versions. The Kotlin validator remains authoritative; the
browser reports interactive and contract checks before export.

The biome selector switches the background, soil, terrain cap, and available
decoration assets. After terrain is locked, the right-hand palette allows
dragging Trees, Rocks, Props, and buried Relics onto the map. Trees, Rocks,
and Props can be positioned freely within the map, including above or below
the terrain and on slopes; Relics must remain inside the soil. The preview
layers regular decorations below the terrain cap and Relics above it. Regular
decoration sprites use the same 94-pixel base width and image aspect ratio as
the Android game renderer. Select and drag a
placed decoration to move it; drag a selected object's corner to scale and
rotate around its center, or use the panel for precise values. Sunken Marshes
currently reuses Green Frontier soil, cap, and
decoration assets. Fortress and catapult sprites are shared across biomes.
While terrain is editable, the foundation controls adjust each fortress and
its catapult together. The protected foundation samples follow the new height,
and nearby terrain samples ease into it using the contour-node falloff.
The whole scene can also be raised or lowered in world units: terrain samples,
fortresses, catapults, protected zones, and placed assets move together while
the background remains fixed. Movement is rejected if it would push map
coordinates or a placed Relic footprint outside the world.
The palette includes all imported legacy decoration variants: Green Frontier
has 6 trees, 6 rocks, and 6 props; Frostbound has 6 trees, 6 rocks, and 12
props; Copperwood has 8 trees, 6 rocks, and 6 props; Ashen March has 6 trees,
6 rocks, and 8 props. Sunken Marshes currently shares the Green Frontier set.

Development and production builds sync the shared terrain asset index with the
images, plus the Relic overlays registered by the Android asset catalog. The
editor derives its preview catapults from fortress placements and validates
decoration keys against the shared catalog; neither editor-only data is
written to map JSON.

The JSON round trip preserves initial camera anchor/zoom and optional
`generationMetadata.authoringVersion`; older maps without camera fields retain
the player-fortress framing and 1× zoom.
