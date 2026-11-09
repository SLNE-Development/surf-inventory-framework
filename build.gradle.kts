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

// `test` is already taken by the `java` plugin on this project, so the delegating task carries a distinct
// name. It is what CI runs to verify a pull request.
val delegatedTasks = mapOf(
    "shadowJar" to "shadowJar",
    "publish" to "publish",
    "publishToMavenLocal" to "publishToMavenLocal",
    "testPatched" to "test",
)

delegatedTasks.forEach { (taskName, delegate) ->
    tasks.register<Exec>(taskName) {
        group = when (taskName) {
            "shadowJar" -> "build"
            "testPatched" -> "verification"
            else -> "publishing"
        }
        description = "Runs './gradlew $delegate' inside inventory-framework."

        dependsOn("applyPatches")
        workingDir = targetDir.asFile
        val args = listOf(delegate)

        if (isWindows) {
            commandLine("cmd", "/c", gradlew, *args.toTypedArray())
        } else {
            commandLine(gradlew, *args.toTypedArray())
        }
    }
}