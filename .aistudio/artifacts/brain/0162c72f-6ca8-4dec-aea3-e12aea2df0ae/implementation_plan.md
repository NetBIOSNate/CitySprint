# Implementation Plan: Street Turns, Construction Roadblocks, Pre-Loaded City Blocks, Real-Life Proportions, Pedestrians & Native 120 FPS

## 1. Overview & Goals
This update upgrades the 3D city runner with interactive street-level navigation, realistic human-relative world scale, lively sidewalk crowds, and ultra-fluid 120Hz performance:

1. **Hold Left/Right to Change Street Direction**: Press and hold the left or right side of the screen to queue and execute a 90° turn onto an intersecting cross-street or around a construction detour, while preserving quick swipes for lane switching, jumping, and sliding.
2. **Full-Street Construction Roadblocks**: Periodically spawn full-width 3D construction zones at intersections (orange-and-white striped barricades, jersey barriers, flashing amber beacons, and directional detour signs) that span all three lanes and require holding left or right to turn onto a cross-street—crashing the player if they run straight into the roadblock without turning (unless protected by an active Shield, which smashes through the barrier).
3. **Pre-Loaded Whole City Block & Smooth Turn Transitions**: Render the entire intersecting city block ahead of time—including the perpendicular left and right cross-street roadways, corner buildings, crosswalks, streetlamps, and receding side-street facades—so turning 90° onto a new street smoothly rotates the camera and transitions into the side street with zero pop-in.
4. **1:1 Real-Life Proportional Scale Calibration**: Anchor all obstacle, vehicle, traffic cone, and street-tree measurements directly to the player character's height (`92.dp` base ≈ `1.75m` human height) using unified perspective projection so nothing appears oversized or undersized.
5. **Animated Sidewalk Pedestrians**: Populate both left and right sidewalks with 3D pedestrians walking along the pavement and reacting (turning heads, waving, or stepping back in surprise) as the runner sprints past.
6. **Native 120 FPS High-Refresh-Rate Engine**: Configure the Android window/display mode and surface frame rate for native 120Hz output, and replace fixed-duration UI tweens in the active 3D viewport with sub-frame delta-time (`withFrameNanos`) interpolation.

---

## 2. Architecture & Component Design

### A. Hold Left / Right Gesture & Street Turn System (`MainActivity.kt`)
- **Dual-Mode Touch Input**:
  - Upgrade the game canvas `pointerInput` block to track both **quick swipes** (horizontal lane switches, vertical jump/slide) and **press-and-hold** on the left half (`x < screenWidth * 0.5f`) or right half (`x >= screenWidth * 0.5f`) of the screen.
  - Holding for `>= 140ms` activates a directional turn intent (`holdingTurnDirection = -1` for Left, `+1` for Right) with an on-screen HUD turn indicator showing the queued turn direction.
- **Intersection & Detour Turn Execution**:
  - Cross-street intersections occur at regular city-block intervals (`zIndex % 6 == 0`).
  - When the player is holding Left or Right as they enter an open intersection window (or a construction roadblock detour), `GameViewModel` triggers a smooth 90° street turn transition (`turnTransitionProgress` from `0f` to `1f`), seamlessly transferring the player onto the selected cross-street and resetting forward lane alignment on the new block.

### B. Full-Street Construction Roadblocks (`MainActivity.kt`, `ObstacleRenderer.kt`)
- **Roadblock Spawning & Mechanics**:
  - At designated city block intersections, spawn a full-street construction roadblock spanning all 3 lanes with an open cross-street detour (left, right, or both).
  - Display clear 3D visual warnings ahead of the intersection: illuminated directional detour arrow signs, striped construction sawhorses, concrete jersey barriers, and blinking hazard lights across all three lanes.
- **Collision & Shield Resolution**:
  - If the player holds a valid turn direction (`holdingTurnDirection`) matching an open cross-street before reaching the barrier plane, the runner smoothly banks and turns onto the side street, awarding bonus detour points.
  - If the player reaches the roadblock without holding to turn:
    - **With Active Shield**: Consumes the shield, triggers a barrier-shatter particle burst, and lets the runner continue.
    - **Without Shield**: Triggers a collision/crash and ends the run.

### C. Pre-Loaded City Block & Smooth 90° Turn Rendering (`StreetAndBuildingRenderer.kt`)
- **Complete Intersecting Block Geometry**:
  - Extend `drawRealisticStreetAndBuildings` so every intersection slice (`zIndex % 6 == 0`) pre-renders the full perpendicular cross-street corridors extending left and right—including cross-street asphalt lanes, dashed centerlines, corner curbs, crosswalk stripes, and recessed side-street building facades along the perpendicular block.
