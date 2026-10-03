plugins {
    id("convention.kotlin.jvm")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
