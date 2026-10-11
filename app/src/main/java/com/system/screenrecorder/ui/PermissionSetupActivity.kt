package com.system.screenrecorder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

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
            // All granted! Transition directly to Recording Prompt
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF222428) // One UI Dark Dialog Surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Izin Perekam Layar",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Agar berfungsi seperti fitur bawaan Samsung, aplikasi memerlukan izin berikut sekali saja.",
                        fontSize = 14.sp,
                        color = Color(0xFFB0B0B8),
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Permission Items
                    PermissionItem(
                        title = "Tampilkan di atas aplikasi lain",
                        desc = "Untuk bilah kontrol melayang & coretan layar",
                        icon = Icons.Rounded.Layers,
                        isGranted = hasOverlay,
                        onClick = onRequestOverlay
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionItem(
                        title = "Mikrofon",
                        desc = "Untuk merekam audio saat merekam layar",
                        icon = Icons.Rounded.Mic,
                        isGranted = hasAudio,
                        onClick = onRequestAudio
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionItem(
                        title = "Notifikasi",
                        desc = "Untuk kontrol rekaman di panel notifikasi",
                        icon = Icons.Rounded.Notifications,
                        isGranted = hasNotification,
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
                                containerColor = Color(0xFF38383E)
                            )
                        ) {
                            Text(text = "Tutup", color = Color.White)
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
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2C2C30), RoundedCornerShape(16.dp))
                .clickable(enabled = !isGranted) { onClick() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isGranted) Color(0xFF285C38) else Color(0xFF3A3A40),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Rounded.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF4CD964) else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = desc,
                    fontSize = 12.sp,
                    color = Color(0xFFA0A0A8)
                )
            }

            if (!isGranted) {
                Text(
                    text = "Izinkan",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4C8DF5),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
