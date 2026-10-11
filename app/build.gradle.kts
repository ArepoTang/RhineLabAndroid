plugins {
    id("com.android.application")
}

android {
    namespace = "cc.lubeiluchen.rhinelab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rhine.lab"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    // 签名材料不进仓库：CI 从 GitHub Secrets 里解码出 .p12 再喂给 Gradle，
    // 本地没有这些环境变量时就出未签名包（不会拿错钥匙签出个装不上的东西）。
    val keystorePath = System.getenv("SIGNING_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("app") {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                // AGP disables v1 for minSdk >= 24, but Huawei's installer rejects
                // v2-only APKs with "解析包时出现问题". Harmless to keep v1 on.
                enableV1Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("app")
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
