package com.example.wallpaperchanger

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
//import android.app.AlarmManager
//import android.app.PendingIntent
//import android.os.SystemClock

import android.app.AlarmManager
import android.os.SystemClock



class WallpaperChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // We need to run this in a background thread
        GlobalScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("WallpaperAppPrefs", Context.MODE_PRIVATE)
            val folderUriString = prefs.getString("folder_uri", null) ?: return@launch
            val folderUri = Uri.parse(folderUriString)

            val imageUris = getImageUrisFromFolder(context, folderUri)
            if (imageUris.isNotEmpty()) {
                val randomImageUri = imageUris.random()
                val wallpaperManager = WallpaperManager.getInstance(context)
                val inputStream = context.contentResolver.openInputStream(randomImageUri)
                wallpaperManager.setStream(inputStream)
                inputStream?.close()
            }
        }
    }

    private fun getImageUrisFromFolder(context: Context, folderUri: Uri): List<Uri> {
        val imageUris = mutableListOf<Uri>()
        try {
            val contentResolver = context.contentResolver
            val documentId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, documentId)

            contentResolver.query(childrenUri, null, null, null, null)?.use { cursor ->
                val mimeTypeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)

                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(mimeTypeIndex)
                    if (mimeType != null && mimeType.startsWith("image/")) {
                        val docId = cursor.getString(idIndex)
                        val imageUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        imageUris.add(imageUri)
                    }
                }
            }
        } catch (e: Exception) {
            // Handle exceptions, e.g., permission revoked
        }
        return imageUris
    }
}