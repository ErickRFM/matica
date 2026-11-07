import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

val maticaApiBaseUrl = providers.gradleProperty("matica.api.baseUrl")
    .orElse(providers.environmentVariable("MATICA_API_BASE_URL"))
    .orElse("")
    .get()
require(maticaApiBaseUrl.isBlank() || maticaApiBaseUrl.startsWith("https://")) {
    "MATICA_API_BASE_URL debe ser HTTPS (o dejarse vacía para modo offline)."
}

// Firma de release opcional: si las propiedades existen, se firma; si no (CI, máquina limpia),
// el APK de release se genera sin firmar en vez de romper el build. Las contraseñas viven en
// ~/.gradle/gradle.properties, nunca en el repositorio.
val releaseStore = providers.gradleProperty("matica.release.storeFile").orNull
val releaseAlias = providers.gradleProperty("matica.release.keyAlias").orNull

android {
    namespace = "mx.matica.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "mx.matica"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MATICA_API_BASE_URL", "\"$maticaApiBaseUrl\"")
    }

    signingConfigs {
        if (releaseStore != null && releaseAlias != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = providers.gradleProperty("matica.release.storePassword").orNull
                keyAlias = releaseAlias
                keyPassword = providers.gradleProperty("matica.release.keyPassword").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

sqldelight {
    databases {
        create("MaticaDatabase") {
            packageName.set("mx.matica.app.db")
        }
    }
}

dependencies {
    // `design-system` se usa directamente en las pruebas de instrumentación; `presentation` aporta el host,
    // las pantallas y, por `api`, los motores matemático y de aprendizaje.
    implementation(project(":design-system"))
    implementation(project(":shared:presentation"))

    implementation(libs.sqldelight.android.driver)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.turbine)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
