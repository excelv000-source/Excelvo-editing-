# EXCEVO EDIT — Revised Cross-Platform Architecture & Roadmap

> **Document Status:** Updated Architecture & Phase 1 Audit Specification (Revised prior to Phase 2).
> **Project Identity:** `EXCEVO EDIT` (`com.aistudio.excevoedit.kxqvmr`)
> **Monetization Policy:** Community Preset System is **100% FREE**. No payments, no marketplace, no paid downloads, no creator monetization.

---

## 1. Visual Identity & Cross-Platform Design System

- **Brand Name:** `EXCEVO EDIT` (100% original branding; zero third-party logos, assets, or layouts copied).
- **Theme Posture:** Calibrated Dark Charcoal studio interface with **Purple Accent System** shared identically across Android (`Jetpack Compose`) and iOS (`SwiftUI`).
- **Design Tokens:**
  - `VoidCharcoal`: `#0B0B10` (primary window canvas)
  - `StudioPanel`: `#14141D` (cards, inspector panels, bottom navigation)
  - `ElevatedSurface`: `#1D1E2A` (dialogs, active tiles, dropdowns)
  - `HairlineBorder`: `#2A2C3D` (1dp structural dividers)
  - `AccentPurple`: `#8B5CF6` (primary CTAs, active nav pill, selected clip highlight)
  - `AccentVioletGlow`: `#A855F7` / `#C084FC` (AI Lab & secondary accents)
  - `PlayheadAmber`: `#F59E0B` (timeline needle & timecode HUD)
  - `SuccessEmerald`: `#10B981` | `DangerCoral`: `#EF4444`
- **Typography:** `Space Grotesk` (Display/Headers), `Plus Jakarta Sans` (Body/UI), `JetBrains Mono` (Timecodes, FPS, Storage & Usage Telemetry).

---

## 2. System Architecture & Cross-Platform Strategy

EXCEVO EDIT preserves the **Phase 1 Hexagonal (Ports & Adapters) + Unidirectional Data Flow (UDF)** core (`com.example.core.*`, zero `android.*` imports) and scales into a Kotlin Multiplatform (`:shared`) + Native UI/Media/GPU architecture:

```text
┌────────────────────────────────────────────────────────────────────────────────┐
│                      PRESENTATION LAYER (Native Per Platform)                  │
│   Android: Jetpack Compose (M3)            │   iOS: SwiftUI + MetalKit         │
│   4-Tab Shell: Home | Projects | Presets | Profile                             │
│   Full-Screen: Studio Workspace | AI Lab | Moderation Console | Creator View   │
└───────────────────────────────────────┬────────────────────────────────────────┘
                                        │ StateFlow / Observation
┌───────────────────────────────────────▼────────────────────────────────────────┐
│                    SHARED APPLICATION & DOMAIN CORE (KMP-Ready)                │
│   - TimelineProject, Track, Clip, Keyframe, Transform, ColorGrade, Mask Specs  │
│   - TimelineMath (Microsecond rational math), KeyframeInterpolator             │
│   - CommandHistoryManager (Non-destructive EditCommands + Undo/Redo)           │
│   - PresetSchemaValidator (Strict declarative JSON check; zero code execution) │
│   - Platform Ports: MediaProbePort, ProjectStoragePort, PreviewPort,           │
│     AudioPort, ExportPort, PresetFileStoragePort, AuthSessionPort              │
└──────────────────┬───────────────────────────────────┬─────────────────────────┘
                   │                                   │
┌──────────────────▼───────────────┐   ┌───────────────▼─────────────────────────┐
│   LOCAL STORAGE & NATIVE MEDIA   │   │      CLOUD BACKEND & AI (Firebase)      │
│   Android:                       │   │   - Firebase Auth (Google Sign-In ONLY) │
│   - Room SQLite + Atomic JSON    │   │   - Cloud Firestore (Named Database)    │
│   - DataStore Preferences        │   │   - Cloud Object Storage (Firebase      │
│   - MediaCodec + OpenGL ES 3.0   │   │     Storage for preview media & JSONs)  │
│   - Media3 Transformer + Audio   │   │   - Firebase AI Logic (gemini-3.8-flash │
│   iOS:                           │   │     + App Check; zero client API keys)  │
│   - SwiftData / SQLite + JSON    │   └─────────────────────────────────────────┘
│   - AVFoundation + Metal (MSL)   │
│   - AVAudioEngine                │
└──────────────────────────────────┘
```

