// Plugins déclarés au niveau racine, appliqués dans :app.
// Versions vérifiées compatibles Gradle 8.14.3 + compileSdk 34.
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("com.google.devtools.ksp") version "2.1.21-2.0.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21" apply false
}
