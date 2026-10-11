package com.system.screenrecorder.service

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.system.screenrecorder.R
import com.system.screenrecorder.model.RecordingState
import com.system.screenrecorder.ui.PermissionSetupActivity
import com.system.screenrecorder.ui.RecordPromptActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScreenRecorderTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var stateObserverJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(ScreenRecorderService.recordingState.value)

        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            ScreenRecorderService.recordingState.collectLatest { state ->
                updateTileState(state)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        stateObserverJob?.cancel()
    }

    private fun updateTileState(state: RecordingState) {
        val tile = qsTile ?: return
        when (state) {
            RecordingState.RECORDING,
            RecordingState.PAUSED,
            RecordingState.COUNTDOWN -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.tile_recording)
            }
            RecordingState.IDLE,
            RecordingState.STOPPED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_name)
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val currentState = ScreenRecorderService.recordingState.value

        if (currentState == RecordingState.RECORDING || currentState == RecordingState.PAUSED) {
            // Stop recording
            val stopIntent = Intent(this, ScreenRecorderService::class.java).apply {
                action = ScreenRecorderService.ACTION_STOP
            }
            startService(stopIntent)
            return
        }

        // Check required permissions
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!hasOverlay || !hasAudio || !hasNotification) {
            // Open One UI Permission Setup Dialog
            val intent = Intent(this, PermissionSetupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            launchActivityFromTile(intent)
        } else {
            // Open One UI Record Preparation Dialog
            val intent = Intent(this, RecordPromptActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            launchActivityFromTile(intent)
        }
    }

    private fun launchActivityFromTile(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
