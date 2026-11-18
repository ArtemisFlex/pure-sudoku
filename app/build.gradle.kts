import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keyProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.puresudoku"
    compileSdk = 36

    defaultConfig {
        // The Play Store ID. Permanent once published. (The code namespace above stays com.puresudoku.)
        applicationId = "app.puregames"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.0"
    }

    // Upload key for Google Play, read from keystore.properties (git-ignored, never committed):
    //   storeFile=C:/path/to/puregames-upload.jks
    //   storePassword=...
    //   keyAlias=upload
    //   keyPassword=...
    // Without that file, release builds fall back to the debug key so they still install for testing.
    signingConfigs {
        if (keyProps.getProperty("storeFile") != null) create("upload") {
            storeFile = file(keyProps.getProperty("storeFile"))
            storePassword = keyProps.getProperty("storePassword")
            keyAlias = keyProps.getProperty("keyAlias")
            keyPassword = keyProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

// Game state lives in plain arrays inside session objects, so screens must redraw
// whenever their parent does. Strong skipping (on by default since Kotlin 2.0.20)
// skips those redraws, which left moves invisible; turn it off app-wide.
composeCompiler {
    featureFlags.add(org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag.StrongSkipping.disabled())
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
