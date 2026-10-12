package com.system.screenrecorder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.system.screenrecorder.ui.theme.OneUITheme
import com.system.screenrecorder.ui.theme.ScreenRecorderTheme

class PermissionSetupActivity : ComponentActivity() {

    private var hasOverlay by mutableStateOf(false)
    private var hasAudio by mutableStateOf(false)
    private var hasNotification by mutableStateOf(false)

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudio = granted
        checkAllGranted()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotification = granted
        checkAllGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updatePermissionStates()

        setContent {
            ScreenRecorderTheme {
                PermissionDialog(
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
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
        checkAllGranted()
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

    private fun checkAllGranted() {
        if (hasOverlay && hasAudio && hasNotification) {
            val intent = Intent(this, RecordPromptActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    @Composable
    private fun PermissionDialog(
        hasOverlay: Boolean,
        hasAudio: Boolean,
        hasNotification: Boolean,
        onRequestOverlay: () -> Unit,
        onRequestAudio: () -> Unit,
        onRequestNotification: () -> Unit,
        onClose: () -> Unit
    ) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onClose() }
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
                        // Left Column: Title, Subtitle, Close Button
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Izin Perekam Layar",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = OneUITheme.colors.textPrimary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Agar berfungsi seperti fitur bawaan Samsung, aplikasi memerlukan izin berikut sekali saja.",
                                fontSize = 12.sp,
                                color = OneUITheme.colors.textSecondary,
                                lineHeight = 17.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Button(
                                    onClick = onClose,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OneUITheme.colors.iconCircleBackground
                                    )
                                ) {
                                    Text(
                                        text = "Tutup",
                                        color = OneUITheme.colors.textPrimary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        // Right Column: Permission items
                        Column(
                            modifier = Modifier.weight(1.2f)
                        ) {
                            PermissionItem(
                                title = "Tampilkan di atas aplikasi lain",
                                desc = "Untuk bilah kontrol melayang & coretan layar",
                                icon = Icons.Rounded.Layers,
                                isGranted = hasOverlay,
                                isCompact = true,
                                onClick = onRequestOverlay
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            PermissionItem(
                                title = "Mikrofon",
                                desc = "Untuk merekam audio saat merekam layar",
                                icon = Icons.Rounded.Mic,
                                isGranted = hasAudio,
                                isCompact = true,
                                onClick = onRequestAudio
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            PermissionItem(
                                title = "Notifikasi",
                                desc = "Untuk kontrol rekaman di panel notifikasi",
                                icon = Icons.Rounded.Notifications,
                                isGranted = hasNotification,
                                isCompact = true,
                                onClick = onRequestNotification
                            )
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
                            text = "Izin Perekam Layar",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = OneUITheme.colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Agar berfungsi seperti fitur bawaan Samsung, aplikasi memerlukan izin berikut sekali saja.",
                            fontSize = 14.sp,
                            color = OneUITheme.colors.textSecondary,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        PermissionItem(
                            title = "Tampilkan di atas aplikasi lain",
                            desc = "Untuk bilah kontrol melayang & coretan layar",
                            icon = Icons.Rounded.Layers,
                            isGranted = hasOverlay,
                            isCompact = false,
                            onClick = onRequestOverlay
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PermissionItem(
                            title = "Mikrofon",
                            desc = "Untuk merekam audio saat merekam layar",
                            icon = Icons.Rounded.Mic,
                            isGranted = hasAudio,
                            isCompact = false,
                            onClick = onRequestAudio
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        PermissionItem(
                            title = "Notifikasi",
                            desc = "Untuk kontrol rekaman di panel notifikasi",
                            icon = Icons.Rounded.Notifications,
                            isGranted = hasNotification,
                            isCompact = false,
                            onClick = onRequestNotification
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = onClose,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = OneUITheme.colors.iconCircleBackground
                                )
                            ) {
                                Text(text = "Tutup", color = OneUITheme.colors.textPrimary)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PermissionItem(
        title: String,
        desc: String,
        icon: ImageVector,
        isGranted: Boolean,
        isCompact: Boolean = false,
        onClick: () -> Unit
    ) {
        val grantedCircleBg = if (OneUITheme.colors.isDark) Color(0xFF1E3A28) else Color(0xFFD4EDDA)
        val defaultCircleBg = OneUITheme.colors.iconCircleBackground

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(OneUITheme.colors.cardBackground, RoundedCornerShape(16.dp))
                .clickable(enabled = !isGranted) { onClick() }
                .padding(if (isCompact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isCompact) 32.dp else 40.dp)
                    .background(
                        if (isGranted) grantedCircleBg else defaultCircleBg,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Rounded.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (isGranted) OneUITheme.colors.successText else OneUITheme.colors.accent,
                    modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
                )
            }

            Spacer(modifier = Modifier.width(if (isCompact) 10.dp else 14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = if (isCompact) 13.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OneUITheme.colors.textPrimary
                )
                Text(
                    text = desc,
                    fontSize = if (isCompact) 11.sp else 12.sp,
                    color = OneUITheme.colors.textSecondary
                )
            }

            if (!isGranted) {
                Text(
                    text = "Izinkan",
                    fontSize = if (isCompact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = OneUITheme.colors.accent,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
