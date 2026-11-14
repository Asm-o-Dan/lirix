plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lirix.app"
    compileSdk = 35
    ndkVersion = "26.1.10909125"
    flavorDimensions += "translation"
    productFlavors {
        create("private") {
            dimension = "translation"
            buildConfigField("String", "TRANSLATION_VARIANT", "\"private\"")
            manifestPlaceholders["translationVariant"] = "private"
            externalNativeBuild.cmake {
                arguments += "-DLIRIX_PRIVATE=ON"
                targets += "lirix_sentencepiece"
            }
        }
        create("compact") {
            dimension = "translation"
            buildConfigField("String", "TRANSLATION_VARIANT", "\"compact\"")
            manifestPlaceholders["translationVariant"] = "compact"
            externalNativeBuild.cmake.arguments += "-DLIRIX_PRIVATE=OFF"
        }
    }
    externalNativeBuild.cmake {
        path = file("src/private/cpp/CMakeLists.txt")
        version = "3.22.1"
    }

    defaultConfig {
        applicationId = "com.lirix.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "1.5.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.generateKotlin", "true")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("RELEASE_STORE_FILE") ?: "lirix-release.jks")
            val releasePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            if (!releasePassword.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()) {
                storePassword = releasePassword
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "lirix"
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val hasReleaseCredentials = !System.getenv("RELEASE_KEYSTORE_PASSWORD").isNullOrBlank()
            if (hasReleaseCredentials) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
        // Instrumented tests install and then uninstall the app they test. A separate package keeps
        // them away from the user's installed com.lirix.app and its data (incident 2026-11-01).
        create("uitest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            matchingFallbacks += listOf("debug")
        }
    }
    testBuildType = "uitest"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets.getByName("androidTest").assets.srcDir("src/test/resources/fixtures")
    // The test build type shares the debug-only Compose test host.
    sourceSets.getByName("uitest").manifest.srcFile("src/debug/AndroidManifest.xml")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += setOf("/README.md", "README.md", "**/README.md")
            // Both Kuromoji artifacts carry notices; preserve all text in the APK.
            merges += setOf("META-INF/LICENSE.md", "META-INF/CONTRIBUTORS.md")
        }
    }
}

dependencies {
    // Shared KMP Module
    implementation(project(":shared"))

    // AndroidX Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose (BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Room Persistence
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines & Flow
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // On-device translation; language models are downloaded separately after opt-in.
    "compactImplementation"("com.google.mlkit:translate:17.0.3")
    "compactImplementation"("com.google.mlkit:language-id:17.0.6")

    "privateImplementation"("com.microsoft.onnxruntime:onnxruntime-android:1.23.2") {
        version { strictly("1.23.2") }
    }
    "privateImplementation"("com.optimaize.languagedetector:language-detector:0.6") {
        exclude(group = "com.intellij", module = "annotations")
    }
    // Full Guava supplies the same interface; keep AndroidX's extraction artifact empty.
    "privateImplementation"("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")

    // Logging
    implementation(libs.timber)

    // DOM Parser for Lyrics Rules
    implementation("org.jsoup:jsoup:1.23.2")

    // QR & Barcode (ZXing)
    implementation("com.google.zxing:core:3.5.3")

    // CameraX for QR Scanning
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Testing
    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}



dependencies {
    // The instrumented-test build type needs the same debug-only Compose helpers.
    "uitestImplementation"(libs.androidx.ui.tooling)
    "uitestImplementation"(libs.androidx.ui.test.manifest)
}

// Instrumented tests also change device state (locale, foreground activity). They run on an
// emulator; a physical phone needs an explicit -Plirix.allowPhysicalDevice=true.
val allowPhysicalDevice = providers.gradleProperty("lirix.allowPhysicalDevice").map { it.toBoolean() }.orElse(false)
val adbExecutable = androidComponents.sdkComponents.adb
tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    val allowed = allowPhysicalDevice
    val adb = adbExecutable
    doFirst {
        if (allowed.get()) return@doFirst
        val adbPath = adb.get().asFile.absolutePath
        fun run(vararg args: String): String {
            val process = ProcessBuilder(adbPath, *args).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            return output
        }
        val serials = run("devices").lines().drop(1)
            .map { it.trim().split(Regex("[ \t]+")) }
            .filter { it.size >= 2 && it[1] == "device" }
            .map { it[0] }
        val physical = serials.filterNot { serial ->
            val props = run("-s", serial, "shell", "getprop ro.kernel.qemu; getprop ro.boot.qemu; getprop ro.hardware")
            props.lines().map { it.trim() }.any { it == "1" || it == "ranchu" || it == "goldfish" }
        }
        if (physical.isNotEmpty()) {
            throw GradleException(
                "Instrumented tests are blocked on physical devices ${physical.joinToString()}. " +
                    "Run them on an emulator, or pass -Plirix.allowPhysicalDevice=true for a test phone without personal data."
            )
        }
    }
}
