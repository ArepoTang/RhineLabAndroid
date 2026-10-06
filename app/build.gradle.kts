plugins {
    id("com.android.application")
}

android {
    namespace = "cc.lubeiluchen.rhinelab"
    compileSdk = 36

    defaultConfig {
        applicationId = "cc.lubeiluchen.rhinelab"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // A committed keystore so consecutive CI runs can upgrade over each other
    // instead of requiring an uninstall. Personal app, not for distribution.
    signingConfigs {
        create("app") {
            storeFile = rootProject.file("keystore/rhinelab.jks")
            storePassword = "rhinelab"
            keyAlias = "rhinelab"
            keyPassword = "rhinelab"
            // AGP disables v1 for minSdk >= 24, but Huawei's installer rejects
            // v2-only APKs with "解析包时出现问题". Harmless to keep v1 on.
            enableV1Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("app")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // Runtime PNGs stay untouched; only build-time duplicates are excluded.
        resources.excludes += setOf("META-INF/*.version", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.17.1")
}
