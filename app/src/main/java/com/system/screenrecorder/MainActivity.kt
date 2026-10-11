package com.system.screenrecorder

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.system.screenrecorder.data.PreferencesManager
import com.system.screenrecorder.data.VideoRepository
import com.system.screenrecorder.model.RecordedVideo
import com.system.screenrecorder.model.RecordingState
import com.system.screenrecorder.model.VideoFrameRate
import com.system.screenrecorder.model.VideoResolution
import com.system.screenrecorder.service.ScreenRecorderService
import com.system.screenrecorder.service.ScreenRecorderTileService
import com.system.screenrecorder.ui.PermissionSetupActivity
import com.system.screenrecorder.ui.RecordPromptActivity
import com.system.screenrecorder.ui.theme.ScreenRecorderTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var prefsManager: PreferencesManager
    private lateinit var videoRepository: VideoRepository
    private var storageUpdateTrigger by mutableStateOf(0)

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
            storageUpdateTrigger++
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
            ScreenRecorderTheme(darkTheme = true) {
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
                    onRequestAddTile = { requestAddQuickSettingsTile() },
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

    private fun requestAddQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = getSystemService(StatusBarManager::class.java)
            val componentName = ComponentName(this, ScreenRecorderTileService::class.java)
            val icon = AndroidIcon.createWithResource(this, R.drawable.ic_screen_recorder_tile)
            statusBarManager?.requestAddTileService(
                componentName,
                getString(R.string.tile_name),
                icon,
                mainExecutor
            ) { result ->
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {
                        Toast.makeText(this, "Tombol berhasil ditambahkan ke Panel Cepat!", Toast.LENGTH_SHORT).show()
                    }
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                        Toast.makeText(this, "Tombol sudah ada di Panel Cepat", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this, "Batal menambahkan tombol", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, "Tarik Panel Cepat dan ketuk Edit untuk menambahkan tombol", Toast.LENGTH_LONG).show()
        }
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
    onRequestAddTile: () -> Unit,
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
        containerColor = Color(0xFF0F0F12) // Deep One UI AMOLED Dark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // One UI Expansive Header
            OneUIHeader(recordingState = recordingState, onOpenPrompt = onOpenPrompt)

            // One UI Segmented Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0F0F12),
                contentColor = Color(0xFF4C8DF5),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF4C8DF5),
                        height = 3.dp
                    )
                },
                divider = {
                    HorizontalDivider(color = Color(0xFF1E1E24))
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
                            color = if (selectedTab == 0) Color.White else Color(0xFF8E8E93)
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
                            color = if (selectedTab == 1) Color.White else Color(0xFF8E8E93)
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
                    onRequestAddTile = onRequestAddTile,
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

@Composable
fun OneUIHeader(
    recordingState: RecordingState,
    onOpenPrompt: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Perekam layar",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Fitur perekam layar bawaan Samsung",
                    fontSize = 13.sp,
                    color = Color(0xFF8E8E93)
                )
            }

            if (recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF801E1A), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "● Merekam",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF453A)
                    )
                }
            } else {
                Button(
                    onClick = onOpenPrompt,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2D72D9)
                    )
                ) {
                    Text(
                        text = "Mulai",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTabContent(
    prefsManager: PreferencesManager,
    hasOverlay: Boolean,
    hasAudio: Boolean,
    hasNotification: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestAudio: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestAddTile: () -> Unit,
    onPickCustomFolder: () -> Unit
) {
    var resolution by remember { mutableStateOf(prefsManager.resolution) }
    var frameRate by remember { mutableStateOf(prefsManager.frameRate) }
    var countdown by remember { mutableIntStateOf(prefsManager.countdownSeconds) }
    var storageDisplay by remember { mutableStateOf(prefsManager.storageDisplayName) }

    var showResDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showCountdownDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }

    val allPermissionsGranted = hasOverlay && hasAudio && hasNotification

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Section: Status Izin
        item {
            if (!allPermissionsGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF382314) // Amber warning dark surface
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9F0A),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Izin Diperlukan",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Agar dapat merekam layar & menampilkan kontrol mengambang, mohon aktifkan izin di bawah:",
                            fontSize = 13.sp,
                            color = Color(0xFFE5C8A8),
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Overlay permission row
                        PermissionActionRow(
                            title = "Tampilkan di atas aplikasi lain",
                            isGranted = hasOverlay,
                            onClick = onRequestOverlay
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Audio permission row
                        PermissionActionRow(
                            title = "Mikrofon",
                            isGranted = hasAudio,
                            onClick = onRequestAudio
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Notification permission row
                        PermissionActionRow(
                            title = "Notifikasi",
                            isGranted = hasNotification,
                            onClick = onRequestNotification
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF142B1A) // Subtle green surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Semua Izin Sistem Aktif",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Perekam layar siap digunakan kapan saja dari Panel Cepat.",
                                fontSize = 12.sp,
                                color = Color(0xFFA2D4AE)
                            )
                        }
                    }
                }
            }
        }

        // Section: Akses Panel Cepat (Quick Settings)
        item {
            Text(
                text = "Pintasan Sistem (Panel Cepat)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4C8DF5),
                modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1E)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF28282E), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = Color(0xFF4C8DF5),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Tombol di Panel Cepat (Quick Settings)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Merekam langsung dari bilah atas Samsung One UI",
                                fontSize = 12.sp,
                                color = Color(0xFF8E8E93)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(
                            onClick = onRequestAddTile,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2D72D9)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tambahkan Tombol ke Panel Cepat",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text(
                        text = "Cara menambahkan secara manual:\n1. Usap ke bawah 2 kali dari bilah status atas.\n2. Ketuk ikon Pensil (Edit) di atas.\n3. Cari 'Perekam layar' di daftar tombol lalu seret ke panel aktif.",
                        fontSize = 12.sp,
                        color = Color(0xFFB0B0B8),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Section: Integrasi Bawaan Samsung
        item {
            Text(
                text = "Tampilan & Integrasi Sistem",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4C8DF5),
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1E)
                )
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF28282E), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFF4C8DF5),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Mode Fitur Sistem Aktif",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aplikasi ini tersembunyi sepenuhnya dari layar depan & daftar aplikasi (App Drawer). Buka menu ini kapan saja dengan menahan (tekan lama) tombol 'Perekam layar' di Panel Cepat.",
                            fontSize = 12.sp,
                            color = Color(0xFF8E8E93),
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // Section: Video Quality
        item {
            Text(
                text = "Kualitas Video",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4C8DF5),
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1E)
                )
            ) {
                Column {
                    OneUISettingRow(
                        title = "Resolusi video",
                        subtitle = resolution.label,
                        icon = Icons.Rounded.Videocam,
                        onClick = { showResDialog = true }
                    )
                    HorizontalDivider(color = Color(0xFF26262B), modifier = Modifier.padding(horizontal = 16.dp))
                    OneUISettingRow(
                        title = "Kecepatan bingkai",
                        subtitle = frameRate.label,
                        icon = Icons.Rounded.Speed,
                        onClick = { showFpsDialog = true }
                    )
                }
            }
        }

        // Section: System & Storage
        item {
            Text(
                text = "Pengaturan Sistem & Penyimpanan",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4C8DF5),
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1E)
                )
            ) {
                Column {
                    OneUISettingRow(
                        title = "Hitung mundur sebelum merekam",
                        subtitle = if (countdown == 0) "Nonaktif" else "$countdown detik",
                        icon = Icons.Rounded.Timer,
                        onClick = { showCountdownDialog = true }
                    )
                    HorizontalDivider(color = Color(0xFF26262B), modifier = Modifier.padding(horizontal = 16.dp))
                    OneUISettingRow(
                        title = "Lokasi penyimpanan",
                        subtitle = storageDisplay,
                        icon = Icons.Rounded.Folder,
                        onClick = { showStorageDialog = true }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Resolution Selection Dialog
    if (showResDialog) {
        OneUISelectionDialog(
            title = "Resolusi video",
            options = VideoResolution.entries.map { it.label },
            selectedOption = resolution.label,
            onSelect = { label ->
                val chosen = VideoResolution.fromLabel(label)
                resolution = chosen
                prefsManager.resolution = chosen
                showResDialog = false
            },
            onDismiss = { showResDialog = false }
        )
    }

    // FPS Selection Dialog
    if (showFpsDialog) {
        OneUISelectionDialog(
            title = "Kecepatan bingkai",
            options = VideoFrameRate.entries.map { it.label },
            selectedOption = frameRate.label,
            onSelect = { label ->
                val chosen = VideoFrameRate.entries.first { it.label == label }
                frameRate = chosen
                prefsManager.frameRate = chosen
                showFpsDialog = false
            },
            onDismiss = { showFpsDialog = false }
        )
    }

    // Countdown Selection Dialog
    if (showCountdownDialog) {
        val options = listOf("Nonaktif (0 detik)", "3 detik", "5 detik")
        val currentStr = when (countdown) {
            0 -> "Nonaktif (0 detik)"
            3 -> "3 detik"
            5 -> "5 detik"
            else -> "$countdown detik"
        }
        OneUISelectionDialog(
            title = "Hitung mundur",
            options = options,
            selectedOption = currentStr,
            onSelect = { opt ->
                val sec = when (opt) {
                    "Nonaktif (0 detik)" -> 0
                    "5 detik" -> 5
                    else -> 3
                }
                countdown = sec
                prefsManager.countdownSeconds = sec
                showCountdownDialog = false
            },
            onDismiss = { showCountdownDialog = false }
        )
    }

    // Storage Location Selection Dialog
    if (showStorageDialog) {
        val options = listOf(
            "DCIM/Screen recordings (Galeri Samsung - Rekomendasi)",
            "Movies/Screen recordings",
            "Download/Screen recordings",
            "Pilih folder kustom…"
        )
        OneUISelectionDialog(
            title = "Lokasi penyimpanan",
            options = options,
            selectedOption = storageDisplay,
            onSelect = { opt ->
                when (opt) {
                    "DCIM/Screen recordings (Galeri Samsung - Rekomendasi)" -> {
                        prefsManager.storageRelativePath = "DCIM/Screen recordings"
                        prefsManager.storageDisplayName = "DCIM/Screen recordings (Galeri Samsung)"
                        prefsManager.storageTreeUri = ""
                        storageDisplay = prefsManager.storageDisplayName
                        showStorageDialog = false
                    }
                    "Movies/Screen recordings" -> {
                        prefsManager.storageRelativePath = "Movies/Screen recordings"
                        prefsManager.storageDisplayName = "Movies/Screen recordings"
                        prefsManager.storageTreeUri = ""
                        storageDisplay = prefsManager.storageDisplayName
                        showStorageDialog = false
                    }
                    "Download/Screen recordings" -> {
                        prefsManager.storageRelativePath = "Download/Screen recordings"
                        prefsManager.storageDisplayName = "Download/Screen recordings"
                        prefsManager.storageTreeUri = ""
                        storageDisplay = prefsManager.storageDisplayName
                        showStorageDialog = false
                    }
                    "Pilih folder kustom…" -> {
                        showStorageDialog = false
                        onPickCustomFolder()
                    }
                }
            },
            onDismiss = { showStorageDialog = false }
        )
    }
}

@Composable
fun PermissionActionRow(
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF26190E), RoundedCornerShape(14.dp))
            .clickable(enabled = !isGranted) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )

        if (isGranted) {
            Text(
                text = "✓ Aktif",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF34C759)
            )
        } else {
            Box(
                modifier = Modifier
                    .background(Color(0xFFE07A25), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Izinkan",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun RecordingsTabContent(
    videos: List<RecordedVideo>,
    onPlay: (RecordedVideo) -> Unit,
    onShare: (RecordedVideo) -> Unit,
    onDelete: (RecordedVideo) -> Unit
) {
    if (videos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF1E1E24), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = Color(0xFF6E6E78),
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Belum ada rekaman layar",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Video yang Anda rekam akan tersimpan langsung di album 'Perekam layar' Galeri Samsung dan muncul di sini.",
                    fontSize = 13.sp,
                    color = Color(0xFF8E8E93),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            items(videos, key = { it.id }) { video ->
                VideoCardItem(
                    video = video,
                    onPlay = { onPlay(video) },
                    onShare = { onShare(video) },
                    onDelete = { onDelete(video) }
                )
            }
        }
    }
}