### Android vs. iOS Compatibility & Boundaries
- **What IS Shared (`:core` / KMP `commonMain`):** Timeline data models, microsecond/rational math, keyframe interpolation curves, command undo/redo, JSON `project.json` v1 codec, `excevo-preset/v1` schema validator, and role/permission enums.
- **What is Platform-Specific (Native Adapters):**
  - **UI:** Jetpack Compose (Android) ↔ SwiftUI + `UIViewRepresentable` (iOS).
  - **Video Decoding & GPU:** `MediaCodec` + `SurfaceTexture` + OpenGL ES 3.0 `GLSL` (Android) ↔ `AVAssetReader` + `CVMetalTextureCache` + `Metal` `MSL` (iOS).
  - **Export:** Media3 `Transformer` + `GlEffect` inside `WorkManager` (Android) ↔ `AVAssetReader` + `MTLCommandQueue` + `AVAssetWriter` inside `BGProcessingTask` (iOS).
  - **Audio:** `MediaCodec` PCM + `AudioTrack`/Oboe (Android) ↔ `AVAudioEngine` (iOS).
- **Compatibility Limitations:**
  - Android Kotlin code cannot compile or execute directly on iOS without extracting `:core` into a KMP `XCFramework` and compiling Swift/Metal adapters in Xcode on macOS.
  - Variable Frame Rate (VFR) footage and HDR (HLG/Dolby Vision) tone-mapping differ between Android `MediaCodec` and iOS `AVFoundation`; both adapters must map timeline `ptsUs` to sample timestamps and normalize to Rec.709/linear working color space.

---

## 3. Navigation & Screen Architecture

### Bottom Navigation (4 Primary Tabs)
1. **Home (`home`)**: Welcome hero banner, **New Composition** purple CTA, **AI Lab** featured entry, **Recent Projects** horizontal carousel (from Room DB), and **AI Tools** grid (only implemented AI capabilities).
2. **Projects (`projects`)**: **New Project** CTA, search bar, **Grid / List view toggle**, **Sort** (Last Edited, Name, Duration), **Filter** (Aspect Ratio), and project actions (**Continue Editing**, **Rename**, **Duplicate**, **Delete**).
3. **Presets (`presets`)** — *100% Free Community*:
   - Categories: **Shakes**, **Effects**, **CCs (Color Corrections)**, **Saved**, **Community** (plus sub-filters for Transitions, Text Animations, Filters).
   - Search, preview media viewer, creator attribution (tappable → Creator Profile), real like/save/download counts, **Apply Preset** to active project/clip, **Report Content**, **My Uploads**, and **Upload Preset** flow.
4. **Profile (`profile`)**:
   - Account header (Guest status or Google Sign-In / Sign-Out + Role badge).
   - Membership & Studio Tier summary (`Free Creator` / `Moderator` / `Admin` — informational, zero payments).
   - Real usage statistics (local projects count, presets uploaded/saved/downloaded, AI Lab usage).
   - **EXCEVO Library** (reusable local/synced LUTs, overlays, fonts, preset packs).
   - **Preferences**, **Appearance & Themes**, **Performance Settings** (proxy resolution, scrub cache limit, hardware codec toggle), **Storage Management** (real byte breakdown + Clear Cache), **Extensions**, **Connected Devices**, **Help & Support**, and **Admin / Moderation Console** link (visible only to `MODERATOR` / `ADMIN`).

