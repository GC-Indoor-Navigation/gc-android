# Android Collector 개발 기록

## 2026-04-27

### 1. 기획서 정리

- 멀티카메라 실내 3차원 재구성용 Android 영상 수집 앱의 목적과 요구사항을 정리했다.
- 문서 위치:
  - `docs/android-collector-requirements.md`

핵심 방향은 다음과 같다.

- 일반 스트리밍 앱이 아니라 프레임 단위 timestamp와 camera metadata를 수집하는 단말 앱으로 개발한다.
- MVP에서는 모든 기기에서 완전한 수동 제어를 보장하지 않고, 가능한 설정은 고정 시도하며 실제 적용 상태를 기록한다.
- 각 프레임에는 `device_timestamp_ms`, `device_monotonic_ns`, `frame_sequence`를 포함한다.
- 초기 전송 방식은 HTTP multipart를 우선 고려한다.

### 2. 프로젝트 구조 확인

- 프로젝트는 Kotlin + Jetpack Compose 기반 Android 앱이다.
- 패키지명은 `com.gc.collector`이다.
- 초기 상태는 기본 Compose 템플릿에 가까웠고, `MainActivity`에는 `Hello Android` 샘플 화면만 있었다.
- CameraX, OkHttp, serialization 의존성은 없었다.

### 3. 기본 모델 추가

다음 모델을 추가했다.

- `CameraCaptureSettings`
- `ResolutionOption`
- `CaptureStats`
- `CollectorUiState`
- `FrameMetadata`
- `FrameMetadataFactory`

목적:

- 카메라 설정값, 프레임 metadata, 수집 상태를 UI와 카메라 처리 계층에서 공통으로 사용하기 위함이다.

### 4. 기본 UI 구현

초기에는 다음 요소를 가진 Compose 화면을 만들었다.

- 서버 URL 입력
- device ID 입력
- camera ID 입력
- 해상도 선택
- FPS 선택
- Start/Stop
- sequence/timestamp/FPS/status 표시

이후 UI는 여러 차례 조정되었고, 최종적으로 다음 흐름으로 변경했다.

1. 시작 화면
   - Camera Mode
   - Use Mode
2. Camera Mode
   - 설정 화면으로 이동
   - 설정 완료 후 전체화면 카메라 화면으로 이동
3. 전체화면 카메라 화면
   - 카메라 전체화면
   - 원형 Start/Stop 버튼
   - 원형 Status 버튼
4. Use Mode
   - 현재는 placeholder 화면

### 5. 의존성 및 권한 추가

다음 권한을 추가했다.

- `android.permission.CAMERA`
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

다음 의존성을 추가했다.

- CameraX
  - `camera-core`
  - `camera-camera2`
  - `camera-lifecycle`
  - `camera-view`
- OkHttp
- kotlinx serialization json

`AndroidManifest.xml`에는 개발 중 HTTP 테스트를 위해 `android:usesCleartextTraffic="true"`도 추가했다.

### 6. CameraX 프리뷰 구현

`CameraPreview` composable을 추가했다.

기능:

- Compose에서 `PreviewView`를 사용해 CameraX 프리뷰 표시
- `ProcessCameraProvider`로 후면 카메라를 lifecycle에 바인딩
- 카메라 권한 요청 및 권한 거부 상태 처리
- dispose 시 `unbindAll()` 호출

카메라 프리뷰가 Compose UI 위로 덮어 그려지는 문제가 있어 다음 설정을 적용했다.

```kotlin
PreviewView.ImplementationMode.COMPATIBLE
```

### 7. 실제 프레임 수신 구현

CameraX `ImageAnalysis`를 추가했다.

기능:

- 프레임 callback 수신
- Start 상태에서만 프레임 처리
- `frame_sequence` 증가
- `device_timestamp_ms` 생성
- `device_monotonic_ns` 생성
- sensor timestamp 기반 FPS 계산

분석 전략:

```kotlin
ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
```

### 8. JPEG 변환 구현

다음 클래스를 추가했다.

- `CapturedFrame`
- `JpegFrameEncoder`

기능:

- `ImageProxy(YUV_420_888)`를 `NV21`로 변환
- `YuvImage.compressToJpeg()`로 JPEG byte array 생성
- JPEG bytes와 `FrameMetadata`를 `CapturedFrame`으로 묶음

현재 앱 내부에서는 다음 흐름까지 구현되어 있다.

```text
CameraX ImageAnalysis frame
-> ImageProxy
-> JPEG ByteArray
-> FrameMetadata
-> CapturedFrame
```

### 9. UI 구조 변경 기록

UI는 실제 사용 흐름에 맞추기 위해 여러 차례 조정했다.

#### 9.1. 설정/상태 접기 방식

처음에는 설정과 상태를 접기/펼치기 패널로 만들었다.

문제:

- 가로모드에서 카메라 프리뷰와 버튼/패널이 겹쳐 보였다.
- CameraX `PreviewView`가 Surface 기반으로 그려져 Compose UI와 겹침 문제가 있었다.

