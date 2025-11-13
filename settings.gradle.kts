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
    // É a ÚNICA fonte de verdade para repositórios de dependências.
    repositories {
        google()
        mavenCentral()
        // CORREÇÃO: Adicionando o repositório JitPack, necessário para a biblioteca colorpicker
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "FloatingSpeedrunTimer"
include(":app")
