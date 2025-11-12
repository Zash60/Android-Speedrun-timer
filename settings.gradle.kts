// settings.gradle.kts

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Esta configuração se aplica a todos os projetos e subprojetos (como :app)
    repositories {
        google()
        mavenCentral()
        // Adicionando jitpack aqui, que estava faltando no seu original,
        // caso alguma dependência futura precise dele.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "FloatingSpeedrunTimer"
include(":app")
