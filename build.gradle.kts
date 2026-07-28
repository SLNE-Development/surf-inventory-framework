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

// The wrapper is addressed by absolute path: `cmd /c gradlew.bat` resolves against PATH rather than the
// task's workingDir and fails with "gradlew.bat is either misspelled or could not be found".
val targetDir = layout.projectDirectory.dir("inventory-framework")
val gradlew = targetDir.file(if (isWindows) "gradlew.bat" else "gradlew").asFile.absolutePath

listOf("shadowJar", "publish", "publishToMavenLocal").forEach { taskName ->
    tasks.register<Exec>(taskName) {
        group = if (taskName == "shadowJar") "build" else "publishing"
        description = "Runs './gradlew $taskName' inside inventory-framework."

        dependsOn("applyPatches")
        workingDir = targetDir.asFile
        val args = listOf(taskName)

        if (isWindows) {
            commandLine("cmd", "/c", gradlew, *args.toTypedArray())
        } else {
            commandLine(gradlew, *args.toTypedArray())
        }
    }
}