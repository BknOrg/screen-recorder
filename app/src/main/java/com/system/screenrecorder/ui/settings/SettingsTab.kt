package com.system.screenrecorder.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.system.screenrecorder.data.PreferencesManager
import com.system.screenrecorder.model.VideoFrameRate
import com.system.screenrecorder.model.VideoResolution
import com.system.screenrecorder.ui.components.OneUISelectionDialog
import com.system.screenrecorder.ui.components.OneUISettingRow
import com.system.screenrecorder.ui.components.OneUISwitchRow
import com.system.screenrecorder.ui.components.PermissionActionRow
import com.system.screenrecorder.ui.theme.OneUITheme

@Composable
fun SettingsTabContent(
    prefsManager: PreferencesManager,
    hasOverlay: Boolean,
    hasAudio: Boolean,
    hasNotification: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestAudio: () -> Unit,
    onRequestNotification: () -> Unit,
    onPickCustomFolder: () -> Unit
) {
    var resolution by remember { mutableStateOf(prefsManager.resolution) }
    var frameRate by remember { mutableStateOf(prefsManager.frameRate) }
    var countdown by remember { mutableIntStateOf(prefsManager.countdownSeconds) }
    var showFloatingToolbar by remember { mutableStateOf(prefsManager.showFloatingToolbar) }
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
                        containerColor = OneUITheme.colors.warningCardBg
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = OneUITheme.colors.warningText,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Izin Diperlukan",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = OneUITheme.colors.warningText
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Agar dapat merekam layar & menampilkan kontrol mengambang, mohon aktifkan izin di bawah:",
                            fontSize = 13.sp,
                            color = OneUITheme.colors.warningSubtext,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        PermissionActionRow(
                            title = "Tampilkan di atas aplikasi lain",
                            isGranted = hasOverlay,
                            onClick = onRequestOverlay
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        PermissionActionRow(
                            title = "Mikrofon",
                            isGranted = hasAudio,
                            onClick = onRequestAudio
                        )

                        Spacer(modifier = Modifier.height(8.dp))

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
                        containerColor = OneUITheme.colors.successCardBg
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = OneUITheme.colors.successText,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Semua Izin Sistem Aktif",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = OneUITheme.colors.successText
                            )
                            Text(
                                text = "Perekam layar siap digunakan kapan saja dari Panel Cepat.",
                                fontSize = 12.sp,
                                color = OneUITheme.colors.successSubtext
                            )
                        }
                    }
                }
            }
        }

        // Section: Bilah Kontrol Layar
        item {
            Text(
                text = "Bilah Kontrol Layar",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = OneUITheme.colors.accent,
                modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OneUITheme.colors.cardBackground
                )
            ) {
                OneUISwitchRow(
                    title = "Bilah kontrol mengambang",
                    subtitle = "Tampilkan bilah jeda, berhenti, dan coret-coret di layar saat merekam",
                    icon = Icons.Rounded.Layers,
                    checked = showFloatingToolbar,
                    onCheckedChange = { isChecked ->
                        showFloatingToolbar = isChecked
                        prefsManager.showFloatingToolbar = isChecked
                    }
                )
            }
        }

        // Section: Kualitas Video
        item {
            Text(
                text = "Kualitas Video",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = OneUITheme.colors.accent,
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OneUITheme.colors.cardBackground
                )
            ) {
                Column {
                    OneUISettingRow(
                        title = "Resolusi video",
                        subtitle = resolution.label,
                        icon = Icons.Rounded.Videocam,
                        onClick = { showResDialog = true }
                    )
                    HorizontalDivider(
                        color = OneUITheme.colors.divider,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    OneUISettingRow(
                        title = "Kecepatan bingkai",
                        subtitle = frameRate.label,
                        icon = Icons.Rounded.Speed,
                        onClick = { showFpsDialog = true }
                    )
                }
            }
        }

        // Section: Pengaturan Sistem & Penyimpanan
        item {
            Text(
                text = "Pengaturan Sistem & Penyimpanan",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = OneUITheme.colors.accent,
                modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OneUITheme.colors.cardBackground
                )
            ) {
                Column {
                    OneUISettingRow(
                        title = "Hitung mundur sebelum merekam",
                        subtitle = if (countdown == 0) "Nonaktif" else "$countdown detik",
                        icon = Icons.Rounded.Timer,
                        onClick = { showCountdownDialog = true }
                    )
                    HorizontalDivider(
                        color = OneUITheme.colors.divider,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
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

    // Dialog Resolusi Video
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

    // Dialog Kecepatan Bingkai (FPS)
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

    // Dialog Hitung Mundur
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

    // Dialog Lokasi Penyimpanan
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