### Full-Screen Routes
- `studio/{projectId}` — Non-destructive multi-track timeline editor (Phase 1 foundation + Phases 3–7 engine).
- `ai_lab` — AI Look/CC generator, AI Smart Cut planner, AI Auto-Captions/Translator.
- `upload_preset` — Preset upload & validation workflow.
- `creator/{uid}` — Public creator profile & approved free uploads.
- `moderation_console` — Pending Queue, Reports Queue, Appeals (Moderator/Admin only).

---

## 4. Database Schema

### A. Local Device Storage (Room SQLite + DataStore + Filesystem)
1. **`projects`** *(Implemented in Phase 1)*: `id (PK)`, `name`, `aspectRatioId`, `frameRateFps`, `durationUs`, `clipCount`, `thumbnailPath`, `documentPath`, `createdAtMs`, `updatedAtMs`.
2. **`downloaded_presets`**: `presetId (PK)`, `title`, `category`, `creatorUid`, `creatorName`, `schemaVersion`, `localJsonPath`, `previewLocalPath`, `downloadedAtMs`, `isSavedFavorite`.
3. **`excevo_library_items`**: `id (PK)`, `itemType` (`LUT`, `OVERLAY`, `FONT`, `PRESET`), `name`, `localPath`, `sizeBytes`, `createdAtMs`.
4. **DataStore Preferences**: `themeVariant`, `proxyResolution`, `hardwareDecodeEnabled`, `snapEnabled`, `scrubCacheLimitMb`, `aiUsageCount`.

### B. Cloud Database (Firestore — Named `R.string.firestore_database_id`)
1. **`/users/{uid}`**: `uid`, `displayName`, `handle`, `avatarUrl`, `bio`, `role` (`"USER"` | `"CREATOR"` | `"MODERATOR"` | `"ADMIN"` — *client cannot self-elevate*), `membershipTier` (`"FREE"`), `stats` (`uploadsCount`, `totalDownloadsReceived`, `aiRequestsCount`), `createdAt`, `updatedAt`.
2. **`/users/{uid}/saved_presets/{presetId}`**: `presetId`, `savedAt`.
3. **`/users/{uid}/devices/{deviceId}`**: `deviceName`, `platform` (`"ANDROID"` | `"IOS"`), `appVersion`, `lastActiveAt`.
4. **`/community_presets/{presetId}`**:
   - `title`, `description`, `tags: List<String>`
   - `category` (`"SHAKES"` | `"EFFECTS"` | `"CC"` | `"TRANSITIONS"` | `"TEXT_ANIMATIONS"` | `"FILTERS"` | `"OTHER"`)
   - `creatorUid`, `creatorName`, `creatorAvatarUrl`
   - `status` (`"PENDING_REVIEW"` | `"APPROVED"` | `"REJECTED"` | `"REMOVED"`)
   - `moderationNote`, `reviewedByUid`, `reviewedAt`
   - `appealStatus` (`"NONE"` | `"SUBMITTED"` | `"UNDER_REVIEW"` | `"RESOLVED"`), `appealMessage`
   - `rightsConfirmed: Boolean` *(enforced `== true`)*
   - `presetFileFormat` (`"excevo-preset/v1"`), `schemaVersion`, `minAppVersion`
   - `presetFileUrl`, `presetFileSizeBytes`, `presetSha256`
   - `previewMediaType` (`"IMAGE"` | `"VIDEO"`), `previewMediaUrl`
   - `likesCount`, `downloadsCount`, `reportsCount`, `createdAt`, `updatedAt`.
