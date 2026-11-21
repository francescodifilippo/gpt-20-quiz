plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// A persistent, private keystore is injected only on trusted CI runs.
// Never put the signing key or passwords into the public repository.
val betaKeystorePath = System.getenv("GPT20_SIGNING_STORE_FILE")
val betaStorePassword = System.getenv("GPT20_SIGNING_STORE_PASSWORD")
val betaKeyAlias = System.getenv("GPT20_SIGNING_KEY_ALIAS")
val betaKeyPassword = System.getenv("GPT20_SIGNING_KEY_PASSWORD")
val stableBetaSigning = listOf(
    betaKeystorePath, betaStorePassword, betaKeyAlias, betaKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.francescodifilippo.gpt20quiz"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    signingConfigs {
        if (stableBetaSigning) {
            create("stableBeta") {
                storeFile = file(betaKeystorePath!!)
                storePassword = betaStorePassword!!
                keyAlias = betaKeyAlias!!
                keyPassword = betaKeyPassword!!
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (stableBetaSigning) {
                signingConfig = signingConfigs.getByName("stableBeta")
            }
        }
    }

    defaultConfig {
        applicationId = "com.francescodifilippo.gpt20quiz"
        minSdk = 26
        targetSdk = 36
        // CI run numbers increase monotonically within the workflow.
        // Local developer builds use the published base version code.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
            ?.takeIf { it > 0 && it < 1_000_000 }
            ?.let { 10000 + it }) ?: 5
        versionName = "0.2.7-early-guesses-effort"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.browser:browser:1.10.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
