import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    android {
        namespace = "mx.matica.shared.mathremote"
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
            api(project(":shared:math-engine"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}

// El contraste Kotlin↔SymPy sólo corre con servicio real: `-Pmatica.symbolic.baseUrl=http://127.0.0.1:8099`.
// Sin propiedad, la prueba se declara no ejecutada y no cuenta como verde.
tasks.withType<Test>().configureEach {
    providers.gradleProperty("matica.symbolic.baseUrl").orNull?.let {
        systemProperty("matica.symbolic.baseUrl", it)
    }
}
