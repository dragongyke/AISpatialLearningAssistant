# Academic Library Environment

The Full Space learning environment is an original Blender-authored collegiate library:
two levels of bookshelves, a timber gallery, a central stone arch and a deeper library
passage. The opposite wall is now an open learning bay, with four reading tables and
chairs along the side walls. The central foreground stays clear. Video, subtitles,
panels, the fitted blackboard and the interactive crystal orb are separate application
entities; the existing VST switch is unchanged.

## Assets

| Path | Purpose |
| --- | --- |
| `art/academic-library/academic-library.blend` | Editable geometry, source materials, lighting and cameras |
| `art/academic-library/reference/approved-library.png` | Approved AI-generated concept, not a runtime screenshot |
| `art/academic-library/textures/oak-albedo.png` | AI-generated wood albedo used by the source material |
| `art/academic-library/preview-front.png` | Actual exported-model render, without application UI |
| `art/academic-library/preview-editor.png` | Actual PICO Spatial Editor render, without application UI |
| `art/academic-library/preview-learning.png` | Empty learning bay reserved for the runtime blackboard |
| `art/academic-library/preview-detail.png` | Exported bookshelf detail |
| `src/main/res3d/AcademicLectureHall/AcademicLectureHall.spatialproject` | PICO Spatial Editor project |
| `src/main/res3d/AcademicLectureHall/Sources/Scenes/AcademicLectureHall.usda` | Runtime scene entry point |
| `src/main/res3d/AcademicLectureHall/Sources/Models/AcademicLibrary/` | Exported geometry and baked texture dependencies |

The geometry and materials are authored for this project; no third-party downloadable
room model is included. Image generation is used for the concept and oak albedo.
The room uses meters. Blender +Y faces into the library and +Z is up; exported assets
use PICO +Y up and -Z forward. The floor is at Y=0 in PICO coordinates.

The exported environment contains 354,379 triangles and 35 mesh/material groups with
35 baked textures: four 4096px, three 2048px, 22 1024px and six 512px. Nearby books
have modeled covers, paper edges and spine details; the floor is divided into 20
world-aligned texture regions so one room-wide atlas no longer limits its clarity.
These are desktop asset budgets, not a headset frame-rate or texture-memory guarantee.

The model keeps its original coordinate system. Its rear wall's inner plane is PICO
Z=+7.295 m. `LearningStagePoseResolver` rotates the room 180 degrees and places that
wall 10 cm behind the default video. The clear mounting bay is X=-2.2..2.2 m,
Y=0.2..4.05 m. `LearningBlackboard` fits the actual video/subtitle/handle size at
runtime; do not bake a second board into the room. The crystal uses native SDK
geometry, animation and a spherical collider, not an AttachmentPanel.

## Rebuild From Blender

Run these commands from the repository root, using your installed Blender executable.
The scripts are tested with Blender 5.2.1 LTS. `build/` holds disposable exports and logs;
the editable source under `art/` is not overwritten by these commands.

```powershell
$blender = 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe'
$output = 'lecture-hall-editor-asset/build/blender-library'
& $blender --background --factory-startup --python-exit-code 1 `
  --python scripts/blender/build_academic_library.py -- --output-dir $output `
  --wood lecture-hall-editor-asset/art/academic-library/textures/oak-albedo.png --bake
& $blender --background --factory-startup --python-exit-code 1 `
  --python scripts/blender/verify_academic_library.py -- --output-dir $output
& $blender --background --factory-startup --python-exit-code 1 `
  --python scripts/blender/export_library_usd.py -- --glb "$output/academic-library.glb" `
  --output-dir "$output/usd"
```

Inspect the front, learning-wall, bookshelf, furniture and floor-detail renders before
replacing runtime assets.
The verifier checks mesh/triangle budgets, units, UVs, embedded textures, unlit GLB
materials, clear space around the default UI, and a fresh GLB import/render comparison.
Lighting is baked in floating point and tone-mapped once into portable textures.
USD uses textured emission with black diffuse and zero specular, since USD Preview
Surface does not define glTF's `KHR_materials_unlit` extension.

After review, copy `AcademicLibrary.usdc` and its `textures/` directory from `$output/usd`
to `Sources/Models/AcademicLibrary/` inside the Editor project. Keep the scene entry
name `AcademicLectureHall` so the existing Kotlin loader continues to resolve it.
The generated USDZ is a standalone import/export deliverable; the project uses USDC
plus relative textures to avoid duplicating those textures inside another archive.

## Package For The Application

```powershell
$root = (Get-Location).Path
$project = "$root/lecture-hall-editor-asset/src/main/res3d/AcademicLectureHall"
pico-cli editor pack --workspace-root $root `
  --project "$project/AcademicLectureHall.spatialproject" `
  --scene-files "$project/Sources/Scenes/AcademicLectureHall.usda" `
  --target-app-root $root --bundle-name lecture-hall-editor-asset --format json
```

Alternatively, explicitly enable the Gradle asset module. This requires matching
PICO Editor 6.0 tools. Ordinary APK builds leave this module disabled and use the
checked-in bundle, so they do not require a local Editor installation.

```powershell
.\gradlew.bat -PbuildSceneAssets=true :lecture-hall-editor-asset:assembleDebug
Copy-Item lecture-hall-editor-asset\build\spatial\bundle\lecture-hall-editor-asset.bundle app\src\main\assets\lecture-hall-editor-asset.bundle
```

With an already-running managed Editor, its native `pack_project` tool can package
the saved scene directly. Use the same scene file and explicit application bundle
path shown above. The tested Editor installation can render and pack this project,
but its Python context is unavailable, so the CLI wrapper's automatic `save_scene`
step fails. Native packing of the saved USD files does not require that step.

The application loads `app/src/main/assets/lecture-hall-editor-asset.bundle` through
`LectureHallAssetBundle.kt`. Blender validation does not replace Editor and headset
checks: confirm material appearance, scale, clipping, load time, frame rate, and VST
switching on the target PICO OS 6 device after each asset replacement.
