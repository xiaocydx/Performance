plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.performance)
}

android {
    namespace = "com.xiaocydx.performance.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xiaocydx.performance.sample"
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
        val dir = project(":performance-convert").projectDir
        isEnabled = true
        keepMethodFile = "${dir}/inputs/keepMethodList.text"
        ignoredClassFile = "${dir}/outputs/ignored/ignoredClassList.text"
        ignoredMethodFile = "${dir}/outputs/ignored/ignoredMethodList.text"
        mappingMethodFile = "${dir}/outputs/mapping/mappingMethodList.text"
    }
}