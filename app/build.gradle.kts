import java.io.File.separator
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.performance)
}

android {
    namespace = "com.xiaocydx.sample.performance"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xiaocydx.sample.performance"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":performance"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

performance {
    val properties = File(rootDir, "gradle.properties")
        .inputStream().use { Properties().apply { load(it) } }
    history {
        isTraceEnabled = true
        isRecordEnabled = true
        excludeManifest = properties.getPath("excludeManifest")
        excludeClassFile = properties.getPath("excludeClassFile")
        excludeMethodFile = properties.getPath("excludeMethodFile")
        mappingMethodFile = properties.getPath("mappingMethodFile")
        val file = File(excludeManifest)
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.printWriter().use {
                it.println("-package kotlin/")
                it.println("-package kotlinx/coroutines/")
            }
        }
    }
}

private fun Properties.getPath(key: String): String {
    val value = getProperty(key, "")
    if (value.isEmpty()) return ""
    return "${rootDir}${separator}${value}"
}