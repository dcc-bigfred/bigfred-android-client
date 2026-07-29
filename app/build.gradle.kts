plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

import java.io.File
import java.net.HttpURLConnection
import java.net.URI

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

        ndk {
            abiFilters += "arm64-v8a"
        }
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
        jniLibs {
            useLegacyPackaging = true
        }
    }

    androidResources {
        noCompress += "db"
    }
}

// Stage native executables into jniLibs as lib*.so (executable from nativeLibraryDir).
val fetchNativeBinaries by tasks.registering {
    val jniOut = layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a")
    val prebuilt = rootProject.layout.projectDirectory.dir("native-prebuilt/arm64-v8a")
    val localLoco = rootProject.layout.projectDirectory.dir("../bigfred/bin")
        .file("loco-server-android-arm64")
    val fetchScript = rootProject.layout.projectDirectory.file("scripts/fetch-github-release-asset.sh")
    outputs.dir(jniOut)
    doLast {
        val outDir = jniOut.asFile
        outDir.mkdirs()

        fun githubToken(): String? =
            System.getenv("BIGFRED_NATIVE_TOKEN")
                ?: System.getenv("GH_TOKEN")
                ?: System.getenv("GITHUB_TOKEN")

        fun copyIfExists(src: File, destName: String): Boolean {
            if (!src.isFile) return false
            src.copyTo(File(outDir, destName), overwrite = true)
            println("Staged $destName from ${src.absolutePath}")
            return true
        }

        fun fetchLatestReleaseAsset(repo: String, assetName: String, dest: File) {
            val script = fetchScript.asFile
            require(script.isFile) { "Missing fetch script: ${script.absolutePath}" }
            val pb = ProcessBuilder(script.absolutePath, repo, assetName, dest.absolutePath)
            pb.directory(rootProject.projectDir)
            pb.redirectErrorStream(true)
            val env = pb.environment()
            githubToken()?.let { env["GITHUB_TOKEN"] = it }
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            val code = proc.waitFor()
            print(output)
            if (code != 0) {
                throw GradleException("Failed to fetch $assetName from $repo (exit $code)")
            }
        }

        fun stageOrFetch(prebuiltName: String, repoProp: String, defaultRepo: String) {
            val dest = File(outDir, prebuiltName)
            if (copyIfExists(prebuilt.file(prebuiltName).asFile, prebuiltName)) return
            val repo = (project.findProperty(repoProp) as String?)
                ?.ifBlank { null }
                ?: defaultRepo
            fetchLatestReleaseAsset(repo, prebuiltName, dest)
            // Keep native-prebuilt in sync for make skip-if-exists.
            val cache = prebuilt.file(prebuiltName).asFile
            cache.parentFile.mkdirs()
            dest.copyTo(cache, overwrite = true)
        }

        // Valkey / supervisord: prefer make-fetched native-prebuilt, else latest GitHub release.
        stageOrFetch(
            "libvalkey-server.so",
            "depsAndroidValkeyRepo",
            "dcc-bigfred/deps-android-valkey",
        )
        stageOrFetch(
            "libsupervisord.so",
            "depsAndroidSupervisordRepo",
            "dcc-bigfred/deps-android-supervisord",
        )
        stageOrFetch(
            "libsupervisorctl.so",
            "depsAndroidSupervisordRepo",
            "dcc-bigfred/deps-android-supervisord",
        )

        val locoDest = File(outDir, "libloco-server.so")
        if (localLoco.asFile.isFile) {
            localLoco.asFile.copyTo(locoDest, overwrite = true)
            println("Staged libloco-server.so from local ${localLoco.asFile}")
        } else {
            val repo = (project.findProperty("bigfredNativeRepo") as String?)
                ?.ifBlank { null }
                ?: System.getenv("BIGFRED_NATIVE_REPO")
            val tag = (project.findProperty("bigfredNativeTag") as String?)
                ?.ifBlank { null }
                ?: System.getenv("BIGFRED_NATIVE_TAG")
            if (repo.isNullOrBlank() || tag.isNullOrBlank()) {
                logger.warn(
                    "libloco-server.so missing: build ../bigfred with 'make android' " +
                        "or set bigfredNativeRepo + bigfredNativeTag",
                )
            } else {
                val token = githubToken()
                val url =
                    "https://github.com/$repo/releases/download/$tag/loco-server-android-arm64"
                val tmp = File(outDir, ".loco-server.download")
                val conn = URI(url).toURL().openConnection() as HttpURLConnection
                if (!token.isNullOrBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("Accept", "application/octet-stream")
                }
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                tmp.renameTo(locoDest)
                println("Downloaded libloco-server.so from $url")
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(fetchNativeBinaries)
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
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
