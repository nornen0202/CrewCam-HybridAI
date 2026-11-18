plugins {
    id("com.android.application")
    kotlin("android")
}

if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.galaxycrew.crewcam"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.galaxycrew.crewcam.hybrid"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2-hybrid"
        testInstrumentationRunner = "com.galaxycrew.crewcam.CrewUiChecks"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core"))
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-video:1.4.2")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("com.google.guava:guava:31.0.1-android")
    implementation("com.google.firebase:firebase-ai:18.2.0")
    implementation("com.google.firebase:firebase-ai-ondevice:16.0.0-beta08")
    debugImplementation("com.google.firebase:firebase-appcheck-debug:20.1.0")
    releaseImplementation("com.google.firebase:firebase-appcheck-playintegrity:20.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
