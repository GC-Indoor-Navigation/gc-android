# GC Android Collector

---

GC Android Collector is a Kotlin-based Android application that turns an Android smartphone into a video collection device for indoor 3D reconstruction and meta-camera experiments.

The goal is not just to display a camera preview. The app is designed to generate frame-level timestamps and capture metadata together with each camera frame, so a server-side collection pipeline can align and analyze frames from multiple Android cameras.

## Features

---

- Rear camera preview based on CameraX
- Mode selection screen with Camera Mode and Use Mode
- Camera setup screen
  - Server URL
  - Device ID
  - Camera ID
  - Resolution: `640 x 480`, `1280 x 720`, `1920 x 1080`
  - Target FPS: `5`, `10`, `15`, `30`
  - Focus, exposure, white balance, and zoom control options
- Full-screen camera capture screen
- Start / Stop control for frame analysis
- Frame-level metadata generation
  - `frame_sequence`
  - `device_timestamp_ms`
  - `device_monotonic_ns`
  - Resolution, format, and FPS target
  - Focus, exposure, white balance, and zoom settings
- Conversion from `ImageAnalysis` frames to JPEG byte arrays
- Details panel for checking sequence, timestamp, FPS, and capture settings
- UI state preservation across screen rotation

## Current Status

---

The current implementation can open the Android camera preview and, while capture is running, convert CameraX `ImageAnalysis` frames into JPEG bytes paired with metadata.

The server upload pipeline is not connected yet. Fields such as `serverUrl`, sent count, and failed count are already present in the app state, but the actual HTTP multipart upload logic is planned as follow-up work.

## Tech Stack

---

- Kotlin
- Jetpack Compose
- Material 3
- CameraX
- Camera2 Interop
- OkHttp
- kotlinx.serialization
- Gradle Kotlin DSL

## Requirements

---

- Android Studio
- JDK 17 or later recommended
- Android SDK
- Android 8.0, API 26 or later device or emulator
- Runtime environment with camera permission support

Project configuration:

- Package: `com.gc.collector`
- minSdk: `26`
- targetSdk: `36`
- compileSdk: `36`
- Gradle Wrapper: `9.3.1`

## Build and Run

---

Clone the repository, open it in Android Studio, and run the `app` module.

To build a debug APK from the command line:

```powershell
.\gradlew.bat :app:assembleDebug
```

If your local Gradle or Android user home causes conflicts, you can keep those paths inside the project directory:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-user-home"
$env:ANDROID_USER_HOME="$PWD\.android-user-home"
.\gradlew.bat --no-daemon --console=plain :app:assembleDebug
```

If the offline cache is already prepared:

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-user-home"
$env:ANDROID_USER_HOME="$PWD\.android-user-home"
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug
```

## App Flow

---

1. Launch the app and select `Camera Mode`.
2. Configure the server URL, device ID, camera ID, resolution, FPS, and camera control options on the `Camera Setup` screen.
3. Press `Open Camera` to enter the full-screen camera screen.
4. Press the circular Start button to begin frame collection.
5. Press the Details button to inspect sequence, timestamp, FPS, and camera control state.
6. Press Stop to end collection.

## Metadata Example

---

Each frame produces metadata in the following shape:

```json
{
  "camera_id": "camera_01",
  "device_id": "android_device_001",
  "frame_sequence": 1,
  "device_timestamp_ms": 1777388400000,
  "device_monotonic_ns": 123456789000,
  "width": 1280,
  "height": 720,
  "format": "jpeg",
  "fps_target": 10,
  "focus_mode": "locked",
  "focus_locked": true,
  "exposure_locked": true,
  "white_balance_locked": true,
  "zoom_disabled": true,
  "orientation_deg": 90
}
```

## Project Structure

---

```text
gc-android/
+-- app/
|   +-- src/main/
|       +-- AndroidManifest.xml
|       +-- java/com/gc/collector/
|           +-- MainActivity.kt
|           +-- camera/
|           |   +-- CapturedFrame.kt
|           |   +-- AnalyzedFrame.kt
|           |   +-- JpegFrameEncoder.kt
|           +-- model/
|           |   +-- CameraCaptureSettings.kt
|           |   +-- CaptureStats.kt
|           |   +-- CollectorUiState.kt
|           |   +-- FrameMetadata.kt
|           |   +-- FrameMetadataFactory.kt
|           +-- ui/
|               +-- camera/CameraPreview.kt
|               +-- screen/MainScreen.kt
|               +-- theme/
+-- gradle/
|   +-- libs.versions.toml
+-- settings.gradle.kts
```

## Implementation Notes

---

- CameraX `PreviewView` uses `ImplementationMode.COMPATIBLE` to reduce rendering issues when embedded in Compose.
- Frame processing uses `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` to prioritize the most recent frame.
- JPEG conversion converts `ImageProxy(YUV_420_888)` to NV21 and then uses `YuvImage.compressToJpeg()`.
- Focus, exposure, and white balance options are requested through Camera2 Interop capture request options.
- Focus lock, exposure lock, and white balance lock support may vary by device. A future step should record the actual applied camera state in metadata.
