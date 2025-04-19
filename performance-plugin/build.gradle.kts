plugins {
    id("java-library")
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    sourceSets.all {
        languageSettings {
            optIn("kotlin.time.ExperimentalTime")
        }
    }
}

dependencies {
    implementation("com.android.tools.build:gradle:7.4.0")
    implementation("org.ow2.asm:asm:9.1")
    implementation("org.ow2.asm:asm-commons:9.1")
    implementation("com.google.code.gson:gson:2.13.0")
}

gradlePlugin {
    plugins {
        create("PerformancePlugin") {
            id = "performance-plugin"
            implementationClass = "com.xiaocydx.performance.plugin.PerformancePlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.xiaocydx.performance"
            artifactId = "plugin"
            version = "1.0.0"
            from(components["java"])
        }
    }
    repositories {
        maven(url = uri("file://${rootProject.projectDir}/repo"))
    }
}