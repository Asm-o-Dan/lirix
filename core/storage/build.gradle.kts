plugins {
    id("convention.android.library")
    id("convention.room")
}

android {
    namespace = "com.example.npc.core.storage"
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":pipeline:dsl"))
    implementation(libs.sqlcipher.android)
    implementation(libs.re2j)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
