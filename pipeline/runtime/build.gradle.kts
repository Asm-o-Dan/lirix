plugins {
    id("convention.kotlin.jvm")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:text"))
    implementation(project(":pipeline:compiler"))
    implementation(project(":pipeline:nodes-api"))
    implementation(project(":extract:universal"))
    implementation(libs.re2j)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
