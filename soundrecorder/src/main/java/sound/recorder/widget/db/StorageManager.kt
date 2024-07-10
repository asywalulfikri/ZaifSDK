package sound.recorder.widget.db

import android.app.Application
import android.content.ContentValues
import android.provider.MediaStore
import sound.recorder.widget.model.Recording
import java.io.FileDescriptor

class StorageManager(private var app: Application) {

    private var resolver = app.contentResolver

    companion object {
        const val RECORDINGDS_FOLDER_NAME = "MyRecordings"
    }

    fun createRecordingFile(fileName: String): FileDescriptor? {
        val uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/$RECORDINGDS_FOLDER_NAME")
        }
        val newFileUri = resolver.insert(uri, contentValues)
        newFileUri?.let{
            return resolver.openFileDescriptor(newFileUri, "rw")?.fileDescriptor
        }

        return null
    }

    fun getRecordings() : List<Recording> {
        val recordingsList = mutableListOf<Recording>()

        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.VideoColumns._ID,
            MediaStore.Video.VideoColumns.DISPLAY_NAME,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Video.VideoColumns.DATE_ADDED,
            MediaStore.Video.VideoColumns.SIZE
        )

        val selection = MediaStore.Video.VideoColumns.RELATIVE_PATH + " like ?"
        val selectionArgs = arrayOf("%$RECORDINGDS_FOLDER_NAME%");

        val recordingsCursor = resolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            MediaStore.Video.VideoColumns.DATE_ADDED + " DESC",
            null
        )


        recordingsCursor?.let {
            val idCol = recordingsCursor.getColumnIndex(MediaStore.Video.VideoColumns._ID)
            val nameCol = recordingsCursor.getColumnIndex(MediaStore.Video.VideoColumns.DISPLAY_NAME)
            val durationCol = recordingsCursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
            val dateAddedCol = recordingsCursor.getColumnIndex(MediaStore.Video.VideoColumns.DATE_ADDED)
            val sizeCol = recordingsCursor.getColumnIndex(MediaStore.Video.VideoColumns.SIZE)

            while (recordingsCursor.moveToNext()) {
                recordingsList.add(
                    Recording(
                        id = recordingsCursor.getLong(idCol),
                        name = recordingsCursor.getString(nameCol),
                        dateAdded = recordingsCursor.getInt(dateAddedCol),
                        duration = recordingsCursor.getInt(durationCol),
                        size = recordingsCursor.getInt(sizeCol)
                    )
                )
            }
        }

        return recordingsList;
    }
}