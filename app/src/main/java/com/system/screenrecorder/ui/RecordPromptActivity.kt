package com.system.screenrecorder.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.system.screenrecorder.model.AudioSourceMode
import com.system.screenrecorder.service.ScreenRecorderService
import com.system.screenrecorder.ui.theme.OneUITheme
import com.system.screenrecorder.ui.theme.ScreenRecorderTheme

class RecordPromptActivity : ComponentActivity() {

    private lateinit var prefsManager: PreferencesManager
    private var selectedAudioMode by mutableStateOf(AudioSourceMode.MEDIA)

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenRecorderService::class.java).apply {
                action = ScreenRecorderService.ACTION_START
                putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenRecorderService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenRecorderService.EXTRA_AUDIO_MODE, selectedAudioMode.id)
            }
            startForegroundService(serviceIntent)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasOverlay || !hasAudio) {
            val intent = Intent(this, PermissionSetupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
            return
        }

        prefsManager = PreferencesManager(this)
        selectedAudioMode = prefsManager.audioMode

        setContent {
            ScreenRecorderTheme {
                PromptDialog(
                    selectedMode = selectedAudioMode,
                    onModeSelected = {
                        selectedAudioMode = it
                        prefsManager.audioMode = it
                    },
                    onStart = {
                        val projectionManager =
                            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        val captureIntent = projectionManager.createScreenCaptureIntent()
                        mediaProjectionLauncher.launch(captureIntent)
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    @Composable
    private fun PromptDialog(
        selectedMode: AudioSourceMode,
        onModeSelected: (AudioSourceMode) -> Unit,
        onStart: () -> Unit,
        onCancel: () -> Unit
    ) {
        var storageDisplay by remember { mutableStateOf(prefsManager.storageDisplayName) }
        var showStorageDialog by remember { mutableStateOf(false) }

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onCancel() }
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = if (isLandscape) 640.dp else 440.dp)
                    .fillMaxWidth(0.92f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = OneUITheme.colors.dialogSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                if (isLandscape) {
                    // Landscape: Dual-column responsive layout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left Column: Title, Subtitle, Storage Selector, Action Buttons
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Mulai merekam dengan Perekam Layar?",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = OneUITheme.colors.textPrimary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Perekam layar akan merekam semua yang ditampilkan di layar Anda atau diputar dari perangkat Anda.",
                                fontSize = 12.sp,
                                color = OneUITheme.colors.textSecondary,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Storage Location Selection Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(OneUITheme.colors.background, RoundedCornerShape(14.dp))
                                    .clickable { showStorageDialog = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = OneUITheme.colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Lokasi penyimpanan",
                                        fontSize = 10.sp,
                                        color = OneUITheme.colors.textSecondary
                                    )
                                    Text(
                                        text = storageDisplay,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OneUITheme.colors.textPrimary,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = "Ubah",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OneUITheme.colors.accent
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onCancel,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent
                                    )
                                ) {
                                    Text(
                                        text = "Batal",
                                        color = OneUITheme.colors.dialogDismissText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = onStart,
                                    shape = RoundedCornerShape(22.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OneUITheme.colors.accentButtonBackground
                                    )
                                ) {
                                    Text(
                                        text = "Mulai merekam",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Right Column: Audio Options
                        Column(
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Text(
                                text = "Pengaturan suara",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OneUITheme.colors.textPrimary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(OneUITheme.colors.background, RoundedCornerShape(18.dp))
                                    .padding(vertical = 2.dp)
                            ) {
                                AudioOptionItem(
                                    title = "Tanpa suara",
                                    subtitle = "Merekam video tanpa audio",
                                    icon = Icons.AutoMirrored.Rounded.VolumeOff,
                                    isSelected = selectedMode == AudioSourceMode.NONE,
                                    isCompact = true,
                                    onClick = { onModeSelected(AudioSourceMode.NONE) }
                                )

                                AudioOptionItem(
                                    title = "Suara media",
                                    subtitle = "Merekam suara dari game dan aplikasi",
                                    icon = Icons.Rounded.Headphones,
                                    isSelected = selectedMode == AudioSourceMode.MEDIA,
                                    isCompact = true,
                                    onClick = { onModeSelected(AudioSourceMode.MEDIA) }
                                )

                                AudioOptionItem(
                                    title = "Suara media dan mikrofon",
                                    subtitle = "Merekam suara media ditambah mikrofon",
                                    icon = Icons.Rounded.Mic,
                                    isSelected = selectedMode == AudioSourceMode.MEDIA_AND_MIC,
                                    isCompact = true,
                                    onClick = { onModeSelected(AudioSourceMode.MEDIA_AND_MIC) }
                                )
                            }
                        }
                    }
                } else {
                    // Portrait: Single vertical column with verticalScroll
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Mulai merekam dengan Perekam Layar?",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITheme.colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Perekam layar akan merekam semua yang ditampilkan di layar Anda atau diputar dari perangkat Anda.",
                            fontSize = 13.sp,
                            color = OneUITheme.colors.textSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Pengaturan suara",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OneUITheme.colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Grouped audio options card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OneUITheme.colors.background, RoundedCornerShape(20.dp))
                                .padding(vertical = 4.dp)
                        ) {
                            AudioOptionItem(
                                title = "Tanpa suara",
                                subtitle = "Merekam video tanpa audio",
                                icon = Icons.AutoMirrored.Rounded.VolumeOff,
                                isSelected = selectedMode == AudioSourceMode.NONE,
                                isCompact = false,
                                onClick = { onModeSelected(AudioSourceMode.NONE) }
                            )

                            AudioOptionItem(
                                title = "Suara media",
                                subtitle = "Merekam suara dari game dan aplikasi",
                                icon = Icons.Rounded.Headphones,
                                isSelected = selectedMode == AudioSourceMode.MEDIA,
                                isCompact = false,
                                onClick = { onModeSelected(AudioSourceMode.MEDIA) }
                            )

                            AudioOptionItem(
                                title = "Suara media dan mikrofon",
                                subtitle = "Merekam suara media ditambah mikrofon",
                                icon = Icons.Rounded.Mic,
                                isSelected = selectedMode == AudioSourceMode.MEDIA_AND_MIC,
                                isCompact = false,
                                onClick = { onModeSelected(AudioSourceMode.MEDIA_AND_MIC) }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Storage Location Selection Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(OneUITheme.colors.background, RoundedCornerShape(16.dp))
                                .clickable { showStorageDialog = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = OneUITheme.colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lokasi penyimpanan",
                                    fontSize = 11.sp,
                                    color = OneUITheme.colors.textSecondary
                                )
                                Text(
                                    text = storageDisplay,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OneUITheme.colors.textPrimary,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "Ubah",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OneUITheme.colors.accent
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onCancel,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent
                                )
                            ) {
                                Text(
                                    text = "Batal",
                                    color = OneUITheme.colors.dialogDismissText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = onStart,
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OneUITheme.colors.accentButtonBackground
                                )
                            ) {
                                Text(
                                    text = "Mulai merekam",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showStorageDialog) {
            val presets = listOf(
                Pair("DCIM/Screen recordings (Galeri Samsung)", "DCIM/Screen recordings"),
                Pair("Movies/Screen recordings", "Movies/Screen recordings"),
                Pair("Download/Screen recordings", "Download/Screen recordings")
            )
            AlertDialog(
                onDismissRequest = { showStorageDialog = false },
                title = {
                    Text(
                        text = "Pilih Lokasi Penyimpanan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OneUITheme.colors.textPrimary
                    )
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        for (item in presets) {
                            val (label, relPath) = item
                            val isSelected = prefsManager.storageRelativePath == relPath
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        prefsManager.storageRelativePath = relPath
                                        prefsManager.storageDisplayName = label
                                        prefsManager.storageTreeUri = ""
                                        storageDisplay = label
                                        showStorageDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    color = if (isSelected) OneUITheme.colors.accent else OneUITheme.colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        color = OneUITheme.colors.accent,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                containerColor = OneUITheme.colors.dialogSurface,
                confirmButton = {
                    TextButton(onClick = { showStorageDialog = false }) {
                        Text(text = "Tutup", color = OneUITheme.colors.accent)
                    }
                }
            )
        }
    }

    @Composable
    private fun AudioOptionItem(
        title: String,
        subtitle: String,
        icon: ImageVector,
        isSelected: Boolean,
        isCompact: Boolean = false,
        onClick: () -> Unit
    ) {
        val selectedCircleBg = if (OneUITheme.colors.isDark) Color(0xFF1E3A5F) else Color(0xFFDCEAFE)
        val defaultCircleBg = OneUITheme.colors.iconCircleBackground

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(
                    horizontal = if (isCompact) 10.dp else 14.dp,
                    vertical = if (isCompact) 6.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isCompact) 30.dp else 36.dp)
                    .background(
                        if (isSelected) selectedCircleBg else defaultCircleBg,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) OneUITheme.colors.accent else OneUITheme.colors.textSecondary,
                    modifier = Modifier.size(if (isCompact) 16.dp else 20.dp)
                )
            }

            Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = if (isCompact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = OneUITheme.colors.textPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = if (isCompact) 11.sp else 12.sp,
                    color = OneUITheme.colors.textSecondary
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = OneUITheme.colors.accent,
                    unselectedColor = OneUITheme.colors.textSecondary
                )
            )
        }
    }
}
