package com.gc.collector.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gc.collector.camera.CapturedFrame
import com.gc.collector.model.CameraCaptureUiState
import com.gc.collector.model.CalibrationCaptureStateReducer
import com.gc.collector.model.CalibrationUploadOutcome
import com.gc.collector.model.CameraControlStatus
import com.gc.collector.model.CollectorUiState
import com.gc.collector.model.RuntimeFrameCaptureStateReducer
import com.gc.collector.model.SessionIdFactory
import com.gc.collector.model.StreamSessionState
import com.gc.collector.model.StreamSessionStateReducer
import com.gc.collector.model.UserAlertEventOutcome
import com.gc.collector.model.UserAlertStateReducer
import com.gc.collector.model.UserModeConnectionStateReducer
import com.gc.collector.network.FrameSendUiStateReducer
import com.gc.collector.network.GrpcEndpoint
import com.gc.collector.network.InternalCalibrationUploadOutcomeMapper
import com.gc.collector.network.InternalCalibrationUploadResult
import com.gc.collector.network.PhoneAlertSseCallHandle
import com.gc.collector.network.PhoneAlertSseClient
import com.gc.collector.network.PhoneAlertSseConnector
import com.gc.collector.network.PhoneAlertSseResult
import com.gc.collector.network.SendResult
import com.gc.collector.network.parseGrpcEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CollectorViewModel(
    private val phoneAlertSseConnector: PhoneAlertSseConnector,
    private val currentTimeMs: () -> Long,
    private val reconnectDelayMs: Long,
) : ViewModel() {
    constructor() : this(
        phoneAlertSseConnector = PhoneAlertSseClient(),
        currentTimeMs = { System.currentTimeMillis() },
        reconnectDelayMs = 1_000L,
    )

    private val _screenState = MutableStateFlow(CollectorScreenState())
    val screenState: StateFlow<CollectorScreenState> = _screenState.asStateFlow()
    private var runtimeFpsWindowStartedNs: Long? = null
    private var runtimeFramesInWindow: Int = 0
    private var userModeAlertJob: Job? = null
    private var userModeAlertCall: PhoneAlertSseCallHandle? = null

    fun setCollectorUiState(state: CollectorUiState) {
        _screenState.update { current ->
            current.copy(collectorUiState = state)
        }
    }

    fun updateCollectorUiState(transform: (CollectorUiState) -> CollectorUiState) {
        _screenState.update { current ->
            current.copy(collectorUiState = transform(current.collectorUiState))
        }
    }

    fun setCameraCaptureUiState(state: CameraCaptureUiState) {
        _screenState.update { current ->
            current.copy(cameraCaptureUiState = state)
        }
    }

    fun updateCameraCaptureUiState(transform: (CameraCaptureUiState) -> CameraCaptureUiState) {
        _screenState.update { current ->
            current.copy(cameraCaptureUiState = transform(current.cameraCaptureUiState))
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        updateCameraCaptureUiState { state ->
            state.withCameraPermissionResult(granted)
        }
    }

    fun onCameraReady() {
        updateCameraCaptureUiState { state ->
            state.withCameraReady()
        }
    }

    fun onCameraError(message: String) {
        updateCameraCaptureUiState { state ->
            state.withCameraError(message)
        }
    }

    fun onCameraControlStatus(status: CameraControlStatus) {
        updateCollectorUiState { state ->
            state.copy(cameraControlStatus = status)
        }
    }

    fun onNetworkStatusChanged(message: String) {
        updateCameraCaptureUiState { state ->
            state.withNetworkStatus(message)
        }
    }

    fun onCloseDetailsPanel() {
        updateCameraCaptureUiState { state ->
            state.closeDetailsPanel()
        }
    }

    fun onToggleDetailsPanel() {
        updateCameraCaptureUiState { state ->
            state.toggleDetailsPanel()
        }
    }

    fun onSingleCaptureRequested() {
        updateCameraCaptureUiState { state ->
            state.requestCalibrationCapture()
        }
    }

    fun onCalibrationUploadCompleted(
        frameSequence: Long,
        outcome: CalibrationUploadOutcome,
    ) {
        updateCameraCaptureUiState { state ->
            state.completeCalibrationUpload(
                frameSequence = frameSequence,
                outcome = outcome,
            )
        }
    }

    fun onCalibrationFrameCaptured(
        frame: CapturedFrame,
        uploadFrame: (CapturedFrame) -> InternalCalibrationUploadResult,
    ) {
        updateCollectorUiState { state ->
            state.copy(
                stats = CalibrationCaptureStateReducer.applyCapturedFrame(
                    stats = state.stats,
                    metadata = frame.metadata,
                ),
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val uploadResult = uploadFrame(frame)
            onCalibrationUploadCompleted(
                frameSequence = frame.metadata.frameSequence,
                outcome = InternalCalibrationUploadOutcomeMapper.toOutcome(uploadResult),
            )
        }
    }

    fun onStreamStartRequested(
        deviceTimestampMs: Long,
        deviceMonotonicNs: Long,
        startStream: (GrpcEndpoint) -> Result<Unit>,
        onSessionStarted: (String) -> Unit = {},
    ) {
        val current = screenState.value.collectorUiState
        val settings = current.settings
        val sessionId = SessionIdFactory.create(settings.deviceId, deviceTimestampMs)

        parseGrpcEndpoint(settings.serverUrl)
            .onSuccess { endpoint ->
                startStream(endpoint)
                    .onSuccess {
                        onSessionStarted(sessionId)
                        val nextState = StreamSessionStateReducer.startSucceeded(
                            state = current,
                            sessionId = sessionId,
                            deviceTimestampMs = deviceTimestampMs,
                            deviceMonotonicNs = deviceMonotonicNs,
                            endpointHost = endpoint.host,
                            endpointPort = endpoint.port,
                        )
                        resetRuntimeFpsWindow()
                        applyStreamSessionState(nextState)
                    }
                    .onFailure { error ->
                        onStreamStartFailed(
                            state = current,
                            message = error.message ?: "Failed to start gRPC stream",
                        )
                    }
            }
            .onFailure { error ->
                onStreamStartFailed(
                    state = current,
                    message = error.message ?: "Invalid gRPC endpoint",
                )
            }
    }

    fun onStreamStopRequested(stopStream: () -> Unit) {
        stopStream()
        applyStreamSessionState(StreamSessionStateReducer.stopped(screenState.value.collectorUiState))
    }

    fun resetRuntimeFpsWindow() {
        runtimeFpsWindowStartedNs = null
        runtimeFramesInWindow = 0
    }

    fun onRuntimeFrameCaptured(
        frame: CapturedFrame,
        sendFrame: (CapturedFrame) -> SendResult,
    ) {
        val current = screenState.value
        if (!current.collectorUiState.isCapturing) return

        val nextFrameState = RuntimeFrameCaptureStateReducer.applyCapturedFrame(
            stats = current.collectorUiState.stats,
            metadata = frame.metadata,
            sensorTimestampNs = frame.sensorTimestampNs,
            lastWindowStartedNs = runtimeFpsWindowStartedNs,
            framesInWindow = runtimeFramesInWindow,
        )
        runtimeFpsWindowStartedNs = nextFrameState.fpsWindowStartedNs
        runtimeFramesInWindow = nextFrameState.framesInWindow
        setCollectorUiState(current.collectorUiState.copy(stats = nextFrameState.stats))

        viewModelScope.launch(Dispatchers.IO) {
            onRuntimeFrameSendResult(sendFrame(frame))
        }
    }

    fun onRuntimeFrameSendResult(result: SendResult) {
        _screenState.update { current ->
            val nextState = FrameSendUiStateReducer.reduce(
                collectorUiState = current.collectorUiState,
                cameraCaptureUiState = current.cameraCaptureUiState,
                result = result,
            )
            current.copy(
                collectorUiState = nextState.collectorUiState,
                cameraCaptureUiState = nextState.cameraCaptureUiState,
            )
        }
    }

    fun onUserModeStartRequested(connectToServer: Boolean = false) {
        _screenState.update { current ->
            current.copy(
                userModeConnectionState = UserModeConnectionStateReducer.start(
                    current.userModeConnectionState,
                ),
            )
        }
        if (connectToServer) {
            startUserModeAlertLoop()
        }
    }

    fun onUserModeConnected(nowMs: Long) {
        _screenState.update { current ->
            current.copy(
                userModeConnectionState = UserModeConnectionStateReducer.connected(
                    state = current.userModeConnectionState,
                    nowMs = nowMs,
                ),
            )
        }
    }

    fun onUserModeConnectionFailed(message: String) {
        _screenState.update { current ->
            current.copy(
                userModeConnectionState = UserModeConnectionStateReducer.failed(
                    state = current.userModeConnectionState,
                    message = message,
                ),
            )
        }
    }

    fun onUserModeStreamCompleted() {
        _screenState.update { current ->
            current.copy(
                userModeConnectionState = UserModeConnectionStateReducer.completed(
                    current.userModeConnectionState,
                ),
            )
        }
    }

    fun onUserModeStopRequested(cancelConnection: () -> Unit = {}) {
        cancelConnection()
        userModeAlertCall?.cancel()
        userModeAlertCall = null
        userModeAlertJob?.cancel()
        userModeAlertJob = null
        _screenState.update { current ->
            current.copy(
                userModeConnectionState = UserModeConnectionStateReducer.stopped(
                    current.userModeConnectionState,
                ),
            )
        }
    }

    fun onUserModeAlertData(
        data: String,
        nowMs: Long,
    ): UserAlertEventOutcome {
        var outcome: UserAlertEventOutcome? = null
        _screenState.update { current ->
            val reduced = UserAlertStateReducer.reduceSseData(
                state = current.userAlertState,
                data = data,
                nowMs = nowMs,
            )
            outcome = reduced.second
            current.copy(userAlertState = reduced.first)
        }
        return checkNotNull(outcome)
    }

    private fun startUserModeAlertLoop() {
        userModeAlertJob?.cancel()
        userModeAlertCall?.cancel()
        userModeAlertCall = null
        userModeAlertJob = viewModelScope.launch(Dispatchers.IO) {
            while (screenState.value.userModeConnectionState.enabled) {
                val settings = screenState.value.collectorUiState.settings
                val callResult = phoneAlertSseConnector.open(
                    baseUrl = settings.calibrationHttpBaseUrl,
                    deviceId = settings.deviceId,
                    onEvent = { event ->
                        onUserModeAlertData(
                            data = event.data,
                            nowMs = currentTimeMs(),
                        )
                    },
                )
                val call = callResult.getOrNull()
                if (call == null) {
                    val error = callResult.exceptionOrNull()
                    onUserModeConnectionFailed(error?.message ?: error?.javaClass?.simpleName ?: "Failed to open SSE stream")
                    delayBeforeReconnectIfEnabled()
                    continue
                }

                userModeAlertCall = call
                onUserModeConnected(currentTimeMs())
                val result = call.execute()
                if (userModeAlertCall === call) {
                    userModeAlertCall = null
                }
                applyUserModeSseResult(result)
                delayBeforeReconnectIfEnabled()
            }
        }
    }

    private fun applyUserModeSseResult(result: PhoneAlertSseResult) {
        if (!screenState.value.userModeConnectionState.enabled) {
            return
        }

        when (result) {
            PhoneAlertSseResult.Completed -> onUserModeStreamCompleted()
            PhoneAlertSseResult.Cancelled -> Unit
            is PhoneAlertSseResult.NetworkError -> onUserModeConnectionFailed(result.message)
            is PhoneAlertSseResult.ServerError -> onUserModeConnectionFailed("HTTP ${result.code}: ${result.message}")
            is PhoneAlertSseResult.StreamError -> onUserModeConnectionFailed(result.message)
        }
    }

    private suspend fun delayBeforeReconnectIfEnabled() {
        if (screenState.value.userModeConnectionState.enabled) {
            delay(reconnectDelayMs)
        }
    }

    private fun onStreamStartFailed(
        state: CollectorUiState,
        message: String,
    ) {
        applyStreamSessionState(
            StreamSessionStateReducer.startFailed(
                state = state,
                message = message,
            ),
        )
    }

    private fun applyStreamSessionState(nextState: StreamSessionState) {
        _screenState.update { current ->
            current.copy(
                collectorUiState = nextState.uiState,
                cameraCaptureUiState = current.cameraCaptureUiState.withNetworkStatus(nextState.networkStatus),
            )
        }
    }
}
