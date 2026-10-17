plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.lirix.app.desktop.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "Lirix"
            packageVersion = "1.4.0"
            description = "Lirix Music & Lyrics Studio Hub for Windows"
            vendor = "Lirix Team"
            copyright = "© 2026 Lirix Team"

            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            windows {
                menu = true
                shortcut = true
                menuGroup = "Lirix"
                upgradeUuid = "68c2d1b8-3f59-4b13-8820-2b21cfbc8fa1"
            }
        }
    }
}

