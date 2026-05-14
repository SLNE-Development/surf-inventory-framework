plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "surf-inventory-framework"

if (file("inventory-framework/build-logic/settings.gradle.kts").exists()) {
    includeBuild("inventory-framework/build-logic")
}

if (file("inventory-framework/settings.gradle.kts").exists()) {
    includeBuild("inventory-framework")
}