package com.kangrio.microgpatcher.patcher

import android.content.Context
import android.content.Intent
import android.support.v4.content.FileProvider
import android.util.Base64
import android.util.Log
import com.android.apksig.apk.ApkUtils
import com.android.apksig.internal.apk.v2.V2SchemeVerifier
import com.android.apksig.util.DataSources
import com.android.apksig.util.RunnablesExecutor
import com.kangrio.microgpatcher.MainActivity
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.chunk.xml.ResXmlElement
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.rewriter.ClassDefRewriter
import org.jf.dexlib2.rewriter.DexRewriter
import org.jf.dexlib2.rewriter.Rewriter
import org.jf.dexlib2.rewriter.RewriterModule
import org.jf.dexlib2.rewriter.Rewriters
import org.jf.dexlib2.writer.io.FileDataStore
import org.jf.dexlib2.writer.pool.DexPool
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class ApkPatcher(val context: Context, val apkFile: File) {
    val TAG = "ApkPatch"

    lateinit var apkModule: ApkModule

    fun startPatch() {
        apkModule = ApkModule.loadApkFile(apkFile)
        MainActivity.updatePatchProgress("processing: AndroidManifest.xml")
        patchAndroidManifest(getSignatureBase64(apkFile.absolutePath))
        addPatchedDexToApk()
        patchMicroG()

        MainActivity.updatePatchProgress("Writing apk file...")
        val patchedApkFile = File(apkFile.parentFile, "patched_" + apkFile.name)
        val outputStream = FileOutputStream(patchedApkFile)
        apkModule.writeApk(outputStream)
        outputStream.close()

        MainActivity.updatePatchProgress("Signing apk file...")
        val signedApk = signApk(patchedApkFile)
        installApk(signedApk)
        apkFile.parentFile.listFiles().forEach {
            if (!it.name.contains("sign")) {
                it.deleteRecursively()
            }
        }
    }

    fun addPatchedDexToApk() {
        val dexInputStream = context.assets.open("classes.dex")
        val dexBytes = dexInputStream.readBytes()

        var classesDexName = "classes${apkModule.listDexFiles().size + 1}.dex"
        val classesDex =
            ByteInputSource(dexBytes, classesDexName)
        apkModule.add(classesDex)
    }

    fun patchMicroG() {
        val modifiedStats = HashMap<Int, Boolean>()
        apkModule.listDexFiles().forEachIndexed { index, dexFileInputSource ->
            if (!dexFileInputSource.name.startsWith("classes")) {
                return@forEachIndexed
            }
            MainActivity.updatePatchProgress("processing: ${dexFileInputSource.name}")
            modifiedStats.put(index, false)
            val outputStream = ByteArrayOutputStream()
            dexFileInputSource.write(outputStream)
            outputStream.close()

            val dexFileInput = DexBackedDexFile(
                Opcodes.forApi(BaksmaliOptions().apiLevel),
                outputStream.toByteArray()
            )

            val outputDir = File(apkFile.parentFile.absolutePath, apkModule.packageName)

            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            Log.d(TAG, "Processing DEX file: ${dexFileInputSource.name}")
            modifyDex(dexFileInput, dexFileInputSource.name)
            modifiedStats.remove(index)
        }
    }

    fun modifyDex(dexFile: DexBackedDexFile, outputDexName: String) {
        val rewriterModule = object : RewriterModule() {
            override fun getClassDefRewriter(rewriters: Rewriters): Rewriter<ClassDef> {
                return object : ClassDefRewriter(rewriters) {
                    override fun rewrite(classDef: ClassDef): ClassDef {
                        if (classDef.superclass == "Landroid/app/Application;" && classDef.type != "Lcom/kangrio/extension/SignaturePatchApplication;") {
//                        if (classDef.superclass == "Landroid/app/Application;" && classDef.type != "Lapp/revanced/extension/shared/SignaturePatchApplication;") {
                            val superClassApp = object : ClassDef by classDef {
                                override fun getSuperclass(): String {
                                    return "Lcom/kangrio/extension/SignaturePatchApplication;"
//                                    return "Lapp/revanced/extension/shared/SignaturePatchApplication;"
                                }
                            }
                            return super.rewrite(superClassApp)
                        }
                        return super.rewrite(classDef)
                    }
                }
            }

//            override fun getInstructionRewriter(rewriters: Rewriters): Rewriter<Instruction> {
//                return object : InstructionRewriter(rewriters) {
//                    override fun rewrite(instruction: Instruction): Instruction {
//                        var newInstruction = instruction
//                        if (instruction is Instruction21c && instruction.reference is StringReference) {
//                            val stringReference = instruction.reference as StringReference
//                            // If the string matches oldString, replace it with newString
//                            if (stringReference.string == "\$appPackageName") {
//                                newInstruction = object : Instruction21c by instruction {
//                                    override fun getReference(): StringReference {
//                                        return ImmutableStringReference(apkModule.packageName)
//                                    }
//                                }
//                            } else if (stringReference.string == "\$originalSignatrue") {
//                                newInstruction = object : Instruction21c by instruction {
//                                    override fun getReference(): StringReference {
//                                        return ImmutableStringReference(getSignatureBase64(apkFile.absolutePath))
//                                    }
//                                }
//                            }
//                        }
//                        return super.rewrite(newInstruction)
//                    }
//                }
//            }

        }

        val rewriter = DexRewriter(rewriterModule)
        val rewrittenDexFile = rewriter.dexFileRewriter.rewrite(dexFile)

        dexFile.classes.forEach {
            if (it.superclass == "Landroid/app/Application;") {
                val dexPool = DexPool(rewrittenDexFile.getOpcodes())
                val startTime = System.currentTimeMillis()
                for (classDef in rewrittenDexFile.getClasses()) {
                    dexPool.internClass(classDef)
                }

                val outputDexDir = File(apkFile.parentFile, apkModule.packageName)

                if (!outputDexDir.exists()) {
                    outputDexDir.mkdirs()
                }

                val outputDexFile = File(outputDexDir, outputDexName)

                dexPool.writeTo(FileDataStore(outputDexFile))

                val dexInputSources = ByteInputSource(outputDexFile.readBytes(), outputDexName)
                apkModule.add(dexInputSources)

                Log.d(
                    TAG,
                    "Modified DEX: $outputDexName, usedTime: ${System.currentTimeMillis() - startTime}ms"
                )
                return
            }
        }
        Log.d(TAG, "Skip Modify DEX: $outputDexName")
    }

    fun patchAndroidManifest(signatureData: String) {
        val application: ResXmlElement = apkModule.androidManifest.applicationElement
        val meta: ResXmlElement = application.createChildElement(AndroidManifest.TAG_meta_data)
        val name = meta.getOrCreateAndroidAttribute(
            AndroidManifest.NAME_name,
            AndroidManifest.ID_name
        )
        name.setValueAsString("org.microg.gms.spoofed_certificates")
        val value = meta.getOrCreateAndroidAttribute(
            AndroidManifest.NAME_value,
            AndroidManifest.ID_value
        )
        value.valueAsString = signatureData
    }

    fun getSignatureBase64(apkFilePath: String): String {
        val apkFile = File(apkFilePath)
        val dataSource = DataSources.asDataSource(RandomAccessFile(apkFile.path, "r"))
        val zipSections = ApkUtils.findZipSections(dataSource)
        val v2 = V2SchemeVerifier.verify(
            RunnablesExecutor.SINGLE_THREADED,
            dataSource,
            zipSections,
            mapOf(2 to "APK Signature Scheme v2"),
            hashSetOf(2),
            24,
            Int.MAX_VALUE
        )
        return Base64.encodeToString(v2.signers[0].certs[0].encoded, Base64.DEFAULT)
    }

    fun signApk(apkFile: File): File {
        val apkSigner = APKSigner(context)
        val outputFile = File(apkFile.parentFile, apkFile.name.replace(".apk", "_sign.apk"))
        apkSigner.sign(apkFile, outputFile)
        return outputFile
    }

    fun installApk(apkFile: File) {
        val apkUri =
            FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(
            apkUri,
            "application/vnd.android.package-archive"
        )
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // without this flag android returned a intent error!
        context.startActivity(intent)
    }
}