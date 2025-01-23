import groovy.util.Node
import groovy.xml.XmlParser
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kangrio.microgpatcher"
    compileSdk = 34

    androidComponents {
        val stringsXml = File(projectDir, "src/main/res/values/strings.xml")
        val rootNode  = XmlParser().parse(stringsXml)
        val stringNodes = rootNode.children().filterIsInstance<Node>()
        var appName = "app"

        stringNodes.forEach { node ->
            if (node.name() == "string" && node.attribute("name") == "app_name") {
                appName = node.text()
            }
        }

        onVariants { variant ->
            variant.outputs.forEach { output ->
                if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                    output.outputFileName = "${appName}-${defaultConfig.versionName}-${SimpleDateFormat("dd-MM-yyyy-hhmmss").format(Date())}-${variant.name}.apk"
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.kangrio.microgpatcher"
        minSdk = 24
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 101
        versionName = "1.1"

    }

    buildTypes {
        release {
            name
            signingConfig = signingConfigs.getByName("debug")
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.support.core.utils)
    implementation(libs.apksig)
    implementation(libs.arsclib)
    implementation(libs.baksmali)
    implementation(libs.smali)
    implementation(libs.dexlib2)
}

tasks.register<GradleBuild>("BuildAll") {
    tasks = listOf(
        ":extension:buildDex",
        ":app:assembleRelease"
    )

    doLast {
        copy {
            from("${rootProject.project("extension").projectDir}/build/libs/classes.dex")
            into("${project.projectDir}/src/main/assets/")
        }
    }
}