buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        // Match Miuix's Kotlin metadata while keeping AGP's built-in Kotlin support.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
