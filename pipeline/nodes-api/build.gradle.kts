plugins {
    id("convention.kotlin.jvm")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.re2j)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
