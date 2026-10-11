package com.system.screenrecorder.model

import android.net.Uri

enum class AudioSourceMode(val id: Int, val titleResName: String) {
    NONE(0, "sound_none"),
    MEDIA(1, "sound_media"),
    MEDIA_AND_MIC(2, "sound_media_mic");

    companion object {
        fun fromId(id: Int): AudioSourceMode = entries.firstOrNull { it.id == id } ?: MEDIA
    }
}

enum class VideoResolution(val width: Int, val height: Int, val label: String) {
    FHD_1080P(1080, 1920, "1080p (FHD)"),
    HD_720P(720, 1280, "720p (HD)"),
    SD_480P(480, 854, "480p (SD)");

    companion object {
        fun fromLabel(label: String): VideoResolution = entries.firstOrNull { it.label == label } ?: FHD_1080P
    }
}

enum class VideoFrameRate(val fps: Int, val label: String) {
    FPS_60(60, "60 fps"),
    FPS_30(30, "30 fps");

    companion object {
        fun fromFps(fps: Int): VideoFrameRate = entries.firstOrNull { it.fps == fps } ?: FPS_60
    }
}

enum class RecordingState {
    IDLE,
    COUNTDOWN,
    RECORDING,
    PAUSED,
    STOPPED
}

data class RecordingSettings(
    val audioMode: AudioSourceMode = AudioSourceMode.MEDIA,
    val resolution: VideoResolution = VideoResolution.FHD_1080P,
    val frameRate: VideoFrameRate = VideoFrameRate.FPS_60,
    val countdownSeconds: Int = 3,
    val showTouches: Boolean = false,
    val storageRelativePath: String = "DCIM/Screen recordings",
    val storageDisplayName: String = "DCIM/Screen recordings (Galeri Samsung)",
    val storageTreeUri: String = ""
)

data class RecordedVideo(
    val id: Long,
    val title: String,
    val uri: Uri,
    val path: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateModified: Long
)
