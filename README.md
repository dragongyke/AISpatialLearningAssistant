# AI Spatial Learning Assistant

[English](README.md) | [简体中文](README_zh.md)

AI Spatial Learning Assistant is a PICO spatial reference application for focused,
AI-assisted video learning. Learners select a public course in Shared Space, continue in
a Full Space lecture environment, ask questions by voice, and retain a Markdown study
record tied to the video timeline.

![AI Spatial Learning Assistant](design/posters/ai-spatial-learning-wide-2560x1440.jpg)

## What the demo supports

### Shared Space

- Five directly playable Stanford Engineering Everywhere lectures.
- Category and text search, a horizontal 3.5-card course carousel, and lightweight
  source availability checks with retry.
- A separate history WindowContainer that tracks progress and links each course to its
  learning summary, last study time, and resume position. The catalog's **Learning history**
  button reopens it after it is closed.
- Direct launch into the learning catalog; the obsolete sample launcher is not part of
  this application.

### Full Space learning

- A central video screen with WebVTT subtitles driven by the same media timeline;
  learners can calibrate subtitle timing in 500 ms steps and reset it at any time.
- A learning outline on the left, an AI coach on the right, playback controls below the
  video, and a three-dimensional voice crystal orb within comfortable reach.
- Play/pause, seek, playback speed, automatic pause for questions, and intent-based
  resume commands such as "continue". Questions containing that word do not resume
  playback. Courses stop at the end instead of looping automatically.
- Gaze-responsive drag handles, smoothed coordinate conversion, per-panel movement
  lanes, release snapping, plus restore-layout and save-as-default actions.
  Only the bottom handles initiate panel dragging. Attachment UI and 3D hitbox input share
  one gesture capture, released on pointer up, cancellation, or leaving the Stage.
  Outline, coach, and playback panels use enlarged spatial scale and typography; their
  visible handles share each panel's layout and sit below its content, with drag hitboxes
  confined to the handles. Playback controls stay visible while auxiliary information scrolls.
  The video handle stays below the subtitles. The crystal orb has its own 3D hitbox:
  a brief pinch starts a question, while pinch-and-move repositions it without asking.
  Gaze highlighting and state-dependent light/core animations distinguish availability,
  listening, analysis, answering, and speech. No separate voice drag bar is required.
  A carved walnut and aged-brass pedestal matches the library; gathering light filaments
  and rotating runes make the waiting state distinct from idle and spoken replies.
- On entry and layout restore, the outline and coach centers align with the video. The
  toolbar stays below the subtitles; floor-aware placement keeps every panel and handle at
  least 12 cm above the floor, raising the layout only when needed.
- A project-authored Blender academic library enabled by default, with two-level
  bookshelves, an open stone archway, reading furniture, and baked lighting. The opposite
  wall becomes the default learning view; the video sits inside a wood-framed blackboard.
  The blackboard and room share the video anchor, while the video remains independently
  draggable. Restore layout docks the video back onto the board; the pre-blackboard saved
  layout is migrated once, retaining side-panel preferences. Editable assets and rebuild
  instructions are in [the environment module](lecture-hall-editor-asset/README.md).
- A **View reality** control that switches between the virtual environment and PICO VST
  passthrough.

### AI learning assistant

- Doubao Seed streams conversational tutor answers grounded in the current playhead,
  nearby transcript, course outline, and conversation history. Brief, deep-dive,
  example, and guided-question teaching modes follow the learner's language.
- Interactive answers disable model thinking and request Ark's `fast` service tier.
  If fast mode is not activated, the client retries with `auto` and remembers this
  for subsequent questions. The service can also return the `default` tier.
  Voice input starts a bounded background frame capture; only visual questions send
  that frame alongside the transcript in the same answer request.
- Doubao online speech recognition for Chinese and English questions.
- A bundled Chinese Vosk model as the offline ASR fallback; no model download is needed
  after installation.
- Doubao V3 bidirectional speech synthesis starts from an opening clause and supports
  barge-in: a new voice question cancels vision, text generation, queued speech, and
  current playback. Text continues into one synthesis session while audio plays through
  one buffered PCM track; later sentences no longer wait for earlier playback to finish.
- Stage UI reports ASR first partial/final, vision, LLM first-token, TTS first-audio,
  and submission-to-first-audible-reply latency. The latter starts at ASR final or typed
  submission; it excludes speaking time and ASR endpointing, and is not a latency SLA.
- Per-course Markdown notes update during learning, are reorganized by AI at course
  completion or Stage exit, and can be opened from history. The note window supports search, editing,
  copying, manual reorganization, and timestamp links back into the video.
- Live records and AI summaries have separate managed sections. In-app edits and
  existing unmarked notes are preserved; organizing notes does not block voice questions.

## Experience flow

1. Launch the application into the Shared Space course catalog.
2. Select a lecture; the Shared Space windows move behind the Full Space Stage.
3. Watch the video and briefly pinch the voice crystal whenever a concept is unclear.
4. The video can pause automatically while ASR and Doubao process the question; visual
   analysis is added only when the question refers to the current picture.
5. Read the streamed answer and hear completed sentences while the rest is still being
   generated; interrupt with another question or resume by button or voice intent.
6. Open a course's live or finalized Markdown note from history, then search, edit, copy,
   reorganize, or jump from a timestamp to the matching video moment.
7. Exit the Stage to restore the catalog and learning history windows.

## Technology

