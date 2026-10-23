# BattlefieldView refactor

Project: `workspace/urkaaz`  
Scope: stages 1–5 from the approved plan  
Status: in progress

## Progress

| Stage | Scope | Status |
|---|---|---|
| 1 | Baseline tests and contracts | Complete |
| 2 | `BattlefieldAssetCatalog` and theme model | Complete |
| 3 | `BattlefieldCameraController` | Complete |
| 4 | `AimGestureController` | Complete |
| 5 | Battlefield renderer split | Complete |

## Validation

- Baseline build before refactor: passed (`./gradlew test assembleDebug --quiet`)
- Latest validation: stages 1–4 passed (`./gradlew :game-application:test :android-app:testDebugUnitTest :android-app:assembleDebug --quiet`)
- Stage 5 now delegates the complete draw pass to `BattlefieldRenderer`; `BattlefieldView`
  retains input/state coordination while rendering primitives remain injectable layer
  operations, preserving the current visual order and behavior.
- Latest validation after stage 5: `:game-application:test`,
  `:android-app:testDebugUnitTest`, `:android-app:assembleDebug`, and `git diff --check`
  passed.

## Notes

- Existing battlefield behavior must remain unchanged.
- Current local changes from the previous visual pass were committed before this refactor.
