import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.wasabi"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.wasabi"
        minSdk = 26          // Retroid Pocket Classic ships Android 14 (API 34)
        targetSdk = 36
        versionCode = 13
        versionName = "1.12"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Personal sideload app: sign release with the debug key so the
            // APK installs without setting up a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// After assembling, copy the APK to build/outputs/wasabi/ with the version in
// its name: wasabi-1.3.apk (release) or wasabi-1.3-debug.apk (debug).
val appVersion = android.defaultConfig.versionName ?: "dev"
androidComponents {
    onVariants { variant ->
        val suffix = if (variant.buildType == "release") "" else "-${variant.name}"
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val copyApk = tasks.register<Copy>("copy${cap}VersionedApk") {
            from(variant.artifacts.get(SingleArtifact.APK)) { include("*.apk") }
            into(layout.buildDirectory.dir("outputs/wasabi"))
            rename(".*\\.apk", "wasabi-$appVersion$suffix.apk")
        }
        tasks.matching { it.name == "assemble$cap" }.configureEach { finalizedBy(copyApk) }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
