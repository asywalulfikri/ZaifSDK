package sound.recorder.widget.db

import android.app.Application
import android.content.ContentValues
import android.provider.MediaStore
import android.os.ParcelFileDescriptor
import sound.recorder.widget.model.Recording
import java.io.FileDescriptor

class StorageManager(private var app: Application) {

    private var resolver = app.contentResolver

    companion object {
        const val RECORDINGDS_FOLDER_NAME = "MyRecordings"
    }

    fun createRecordingFile(fileName: String): ParcelFileDescriptor? {
        val uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/$RECORDINGDS_FOLDER_NAME")
        }
        val newFileUri = resolver.insert(uri, contentValues)
        return newFileUri?.let { resolver.openFileDescriptor(it, "rw") }
    }

    fun getRecordings() : List<Recording> {
        val recordingsList = mutableListOf<Recording>()

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.AudioColumns._ID,
            MediaStore.Audio.AudioColumns.DISPLAY_NAME,
            MediaStore.Audio.AudioColumns.DURATION,
            MediaStore.Audio.AudioColumns.DATE_ADDED,
            MediaStore.Audio.AudioColumns.SIZE
        )

        val selection = MediaStore.Audio.AudioColumns.RELATIVE_PATH + " like ?"
        val selectionArgs = arrayOf("%$RECORDINGDS_FOLDER_NAME%");

        val recordingsCursor = resolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            MediaStore.Audio.AudioColumns.DATE_ADDED + " DESC",
            null
        )


        recordingsCursor?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Audio.AudioColumns._ID)
            val nameCol = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION)
            val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.DATE_ADDED)
            val sizeCol = cursor.getColumnIndex(MediaStore.Audio.AudioColumns.SIZE)

            while (cursor.moveToNext()) {
                recordingsList.add(
                    Recording(
                        id = cursor.getLong(idCol),
                        name = cursor.getString(nameCol),
                        dateAdded = cursor.getInt(dateAddedCol),
                        duration = cursor.getInt(durationCol),
                        size = cursor.getInt(sizeCol)
                    )
                )
            }
        }

        return recordingsList;
    }
}
