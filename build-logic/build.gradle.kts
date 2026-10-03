plugins {
    `kotlin-dsl`
}

group = "com.example.npc.buildlogic"

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.room.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
    implementation(libs.compose.compiler.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinJvm") {
            id = "convention.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("kotlinJvmLegacy") {
            id = "kotlin-jvm-convention"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("androidLibrary") {
            id = "convention.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryLegacy") {
            id = "android-library-convention"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "convention.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationLegacy") {
            id = "android-application-convention"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("room") {
            id = "convention.room"
            implementationClass = "RoomConventionPlugin"
        }
        register("roomLegacy") {
            id = "room-convention"
            implementationClass = "RoomConventionPlugin"
        }
        register("hilt") {
            id = "convention.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("hiltLegacy") {
            id = "hilt-convention"
            implementationClass = "HiltConventionPlugin"
        }
        register("compose") {
            id = "convention.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("composeLegacy") {
            id = "compose-convention"
            implementationClass = "ComposeConventionPlugin"
        }
    }
}
