plugins {
    id("convention.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
