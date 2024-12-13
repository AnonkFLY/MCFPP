rootProject.name = "Example"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("https://jitpack.io/")
        maven("https://libraries.minecraft.net")
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
    }
}

