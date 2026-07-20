plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun gitCommand(vararg args: String): String {
    return try {
        val process = ProcessBuilder("git", *args)
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (process.exitValue() == 0 && output.isNotEmpty()) output else ""
    } catch (_: Exception) {
        ""
    }
}

val gitCommitShort = (System.getenv("GITHUB_SHA")?.take(12)
    ?: gitCommand("rev-parse", "--short=12", "HEAD"))
    .ifEmpty { "unknown" }
val gitCommitFull = (System.getenv("GITHUB_SHA")
    ?: gitCommand("rev-parse", "HEAD"))
    .ifEmpty { "unknown" }
val gitDirty = when {
    System.getenv("CI") != null -> false
    else -> gitCommand("status", "--porcelain").isNotEmpty()
}

android {
    namespace = "com.dccbigfred.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dccbigfred.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.1"

        buildConfigField("String", "GIT_COMMIT", "\"$gitCommitShort\"")
        buildConfigField("String", "GIT_COMMIT_FULL", "\"$gitCommitFull\"")
        buildConfigField("boolean", "GIT_DIRTY", "$gitDirty")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("BIGFRED_STORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("BIGFRED_STORE_PASSWORD")
                keyAlias = System.getenv("BIGFRED_KEY_ALIAS")
                keyPassword = System.getenv("BIGFRED_KEY_PASSWORD")
            } else {
                // Local / CI without a release keystore: sign with the debug key.
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += "db"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
