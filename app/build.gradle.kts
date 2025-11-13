plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.floatingspeedruntimer"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.floatingspeedruntimer"
        minSdk = 26
        targetSdk = 34
        versionCode = 34
        versionName = "9.1"
    }
    buildFeatures {
        viewBinding = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.dhaval2404:colorpicker:2.3")

    // CORREÇÃO: Readicionando a dependência necessária para PreferenceManager
    implementation("androidx.preference:preference-ktx:1.2.1")
}