조치:

- `PreviewView.ImplementationMode.COMPATIBLE` 적용
- 이후 UI 구조 자체를 전체화면 카메라 오버레이 방식으로 변경

#### 9.2. 전체화면 카메라 오버레이

전체화면 카메라 위에 원형 버튼 2개를 올리는 구조로 변경했다.

- Start/Stop 버튼
- Details 또는 Status 버튼

세로모드:

- 버튼은 하단 중앙에 배치
- 패널은 아래에서 위로 올라오는 bottom sheet 방식

가로모드:

- 버튼은 우상단에 세로 배치
- 패널은 오른쪽에서 열림

#### 9.3. Camera Mode / Use Mode 분리

앱 시작 시 바로 카메라가 뜨는 구조에서 모드 선택 구조로 변경했다.

이유:

- 캡처 전에 camera_id, device_id, 서버 URL, FPS, 해상도 등을 먼저 설정하는 흐름이 더 적합하다.
- 전체화면 카메라 화면에서는 조작을 최소화하는 것이 사용성이 좋다.

현재 구조:

- Camera Mode 선택
- Camera Setup 화면에서 설정
- Open Camera
- 전체화면 카메라
- Status 버튼으로 상태만 확인

### 10. 뒤로가기 처리

`BackHandler`를 추가했다.

동작:

- Camera Setup 화면에서 뒤로가기: Mode Selection으로 이동
- Use Mode 화면에서 뒤로가기: Mode Selection으로 이동
- 카메라 화면에서 Status 패널이 열려 있으면: 패널만 닫음
- 패널이 닫힌 카메라 화면에서는 기본 뒤로가기 동작을 따른다.

### 11. 전체화면 관련 조정

카메라 화면에서 버튼이 오른쪽 끝에 붙지 않는 문제가 있었다.

확인 결과:

- `MainActivity`의 `Scaffold`가 `innerPadding`을 주고 있었다.
- 이 때문에 전체화면 카메라가 실제 전체 화면이 아니라 padding이 적용된 영역 안에서 렌더링되었다.

조치:

- `Scaffold` 제거
- `MainScreen(modifier = Modifier.fillMaxSize())`로 직접 렌더링
- 카메라 화면의 `1280 x 720` 해상도 배지 제거

### 12. 현재 빌드 상태

다음 명령으로 빌드를 확인했다.

```powershell
$env:GRADLE_USER_HOME='C:\Users\user\Documents\inha\GeoCapstone\gc-android\.gradle-user-home'
$env:ANDROID_USER_HOME='C:\Users\user\Documents\inha\GeoCapstone\gc-android\.android-user-home'
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug
```

결과:

```text
BUILD SUCCESSFUL
```

반복적으로 다음 경고가 발생한다.

- Kotlin daemon 접근 실패 후 fallback compile
- `LocalLifecycleOwner` deprecation warning
- Android SDK XML version warning

현재 빌드는 성공하므로 기능 개발을 막는 문제는 아니다.

### 13. 다음 단계 후보

다음 개발 단계는 서버 전송 기능이다.

권장 순서:

1. `FrameSender` 추가
2. `CapturedFrame`을 HTTP multipart로 전송
3. metadata를 JSON으로 직렬화
4. 전송 성공/실패 카운트 갱신
5. Status 패널에 마지막 전송 시각 표시
6. 네트워크 실패 시 실패 카운트 증가

전송 형식 후보:

- `frame`: JPEG binary
- `metadata`: JSON string

## 2026-04-28

### 14. 전체화면 카메라 UI 개선

카메라 화면의 조작 UI를 계속 조정했다.

최종 방향:

- 카메라 화면은 전체화면 프리뷰를 기본으로 유지한다.
- 세로모드에서는 하단 중앙에 원형 버튼 2개를 둔다.
- 가로모드에서는 오른쪽 가운데에 원형 버튼 2개를 세로로 둔다.
- 버튼은 반투명 원형 스타일로 표시한다.
- 버튼 크기는 방향별로 동일하게 맞춘다.
  - 세로모드: 두 버튼 모두 `64dp`
  - 가로모드: 두 버튼 모두 `60dp`

버튼 역할:

- 재생/정지 버튼: 캡처 start/stop 토글
- 설정 모양 버튼: Status 패널 열기/닫기

가로모드 버튼 위치는 여러 차례 조정했다.

- 처음에는 우상단 배치
- 이후 오른쪽 끝 여백 문제를 확인
- `Scaffold(innerPadding)` 제거로 실제 전체화면 기준을 확보
- 최종적으로 가로모드 버튼은 오른쪽 가운데 배치로 변경

### 15. 화면 회전 상태 유지

가로/세로 전환 시 화면이 처음 Mode Selection으로 돌아가는 문제가 있었다.

조치:

- `currentScreen`을 `rememberSaveable` 기반으로 저장하도록 변경
- `CollectorUiState`에 대한 custom `Saver` 추가
- 설정값, 수집 상태, 통계값이 회전 후에도 유지되도록 했다.

