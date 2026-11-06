pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "matica"

// El contenedor de API se construye sin descargar Android SDK ni configurar módulos de app.
if (providers.gradleProperty("matica.backendOnly").orNull == "true") {
    include(":backend:api")
} else {
    include(":apps:androidApp")
    include(":shared:math-engine")
    include(":shared:math-remote")
    include(":shared:learning-engine")
    include(":shared:exercises")
    include(":shared:presentation")
    include(":design-system")
    include(":backend:api")
}
