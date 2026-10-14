# Urkaaaz web map editor

Local React/Vite editor for the existing `MapDefinition` JSON contract. The
Canvas is a world-space view of the 7200 × 4800 map; terrain is edited as a
profile and exported through the shared campaign map JSON contract.

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

Development and production builds sync the shared terrain asset index with the
images. The editor derives its preview catapults from fortress placements and
validates decoration keys against that index; neither editor-only data is
written to map JSON.
