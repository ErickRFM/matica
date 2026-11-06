import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "mx.matica.presentation"
        compileSdk = 37
        minSdk = 26
        withHostTest { }
        withDeviceTest { }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.animation)
            implementation(libs.kotlinx.coroutines.core)
            api(project(":design-system"))
            api(project(":shared:math-engine"))
            api(project(":shared:learning-engine"))
            api(project(":shared:exercises"))
            // `api`: MaticaSession expone `today: LocalDate` en su superficie, así que quien consume
            // la presentación también necesita el tipo.
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
