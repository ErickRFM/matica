import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
    // Reutiliza exactamente SkillId del motor; evita copiar el catálogo en otro archivo.
    // El Docker de Ktor puede compilar esta fuente sin aplicar el plugin Android.
    sourceSets {
        main {
            kotlin.srcDir(rootProject.file("shared/math-engine/src/commonMain/kotlin/mx/matica/math/skills"))
        }
    }
}

application {
    mainClass.set("mx.matica.backend.ApplicationKt")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.postgres.jdbc)
    implementation(libs.logback.classic)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
