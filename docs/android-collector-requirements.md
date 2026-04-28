# 멀티카메라 실내 3차원 재구성용 Android 영상 수집 앱 기획서

## 1. 개발 목적

본 앱은 여러 대의 Android 스마트폰을 실내 3차원 재구성 및 위치 추정을 위한 영상 수집 단말기로 활용하기 위해 개발한다.

일반적인 카메라 또는 스트리밍 앱과 달리, 본 앱의 핵심 목적은 단순 영상 송출이 아니다. 프레임별 촬영 조건을 최대한 일정하게 유지하고, 각 프레임에 단말 기준 timestamp와 metadata를 부여하여 서버에서 다중 카메라 영상을 정렬, 저장, 분석할 수 있도록 하는 것이다.

본 앱은 다음 두 가지를 핵심 목표로 한다.

1. 촬영 조건의 일관성 확보
2. 프레임 단위 timestamp 및 metadata 기록

## 2. 핵심 문제 정의

### 2.1. 촬영 조건 변동 문제

실내 3차원 재구성, 카메라 캘리브레이션, 멀티뷰 기반 위치 추정을 수행하려면 프레임 간 영상 특성이 안정적이어야 한다.

그러나 일반 스마트폰 카메라는 기본적으로 다음 기능을 자동으로 조정한다.

- 자동 초점, AF
- 자동 노출, AE
- 자동 화이트밸런스, AWB
- 디지털 줌
- 흔들림 보정
- 장면 최적화
- HDR 또는 저조도 보정

이러한 자동 보정은 일반 촬영에는 유리하지만, 캘리브레이션과 멀티뷰 매칭에는 불리할 수 있다.

예를 들어 프레임마다 초점, 밝기, 색온도, 노출 시간이 변하면 체커보드 코너 검출, 특징점 매칭, 사람 기반 대응점 추출의 안정성이 떨어질 수 있다.

따라서 앱은 가능한 범위에서 촬영 조건을 고정하고, 고정이 불가능한 경우 실제 적용 상태를 metadata로 기록해야 한다.

### 2.2. 프레임 시간 기준 문제

서버 수신 시각만으로는 실제 프레임이 언제 촬영되었는지 정확히 알기 어렵다.

네트워크 지연, 전송 큐, 서버 처리 지연 때문에 서버 도착 시간은 실제 캡처 시간과 차이가 날 수 있다.

따라서 각 프레임에는 단말에서 프레임이 생성되거나 캡처된 시점을 기준으로 timestamp를 부여해야 한다.

필수 timestamp는 다음 두 가지다.

- `device_timestamp_ms`
- `device_monotonic_ns`

`device_timestamp_ms`는 사람이 해석하기 쉬운 wall clock 기준 시간이고, `device_monotonic_ns`는 프레임 간 상대 시간 비교에 적합한 monotonic clock 기준 시간이다.

## 3. 개발 목표

본 앱은 Android 스마트폰을 다음 조건을 만족하는 영상 수집 단말기로 만드는 것을 목표로 한다.

- 카메라 선택 가능
- 해상도 고정 가능
- 목표 FPS 설정 가능
- 촬영 조건 고정 시도
- 프레임별 sequence 부여
- 프레임별 timestamp 부여
- 프레임별 camera metadata 기록
- 서버로 프레임과 metadata 전송
- 전송 상태와 실패 로그 확인 가능

MVP에서는 모든 Android 기기에서 완전한 수동 제어를 보장하지 않는다.

대신 다음 원칙을 따른다.

> 가능한 기기에서는 설정을 고정하고, 불가능한 기기에서는 현재 적용 상태를 metadata로 기록한다.

## 4. MVP 기능 범위

### 4.1. 카메라 기능

MVP에서 반드시 제공해야 하는 카메라 기능은 다음과 같다.

- 카메라 선택
- 전면/후면 또는 camera id 선택
- 해상도 선택
- 프리뷰 표시
- 프레임 캡처
- JPEG 인코딩
- 목표 FPS 설정
- orientation 고정
- zoom 비활성화 또는 기본값 고정

가능한 경우 다음 설정을 고정한다.

- AF 비활성화 또는 고정 초점
- AE 비활성화 또는 노출 잠금
- AWB 비활성화 또는 화이트밸런스 잠금
- ISO 고정
- 셔터 시간 고정

단, 기기별 지원 여부가 다르므로 실제 적용 결과를 기록한다.

### 4.2. Metadata 기능

각 프레임 또는 전송 단위에는 다음 metadata를 포함한다.

```json
{
  "camera_id": "camera_01",
  "device_id": "android_a14_001",
  "frame_sequence": 1523,
  "device_timestamp_ms": 1775404088703,
  "device_monotonic_ns": 8234567812345,
  "width": 1280,
  "height": 720,
  "format": "jpeg",
  "fps_target": 10,
  "focus_mode": "fixed",
  "exposure_locked": true,
  "white_balance_locked": true,
  "orientation_deg": 90
}
```

