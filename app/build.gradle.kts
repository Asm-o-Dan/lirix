plugins {
    id("convention.android.application")
    id("convention.compose")
    id("convention.hilt")
}

android {
    namespace = "com.example.npc.app"

    defaultConfig {
        applicationId = "com.example.npc"
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}


dependencies {
    // Feature modules
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(project(":ingest:notification"))
    implementation(project(":ingest:sms"))
    implementation(project(":ingest:media"))
    implementation(project(":ui:timeline"))
    implementation(project(":classify:rules"))
    implementation(project(":extract:finance"))
    implementation(project(":domain"))
    implementation(project(":feature:replay"))

    // AndroidX Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Room (needed by AppModule.kt for Room.databaseBuilder / RoomDatabase)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    // WorkManager + Hilt integration
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Hilt Navigation Compose
    implementation(libs.androidx.hilt.navigation.compose)

    // SQLCipher (ADR-007)
    implementation(libs.sqlcipher.android)

    // Compose UI (BOM managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Debug tooling
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Unit tests
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
