import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "1.7.4"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("xyz.jpenilla.resource-factory-bukkit-convention") version "1.2.0"
}

group = "kr.toxicity.hud.profiler"
version = "1.0"

val minecraft = "1.20.4"

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("$minecraft-R0.1-SNAPSHOT")
}

paperweight.reobfArtifactConfiguration = ReobfArtifactConfiguration.REOBF_PRODUCTION

tasks {
    test {
        useJUnitPlatform()
    }
    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }
    runServer {
        version(minecraft)
        pluginJars(fileTree("plugins"))
        downloadPlugins {
            hangar("BetterHud", "1.9.1")
            hangar("PlaceholderAPI", "2.11.6")
        }
    }
    assemble {
        dependsOn(reobfJar)
    }
}

bukkitPluginYaml {
    main = "${project.group}.BetterHudProfiler"
    apiVersion = "1.20"
    name = "BetterHudProfiler"

    author = "toxicity"
    description = "profile BetterHud packet."
}