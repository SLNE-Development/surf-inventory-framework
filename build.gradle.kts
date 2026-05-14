plugins {
    java
    id("ca.stellardrift.gitpatcher") version "2.0.0"
}

allprojects {
    group = "dev.slne.forks.inventoryframework"
    version = "1.0-SNAPSHOT"
}

repositories {
    mavenCentral()
}

dependencies {
}

gitPatcher.patchedRepos {
    register("inventory-framework") {
        submodule = "upstream"
        target = file("inventory-framework")
        patches = file("patches")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}