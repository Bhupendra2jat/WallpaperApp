package com.example.wallpaperchanger // FIXED: Changed to your project's package name

import android.app.AlarmManager
//import android.os.SystemClock
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.wallpaperchanger.databinding.ActivityMainBinding // FIXED: Changed to your project's package name
import java.util.concurrent.TimeUnit


import android.app.PendingIntent
import android.os.SystemClock



class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var selectedFolderUri: Uri? = null

    companion object {
        private const val PREFS_NAME = "WallpaperAppPrefs"
        private const val KEY_FOLDER_URI = "folder_uri"
        private const val KEY_INTERVAL_VALUE = "interval_value"
        private const val KEY_INTERVAL_UNIT = "interval_unit"
        private const val KEY_SWITCH_STATE = "switch_state"
        private const val UNIQUE_WORK_NAME = "periodic_wallpaper_changer"
    }

    private val selectFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Persist permission across reboots
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selectedFolderUri = it
            updateSelectedFolderText()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        createNotificationChannel()
        setupUI()
        loadPreferences()
    }

    private fun setupUI() {
        // Setup spinner
        val units = arrayOf("Seconds", "Minutes", "Hours", "Days")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, units)
        binding.intervalUnitSpinner.setAdapter(adapter)

        // Setup listeners
        binding.selectFolderButton.setOnClickListener {
            selectFolderLauncher.launch(null)
        }

        binding.applyButton.setOnClickListener {
            if (binding.autoChangeSwitch.isChecked && selectedFolderUri == null) {
                Toast.makeText(this, "Please select a folder first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            savePreferences()
            if (binding.autoChangeSwitch.isChecked) {
                scheduleWallpaperChange()
            } else {
                cancelWallpaperChange()
            }
            Toast.makeText(this, "Settings Applied!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleWallpaperChange() {
        val intervalValue = binding.intervalValueEditText.text.toString().toLongOrNull() ?: 30
        val intervalUnit = binding.intervalUnitSpinner.text.toString()

        val intervalMillis = when (intervalUnit) {
            "Seconds" -> intervalValue * 1000
            "Minutes" -> intervalValue * 60 * 1000
            "Hours" -> intervalValue * 60 * 60 * 1000
            "Days" -> intervalValue * 24 * 60 * 60 * 1000
            else -> 30 * 1000 // Default to 30 seconds
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, WallpaperChangeReceiver::class.java).apply {
            action = "com.example.wallpaperchanger.CHANGE_WALLPAPER"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Cancel any existing alarm before setting a new one
        alarmManager.cancel(pendingIntent)
        // Set a repeating alarm
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + intervalMillis,
            intervalMillis,
            pendingIntent
        )
    }

    private fun cancelWallpaperChange() {
        WorkManager.getInstance(this).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun savePreferences() {
        prefs.edit().apply {
            putString(KEY_FOLDER_URI, selectedFolderUri?.toString())
            putString(KEY_INTERVAL_VALUE, binding.intervalValueEditText.text.toString())
            putString(KEY_INTERVAL_UNIT, binding.intervalUnitSpinner.text.toString())
            putBoolean(KEY_SWITCH_STATE, binding.autoChangeSwitch.isChecked)
            apply()
        }
    }

    private fun loadPreferences() {
        val folderUriString = prefs.getString(KEY_FOLDER_URI, null)
        if (folderUriString != null) {
            selectedFolderUri = Uri.parse(folderUriString)
            updateSelectedFolderText()
        }
        binding.intervalValueEditText.setText(prefs.getString(KEY_INTERVAL_VALUE, "1"))
        val savedUnit = prefs.getString(KEY_INTERVAL_UNIT, "Hours")
        binding.intervalUnitSpinner.setText(savedUnit, false) // `false` to prevent dropdown from showing
        binding.autoChangeSwitch.isChecked = prefs.getBoolean(KEY_SWITCH_STATE, false)
    }

    private fun updateSelectedFolderText() {
        selectedFolderUri?.let {
            val path = it.path ?: "Unknown Path"
            binding.selectedFolderText.text = "Folder: ...${path.substringAfterLast('/')}"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Wallpaper Changer Service"
            val descriptionText = "Notifications for the wallpaper changing service"
            val importance = NotificationManager.IMPORTANCE_LOW
            // This is the correct line
            val channel = NotificationChannel("wallpaper_changer_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}