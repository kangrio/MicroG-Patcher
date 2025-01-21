package com.kangrio.microgpatcher


import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File

object Utils {
    val itemsHashmap = HashMap<Int, Package>()
    var _downloadsDir: File? = null
    val downloadsDir get() = _downloadsDir!!
    val subdirectory = "patch"


    init {
//        val microg = Package( "MicroG", "com.google.android.gms")
//        val youtube = Package( "Youtube", "com.google.android.youtube")
//        val youtubeMusic =
//            Package( "Youtube Music", "com.google.android.apps.youtube.music")
//        val gBoard = Package( "Gboard", "com.google.android.inputmethod.latin")

        val packageLists = ArrayList<Package>()
//        packageLists.add(Package("Youtube", "com.google.android.youtube"))
//        packageLists.add(Package("Youtube Music", "com.google.android.apps.youtube.music"))
//        packageLists.add(Package("Gboard", "com.google.android.inputmethod.latin"))

        packageLists.forEachIndexed { index, item ->
            itemsHashmap[index] = item
        }
    }

    fun setDownloadsDir(downloadsDir: File) {
        _downloadsDir = downloadsDir
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun downloadApk(
        appId: String,
        versionCode: String = "",
        onDownloadComplete: (id: Long) -> Unit
    ): Long {
        val context = App.getAppContext()
        val targetDir = File(downloadsDir, subdirectory).path
        val targetFile = File(targetDir, "$appId.apk")
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val apkVersionCode =
            if (versionCode.isEmpty()) "version=latest" else "versionCode=$versionCode"
        val apkDownloadUrl = "https://d.apkpure.com/b/APK/$appId?$apkVersionCode&nc=arm64-v8a"

        // Initialize the DownloadManager
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkDownloadUrl)).apply {
            setTitle("$appId APK")
            setDescription("Downloading the latest version of $appId.")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(targetFile))
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        }

        // Start the download
        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this) // Unregister receiver
                    onDownloadComplete(id) // Trigger callback
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            )
        }

        val handler = Handler(Looper.getMainLooper())

        handler.post(object : Runnable {
            override fun run() {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloaded =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val totalBytes =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    val status =
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        // Download is complete
                        cursor.close()
                        return
                    }

                    if (totalBytes > 0) {
                        val progress = (bytesDownloaded * 100L / totalBytes).toInt()
                        // Update your UI with progress
                        MainActivity.updatePatchProgress("Downloading... $progress%")
                        println("Progress: $progress%")
                    }
                    cursor.close()
                }

                // Schedule the next check
                handler.postDelayed(this, 1000)
            }
        })
        return downloadId
    }
}