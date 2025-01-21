plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.kangrio.extension"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
}

dependencies {
    implementation(libs.hiddenapibypass)
}

tasks.register<Sync>("buildDex") {
    dependsOn(tasks.getByName("minifyReleaseWithR8"))

    from("${project.buildDir}/intermediates/dex/release/minifyReleaseWithR8/classes.dex")
    into("${project.buildDir}/libs/")
}