- Kotlin and Jetpack Compose
- PICO Spatial SDK BOM 6.0.0
- PICO Spatial UI WindowContainer, Stage, Entity, and asset bundle APIs
- PICO CypressMediaPlayer-based network playback
- Volcengine Ark Doubao Seed streaming chat and conditional vision APIs
- Volcengine Doubao streaming ASR and V3 bidirectional WebSocket TTS with AudioTrack playback
- Vosk Android offline fallback
- Koin dependency injection

## Project layout

| Path | Purpose |
| --- | --- |
| `app/` | Android application, spatial UI, playback, learning state, AI, speech, subtitles, history, and tests |
| `lecture-hall-editor-asset/` | Source and packaging module for the project-authored academic lecture hall |
| `design/` | Application icon, store posters, and detail-page artwork |

The Kotlin namespace remains `com.pico.spatial.sample.spatialvideo` because the project
evolved from PICO's Spatial Video sample. The installable application ID is
`com.pico.spatial.sample.aispatiallearning`.

## Requirements

- Windows development host with Android Studio
- JDK 17 for the Gradle runtime
- Android SDK 35
- PICO Spatial SDK 6.0.0 (resolved by Gradle); matching Spatial Editor tools are
  required only when rebuilding the scene assets
- A compatible PICO OS 6 device or PICO Emulator
- Network access for course streaming and cloud AI services

## Clone the project

Install [Git LFS](https://git-lfs.com/) before cloning. Images, models, the offline
speech model, and the ready-to-use scene bundle use LFS.

```powershell
git lfs install
git clone https://github.com/dragongyke/AISpatialLearningAssistant.git
cd AISpatialLearningAssistant
git lfs pull
```

Cloud build services must also download LFS objects before invoking Gradle. Pointer
files are not usable images or models. API credentials and APKs are not included.

## API configuration

Cloud credentials are read at build time from Gradle properties or environment
variables. Put local test credentials in
`%USERPROFILE%\.gradle\gradle.properties`:

```properties
VOLCENGINE_SPEECH_APP_ID=your-speech-app-id
VOLCENGINE_SPEECH_ACCESS_TOKEN=your-speech-access-token
ARK_API_KEY=your-ark-api-key
ARK_VISION_MODEL=your-vision-model-or-endpoint-id
ARK_TEXT_MODEL=your-text-model-or-endpoint-id
```

When `ARK_TEXT_MODEL` is omitted, the demo reuses `ARK_VISION_MODEL` for text
answers. It currently uses `volc.bigasr.sauc.duration` for ASR and
`volc.service_type.10029` for TTS. Missing cloud configuration degrades explicitly:
the assistant uses a local transcript fallback, Ark vision is skipped, and speech
recognition falls back to the bundled Chinese Vosk model where possible.

Credentials belong only in the user-level file above or environment variables, never
in tracked source, project Gradle files, documentation, or archives. Debug builds compile
local credentials into `BuildConfig`: keep these APKs private and do not upload them.
Release builds explicitly clear the provider keys, speech token, and speech app ID;
cloud features therefore need a separate runtime credential/backend design before distribution.

Enable the repository's commit/push checks after cloning (the push hook retains Git LFS support):

```powershell
git config core.hooksPath .githooks
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-secrets.ps1 -SelfTest
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-secrets.ps1 -AllHistory
```

The scanner reports paths, never credential values. It checks common credential patterns
and locally configured keys, but is not a substitute for server-side secret scanning.
If a key is exposed, revoke/rotate it: deleting the latest file does not remove Git history.

## Build and verify

Standard APK builds use the checked-in scene bundle and do not require PICO Editor
or `--configure-on-demand`. API credentials are optional for compilation.

```powershell
.\gradlew.bat clean spotlessCheck testDebugUnitTest lintDebug assembleDebug assembleRelease
```

On Linux/macOS build hosts, use `./gradlew` instead of `.\gradlew.bat` and configure
the Android SDK location with `ANDROID_HOME` or an untracked `local.properties`.
Release output is unsigned until a private signing configuration is supplied; it
does not contain provider credentials and is not a ready-to-publish cloud-AI build.

Install the debug package on a connected device:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

When the lecture hall scene changes, explicitly enable its authoring module and
rebuild the asset bundle with matching PICO Editor 6.0 tools installed:

```powershell
.\gradlew.bat -PbuildSceneAssets=true :lecture-hall-editor-asset:assembleDebug
Copy-Item lecture-hall-editor-asset\build\spatial\bundle\lecture-hall-editor-asset.bundle app\src\main\assets\lecture-hall-editor-asset.bundle
```

## Local data

Learning progress, conversation history, saved layout transforms, and summary metadata
are local single-user data. Notes are first saved atomically inside the app, then exported
through Android's Storage Access Framework or to `Documents/AI Spatial Learning`.
If export is unavailable, the in-app note window can still read the local copy. Private
copies are removed on uninstall; export important notes before uninstalling.
The application has no account or cloud synchronization layer.

## Current scope

- Avatar presentation is intentionally excluded.
- Online course URLs and cloud services can change or become unavailable.
- Ark visual understanding depends on successful frame extraction from the current
  stream.
- The bundled offline ASR fallback is Chinese-only and is less accurate than the online
  service.

## License and content

Project source code is provided under [Apache License 2.0](LICENSE.txt). Public course
media, bundled transcripts, the Vosk model, and third-party SDKs retain their respective
terms. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
