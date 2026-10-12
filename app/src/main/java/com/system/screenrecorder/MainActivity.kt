package com.system.screenrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.system.screenrecorder.data.PreferencesManager
import com.system.screenrecorder.data.VideoRepository
import com.system.screenrecorder.model.RecordedVideo
import com.system.screenrecorder.service.ScreenRecorderService
import com.system.screenrecorder.ui.PermissionSetupActivity
import com.system.screenrecorder.ui.RecordPromptActivity
import com.system.screenrecorder.ui.components.OneUIHeader
import com.system.screenrecorder.ui.recordings.RecordingsTabContent
import com.system.screenrecorder.ui.settings.SettingsTabContent
import com.system.screenrecorder.ui.theme.OneUITheme
import com.system.screenrecorder.ui.theme.ScreenRecorderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: PreferencesManager
    private lateinit var videoRepository: VideoRepository

    private var hasOverlay by mutableStateOf(false)
    private var hasAudio by mutableStateOf(false)
    private var hasNotification by mutableStateOf(false)

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updatePermissionStates() }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updatePermissionStates() }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            prefsManager.storageTreeUri = uri.toString()
            val folderName = uri.lastPathSegment?.substringAfterLast(":") ?: "Folder Kustom"
            prefsManager.storageDisplayName = "Folder Kustom: $folderName"
            Toast.makeText(this, "Lokasi diatur ke: ${prefsManager.storageDisplayName}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefsManager = PreferencesManager(this)
        videoRepository = VideoRepository(this)

        updatePermissionStates()

        setContent {
            // Otomatis mendeteksi tema sistem (Dark / Light)
            ScreenRecorderTheme {
                MainOneUIScreen(
                    prefsManager = prefsManager,
                    videoRepository = videoRepository,
                    hasOverlay = hasOverlay,
                    hasAudio = hasAudio,
                    hasNotification = hasNotification,
                    onRequestOverlay = { requestOverlayPermission() },
                    onRequestAudio = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    onRequestNotification = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenPrompt = {
                        if (!hasOverlay || !hasAudio) {
                            startActivity(Intent(this, PermissionSetupActivity::class.java))
                        } else {
                            startActivity(Intent(this, RecordPromptActivity::class.java))
                        }
                    },
                    onPlayVideo = { video -> playVideo(video) },
                    onShareVideo = { video -> shareVideo(video) },
                    onPickCustomFolder = { folderPickerLauncher.launch(null) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    private fun updatePermissionStates() {
        hasOverlay = Settings.canDrawOverlays(this)
        hasAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun playVideo(video: RecordedVideo) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(video.uri, "video/mp4")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak dapat memutar video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareVideo(video: RecordedVideo) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, video.uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(Intent.createChooser(intent, "Bagikan rekaman layar"))
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membagikan video", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainOneUIScreen(
    prefsManager: PreferencesManager,
    videoRepository: VideoRepository,
    hasOverlay: Boolean,
    hasAudio: Boolean,
    hasNotification: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestAudio: () -> Unit,
    onRequestNotification: () -> Unit,
    onOpenPrompt: () -> Unit,
    onPlayVideo: (RecordedVideo) -> Unit,
    onShareVideo: (RecordedVideo) -> Unit,
    onPickCustomFolder: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var videos by remember { mutableStateOf<List<RecordedVideo>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val recordingState by ScreenRecorderService.recordingState.collectAsState()

    fun refreshVideos() {
        scope.launch {
            videos = videoRepository.getRecordedVideos()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            refreshVideos()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = OneUITheme.colors.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // One UI Expansive Header
            OneUIHeader(recordingState = recordingState, onOpenPrompt = onOpenPrompt)

            // One UI Segmented Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = OneUITheme.colors.background,
                contentColor = OneUITheme.colors.accent,
                divider = {
                    HorizontalDivider(color = OneUITheme.colors.divider)
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Pengaturan",
                            fontSize = 15.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) OneUITheme.colors.accent else OneUITheme.colors.textSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        refreshVideos()
                    },
                    text = {
                        Text(
                            text = "Rekaman tersimpan",
                            fontSize = 15.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) OneUITheme.colors.accent else OneUITheme.colors.textSecondary
                        )
                    }
                )
            }

            // Tab Content
            if (selectedTab == 0) {
                SettingsTabContent(
                    prefsManager = prefsManager,
                    hasOverlay = hasOverlay,
                    hasAudio = hasAudio,
                    hasNotification = hasNotification,
                    onRequestOverlay = onRequestOverlay,
                    onRequestAudio = onRequestAudio,
                    onRequestNotification = onRequestNotification,
                    onPickCustomFolder = onPickCustomFolder
                )
            } else {
                RecordingsTabContent(
                    videos = videos,
                    onPlay = onPlayVideo,
                    onShare = onShareVideo,
                    onDelete = { video ->
                        scope.launch {
                            videoRepository.deleteVideo(video.uri)
                            refreshVideos()
                        }
                    }
                )
            }
        }
    }
}