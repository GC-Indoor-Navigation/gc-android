package com.gc.collector.ui.screen

import com.gc.collector.camera.CapturedFrame
import com.gc.collector.feedback.PhoneAlertFeedbackPlayer
import com.gc.collector.model.CameraCaptureUiState
import com.gc.collector.model.CameraControlStatus
import com.gc.collector.model.CalibrationUploadOutcome
import com.gc.collector.model.CollectorUiState
import com.gc.collector.model.FrameMetadata
import com.gc.collector.model.PhoneAlertFeedbackPolicy
import com.gc.collector.model.PhoneAlertFeedbackPolicyMapper
import com.gc.collector.model.ProcessingAlertSeverity
import com.gc.collector.model.UserAlertEventOutcome
import com.gc.collector.model.UserModeConnectionStatus
import com.gc.collector.network.GrpcEndpoint
import com.gc.collector.network.InternalCalibrationUploadResult
import com.gc.collector.network.PhoneAlertSseCallHandle
import com.gc.collector.network.PhoneAlertSseConnector
import com.gc.collector.network.PhoneAlertSseResult
import com.gc.collector.network.SendResult
import com.gc.collector.network.SseEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectorViewModelTest {
    @Test
    fun initialStateUsesDefaultCollectorAndCameraCaptureState() {
        val viewModel = CollectorViewModel()
        val state = viewModel.screenState.value

        assertFalse(state.collectorUiState.isCapturing)
        assertNull(state.collectorUiState.sessionId)
        assertEquals("Camera preview not started", state.cameraCaptureUiState.cameraStatus)
        assertEquals("gRPC disconnected", state.cameraCaptureUiState.networkStatus)
        assertFalse(state.cameraCaptureUiState.detailsPanelOpen)
        assertEquals("Calibration idle", state.cameraCaptureUiState.calibrationStatus)
        assertFalse(state.userModeConnectionState.enabled)
        assertEquals(UserModeConnectionStatus.Idle, state.userModeConnectionState.status)
        assertEquals("User mode idle", state.userAlertState.status)
    }

    @Test
    fun setCollectorUiStateReplacesCollectorStateOnly() {
        val viewModel = CollectorViewModel()

        viewModel.setCollectorUiState(CollectorUiState(isCapturing = true, sessionId = "session_01"))

        val state = viewModel.screenState.value
        assertTrue(state.collectorUiState.isCapturing)
        assertEquals("session_01", state.collectorUiState.sessionId)
        assertEquals("Camera preview not started", state.cameraCaptureUiState.cameraStatus)
    }

    @Test
    fun updateCollectorUiStateTransformsCollectorStateOnly() {
        val viewModel = CollectorViewModel()

        viewModel.updateCollectorUiState { state ->
            state.copy(isCapturing = true)
        }

        val state = viewModel.screenState.value
        assertTrue(state.collectorUiState.isCapturing)
        assertEquals("gRPC disconnected", state.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun setCameraCaptureUiStateReplacesCameraCaptureStateOnly() {
        val viewModel = CollectorViewModel()

        viewModel.setCameraCaptureUiState(CameraCaptureUiState(networkStatus = "gRPC streaming"))

        val state = viewModel.screenState.value
        assertFalse(state.collectorUiState.isCapturing)
        assertEquals("gRPC streaming", state.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun updateCameraCaptureUiStateTransformsCameraCaptureStateOnly() {
        val viewModel = CollectorViewModel()

        viewModel.updateCameraCaptureUiState { state ->
            state.withCameraReady()
        }

        val state = viewModel.screenState.value
        assertNull(state.collectorUiState.sessionId)
        assertEquals("Back camera preview ready", state.cameraCaptureUiState.cameraStatus)
    }

    @Test
    fun cameraStatusEventsUpdateCameraCaptureState() {
        val viewModel = CollectorViewModel()

        viewModel.onCameraPermissionResult(granted = true)
        assertEquals("Camera permission granted", viewModel.screenState.value.cameraCaptureUiState.cameraStatus)

        viewModel.onCameraReady()
        assertEquals("Back camera preview ready", viewModel.screenState.value.cameraCaptureUiState.cameraStatus)

        viewModel.onCameraError("Camera unavailable")
        assertEquals("Camera unavailable", viewModel.screenState.value.cameraCaptureUiState.cameraStatus)
    }

    @Test
    fun cameraControlStatusEventUpdatesCollectorState() {
        val viewModel = CollectorViewModel()
        val status = CameraControlStatus(
            focusLockSupported = true,
            focusLockApplied = true,
            exposureLockSupported = false,
            whiteBalanceLockSupported = true,
            whiteBalanceLockApplied = true,
        )

        viewModel.onCameraControlStatus(status)

        assertEquals(status, viewModel.screenState.value.collectorUiState.cameraControlStatus)
    }

    @Test
    fun networkStatusEventUpdatesCameraCaptureState() {
        val viewModel = CollectorViewModel()

        viewModel.onNetworkStatusChanged("gRPC streaming")

        assertEquals("gRPC streaming", viewModel.screenState.value.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun detailPanelEventsUpdateCameraCaptureState() {
        val viewModel = CollectorViewModel()

        viewModel.onToggleDetailsPanel()
        assertTrue(viewModel.screenState.value.cameraCaptureUiState.detailsPanelOpen)

        viewModel.onCloseDetailsPanel()
        assertFalse(viewModel.screenState.value.cameraCaptureUiState.detailsPanelOpen)
    }

    @Test
    fun calibrationEventsUpdateCameraCaptureState() {
        val viewModel = CollectorViewModel()

        viewModel.onSingleCaptureRequested()
        assertEquals("Calibration capture requested", viewModel.screenState.value.cameraCaptureUiState.calibrationStatus)
        assertTrue(viewModel.screenState.value.cameraCaptureUiState.singleCaptureInProgress)

        viewModel.onCalibrationUploadCompleted(
            frameSequence = 17L,
            outcome = CalibrationUploadOutcome.Uploaded,
        )
        assertEquals("Calibration uploaded: 17", viewModel.screenState.value.cameraCaptureUiState.calibrationStatus)
        assertFalse(viewModel.screenState.value.cameraCaptureUiState.singleCaptureInProgress)
    }

    @Test
    fun calibrationFrameCapturedUpdatesStats() {
        val viewModel = CollectorViewModel()

        viewModel.onCalibrationFrameCaptured(sampleFrame(frameSequence = 21L)) {
            InternalCalibrationUploadResult.Uploaded
        }

        val stats = viewModel.screenState.value.collectorUiState.stats
        assertEquals(21L, stats.frameSequence)
        assertEquals(1_775_404_088_703L, stats.lastDeviceTimestampMs)
        assertEquals(8_234_567_812_345L, stats.lastDeviceMonotonicNs)
    }

    @Test
    fun runtimeFrameCapturedDoesNothingWhenNotCapturing() {
        val viewModel = CollectorViewModel()
        var sendCalled = false

        viewModel.onRuntimeFrameCaptured(sampleFrame(frameSequence = 9L)) {
            sendCalled = true
            SendResult.Sent
        }

        val state = viewModel.screenState.value
        assertEquals(0L, state.collectorUiState.stats.frameSequence)
        assertFalse(sendCalled)
    }

    @Test
    fun runtimeFrameCapturedUpdatesStatsWhenCapturing() {
        val viewModel = CollectorViewModel()
        viewModel.setCollectorUiState(CollectorUiState(isCapturing = true))

        viewModel.onRuntimeFrameCaptured(
            frame = sampleFrame(frameSequence = 9L, sensorTimestampNs = 123_000L),
            sendFrame = { SendResult.NotStarted },
        )

        val stats = viewModel.screenState.value.collectorUiState.stats
        assertEquals(9L, stats.frameSequence)
        assertEquals(1_775_404_088_703L, stats.lastDeviceTimestampMs)
        assertEquals(8_234_567_812_345L, stats.lastDeviceMonotonicNs)
    }

    @Test
    fun runtimeFrameSendResultUpdatesCollectorAndCameraCaptureState() {
        val viewModel = CollectorViewModel()

        viewModel.onRuntimeFrameSendResult(SendResult.Sent)

        val state = viewModel.screenState.value
        assertEquals(1L, state.collectorUiState.stats.sentCount)
        assertEquals("gRPC streaming", state.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun streamStartSuccessStartsCaptureAndUpdatesNetworkStatus() {
        val viewModel = CollectorViewModel()
        var capturedEndpoint: GrpcEndpoint? = null
        var loggedSessionId: String? = null

        viewModel.onStreamStartRequested(
            deviceTimestampMs = 1_779_055_200_000L,
            deviceMonotonicNs = 12_345_678_900L,
            startStream = { endpoint ->
                capturedEndpoint = endpoint
                Result.success(Unit)
            },
            onSessionStarted = { sessionId ->
                loggedSessionId = sessionId
            },
        )

        val state = viewModel.screenState.value
        assertTrue(state.collectorUiState.isCapturing)
        assertEquals("localhost", capturedEndpoint?.host)
        assertEquals(50051, capturedEndpoint?.port)
        assertTrue(capturedEndpoint?.usePlaintext == true)
        assertEquals(state.collectorUiState.sessionId, loggedSessionId)
        assertTrue(state.collectorUiState.sessionId?.startsWith("android_device_001_") == true)
        assertEquals(0L, state.collectorUiState.stats.frameSequence)
        assertEquals(1_779_055_200_000L, state.collectorUiState.stats.lastDeviceTimestampMs)
        assertEquals(12_345_678_900L, state.collectorUiState.stats.lastDeviceMonotonicNs)
        assertEquals("gRPC connected to localhost:50051", state.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun streamStartFailureKeepsCaptureStoppedAndUpdatesNetworkStatus() {
        val viewModel = CollectorViewModel()

        viewModel.onStreamStartRequested(
            deviceTimestampMs = 1_779_055_200_000L,
            deviceMonotonicNs = 12_345_678_900L,
            startStream = {
                Result.failure(IllegalStateException("socket unavailable"))
            },
        )

        val state = viewModel.screenState.value
        assertFalse(state.collectorUiState.isCapturing)
        assertNull(state.collectorUiState.sessionId)
        assertEquals(1L, state.collectorUiState.stats.failedCount)
        assertEquals("socket unavailable", state.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun streamStartInvalidEndpointDoesNotStartStream() {
        val viewModel = CollectorViewModel()
        var startCalled = false
        viewModel.updateCollectorUiState { state ->
            state.copy(settings = state.settings.copy(serverUrl = " "))
        }

        viewModel.onStreamStartRequested(
            deviceTimestampMs = 1_779_055_200_000L,
            deviceMonotonicNs = 12_345_678_900L,
            startStream = {
                startCalled = true
                Result.success(Unit)
            },
        )

        val state = viewModel.screenState.value
        assertFalse(startCalled)
        assertFalse(state.collectorUiState.isCapturing)
        assertNull(state.collectorUiState.sessionId)
        assertEquals(1L, state.collectorUiState.stats.failedCount)
        assertEquals("Server address is empty", state.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun streamStopStopsCaptureAndRunsStopCallback() {
        val viewModel = CollectorViewModel()
        var stopCalled = false
        viewModel.setCollectorUiState(CollectorUiState(isCapturing = true, sessionId = "session_01"))

        viewModel.onStreamStopRequested {
            stopCalled = true
        }

        val state = viewModel.screenState.value
        assertTrue(stopCalled)
        assertFalse(state.collectorUiState.isCapturing)
        assertNull(state.collectorUiState.sessionId)
        assertEquals("gRPC stopped", state.cameraCaptureUiState.networkStatus)
    }

    @Test
    fun userModeStartAndConnectedUpdateConnectionState() {
        val viewModel = CollectorViewModel()

        viewModel.onUserModeStartRequested()
        assertTrue(viewModel.screenState.value.userModeConnectionState.enabled)
        assertEquals(UserModeConnectionStatus.Connecting, viewModel.screenState.value.userModeConnectionState.status)
        assertEquals(1L, viewModel.screenState.value.userModeConnectionState.connectAttempt)

        viewModel.onUserModeConnected(nowMs = 1234L)

        val state = viewModel.screenState.value.userModeConnectionState
        assertTrue(state.enabled)
        assertEquals(UserModeConnectionStatus.Connected, state.status)
        assertEquals(1234L, state.lastConnectedAtMs)
    }

    @Test
    fun userModeFailureWhileEnabledSchedulesReconnect() {
        val viewModel = CollectorViewModel()

        viewModel.onUserModeStartRequested()
        viewModel.onUserModeConnectionFailed("network closed")

        val state = viewModel.screenState.value.userModeConnectionState
        assertTrue(state.enabled)
        assertEquals(UserModeConnectionStatus.Reconnecting, state.status)
        assertEquals(1L, state.reconnectCount)
        assertEquals("network closed", state.lastError)
    }

    @Test
    fun userModeStopCancelsConnectionAndDisablesMode() {
        val viewModel = CollectorViewModel()
        var cancelCalled = false

        viewModel.onUserModeStartRequested()
        viewModel.onUserModeStopRequested {
            cancelCalled = true
        }

        val state = viewModel.screenState.value.userModeConnectionState
        assertTrue(cancelCalled)
        assertFalse(state.enabled)
        assertEquals(UserModeConnectionStatus.Stopped, state.status)
    }

    @Test
    fun userModeStreamCompletedWhileEnabledSchedulesReconnect() {
        val viewModel = CollectorViewModel()

        viewModel.onUserModeStartRequested()
        viewModel.onUserModeConnected(nowMs = 1234L)
        viewModel.onUserModeStreamCompleted()

        val state = viewModel.screenState.value.userModeConnectionState
        assertTrue(state.enabled)
        assertEquals(UserModeConnectionStatus.Reconnecting, state.status)
        assertEquals(1L, state.reconnectCount)
    }

    @Test
    fun userModeAlertDataUpdatesAlertStateAndReturnsOutcome() {
        val viewModel = CollectorViewModel()

        val outcome = viewModel.onUserModeAlertData(
            data = sampleAlertPayload(eventId = "alert-1", severity = "danger"),
            nowMs = 1_780_624_971_101L,
        )

        val state = viewModel.screenState.value.userAlertState
        assertTrue(outcome is UserAlertEventOutcome.Accepted)
        assertEquals("alert-1", state.latestAlert?.eventId)
        assertEquals(ProcessingAlertSeverity.Danger, state.latestAlert?.severity)
        assertEquals(1L, state.receivedCount)
    }

    @Test
    fun userModeAlertDataDropsDuplicate() {
        val viewModel = CollectorViewModel()

        viewModel.onUserModeAlertData(
            data = sampleAlertPayload(eventId = "alert-1"),
            nowMs = 1_780_624_971_101L,
        )
        val outcome = viewModel.onUserModeAlertData(
            data = sampleAlertPayload(eventId = "alert-1"),
            nowMs = 1_780_624_971_101L,
        )

        val state = viewModel.screenState.value.userAlertState
        assertTrue(outcome is UserAlertEventOutcome.Duplicate)
        assertEquals(1L, state.receivedCount)
        assertEquals(1L, state.duplicateCount)
    }

    @Test
    fun userModeStartWithServerConnectionProcessesSseAlertAndCancelsOnStop() {
        val cancelled = AtomicBoolean(false)
        val executed = CountDownLatch(1)
        val connector = PhoneAlertSseConnector { baseUrl, deviceId, onEvent ->
            assertEquals("http://localhost:8080", baseUrl)
            assertEquals("android_device_001", deviceId)
            Result.success(
                object : PhoneAlertSseCallHandle {
                    override fun execute(): PhoneAlertSseResult {
                        onEvent(
                            SseEvent(
                                event = "processing_alert",
                                data = sampleAlertPayload(eventId = "live-alert", severity = "danger"),
                            ),
                        )
                        executed.countDown()
                        while (!cancelled.get()) {
                            Thread.sleep(5L)
                        }
                        return PhoneAlertSseResult.Cancelled
                    }

                    override fun cancel() {
                        cancelled.set(true)
                    }
                },
            )
        }
        val viewModel = CollectorViewModel(
            phoneAlertSseConnector = connector,
            currentTimeMs = { 1_780_624_971_101L },
            reconnectDelayMs = 10L,
        )

        viewModel.onUserModeStartRequested(connectToServer = true)

        assertTrue(executed.await(1, TimeUnit.SECONDS))
        waitUntil {
            viewModel.screenState.value.userAlertState.latestAlert?.eventId == "live-alert"
        }
        assertEquals(UserModeConnectionStatus.Connected, viewModel.screenState.value.userModeConnectionState.status)
        assertEquals(ProcessingAlertSeverity.Danger, viewModel.screenState.value.userAlertState.latestAlert?.severity)

        viewModel.onUserModeStopRequested()

        waitUntil { !viewModel.screenState.value.userModeConnectionState.enabled }
        assertTrue(cancelled.get())
        assertEquals(UserModeConnectionStatus.Stopped, viewModel.screenState.value.userModeConnectionState.status)
    }

    @Test
    fun userModeStartWithOpenFailureSchedulesReconnect() {
        val connector = PhoneAlertSseConnector { _, _, _ ->
            Result.failure(IllegalStateException("server unavailable"))
        }
        val viewModel = CollectorViewModel(
            phoneAlertSseConnector = connector,
            currentTimeMs = { 1_780_624_971_101L },
            reconnectDelayMs = 10L,
        )

        viewModel.onUserModeStartRequested(connectToServer = true)

        waitUntil {
            viewModel.screenState.value.userModeConnectionState.reconnectCount > 0L
        }
        val state = viewModel.screenState.value.userModeConnectionState
        assertTrue(state.enabled)
        assertEquals(UserModeConnectionStatus.Reconnecting, state.status)
        assertEquals("server unavailable", state.lastError)

        viewModel.onUserModeStopRequested()
    }

    @Test
    fun userModeFakeSseIntegrationAcceptsOnlyValidUniqueAlertsForFeedback() {
        val cancelled = AtomicBoolean(false)
        val executed = CountDownLatch(1)
        val playedPolicies = CopyOnWriteArrayList<PhoneAlertFeedbackPolicy>()
        val connector = PhoneAlertSseConnector { _, _, onEvent ->
            Result.success(
                object : PhoneAlertSseCallHandle {
                    override fun execute(): PhoneAlertSseResult {
                        onEvent(
                            SseEvent(
                                event = "processing_alert",
                                data = sampleAlertPayload(eventId = "accepted-warning", severity = "warning"),
                            ),
                        )
                        onEvent(
                            SseEvent(
                                event = "processing_alert",
                                data = sampleAlertPayload(eventId = "accepted-warning", severity = "warning"),
                            ),
                        )
                        onEvent(
                            SseEvent(
                                event = "processing_alert",
                                data = sampleAlertPayload(eventId = "expired-danger", severity = "danger"),
                            ),
                        )
                        onEvent(
                            SseEvent(
                                event = "processing_alert",
                                data = sampleAlertPayload(eventId = "accepted-danger", severity = "danger"),
                            ),
                        )
                        onEvent(
                            SseEvent(
                                event = "processing_alert",
                                data = "{not-json",
                            ),
                        )
                        executed.countDown()
                        while (!cancelled.get()) {
                            Thread.sleep(5L)
                        }
                        return PhoneAlertSseResult.Cancelled
                    }

                    override fun cancel() {
                        cancelled.set(true)
                    }
                },
            )
        }
        val now = 1_780_624_971_101L
        val expiredNow = 1_780_624_971_103L
        var timeCallCount = 0
        val viewModel = CollectorViewModel(
            phoneAlertSseConnector = connector,
            phoneAlertFeedbackPlayer = PhoneAlertFeedbackPlayer { policy ->
                playedPolicies += policy
            },
            currentTimeMs = {
                timeCallCount += 1
                if (timeCallCount == 4) expiredNow else now
            },
            reconnectDelayMs = 10L,
        )

        viewModel.onUserModeStartRequested(connectToServer = true)

        assertTrue(executed.await(1, TimeUnit.SECONDS))
        waitUntil {
            viewModel.screenState.value.userAlertState.parseFailureCount == 1L
        }

        val alertState = viewModel.screenState.value.userAlertState
        assertEquals("accepted-danger", alertState.latestAlert?.eventId)
        assertEquals(2L, alertState.receivedCount)
        assertEquals(1L, alertState.duplicateCount)
        assertEquals(1L, alertState.expiredCount)
        assertEquals(1L, alertState.parseFailureCount)
        assertEquals(setOf("accepted-warning", "accepted-danger"), alertState.handledEventIds)
        assertEquals(
            listOf(
                PhoneAlertFeedbackPolicyMapper.fromSeverity(ProcessingAlertSeverity.Warning),
                PhoneAlertFeedbackPolicyMapper.fromSeverity(ProcessingAlertSeverity.Danger),
            ),
            playedPolicies,
        )

        viewModel.onUserModeStopRequested()
        waitUntil { cancelled.get() }
    }

    private fun sampleFrame(
        frameSequence: Long,
        sensorTimestampNs: Long = 1_000L,
    ): CapturedFrame {
        return CapturedFrame(
            jpegBytes = byteArrayOf(1, 2, 3),
            metadata = FrameMetadata(
                cameraId = "camera_01",
                deviceId = "device_01",
                frameSequence = frameSequence,
                sessionId = "session_01",
                deviceTimestampMs = 1_775_404_088_703L,
                deviceMonotonicNs = 8_234_567_812_345L,
                width = 1280,
                height = 720,
                fpsTarget = 10,
                focusMode = "locked",
                focusLocked = true,
                exposureLocked = true,
                whiteBalanceLocked = true,
                zoomDisabled = true,
                orientationDeg = 90,
                focusLockRequested = true,
                focusLockSupport = "supported",
                focusLockApplied = "applied",
                exposureLockRequested = true,
                exposureLockSupport = "supported",
                exposureLockApplied = "applied",
                whiteBalanceLockRequested = true,
                whiteBalanceLockSupport = "supported",
                whiteBalanceLockApplied = "applied",
                fpsTargetSupport = "supported",
                resolutionSupport = "supported",
                manualExposureSupport = "supported",
                manualExposureRequested = false,
                manualExposureApplied = "not_applied",
                isoRequested = 400,
                isoApplied = null,
                exposureTimeNsRequested = 10_000_000L,
                exposureTimeNsApplied = null,
                focalLengthMm = null,
            ),
            sensorTimestampNs = sensorTimestampNs,
        )
    }

    private fun sampleAlertPayload(
        eventId: String,
        severity: String = "warning",
    ): String {
        return """
            {
              "event_id": "$eventId",
              "frame_set_id": 100,
              "relay_run_id": 1,
              "timestamp_ms": 1780624911102,
              "severity": "$severity",
              "distance_m": 0.62,
              "joint": "pelvis",
              "obstacle_id": "unknown",
              "ttl_ms": 60000,
              "source": {
                "processor": "mmpose_triangulation",
                "camera_devices": ["android_device_001"]
              },
              "received_at_ms": 1780624911413,
              "expires_at_ms": 1780624971102,
              "routing": {
                "camera_devices": ["android_device_001"],
                "session_id": null,
                "delivery_status": "not_delivered"
              }
            }
        """.trimIndent()
    }

    private fun waitUntil(
        timeoutMs: Long = 1_000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return
            }
            Thread.sleep(10L)
        }
        assertTrue("condition was not met before timeout", condition())
    }
}
