plugins {
    java
    id("ca.stellardrift.gitpatcher") version "2.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
}

group = findProperty("group") as String
version = findProperty("version") as String

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

// Delegate shadowJar and publish to the inventory-framework composite build (populated after applyPatches).
val isWindows = System.getProperty("os.name")
    .lowercase()
    .contains("windows")

val gradlew = if (isWindows) "gradlew.bat" else "./gradlew"

listOf("shadowJar", "publish").forEach { taskName ->
    tasks.register<Exec>(taskName) {
        group = if (taskName == "shadowJar") "build" else "publishing"
        description = "Runs './gradlew $taskName' inside inventory-framework."

        dependsOn("applyPatches")
        workingDir = layout.projectDirectory.dir("inventory-framework").asFile
        val args = listOf(taskName)

        if (isWindows) {
            commandLine("cmd", "/c", gradlew, *args.toTypedArray())
        } else {
            commandLine(gradlew, *args.toTypedArray())
        }
    }
}