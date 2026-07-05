
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
val releaseStoreFile = keystoreProperties["storeFile"]
    ?.toString()
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }
val hasReleaseSigning = releaseStoreFile?.exists() == true &&
    listOf("storePassword", "keyAlias", "keyPassword")
        .all { key -> !keystoreProperties[key]?.toString().isNullOrBlank() }

android {
    namespace = "info.meuse24.pdf_scanner"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "info.meuse24.pdf_scanner"
        minSdk = 29
        targetSdk = 36
        versionCode = 13
        versionName = "2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = checkNotNull(releaseStoreFile)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "FULL"
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    androidResources {
        // AndroidX/ML-Kit/Play bringen Übersetzungen für 85+ Locales mit. Play liefert per
        // Language-Split ohnehin nur die Gerätesprache aus; dieser Filter entfernt die nicht
        // unterstützten Locales auch aus Universal-/Debug-APKs (~1-2 MB).
        localeFilters += listOf("en", "de", "es", "fr", "pt", "zh-rCN", "ar", "ja", "ru", "hi")
        // TFLite-Modelle (falls von gebündelten Libs mitgeliefert) und die kleinen
        // Noto-Fallback-Fonts bleiben unkomprimiert (mmap). Die 36 MB grosse
        // NotoSansCJKjp-VF.ttf ist bewusst NICHT gelistet: Sie wird nur lazy beim
        // Formular-Speichern via InputStream gelesen, Kompression spart ~16 MB im APK und
        // kostet beim Lesen nur Millisekunden.
        noCompress += listOf(
            "tflite",
            "NotoSans-Regular.ttf",
            "NotoSansArabic-Regular.ttf",
            "NotoSansDevanagari-Regular.ttf"
        )
    }
    packaging {
        // ktor-server-test-host (androidTest only) pulls in Apache HttpComponents 5 as its
        // default test-client engine; those jars duplicate these META-INF files, which only
        // the androidTest APK actually merges (main/unit-test builds never see this).
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                // BouncyCastle Post-Quantum-Krypto (Picnic/SIKE): ~8 MB Konstanten-Properties,
                // die via pdfbox-android → bcprov mitkommen. PdfBox nutzt BouncyCastle nur für
                // klassische PDF-Verschlüsselung (RC4/AES/PKCS), niemals PQC; SIKE ist zudem
                // seit 2022 kryptografisch gebrochen. Reines totes Gewicht.
                "org/bouncycastle/pqc/crypto/picnic/**",
                "org/bouncycastle/pqc/crypto/sike/**"
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.jvmArgs(
                    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                    "-XX:+EnableDynamicAgentLoading"
                )
            }
        }
    }
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.matching { it.name.contains("AndroidTest", ignoreCase = true) }.configureEach {
    resolutionStrategy.force(
        "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
        "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1"
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.biometric)
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tink.android)
    implementation(libs.argon2kt)
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Google ML Kit Document Scanner
    implementation(libs.document.scanner)

    // Google ML Kit Text Recognition – GMS unbundled (Latin model provided by Play Services;
    // preloaded at install time via the com.google.mlkit.vision.DEPENDENCIES manifest entry,
    // saves ~13 MB per device vs. the bundled artifact)
    implementation(libs.mlkit.text.recognition)
    // ML Kit Text Recognition – GMS unbundled (model downloaded on first use)
    implementation(libs.mlkit.text.chinese)
    implementation(libs.mlkit.text.japanese)
    implementation(libs.mlkit.text.devanagari)
    implementation(libs.mlkit.text.korean)
    // Barcode-Scanning – GMS unbundled (libbarhopper via Play Services statt gebündelt,
    // spart ~5,6 MB pro Gerät; Modell wird via ModuleInstall bei Bedarf geladen)
    implementation(libs.mlkit.barcode.scanning)
    // ML Kit Translate – models downloaded on demand, NOT bundled in APK
    implementation(libs.mlkit.translate)

    // PdfBox-Android – searchable PDF generation
    implementation(libs.pdfbox.android)

    // Navigation
    implementation(libs.navigation.compose)

    // Drag & Drop for Reorder screen
    implementation(libs.reorderable)

    // Ktor – embedded local HTTP server for Wi-Fi PC-Sync (LAN only, no client engine needed)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.sessions)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.ktor.server.test.host)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.ktor.server.test.host)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
