# Notable: Fixed A4 Sheet + Fit-to-Screen / Pan & Zoom

- [x] Phase 0: Build-Time Toggle for Dual Mode
    - [x] Define `USE_A4_SHEET_MODE = false` (default) in `AppSettings.kt`
    - [ ] Create a helper/extension to query this globally (`isA4Mode`)
    - [ ] **Safety Net:** Ensure the code path for `USE_A4_SHEET_MODE = false` is an exact copy of the current infinite canvas logic.

- [x] Phase 1: Constants & Sheet Dimensions
    - [x] Define A4 dimensions (Points: ~2480×3508 @ 300dpi)
    - [x] Add `A4_WIDTH_DP` / `A4_HEIGHT_DP` to constants for consistent density scaling

- [ ] Phase 2: Sheet-to-Screen Mapper (Viewport Logic)
    - [ ] Define fixed A4 page coordinate system (independent of screen orientation/zoom)
    - [ ] Define SheetViewportState (zoom, translationX, translationY)
    - [ ] Implement fit-to-screen calculation
    - [ ] Center fitted page when it doesn't fill the viewport
    - [ ] Implement `pageToScreen()` transformation
    - [ ] Implement `screenToPage()` inverse transformation
    - [ ] Store all drawing/stroke coordinates in page coordinates
    - [ ] Implement pan limits based on scaled page dimensions
    - [ ] Disable/lock panning on an axis when the page is smaller than the viewport on that axis
    - [ ] Recalculate viewport when screen size/orientation changes without modifying page/stroke coordinates

- [ ] Phase 3: PageView & Canvas Constraints
    - [ ] Replace dynamic/infinite `height` logic with strict fixed `sheetWidth` and `sheetHeight` (only if `USE_A4_SHEET_MODE` is true)
    - [ ] Update `PageView` constructor/viewport logic to use A4 dimensions instead of `SCREEN_WIDTH`/`viewWidth`
    - [ ] Ensure `windowedBitmap` & `windowedCanvas` scale according to the mapped viewport, not full screen size

- [ ] Phase 4: Gesture & Input Mapping
    - [ ] Update `EditorGestureReceiver` to pan/zoom constrained within A4 bounds (no infinite scroll yet)
    - [ ] Map touch coordinates → A4 coordinate space for drawing & selection (invert the viewport matrix)

- [ ] Phase 5: Rendering & Backgrounds
    - [ ] Adapt `drawBg` / `drawLinedBg` etc. to render strictly within A4 bounds (no infinite tiling)
    - [ ] Remove/disable pagination lines (`drawPaginationLine`) until infinite scroll is added later
    - [ ] Verify stroke coordinates, image positions, and zoom levels all align with the fixed A4 grid
