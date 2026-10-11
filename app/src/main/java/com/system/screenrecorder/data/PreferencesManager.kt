package com.system.screenrecorder.data

import android.content.Context
import android.content.SharedPreferences
import com.system.screenrecorder.model.AudioSourceMode
import com.system.screenrecorder.model.RecordingSettings
import com.system.screenrecorder.model.VideoFrameRate
import com.system.screenrecorder.model.VideoResolution

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("screen_recorder_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AUDIO_MODE = "pref_audio_mode"
        private const val KEY_RESOLUTION = "pref_resolution"
        private const val KEY_FPS = "pref_fps"
        private const val KEY_COUNTDOWN = "pref_countdown"
        private const val KEY_SHOW_TOUCHES = "pref_show_touches"
        private const val KEY_STORAGE_PATH = "pref_storage_path"
        private const val KEY_STORAGE_DISPLAY = "pref_storage_display"
        private const val KEY_STORAGE_TREE_URI = "pref_storage_tree_uri"
    }

    var audioMode: AudioSourceMode
        get() = AudioSourceMode.fromId(prefs.getInt(KEY_AUDIO_MODE, AudioSourceMode.MEDIA.id))
        set(value) = prefs.edit().putInt(KEY_AUDIO_MODE, value.id).apply()

    var resolution: VideoResolution
        get() = VideoResolution.fromLabel(
            prefs.getString(KEY_RESOLUTION, VideoResolution.FHD_1080P.label)
                ?: VideoResolution.FHD_1080P.label
        )
        set(value) = prefs.edit().putString(KEY_RESOLUTION, value.label).apply()

    var frameRate: VideoFrameRate
        get() = VideoFrameRate.fromFps(prefs.getInt(KEY_FPS, VideoFrameRate.FPS_60.fps))
        set(value) = prefs.edit().putInt(KEY_FPS, value.fps).apply()

    var countdownSeconds: Int
        get() = prefs.getInt(KEY_COUNTDOWN, 3)
        set(value) = prefs.edit().putInt(KEY_COUNTDOWN, value).apply()

    var showTouches: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TOUCHES, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TOUCHES, value).apply()

    var storageRelativePath: String
        get() = prefs.getString(KEY_STORAGE_PATH, "DCIM/Screen recordings") ?: "DCIM/Screen recordings"
        set(value) = prefs.edit().putString(KEY_STORAGE_PATH, value).apply()

    var storageDisplayName: String
        get() = prefs.getString(KEY_STORAGE_DISPLAY, "DCIM/Screen recordings (Galeri Samsung)")
            ?: "DCIM/Screen recordings (Galeri Samsung)"
        set(value) = prefs.edit().putString(KEY_STORAGE_DISPLAY, value).apply()

    var storageTreeUri: String
        get() = prefs.getString(KEY_STORAGE_TREE_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STORAGE_TREE_URI, value).apply()

    fun getSettings(): RecordingSettings {
        return RecordingSettings(
            audioMode = audioMode,
            resolution = resolution,
            frameRate = frameRate,
            countdownSeconds = countdownSeconds,
            showTouches = showTouches,
            storageRelativePath = storageRelativePath,
            storageDisplayName = storageDisplayName,
            storageTreeUri = storageTreeUri
        )
    }
}