추가로 다음 항목을 포함할 수 있다.

- ISO
- exposure time
- focal length
- lens facing
- sensor timestamp
- battery level
- network status
- app version
- capture_started_at_ms
- server_sent_at_ms

### 4.3. 서버 전송 기능

MVP에서는 구현과 디버깅이 쉬운 HTTP multipart 전송을 우선 사용한다.

전송 단위는 다음과 같다.

- JPEG frame
- JSON metadata

앱에서 제공해야 하는 서버 전송 기능은 다음과 같다.

- 서버 주소 입력
- start 전송
- stop 전송
- 프레임 + metadata 전송
- 전송 성공 수 표시
- 전송 실패 수 표시
- 마지막 전송 시각 표시

초기 MVP에서는 실시간 스트리밍 품질보다 정확한 프레임과 metadata를 안정적으로 보내는 것을 우선한다.

## 5. UI 구성

### 5.1. 메인 화면

메인 화면에는 다음 요소를 배치한다.

- 서버 주소 입력
- device_id 입력 또는 자동 생성
- camera_id 입력
- 카메라 선택
- 해상도 선택
- FPS 선택
- 프리뷰 화면
- start 버튼
- stop 버튼

### 5.2. 상태 영역

상태 영역에는 다음 정보를 표시한다.

- 현재 frame sequence
- 마지막 `device_timestamp_ms`
- 마지막 `device_monotonic_ns`
- 전송 성공 수
- 전송 실패 수
- 현재 FPS
- 프레임 drop 수
- focus lock 상태
- exposure lock 상태
- white balance lock 상태
- 현재 해상도
- 현재 camera id

## 6. 기술 구현 방향

### 6.1. 권장 기술 스택

- Kotlin
- CameraX
- 필요 시 Camera2 API
- OkHttp 또는 Ktor Client
- JSON 직렬화 라이브러리
- Android DataStore 또는 SharedPreferences

### 6.2. 구현 전략

초기 MVP는 CameraX 기반으로 구현한다.

CameraX를 사용하는 이유는 다음과 같다.

- 프리뷰 구현이 빠름
- 해상도 설정이 비교적 간단함
- ImageAnalysis 또는 ImageCapture 기반 프레임 처리 가능
- MVP 개발에 적합함

단, CameraX만으로 세밀한 카메라 제어가 부족할 경우 Camera2 interop 또는 Camera2 API를 병행한다.

특히 다음 항목은 Camera2 기반 제어가 필요할 수 있다.

- 수동 초점
- 수동 노출
- ISO 고정
- 셔터 시간 고정
- 센서 timestamp 확인
- 카메라 특성 조회

## 7. 비기능 요구사항

본 앱은 다음 비기능 요구사항을 만족해야 한다.

- 장시간 실행 중 앱이 비정상 종료되지 않아야 한다.
- 전송 실패가 발생해도 앱 전체가 중단되지 않아야 한다.
- 네트워크 실패 횟수를 기록해야 한다.
- 카메라 설정 적용 실패 여부를 확인할 수 있어야 한다.
- 프레임 sequence는 start 이후 단조 증가해야 한다.
- timestamp는 프레임 캡처 시점에 최대한 가깝게 생성해야 한다.
- 서버 수신 시각을 프레임 캡처 시각으로 사용하지 않아야 한다.

## 8. 향후 확장 기능

MVP 이후 다음 기능을 확장할 수 있다.

- calibration 모드
- runtime 수집 모드
- checkerboard 촬영 보조 모드
- 서버 heartbeat
- 네트워크 재시도
- 로컬 버퍼링
- 전송 실패 프레임 재전송
- 기기 시간 동기화 보조 기능
- 장치 상태 화면
- WebSocket 전송
- WebRTC 또는 RTMP 기반 영상 전송
- gRPC streaming 기반 ingest 구조
- 서버에서 수집 세션 관리

## 9. MVP 우선순위

개발 우선순위는 다음과 같이 잡는다.

1. 카메라 프리뷰 표시
2. 해상도 선택
3. FPS 목표값 설정
4. frame sequence 생성
5. timestamp 생성
6. JPEG 프레임 캡처
7. metadata 생성
8. 서버 주소 입력
9. HTTP multipart 전송
10. 전송 성공/실패 상태 표시
11. AF/AE/AWB lock 시도
12. 실제 카메라 설정 상태 metadata 기록

초기에는 카메라 제어를 완벽하게 만드는 것보다, 프레임과 metadata가 서버까지 정상적으로 도달하는 전체 흐름을 먼저 완성하는 것이 좋다.

## 10. 한 줄 정의

본 앱은 단순 스트리밍 앱이 아니라, 실내 3차원 재구성과 멀티카메라 시간 정렬을 위해 촬영 조건과 프레임 시간을 통제하는 Android 영상 수집 단말기 앱이다.
