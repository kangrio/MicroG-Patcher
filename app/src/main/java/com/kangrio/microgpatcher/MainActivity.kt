package com.kangrio.microgpatcher

import android.Manifest
import android.R
import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import com.kangrio.microgpatcher.databinding.ActivityMainBinding
import com.kangrio.microgpatcher.patcher.ApkPatcher
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : Activity() {
    val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding


    companion object {
        private const val PERMISSION_FILE_REQUEST_CODE = 100
        private const val FILE_PICKER_REQUEST_CODE = 101
        lateinit var progressDialog: ProgressDialog
        fun updatePatchProgress(progressName: String) {
            Handler(Looper.getMainLooper()).post {
                progressDialog.setMessage(progressName)
            }
        }

        fun progressFinish() {
            Handler(Looper.getMainLooper()).post {
                progressDialog.dismiss()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        progressDialog = ProgressDialog(this)
        progressDialog.setCancelable(false)
        Utils.setDownloadsDir(getExternalFilesDir(null)!!)
        generateItemsListView()
        binding.btnPatch.setOnClickListener {
            customPackagePatch()
        }

        binding.btnSelectApk.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                checkAndRequestPermission()
            } else {
                openFilePicker()
            }
        }


    }

    fun customPackagePatch() {
        val packageName = binding.editTextPackageName.text.toString()
        val versionCode = binding.editTextVersionCode.text.toString()
        val downloadFinishCallback: (downloadId: Long) -> Unit = {
            val apkFile = File(
                "${Utils.downloadsDir.path}/${Utils.subdirectory}/${
                    packageName
                }.apk"
            )

            Thread {
                updatePatchProgress("Reading apk file...")
                val apkPatcher = ApkPatcher(this, apkFile)
                apkPatcher.startPatch()
                progressFinish()
            }.start()
        }

        Thread {
            startDownloadProgress()
            Utils.downloadApk(
                appId = packageName,
                versionCode = versionCode,
                onDownloadComplete = downloadFinishCallback
            )
        }.start()
    }

    fun generateItemsListView() {
        val adapter =
            ArrayAdapter(
                this,
                R.layout.simple_list_item_1,
                Utils.itemsHashmap.values.map { it.name }.toTypedArray()
            )


        binding.appDetailListView.adapter = adapter

        binding.appDetailListView.setOnItemClickListener { parent, view, position, id ->
            val downloadFinishCallback: (downloadId: Long) -> Unit = {
                val apkFile = File(
                    "${Utils.downloadsDir.path}/${Utils.subdirectory}/${
                        Utils.itemsHashmap.get(position)!!.packageName
                    }.apk"
                )

                Thread {
                    updatePatchProgress("Reading apk file...")
                    val apkPatcher = ApkPatcher(this, apkFile)
                    apkPatcher.startPatch()
                    progressFinish()
                }.start()
            }

            Thread {
                startDownloadProgress()
                Utils.downloadApk(
                    appId = Utils.itemsHashmap.get(position)!!.packageName,
                    onDownloadComplete = downloadFinishCallback
                )
            }.start()
        }
    }

    fun startDownloadProgress() {
        Handler(Looper.getMainLooper()).post {
            progressDialog.setMessage("Downloading...")
            progressDialog.show()
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type =
                "application/vnd.android.package-archive"
        }
        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                handleFileUri(uri)
            } else {
                Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleFileUri(uri: Uri) {
        // Process the file URI
        Log.d(TAG, "File Selected: $uri")
        val inputStream = contentResolver.openInputStream(uri)

        var fileName: String? = null
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }

        val file = File(getExternalFilesDir("patch")!!.absolutePath, fileName)

        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        if (file.exists()) {
            file.delete()
        }

        Thread {
            startDownloadProgress()
            updatePatchProgress("Reading apk file...")
            val outputStream = FileOutputStream(file)
            try {
                val inputSize = inputStream?.available()!! // Size of the target file
                val maxBufferSize = 5 * 1024 * 1024 // Maximum buffer size (5MB)
                val defaultBufferSize = 64 * 1024 // Default buffer size (64KB)

                val bufferSize = when {
                    inputSize in 1..maxBufferSize -> inputSize // Use exact size for small files
                    inputSize > maxBufferSize -> maxBufferSize // Cap buffer size for large files
                    else -> defaultBufferSize // Use default size for unknown or zero-length files
                }

                val buffer = ByteArray(bufferSize) // Read in 5MB chunks

                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                inputStream!!.close()
                outputStream.close()
            }

            val apkPatcher = ApkPatcher(this, file)
            apkPatcher.startPatch()
            progressFinish()
        }.start()

    }

    private fun checkAndRequestPermission() {
        when {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is granted
                Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()

                val sdcardDir = File(Environment.getExternalStorageDirectory().path)

                sdcardDir.listFiles()?.forEach {
                    println(it.name)
                }

            }

            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                // Show an explanation to the user
                Toast.makeText(
                    this,
                    "We need this permission to access files",
                    Toast.LENGTH_SHORT
                ).show()
            }

            else -> {
                // Request the permission
                requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSION_FILE_REQUEST_CODE
                )
            }
        }
    }
}