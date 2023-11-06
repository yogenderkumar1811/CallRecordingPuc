package com.example.callrecordingapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {


    lateinit var getPhoneNumberButton: Button;
    lateinit var latestRecordingTextView: TextView;
    lateinit var phoneNumberEditText: EditText;
    lateinit var playButton: Button
    var mediaPlayer: MediaPlayer? = null
    var recordingPath = ""

    private var recordings: List<Recording> = listOf()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        getPhoneNumberButton = findViewById(R.id.btn_get_recording)
        phoneNumberEditText = findViewById(R.id.et_phone_number)
        latestRecordingTextView = findViewById(R.id.tv_recording_name)
        playButton = findViewById(R.id.btn_play_recording)


        val readPermission = Manifest.permission.READ_EXTERNAL_STORAGE
        checkReadExternalStoragePermission(readPermission)

        getPhoneNumberButton.setOnClickListener {
//            var latestRecordingText = ""
            latestRecordingTextView.text = ""
            val phoneNumber = phoneNumberEditText.text.toString()
            recordingPath =  getLatestPhoneRecording(phoneNumber, recordings)
            latestRecordingTextView.text = recordingPath
        }

        playButton.setOnClickListener {
            playMusic(recordingPath, this)
        }



    }

    private fun playMusic(recordingPath: String, context: Context) {
        var recordingUri = Uri.parse(recordingPath)
        if(mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, recordingUri)
                prepare()
                start()
            }
        }
    }

    private fun checkReadExternalStoragePermission(readPermission: String) {
        when {
            ContextCompat.checkSelfPermission(
                applicationContext,
                readPermission
            ) == PackageManager.PERMISSION_GRANTED -> {
                recordings = getCallRecording(this)
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this, readPermission
            ) -> {
                Toast.makeText(this, "Please Provide read external storage permission", Toast.LENGTH_SHORT).show()
                getPermissionDirectly()
            }
            else -> {
                requestPermissionLauncher.launch(readPermission)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {isGranted ->
        if(isGranted) {
            recordings = getCallRecording(this)
        } else {
            Toast.makeText(this, "Please Provide read external storage permission 1 !", Toast.LENGTH_SHORT).show()
            getPermissionDirectly()
        }
    }

    private fun getPermissionDirectly() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun getLatestPhoneRecording(phoneNumber: String, recordings: List<Recording>): String {
        var recordPath = ""
        for (record in recordings) {
            if (record.name.contains(phoneNumber) && phoneNumber != "") {
                recordPath = record.filePath
                break
            }
        }
        return recordPath
    }


    private fun checkBuildVersion(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }


    @SuppressLint("Range")
    fun getCallRecording(context: Context): List<Recording> {
        val recordings = mutableListOf<Recording>()
        val collection =
            if (checkBuildVersion()) Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            Media._ID,
            Media.DISPLAY_NAME,
            Media.DURATION,
            Media.SIZE,
            Media.DATE_ADDED,
            Media.DATA
        )

        val selection = "${Media.DATA} like ?"
        val selectionArgs = arrayOf("%Recording%")
        val sortOrder = "${Media.DATE_ADDED} DESC"

        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)

                val contentUri = ContentUris.withAppendedId(collection, id)
                val filePath = cursor.getString(cursor.getColumnIndex(Media.DATA))

                val recording = Recording(name, size, dateAdded, contentUri, filePath)
                recordings.add(recording)
            }
        }
        return recordings
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}


data class Recording(
    val name: String,
    val size: Long,
    val dateAdded: Long,
    val contentUri: Uri,
    val filePath: String,
)