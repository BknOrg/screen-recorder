package com.system.screenrecorder.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.system.screenrecorder.model.RecordedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoRepository(private val context: Context) {

    suspend fun getRecordedVideos(): List<RecordedVideo> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<RecordedVideo>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATA
        )

        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        } else {
            "${MediaStore.Video.Media.DATA} LIKE ? OR ${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        }

        val selectionArgs = arrayOf("%Screen recordings%", "Screen_Recording_%.mp4")
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Video_$id.mp4"
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateModified = cursor.getLong(dateColumn)
                    val path = cursor.getString(dataColumn) ?: ""
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    videos.add(
                        RecordedVideo(
                            id = id,
                            title = name,
                            uri = contentUri,
                            path = path,
                            durationMs = duration,
                            sizeBytes = size,
                            dateModified = dateModified
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        videos
    }

    suspend fun deleteVideo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