- **Continuous Turn Camera Interpolation**:
  - Drive the vanishing point (`vanishingX`), road spline curvature, and building perspective shear directly from `turnTransitionProgress` and `activeTurnDirection` at 120Hz so the cross-street smoothly swings into the main forward heading without sudden geometry swaps or clipping.

### D. 1:1 Real-Life Scale Calibration (`ObstacleRenderer.kt`, `StreetAndBuildingRenderer.kt`)
- **Unified Perspective & Human-Relative Reference (`1.75m = 92.dp * scale`)**:
  - Align obstacle perspective scaling with the runner's depth scale (`scale = lerp(0.08f, 1.0f, yRatio)`) so objects grow at the exact same rate as the street and matches the runner (`h = 92.dp.toPx() * scale` at `yRatio = 0.8f`).
  - Calibrate every object's physical width and height relative to the `1.75m` runner (`runnerH = 92.dp.toPx() * scale`):
    - **Traffic Cones**: `0.42f * runnerH` (~0.75m knee-to-mid-thigh height) with proportional square rubber base (`0.28f * runnerH`)—fixing oversized cones.
    - **Street Trees**: Convert raw pixel values (`95f`, `34f`) to density-independent `.dp.toPx()` scaled to `2.4x–2.8x * runnerH` (~4.2m–5.0m tall with full 3D layered canopy and trunk)—fixing tiny trees on high-DPI displays.
    - **Small Street Props** (Bonsai, Roman Urn, Trash Can, Cafe Table): `0.45f–0.58f * runnerH` (~0.8m–1.0m waist height).
    - **Medium Obstacles** (Construction Barrier, Park Bench, Rickshaw, Stone Lion, Streetlamp base): `0.55f–0.95f * runnerH` (~1.0m–1.65m).
    - **Tall Booths & Stands** (Hotdog Stand, Newspaper Kiosk, Vending Machine, Red Phone Box, Obelisk): `1.10f–1.35f * runnerH` (~1.9m–2.35m, slightly taller than the runner).
    - **Vehicles**:
      - **Taxis**: Roof height at `0.86f * runnerH` (~1.5m chest/shoulder height), realistic chassis width and length.
      - **Double-Decker Buses**: Roof height at `2.25f * runnerH` (~3.95m tall, towering realistically over the runner).

### E. Sidewalk Pedestrians (`StreetAndBuildingRenderer.kt`)
- **3D Sidewalk Crowd System**:
  - Deterministically spawn and animate pedestrians along the left and right sidewalks (`drawSidewalkPedestrian`) outside the road curbs so they never obstruct the runner's 3 road lanes.
  - Scale pedestrians to `0.92f–1.04f * runnerH` (natural adult height variation) at matching depth `zRatio`.
  - Render articulated 3D torso, head, hair/hat, swinging arms, and walking legs with varied clothing palettes suited to each city, plus proximity reactions (turning toward the street or raising an arm when the runner passes nearby).

### F. Native 120 FPS High-Refresh-Rate Support (`MainActivity.kt`)
- **Display Mode & Window Frame Rate Configuration**:
  - In `MainActivity.onCreate`, query `display.supportedModes` and select the highest refresh rate mode matching the native resolution (targeting 120Hz) via `window.attributes.preferredDisplayModeId` and `preferredRefreshRate = 120f`.
  - On API 30+, set `Surface.FRAME_RATE_COMPATIBILITY_DEFAULT` / `setFrameRate(120f, ...)` on the host window surface.
- **120Hz Frame-Synchronized Game Loop**:
  - Update `GameViewModel.update(dt)` to clamp `dt` cleanly for high-refresh intervals (`1/120s ≈ 8.33ms`) and advance player lane position, camera turn banking, pedestrian walk cycles, and obstacle motion smoothly every frame without 60Hz jitter.

---

## 3. Verification Plan
1. **Compilation Check**: Run `compile_applet` to verify all Kotlin files, Compose state flows, and Android display mode APIs compile cleanly with zero errors.
2. **Scale & Visual Audit**: Verify that traffic cones (~0.42x player height), street trees (~2.6x player height), pedestrians (~1.0x player height), taxis (~0.86x player height), and double-decker buses (~2.25x player height) maintain consistent proportions across all depth slices.
3. **Gameplay Flow Check**: Verify hold-left/hold-right turns at intersections and full-street construction roadblocks, including shield-break vs. crash behavior when running straight into a roadblock.
