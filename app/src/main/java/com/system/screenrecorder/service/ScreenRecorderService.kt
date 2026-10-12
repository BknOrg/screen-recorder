package com.system.screenrecorder.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.system.screenrecorder.R
import com.system.screenrecorder.data.PreferencesManager
import com.system.screenrecorder.model.AudioSourceMode
import com.system.screenrecorder.model.RecordingState
import com.system.screenrecorder.model.VideoFrameRate
import com.system.screenrecorder.model.VideoResolution
import com.system.screenrecorder.overlay.CountdownOverlayManager
import com.system.screenrecorder.overlay.DoodleOverlayManager
import com.system.screenrecorder.overlay.FloatingToolbarManager
import com.system.screenrecorder.recorder.ScreenRecordingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class ScreenRecorderService : Service() {

    companion object {
        private const val TAG = "ScreenRecorderService"
        private const val NOTIFICATION_CHANNEL_ID = "screen_recording_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.system.screenrecorder.ACTION_START"
        const val ACTION_PAUSE = "com.system.screenrecorder.ACTION_PAUSE"
        const val ACTION_RESUME = "com.system.screenrecorder.ACTION_RESUME"
        const val ACTION_STOP = "com.system.screenrecorder.ACTION_STOP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_AUDIO_MODE = "extra_audio_mode"

        private val _recordingState = MutableStateFlow(RecordingState.IDLE)
        val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

        private val _durationSeconds = MutableStateFlow(0L)
        val durationSeconds: StateFlow<Long> = _durationSeconds.asStateFlow()
    }

    private lateinit var recorderEngine: ScreenRecordingEngine
    private lateinit var floatingToolbar: FloatingToolbarManager
    private lateinit var doodleOverlay: DoodleOverlayManager
    private lateinit var countdownOverlay: CountdownOverlayManager
    private lateinit var prefsManager: PreferencesManager

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var tickerJob: Job? = null
    private var currentSeconds = 0L

    private fun updateRecordingState(newState: RecordingState) {
        _recordingState.value = newState
        ScreenRecorderTileService.requestTileUpdate(this)
    }

    override fun onCreate() {
        super.onCreate()
        recorderEngine = ScreenRecordingEngine(this)
        floatingToolbar = FloatingToolbarManager(this)
        doodleOverlay = DoodleOverlayManager(this)
        countdownOverlay = CountdownOverlayManager(this)
        prefsManager = PreferencesManager(this)

        createNotificationChannel()
        setupOverlayListeners()

        recorderEngine.onRecordingStoppedListener = { uri ->
            updateRecordingState(RecordingState.STOPPED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            floatingToolbar.dismiss()
            doodleOverlay.dismiss()
            countdownOverlay.dismiss()
            tickerJob?.cancel()

            if (uri != null) {
                Toast.makeText(this, "Rekaman layar disimpan ke Galeri", Toast.LENGTH_SHORT).show()
            }
            stopSelf()
        }
    }

    private fun setupOverlayListeners() {
        floatingToolbar.onPauseResumeListener = {
            if (_recordingState.value == RecordingState.RECORDING) {
                pauseRecording()
            } else if (_recordingState.value == RecordingState.PAUSED) {
                resumeRecording()
            }
        }

        floatingToolbar.onStopListener = {
            stopRecording()
        }

        floatingToolbar.onDoodleToggleListener = {
            if (doodleOverlay.isDoodleActive) {
                doodleOverlay.dismiss()
            } else {
                doodleOverlay.showDoodleOverlay()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val audioModeId = intent.getIntExtra(EXTRA_AUDIO_MODE, prefsManager.audioMode.id)
                val audioMode = AudioSourceMode.fromId(audioModeId)

                if (resultCode != 0 && resultData != null) {
                    startRecordingFlow(resultCode, resultData, audioMode)
                }
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecordingFlow(
        resultCode: Int,
        resultData: Intent,
        audioMode: AudioSourceMode
    ) {
        val initialNotification = buildNotification(0L, false)

        // Dynamic foreground service type based on audio mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (audioMode == AudioSourceMode.MEDIA_AND_MIC) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, initialNotification, fgType)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        updateRecordingState(RecordingState.COUNTDOWN)
        val countdownSec = prefsManager.countdownSeconds

        if (countdownSec > 0) {
            countdownOverlay.showCountdown(countdownSec) {
                beginActualRecording(resultCode, resultData, audioMode)
            }
        } else {
            beginActualRecording(resultCode, resultData, audioMode)
        }
    }

    private fun beginActualRecording(
        resultCode: Int,
        resultData: Intent,
        audioMode: AudioSourceMode
    ) {
        val settings = prefsManager.getSettings().copy(audioMode = audioMode)
        val started = recorderEngine.startRecording(resultCode, resultData, settings)

        if (started) {
            updateRecordingState(RecordingState.RECORDING)
            currentSeconds = 0L
            _durationSeconds.value = 0L

            if (settings.showFloatingToolbar) {
                floatingToolbar.showToolbar()
                floatingToolbar.updateDuration(0L)
                floatingToolbar.updatePausedState(false)
            }

            startTicker()
        } else {
            Toast.makeText(this, "Gagal memulai perekaman layar", Toast.LENGTH_SHORT).show()
            updateRecordingState(RecordingState.IDLE)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (_recordingState.value == RecordingState.RECORDING) {
                    currentSeconds++
                    _durationSeconds.value = currentSeconds
                    if (floatingToolbar.isShowing) {
                        floatingToolbar.updateDuration(currentSeconds)
                    }
                    updateNotification(currentSeconds, false)
                }
            }
        }
    }

    private fun pauseRecording() {
        recorderEngine.pauseRecording()
        updateRecordingState(RecordingState.PAUSED)
        if (floatingToolbar.isShowing) {
            floatingToolbar.updatePausedState(true)
        }
        updateNotification(currentSeconds, true)
    }

    private fun resumeRecording() {
        recorderEngine.resumeRecording()
        updateRecordingState(RecordingState.RECORDING)
        if (floatingToolbar.isShowing) {
            floatingToolbar.updatePausedState(false)
        }
        updateNotification(currentSeconds, false)
    }

    private fun stopRecording() {
        tickerJob?.cancel()
        serviceScope.launch(Dispatchers.IO) {
            recorderEngine.stopRecording()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(seconds: Long, isPaused: Boolean): Notification {
        val minutes = seconds / 60
        val remainingSecs = seconds % 60
        val timeString = String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSecs)

        val pauseResumeAction = if (isPaused) {
            val resumeIntent = Intent(this, ScreenRecorderService::class.java).apply {
                action = ACTION_RESUME
            }
            val resumePendingIntent = PendingIntent.getService(
                this,
                101,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification_recording,
                getString(R.string.action_resume),
                resumePendingIntent
            ).build()
        } else {
            val pauseIntent = Intent(this, ScreenRecorderService::class.java).apply {
                action = ACTION_PAUSE
            }
            val pausePendingIntent = PendingIntent.getService(
                this,
                102,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                R.drawable.ic_notification_recording,
                getString(R.string.action_pause),
                pausePendingIntent
            ).build()
        }

        val stopIntent = Intent(this, ScreenRecorderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            103,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification_recording,
            getString(R.string.action_stop),
            stopPendingIntent
        ).build()

        val title = if (isPaused) {
            getString(R.string.notification_title_paused)
        } else {
            getString(R.string.notification_title_recording)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(timeString)
            .setSmallIcon(R.drawable.ic_notification_recording)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun updateNotification(seconds: Long, isPaused: Boolean) {
        val notification = buildNotification(seconds, isPaused)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (recorderEngine.isRecordingActive()) {
            recorderEngine.stopRecording()
        }
        updateRecordingState(RecordingState.IDLE)
        tickerJob?.cancel()
        floatingToolbar.dismiss()
        doodleOverlay.dismiss()
        countdownOverlay.dismiss()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
