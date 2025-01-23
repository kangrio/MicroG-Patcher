package com.kangrio.microgpatcher.patcher

import android.content.Context
import android.content.Intent
import android.support.v4.content.FileProvider
import android.util.Base64
import android.util.Log
import com.kangrio.microgpatcher.MainActivity
import com.kangrio.microgpatcher.loadApkInputStream
import com.reandroid.apk.ApkModule
import com.reandroid.app.AndroidManifest
import com.reandroid.archive.ByteInputSource
import com.reandroid.archive.block.SignatureId
import com.reandroid.arsc.chunk.xml.ResXmlElement
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.ClassDef
import org.jf.dexlib2.rewriter.ClassDefRewriter
import org.jf.dexlib2.rewriter.DexRewriter
import org.jf.dexlib2.rewriter.Rewriter
import org.jf.dexlib2.rewriter.RewriterModule
import org.jf.dexlib2.rewriter.Rewriters
import org.jf.dexlib2.writer.io.MemoryDataStore
import org.jf.dexlib2.writer.pool.DexPool
import java.io.File
import java.io.InputStream

class ApkPatcher {
    val TAG = "ApkPatch"
    var context: Context
    var patchDir: File
    var apkFile: File
    var apkModule: ApkModule

    constructor(context: Context, apkInputStream: InputStream, fileName: String = "file.apk") {
        this.context = context
        this.apkModule = ApkModule().loadApkInputStream(apkInputStream)
        apkInputStream.close()
        this.patchDir = File(context.externalCacheDir, "patch")
        this.apkFile = File(patchDir, fileName)
    }

    constructor(context: Context, apkFile: File) {
        this.context = context
        this.apkModule = ApkModule.loadApkFile(apkFile)
        this.patchDir = File(context.externalCacheDir, "patch")
        this.apkFile = apkFile
    }

    fun startPatch() {
        MainActivity.updatePatchProgress("processing: AndroidManifest.xml")
        patchAndroidManifest(getSignatureBase64())
        addPatchedDexToApk()
        patchMicroG()

        MainActivity.updatePatchProgress("Writing apk file...")
        val patchedApkFile = File(patchDir, "patched_" + apkFile.name)
        apkModule.writeApk(patchedApkFile)

        MainActivity.updatePatchProgress("Signing apk file...")
        val signedApk = signApk(patchedApkFile)
        installApk(signedApk)
        patchDir.listFiles()?.forEach {
            if (!it.name.contains("sign")) {
                it.deleteRecursively()
            }
        }
    }

    fun addPatchedDexToApk() {
        val dexInputStream = context.assets.open("classes.dex")
        val dexBytes = dexInputStream.readBytes()

        var classesDexName = "classes${apkModule.listDexFiles().filter { it.name.startsWith("classes") && !it.name.contains("/") }.size + 1}.dex"
        val classesDex =
            ByteInputSource(dexBytes, classesDexName)
        apkModule.add(classesDex)
    }

    fun patchMicroG() {
        apkModule.listDexFiles().forEach { dexFileInputSource ->
            if (!dexFileInputSource.name.startsWith("classes")) {
                return@forEach
            }
            MainActivity.updatePatchProgress("processing: ${dexFileInputSource.name}")
            val outputStream = dexFileInputSource.openStream()

            val dexFileInput = DexBackedDexFile(
                null,
                outputStream.readBytes()
            )

            Log.d(TAG, "Processing DEX file: ${dexFileInputSource.name}")
            modifyDex(dexFileInput, dexFileInputSource.name)
            outputStream.close()
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

        dexFile.classes.find { it.superclass == "Landroid/app/Application;" }?.let {
            val dexPool = DexPool(rewrittenDexFile.opcodes)
            val startTime = System.currentTimeMillis()
            for (classDef in rewrittenDexFile.classes) {
                dexPool.internClass(classDef)
            }

            val outputDexMemory = MemoryDataStore()
            dexPool.writeTo(outputDexMemory)
            val dexInputSources = ByteInputSource(outputDexMemory.data, outputDexName)
            dexInputSources.write(File(patchDir, outputDexName))
            apkModule.add(dexInputSources)

            Log.d(
                TAG,
                "Modified DEX: $outputDexName, usedTime: ${System.currentTimeMillis() - startTime}ms"
            )
            return
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

    fun getSignatureBase64(): String {
        return Base64.encodeToString(
            apkModule.apkSignatureBlock.getSignature(SignatureId.V2).certificates.next().certificateBytes,
            Base64.DEFAULT
        )
    }

    fun signApk(apkFile: File): File {
        val apkSigner = APKSigner(context)
        val outputFile = File(patchDir, apkFile.name.replace(".apk", "_sign.apk"))
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
        val packageInstallerLists = listOf("com.android.packageinstaller", "com.google.android.packageinstaller")
        val installerPackager = packageInstallerLists.filter{
            val i = Intent()
            i.setPackage(it)
            context.packageManager.resolveActivity(i, Intent.FLAG_ACTIVITY_NEW_TASK) != null
        }.firstOrNull()

        intent.setPackage(installerPackager)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // without this flag android returned a intent error!
        context.startActivity(intent)
    }
}