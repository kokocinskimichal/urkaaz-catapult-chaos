# Urkaaaz web map editor

Local React/Vite editor for the existing `MapDefinition` JSON contract. The
Canvas is a world-space view of the 7200 × 4800 map; terrain is edited as a
profile, while legacy `terrainTiles` are retained during import/export.

```sh
npm install
npm run dev
npm run test
npm run build
```

Drafts are autosaved in `localStorage` under a map-editor-specific key. Draft
autosave never replaces an exported file. Import rejects future schema versions
and preserves unknown top-level fields on export where possible. The Kotlin
validator remains authoritative; the browser reports interactive and contract
checks before export.
