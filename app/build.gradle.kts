import java.io.File.separator

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
    history {
        val projectBuildDir = project.layout.buildDirectory.asFile.get()
        val outputDir = "${projectBuildDir.absolutePath}${separator}performance"
        val inputDir = "${rootDir}${separator}build-files"
        isTraceEnabled = true
        isRecordEnabled = true
        excludeManifest = "${inputDir}${separator}exclude${separator}ExcludeManifest.text"
        excludeClassFile = "${outputDir}${separator}exclude${separator}ExcludeClassList.text"
        excludeMethodFile = "${outputDir}${separator}exclude${separator}ExcludeMethodList.text"
        mappingMethodFile = "${outputDir}${separator}mapping${separator}MappingMethodList.text"
        mappingSnapshotDir = "${inputDir}${separator}snapshot"
        File(excludeManifest).takeIf { !it.exists() }?.let {
            it.parentFile.mkdirs()
            it.printWriter().use { writer ->
                writer.println("-package kotlin/")
                writer.println("-package kotlinx/coroutines/")
            }
        }
        File(mappingSnapshotDir).takeIf { !it.exists() }?.mkdirs()
    }
}