저장 대상:

- 현재 화면
- `isCapturing`
- camera ID
- device ID
- server URL
- 해상도
- FPS
- focus/exposure/white balance/zoom 관련 설정
- frame sequence
- 마지막 timestamp
- 전송/실패/drop 통계
- 현재 FPS

주의:

- 회전 중 CameraX는 lifecycle에 의해 다시 바인딩된다.
- 앱 상태는 유지하지만 카메라 세션 자체는 재생성될 수 있다.

### 16. 뒤로가기 흐름 정리

카메라 화면에서 뒤로가기 동작을 다음 흐름으로 정리했다.

- Status 패널이 열려 있으면 뒤로가기 1회로 패널만 닫는다.
- Status 패널이 닫힌 카메라 화면에서 뒤로가기 1회로 Camera Setup 화면으로 돌아간다.
- Camera Setup 화면에서 뒤로가기 1회로 Mode Selection 화면으로 돌아간다.

카메라 화면에서 Camera Setup으로 돌아갈 때는 `isCapturing = false`로 변경하여 수집을 중지한다.

### 17. 카메라 설정 토글 추가

Camera Setup 화면에 촬영 조건 제어용 토글을 추가했다.

추가한 토글:

- `Lock focus`
- `Lock exposure`
- `Lock white balance`
- `Disable zoom`

설정 모델 확장:

- `focusLocked`
- `exposureLocked`
- `whiteBalanceLocked`
- `zoomDisabled`

metadata 확장:

- `focus_locked`
- `exposure_locked`
- `white_balance_locked`
- `zoom_disabled`
- `focus_mode`

Status 패널에도 관련 요청 상태를 표시한다.

### 18. Camera2 interop 적용

CameraX 바인딩 시 Camera2 interop을 이용해 촬영 조건 고정을 시도하도록 했다.

현재 적용 시도:

- focus lock ON
  - `CONTROL_AF_MODE_OFF`
  - `LENS_FOCUS_DISTANCE = 0f`
- focus lock OFF
  - `CONTROL_AF_MODE_CONTINUOUS_VIDEO`
- exposure lock
  - `CONTROL_AE_LOCK`
- white balance lock
  - `CONTROL_AWB_LOCK`
- zoom disabled
  - `cameraControl.setZoomRatio(1.0f)`

주의:

- Android 기기마다 AF/AE/AWB lock 및 manual focus 지원 범위가 다르다.
- 현재 구현은 설정을 요청하는 단계다.
- 다음 단계에서는 실제 적용 결과를 Camera2 characteristics/result 기반으로 확인하고 Status/metadata에 기록하는 작업이 필요하다.

### 19. 현재 상태 요약

현재 앱 구조:

1. Mode Selection
   - Camera Mode
   - Use Mode
2. Camera Setup
   - 서버 URL
   - device ID
   - camera ID
   - 해상도
   - FPS
   - focus/exposure/white balance/zoom 토글
3. Camera Capture
   - 전체화면 CameraX preview
   - start/stop 원형 버튼
   - Status 원형 버튼
   - Status 패널
4. Use Mode
   - placeholder

현재 프레임 처리 흐름:

```text
CameraX Preview
+ ImageAnalysis
-> ImageProxy
-> JPEG ByteArray
-> FrameMetadata
-> CapturedFrame
```

현재까지 빌드 상태:

```text
BUILD SUCCESSFUL
```

남은 주요 작업:

- 실제 Camera2 적용 상태 확인 및 기록
- gRPC 서버 ingest 구현
- 실제 서버와 end-to-end 수신 테스트
- 마지막 전송 시각 표시
- 네트워크 실패 처리

### 20. gRPC Android client 구현

서버 전송 방향을 HTTP가 아니라 gRPC client streaming으로 확정했다.

추가한 항목:

- `app/src/main/proto/frame_ingest.proto`
- `FrameSender`
- `GrpcFrameSender`
- `FrameIngestServiceGrpc`
- `FrameIngestMessages`
- `FramePacketMapper`
- `GrpcEndpoint`

구현 내용:

- `FrameIngestService.StreamFrames` client streaming 호출을 Android 앱에서 시작한다.
- Start 버튼을 누르면 서버 주소를 파싱하고 gRPC stream을 연다.
- 캡처 중인 각 `CapturedFrame`을 metadata + JPEG packet으로 전송한다.
- Stop 버튼이나 카메라 화면 뒤로가기를 누르면 stream을 종료한다.
- Status 패널에서 network 상태, sent count, failed count, drop count를 확인할 수 있다.

구현 방식:

- Android Gradle Plugin 9.1 환경에서 protobuf Gradle plugin이 호환되지 않아 codegen을 사용하지 않았다.
- 대신 `frame_ingest.proto`는 서버와 공유할 API 계약으로 유지한다.
- Android 앱 내부에서는 `MethodDescriptor`와 protobuf wire encoding을 직접 구성한다.

현재 빌드 상태:

```text
BUILD SUCCESSFUL
```
