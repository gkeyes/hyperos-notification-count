plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.hyperos.notificationcount"
    compileSdk { version = release(37) }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "dev.hyperos.notificationcount"
        minSdk = 37
        targetSdk = 37
        versionCode = 4
        versionName = "0.1.3"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    // AGP 9 generates unit tests only for the selected tested build type.
    testBuildType = "release"
    val cloudKeystore = providers.environmentVariable("MODULE_KEYSTORE_PATH").orNull
    if (cloudKeystore != null) {
        signingConfigs {
            create("cloud") {
                storeFile = file(cloudKeystore)
                storePassword = providers.environmentVariable("MODULE_KEYSTORE_PASSWORD").get()
                keyAlias = "notification-count"
                keyPassword = storePassword
            }
        }
    }
    buildTypes {
        debug {
            if (cloudKeystore != null) signingConfig = signingConfigs.getByName("cloud")
        }
        release {
            isMinifyEnabled = false
            if (cloudKeystore != null) signingConfig = signingConfigs.getByName("cloud")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.github.libxposed:api:102.0.0")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.3.0")
}

tasks.withType<Test>().configureEach {
    // Android 17's shared-memory setup uses FileDescriptor internals in Robolectric's JVM bridge.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED")
}