@Composable
fun VideoCardItem(
    video: RecordedVideo,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onPlay() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1E)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF2C2C32), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF4C8DF5),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                val sizeMb = String.format(Locale.getDefault(), "%.1f MB", video.sizeBytes / (1024f * 1024f))
                val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    .format(Date(video.dateModified * 1000))
                Text(
                    text = "$sizeMb • $dateStr",
                    fontSize = 12.sp,
                    color = Color(0xFF8E8E93)
                )
            }

            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Bagikan",
                    tint = Color(0xFFB0B0B8),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Hapus",
                    tint = Color(0xFFFF453A),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(text = "Hapus rekaman?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(text = "Video ini akan dihapus secara permanen dari perangkat.", color = Color(0xFFB0B0B8)) },
            containerColor = Color(0xFF242428),
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(text = "Hapus", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(text = "Batal", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun OneUISettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF28282E), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF4C8DF5),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

@Composable
fun OneUISelectionDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column {
                for (opt in options) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(opt) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = opt,
                            fontSize = 15.sp,
                            color = if (opt == selectedOption) Color(0xFF4C8DF5) else Color.White,
                            fontWeight = if (opt == selectedOption) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (opt == selectedOption) {
                            Text(
                                text = "✓",
                                color = Color(0xFF4C8DF5),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF222428),
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Tutup", color = Color(0xFF4C8DF5))
            }
        }
    )
}