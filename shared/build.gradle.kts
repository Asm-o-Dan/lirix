plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.room.runtime)
                implementation(libs.sqlite.bundled)
            }
        }

        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jsoup:jsoup:1.23.2")
                implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
                implementation("dev.esnault.wanakana:wanakana-core:1.1.1")
                implementation("com.github.houbb:pinyin:0.4.0")
                implementation("com.github.houbb:heaven:0.2.0")
                implementation("com.github.houbb:nlp-common:0.0.5")
            }
        }

        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
            }
        }

        val desktopMain by getting {
            dependsOn(jvmShared)
            dependencies {
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

android {
    namespace = "com.lirix.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
}

