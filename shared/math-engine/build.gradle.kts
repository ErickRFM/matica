import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "mx.matica.shared.mathengine"
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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // `api` y no `implementation`: Rational expone BigInteger en su constructor, en
            // `numerator` y en `denominator`, así que el tipo es parte de la superficie pública.
            api(libs.bignum)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
