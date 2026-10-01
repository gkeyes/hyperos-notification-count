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
        versionCode = 5
        versionName = "0.1.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testProguardFiles("test-proguard-rules.pro")
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (cloudKeystore != null) signingConfig = signingConfigs.getByName("cloud")
        }
    }
    packaging {
        // Compress the installable APK's DEX; Android extracts it during installation.
        dex { useLegacyPackaging = true }
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
    // 3.7 uses getSystemService; older transitive Espresso reflects a removed SDK 37 method.
    testImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    // AndroidX Test refers to these compiler annotations without packaging them.
    androidTestImplementation("com.google.errorprone:error_prone_annotations:2.15.0")
}

tasks.withType<Test>().configureEach {
    // Android 17's shared-memory setup uses FileDescriptor internals in Robolectric's JVM bridge.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED")
}
