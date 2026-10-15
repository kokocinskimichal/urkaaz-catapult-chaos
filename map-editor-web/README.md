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
dragging Trees, Rocks, Props, and buried Relics onto the map. Grounded
decorations can only be placed on locally flat terrain; Relics are placed
inside the soil. The preview layers grounded decorations below the terrain cap
and Relics above it. Sunken Marshes currently reuses Green Frontier soil, cap,
and decoration assets. Fortress and catapult sprites are shared across biomes.

Development and production builds sync the shared terrain asset index with the
images, plus the Relic overlays registered by the Android asset catalog. The
editor derives its preview catapults from fortress placements and validates
decoration keys against the shared catalog; neither editor-only data is
written to map JSON.

The JSON round trip preserves initial camera anchor/zoom and optional
`generationMetadata.authoringVersion`; older maps without camera fields retain
the player-fortress framing and 1× zoom.