5. **`/community_presets/{presetId}/likes/{uid}`**: `uid`, `createdAt`.
6. **`/community_presets/{presetId}/downloads/{uid}`**: `uid`, `downloadedAt`.
7. **`/reports/{reportId}`**: `presetId`, `presetTitle`, `reporterUid`, `reason` (`"COPYRIGHT"` | `"MALICIOUS_FILE"` | `"INAPPROPRIATE"` | `"BROKEN_COMPATIBILITY"` | `"SPAM"`), `details`, `status` (`"OPEN"` | `"ACTIONED"` | `"DISMISSED"`), `reviewedByUid`, `createdAt`.
8. **`/moderation_logs/{logId}`**: `targetType`, `targetId`, `actorUid`, `action`, `reason`, `createdAt`.

### C. Cloud File Storage (Non-Firestore Object Storage)
- `presets/{presetId}/preset.excevo.json` (max `512 KB`, `application/json`, SHA-256 verified)
- `presets/{presetId}/preview.{jpg|webp|mp4}` (max `2 MB` image / `20 MB` demo video)

---

## 5. User Roles, Permissions & Security Rules

| Capability | `GUEST` | `USER` / `CREATOR` | `MODERATOR` | `ADMIN` |
| :--- | :---: | :---: | :---: | :---: |
| Local Projects, Studio Editor, Export, Built-in Local Presets | ✅ | ✅ | ✅ | ✅ |
| Browse & Preview `APPROVED` Free Community Presets | ✅ Read-Only | ✅ | ✅ | ✅ |
| Download/Apply Community Presets, Like, Save, Report | ⛔ Sign-In Required | ✅ | ✅ | ✅ |
| Upload Preset (forced `PENDING_REVIEW`, `rightsConfirmed == true`) | ❌ | ✅ | ✅ | ✅ |
| Edit Own Description, View Status, Appeal, Delete Own (`My Uploads`) | ❌ | ✅ Own Only | ✅ Own Only | ✅ Any |
| Approve / Reject Pending Uploads, Remove Reported Content | ❌ | ❌ | ✅ | ✅ |
| Manage User Roles & Permissions | ❌ | ❌ | ❌ | ✅ |

- **Authentication:** Google Sign-In via Jetpack `CredentialManager` (`GetSignInWithGoogleOption`) exclusively.
- **Anti-Malware & Play Policy DCL Compliance:** `PresetSchemaValidator` rejects all `.dex`, `.jar`, `.so`, `.apk`, `.sh`, `.js`, HTML/SVG scripts, and out-of-range parameters both before upload and before applying a downloaded preset.

---

## 6. Revised Development Roadmap (Phases 2–7)

- **Phase 2:** Purple Dark Design System migration, 4-tab shell (`Home`, `Projects`, `Presets` local foundation, `Profile` local preferences/storage/EXCEVO Library/help), Projects Grid/List + search/sort/filter.
- **Phase 3:** Hardware `MediaCodec` + `SurfaceTexture` decoder pool, OpenGL ES 3.0 FBO compositor (`GLSL`), real-time stage playback, crop/rotate/speed/color grading/effects/shakes, filmstrips & drag-trim handles.
- **Phase 4:** 48 kHz Float32 PCM audio mixer, `.wfbin` waveforms, volume/fade/mute/detach audio, pitch-preserved speed control, animated text & subtitle tracks.
- **Phase 5:** Keyframe diamond editor + Bezier interpolation, GLSL chroma key, parametric masking, GPU transitions, full `excevo-preset/v1` engine (`PresetSchemaValidator`, Apply Preset, local Shakes/Effects/CCs/Saved).
- **Phase 6:** Firebase provisioning + `firestore.rules` + `firestore.test.js`, Google Sign-In (`CredentialManager`), 100% Free Community ecosystem (Upload → Pending Review → Approved, Browse, Free Download, Like, Save, Report, My Uploads, Creator Profiles), Connected Devices, EXCEVO Library sync, and Admin/Moderator Console.
- **Phase 7:** AI Lab (`Firebase.ai` `gemini-3.8-flash` + App Check + on-device beat/segmentation), deterministic `WorkManager` MP4 export via Media3 `Transformer` + `GlEffect` + AAC mix.
