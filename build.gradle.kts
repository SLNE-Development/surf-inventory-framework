plugins {
    java
    id("ca.stellardrift.gitpatcher") version "2.0.0"
}

group = "dev.slne.forks.inventoryframework"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}