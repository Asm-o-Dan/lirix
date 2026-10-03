plugins {
    id("convention.android.library")
    id("convention.room")
}

android {
    namespace = "com.example.npc.feature.replay"
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":pipeline:compiler"))
    implementation(project(":pipeline:nodes-api"))
    implementation(project(":pipeline:runtime"))
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.re2j)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
