package com.system.screenrecorder.recorder

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.documentfile.provider.DocumentFile
import com.system.screenrecorder.model.AudioSourceMode
import com.system.screenrecorder.model.RecordingSettings
import com.system.screenrecorder.model.VideoFrameRate
import com.system.screenrecorder.model.VideoResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class ScreenRecordingEngine(private val context: Context) {

    companion object {
        private const val TAG = "ScreenRecordingEngine"
        private const val VIDEO_MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val AUDIO_SAMPLE_RATE = 48000
        private const val AUDIO_CHANNEL_COUNT = 2
        private const val AUDIO_BIT_RATE = 192000
        private const val TIMEOUT_USEC = 10000L
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null

    private var mediaAudioRecord: AudioRecord? = null
    private var micAudioRecord: AudioRecord? = null

    private var tempOutputFile: File? = null
    private val isMuxerStarted = AtomicBoolean(false)
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1

    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var videoWorkerJob: Job? = null
    private var audioWorkerJob: Job? = null
    private var audioMixerJob: Job? = null

    private var pauseStartTime = 0L
    private var totalPauseOffsetUs = 0L
    private var firstVideoPtsUs = -1L
    private var lastVideoPtsUs = 0L
    private var lastAudioPtsUs = 0L
    private var totalAudioFramesEnqueued = 0L
    private var activeSettings: RecordingSettings? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    var onRecordingStoppedListener: ((Uri?) -> Unit)? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped by system")
            stopRecording()
        }
    }

    fun isRecordingActive(): Boolean = isRecording.get()
    fun isRecordingPaused(): Boolean = isPaused.get()

    @SuppressLint("WrongConstant")
    fun startRecording(
        resultCode: Int,
        resultData: Intent,
        settings: RecordingSettings
    ): Boolean {
        if (isRecording.get()) return false

        try {
            val projectionManager =
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            if (mediaProjection == null) {
                Log.e(TAG, "Failed to obtain MediaProjection")
                return false
            }

            mediaProjection?.registerCallback(projectionCallback, mainHandler)

            // Setup dimensions with dynamic orientation detection
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val screenDensity = metrics.densityDpi

            val (videoWidth, videoHeight) = calculateDimensions(
                screenWidth,
                screenHeight,
                settings.resolution
            )

            // Setup temp output file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val tempFileName = "Screen_Recording_$timestamp.mp4"
            tempOutputFile = File(context.cacheDir, tempFileName)

            mediaMuxer = MediaMuxer(
                tempOutputFile!!.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            // Configure Video Codec
            val bitRate = when (settings.resolution) {
                VideoResolution.FHD_1080P -> if (settings.frameRate == VideoFrameRate.FPS_60) 14_000_000 else 10_000_000
                VideoResolution.HD_720P -> if (settings.frameRate == VideoFrameRate.FPS_60) 8_000_000 else 6_000_000
                VideoResolution.SD_480P -> 3_000_000
            }

            val videoFormat = MediaFormat.createVideoFormat(VIDEO_MIME, videoWidth, videoHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, settings.frameRate.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            videoCodec = MediaCodec.createEncoderByType(VIDEO_MIME)
            videoCodec?.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = videoCodec?.createInputSurface()
            videoCodec?.start()

            // Configure Audio if requested
            val needAudio = settings.audioMode != AudioSourceMode.NONE
            if (needAudio) {
                setupAudio(settings.audioMode)
            }

            // Create VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecorderDisplay",
                videoWidth,
                videoHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                mainHandler
            )

            activeSettings = settings
            isRecording.set(true)
            isPaused.set(false)
            pauseStartTime = 0L
            totalPauseOffsetUs = 0L
            firstVideoPtsUs = -1L
            lastVideoPtsUs = 0L
            lastAudioPtsUs = 0L
            totalAudioFramesEnqueued = 0L
            isMuxerStarted.set(false)
            videoTrackIndex = -1
            audioTrackIndex = -1

            // Launch worker threads
            startEncodingLoops(needAudio)

            Log.i(TAG, "Screen recording started successfully: ${videoWidth}x$videoHeight @ ${settings.frameRate.fps}fps")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            cleanup()
            return false
        }
    }

    private fun calculateDimensions(
        screenWidth: Int,
        screenHeight: Int,
        resolution: VideoResolution
    ): Pair<Int, Int> {
        val isLandscape = screenWidth > screenHeight
        val shortEdgeTarget = resolution.width

        val (w, h) = if (isLandscape) {
            val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
            val targetHeight = shortEdgeTarget
            val targetWidth = (targetHeight * aspectRatio).toInt()
            Pair(targetWidth, targetHeight)
        } else {
            val aspectRatio = screenHeight.toFloat() / screenWidth.toFloat()
            val targetWidth = shortEdgeTarget
            val targetHeight = (targetWidth * aspectRatio).toInt()
            Pair(targetWidth, targetHeight)
        }

        // Align dimensions to multiples of 16 for hardware encoder compatibility
        val finalWidth = (w / 16) * 16
        val finalHeight = (h / 16) * 16
        return Pair(finalWidth, finalHeight)
    }

    @SuppressLint("MissingPermission")
    private fun setupAudio(mode: AudioSourceMode) {
        try {
            val audioFormat = MediaFormat.createAudioFormat(
                AUDIO_MIME,
                AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL_COUNT
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
            }

            audioCodec = MediaCodec.createEncoderByType(AUDIO_MIME)
            audioCodec?.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioCodec?.start()

            val minBufferSize = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSizeInBytes = maxOf(minBufferSize * 2, 4096)

            val recordFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AUDIO_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()

            if (mode == AudioSourceMode.MEDIA || mode == AudioSourceMode.MEDIA_AND_MIC) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                    val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()

                    mediaAudioRecord = AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(playbackConfig)
                        .setAudioFormat(recordFormat)
                        .setBufferSizeInBytes(bufferSizeInBytes)
                        .build()
                    mediaAudioRecord?.startRecording()
                }
            }

            if (mode == AudioSourceMode.MEDIA_AND_MIC) {
                micAudioRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(recordFormat)
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .build()
                micAudioRecord?.startRecording()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup audio capture", e)
        }
    }

    private fun startMuxerSafely() {
        synchronized(mediaMuxer ?: Any()) {
            if (!isMuxerStarted.get()) {
                try {
                    mediaMuxer?.start()
                    isMuxerStarted.set(true)
                    Log.i(TAG, "MediaMuxer started successfully")

                    // Request immediate IDR keyframe to prevent initial black screen
                    val syncBundle = Bundle().apply {
                        putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                    }
                    videoCodec?.setParameters(syncBundle)
                    Log.d(TAG, "Requested immediate IDR sync frame after muxer start")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start MediaMuxer", e)
                }
            }
        }
    }

    private fun startEncodingLoops(needAudio: Boolean) {
        val scope = CoroutineScope(Dispatchers.IO)

        // Video drain loop
        videoWorkerJob = scope.launch {
            val bufferInfo = MediaCodec.BufferInfo()
            while (isActive && isRecording.get()) {
                val encoder = videoCodec ?: break
                val outputIndex = try {
                    encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                } catch (e: Exception) {
                    break
                }

                when (outputIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!isMuxerStarted.get()) {
                            val newFormat = encoder.outputFormat
                            videoTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                            Log.d(TAG, "Video format registered, track index: $videoTrackIndex")
                            if (!needAudio || audioTrackIndex >= 0) {
                                startMuxerSafely()
                            }
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                    else -> {
                        if (outputIndex >= 0) {
                            val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            if (isPaused.get() && !isEos) {
                                encoder.releaseOutputBuffer(outputIndex, false)
                                continue
                            }

                            val encodedBuffer = encoder.getOutputBuffer(outputIndex)
                            if (encodedBuffer != null && bufferInfo.size > 0 && isMuxerStarted.get()) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                    if (firstVideoPtsUs < 0) {
                                        firstVideoPtsUs = bufferInfo.presentationTimeUs
                                    }
                                    var pts = bufferInfo.presentationTimeUs - firstVideoPtsUs - totalPauseOffsetUs
                                    if (pts <= lastVideoPtsUs) {
                                        pts = lastVideoPtsUs + 1000L
                                    }
                                    bufferInfo.presentationTimeUs = pts
                                    lastVideoPtsUs = pts

                                    encodedBuffer.position(bufferInfo.offset)
                                    encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    synchronized(mediaMuxer ?: Any()) {
                                        try {
                                            mediaMuxer?.writeSampleData(videoTrackIndex, encodedBuffer, bufferInfo)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error writing video sample", e)
                                        }
                                    }
                                }
                            }
                            encoder.releaseOutputBuffer(outputIndex, false)
                            if (isEos) {
                                Log.d(TAG, "Video EOS received")
                                break
                            }
                        }
                    }
                }
            }
        }

        // Audio drain loop
        if (needAudio && audioCodec != null) {
            audioWorkerJob = scope.launch {
                val bufferInfo = MediaCodec.BufferInfo()
                while (isActive && isRecording.get()) {
                    val encoder = audioCodec ?: break
                    val outputIndex = try {
                        encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                    } catch (e: Exception) {
                        break
                    }

                    when (outputIndex) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!isMuxerStarted.get()) {
                                val newFormat = encoder.outputFormat
                                audioTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                                Log.d(TAG, "Audio format registered, track index: $audioTrackIndex")
                                if (videoTrackIndex >= 0) {
                                    startMuxerSafely()
                                }
                            }
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {}
                        else -> {
                            if (outputIndex >= 0) {
                                val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                                if (isPaused.get() && !isEos) {
                                    encoder.releaseOutputBuffer(outputIndex, false)
                                    continue
                                }

                                val encodedBuffer = encoder.getOutputBuffer(outputIndex)
                                if (encodedBuffer != null && bufferInfo.size > 0 && isMuxerStarted.get()) {
                                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                        var pts = bufferInfo.presentationTimeUs
                                        if (pts <= lastAudioPtsUs) {
                                            pts = lastAudioPtsUs + 1000L
                                        }
                                        bufferInfo.presentationTimeUs = pts
                                        lastAudioPtsUs = pts

                                        encodedBuffer.position(bufferInfo.offset)
                                        encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                        synchronized(mediaMuxer ?: Any()) {
                                            try {
                                                mediaMuxer?.writeSampleData(audioTrackIndex, encodedBuffer, bufferInfo)
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Error writing audio sample", e)
                                            }
                                        }
                                    }
                                }
                                encoder.releaseOutputBuffer(outputIndex, false)
                                if (isEos) {
                                    Log.d(TAG, "Audio EOS received")
                                    break
                                }
                            }
                        }
                    }
                }
            }

            // Audio Feed loop (PCM -> Encoder)
            audioMixerJob = scope.launch {
                val mode = activeSettings?.audioMode ?: AudioSourceMode.MEDIA
                if (mode == AudioSourceMode.MEDIA) {
                    // Fast-path: internal system audio directly with native byte order
                    val bufferSizeInBytes = 4096
                    val directByteBuffer = ByteBuffer.allocateDirect(bufferSizeInBytes)
                        .order(ByteOrder.nativeOrder())

                    while (isActive && isRecording.get()) {
                        val record = mediaAudioRecord ?: break

                        if (isPaused.get()) {
                            directByteBuffer.clear()
                            record.read(directByteBuffer, bufferSizeInBytes)
                            delay(20)
                            continue
                        }

                        directByteBuffer.clear()
                        val bytesRead = record.read(directByteBuffer, bufferSizeInBytes)
                        if (bytesRead <= 0) {
                            delay(5)
                            continue
                        }

                        directByteBuffer.position(0)
                        directByteBuffer.limit(bytesRead)

                        val encoder = audioCodec ?: break
                        val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
                        if (inputIndex >= 0) {
                            val inputBuffer = encoder.getInputBuffer(inputIndex)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                inputBuffer.put(directByteBuffer)
                                val framesInThisBuffer = bytesRead / (AUDIO_CHANNEL_COUNT * 2)
                                val ptsUs = (totalAudioFramesEnqueued * 1_000_000L) / AUDIO_SAMPLE_RATE
                                totalAudioFramesEnqueued += framesInThisBuffer
                                encoder.queueInputBuffer(inputIndex, 0, bytesRead, ptsUs, 0)
                            }
                        }
                    }
                } else if (mode == AudioSourceMode.MEDIA_AND_MIC) {
                    // Mixed path: internal audio + microphone with native byte order
                    val pcmSampleCount = 2048
                    val mediaShortBuffer = ShortArray(pcmSampleCount)
                    val micShortBuffer = ShortArray(pcmSampleCount)
                    val mixedShortBuffer = ShortArray(pcmSampleCount)
                    val directByteBuffer = ByteBuffer.allocateDirect(pcmSampleCount * 2)
                        .order(ByteOrder.nativeOrder())

                    while (isActive && isRecording.get()) {
                        val micRecord = micAudioRecord ?: break
                        val mediaRecord = mediaAudioRecord

                        if (isPaused.get()) {
                            mediaRecord?.read(mediaShortBuffer, 0, pcmSampleCount, AudioRecord.READ_NON_BLOCKING)
                            micRecord.read(micShortBuffer, 0, pcmSampleCount, AudioRecord.READ_NON_BLOCKING)
                            delay(20)
                            continue
                        }

                        // Read mic with blocking to maintain steady 48kHz pace
                        val micReadCount = micRecord.read(micShortBuffer, 0, pcmSampleCount)
                        val mediaReadCount = mediaRecord?.read(
                            mediaShortBuffer,
                            0,
                            pcmSampleCount,
                            AudioRecord.READ_NON_BLOCKING
                        ) ?: 0

                        val safeMicCount = if (micReadCount > 0) micReadCount else 0
                        val safeMediaCount = if (mediaReadCount > 0) mediaReadCount else 0
                        val samplesToProcess = maxOf(safeMicCount, safeMediaCount)

                        if (samplesToProcess <= 0) {
                            delay(5)
                            continue
                        }

                        for (i in 0 until samplesToProcess) {
                            val sMic = if (i < safeMicCount) micShortBuffer[i].toInt() else 0
                            val sMedia = if (i < safeMediaCount) mediaShortBuffer[i].toInt() else 0
                            val mixed = (sMic + sMedia).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            mixedShortBuffer[i] = mixed.toShort()
                        }

                        directByteBuffer.clear()
                        directByteBuffer.asShortBuffer().put(mixedShortBuffer, 0, samplesToProcess)
                        val bytesToWrite = samplesToProcess * 2
                        directByteBuffer.position(0)
                        directByteBuffer.limit(bytesToWrite)

                        val encoder = audioCodec ?: break
                        val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC)
                        if (inputIndex >= 0) {
                            val inputBuffer = encoder.getInputBuffer(inputIndex)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                inputBuffer.put(directByteBuffer)
                                val framesInThisBuffer = samplesToProcess / AUDIO_CHANNEL_COUNT
                                val ptsUs = (totalAudioFramesEnqueued * 1_000_000L) / AUDIO_SAMPLE_RATE
                                totalAudioFramesEnqueued += framesInThisBuffer
                                encoder.queueInputBuffer(inputIndex, 0, bytesToWrite, ptsUs, 0)
                            }
                        }
                    }
                }
            }
        }
    }

    fun pauseRecording() {
        if (isRecording.get() && !isPaused.get()) {
            isPaused.set(true)
            pauseStartTime = System.nanoTime() / 1000
            Log.d(TAG, "Recording paused at $pauseStartTime")
        }
    }

    fun resumeRecording() {
        if (isRecording.get() && isPaused.get()) {
            val nowUs = System.nanoTime() / 1000
            val pauseDurationUs = nowUs - pauseStartTime
            totalPauseOffsetUs += pauseDurationUs
            isPaused.set(false)
            Log.d(TAG, "Recording resumed, pause duration: ${pauseDurationUs}us, total offset: ${totalPauseOffsetUs}us")

            // Request keyframe upon resume so video immediately unfreezes
            try {
                val syncBundle = Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                }
                videoCodec?.setParameters(syncBundle)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request sync frame on resume: ${e.message}")
            }
        }
    }

    fun stopRecording() {
        if (!isRecording.get()) return
        isRecording.set(false)
        isPaused.set(false)

        Log.d(TAG, "Stopping recording and finalizing MP4 container...")

        // 1. Signal EOS to video encoder
        try {
            videoCodec?.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.w(TAG, "Error signaling EOS to videoCodec: ${e.message}")
        }

        // 2. Stop audio recording hardware
        try {
            mediaAudioRecord?.stop()
            micAudioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        // 3. Signal EOS to audio encoder
        if (audioCodec != null) {
            try {
                val inputIndex = audioCodec?.dequeueInputBuffer(TIMEOUT_USEC) ?: -1
                if (inputIndex >= 0) {
                    val ptsUs = (totalAudioFramesEnqueued * 1_000_000L) / AUDIO_SAMPLE_RATE
                    audioCodec?.queueInputBuffer(
                        inputIndex,
                        0,
                        0,
                        ptsUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error sending audio EOS: ${e.message}")
            }
        }

        // 4. Wait briefly for drain loops to finish writing trailing frames
        try {
            runBlocking {
                withTimeoutOrNull(400) {
                    videoWorkerJob?.join()
                    audioWorkerJob?.join()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wait for EOS drain interrupted: ${e.message}")
        }

        videoWorkerJob?.cancel()
        audioWorkerJob?.cancel()
        audioMixerJob?.cancel()

        cleanup()

        // Export file to Samsung Gallery or selected storage location
        val savedUri = exportToGallery()
        mainHandler.post {
            onRecordingStoppedListener?.invoke(savedUri)
        }
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null

            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
            mediaProjection = null

            try {
                mediaAudioRecord?.release()
            } catch (e: Exception) {}
            mediaAudioRecord = null

            try {
                micAudioRecord?.release()
            } catch (e: Exception) {}
            micAudioRecord = null

            try {
                videoCodec?.stop()
                videoCodec?.release()
            } catch (e: Exception) {}
            videoCodec = null

            try {
                audioCodec?.stop()
                audioCodec?.release()
            } catch (e: Exception) {}
            audioCodec = null

            if (isMuxerStarted.get()) {
                try {
                    mediaMuxer?.stop()
                    mediaMuxer?.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping MediaMuxer: ${e.message}")
                }
                mediaMuxer = null
                isMuxerStarted.set(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during recorder cleanup", e)
        }
    }

    private fun exportToGallery(): Uri? {
        val file = tempOutputFile ?: return null
        if (!file.exists() || file.length() <= 0) {
            Log.w(TAG, "Temp video file is empty or does not exist")
            return null
        }

        try {
            val fileName = file.name
            val resolver = context.contentResolver
            val targetRelPath = activeSettings?.storageRelativePath ?: "DCIM/Screen recordings"
            val customTree = activeSettings?.storageTreeUri ?: ""

            // If user selected custom SAF tree folder
            if (customTree.isNotEmpty()) {
                try {
                    val treeUri = Uri.parse(customTree)
                    val docTree = DocumentFile.fromTreeUri(context, treeUri)
                    val targetDoc = docTree?.createFile("video/mp4", fileName)
                    if (targetDoc != null) {
                        resolver.openOutputStream(targetDoc.uri)?.use { out ->
                            FileInputStream(file).use { input -> input.copyTo(out) }
                        }
                        file.delete()
                        Log.i(TAG, "Video successfully saved to custom folder: ${targetDoc.uri}")
                        return targetDoc.uri
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed writing to custom tree URI, falling back to MediaStore", e)
                }
            }

            // Standard MediaStore insertion into Samsung Gallery DCIM
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, targetRelPath)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (videoUri != null) {
                resolver.openOutputStream(videoUri)?.use { outputStream ->
                    FileInputStream(file).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(videoUri, contentValues, null, null)
                }

                file.delete()
                Log.i(TAG, "Video successfully saved to $targetRelPath: $videoUri")
                return videoUri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export video", e)
        }
        return null
    }
}
