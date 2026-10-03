plugins {
    id("convention.kotlin.jvm")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
