import java.security.KeyStore
import java.security.MessageDigest

plugins {
    id("com.android.application")
}

android {
    namespace = "me.pipi.codexmeter"
    compileSdk = 37

    defaultConfig {
        applicationId = "me.pipi.codexmeter"
        minSdk = 31
        targetSdk = 37
        versionCode = 3
        versionName = "0.2"
        providers.gradleProperty("demoVersionCode").orNull?.toIntOrNull()?.let {
            versionCode = it
        }
        providers.gradleProperty("demoVersionName").orNull?.let {
            versionName = it
        }
        val updateApiUrl = providers.gradleProperty("demoUpdateUrl").orNull
            ?: "https://api.github.com/repos/HotKids/Codex-Meter/releases?per_page=30" // pragma: allowlist secret
        buildConfigField("String", "UPDATE_API_URL",
            "\"${updateApiUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    signingConfigs {
        create("localRelease") {
            val signingDir = rootProject.file(".local-signing")
            val keyStore = signingDir.resolve("codex-meter-local.p12")
            val passwordFile = signingDir.resolve("password")
            if (keyStore.isFile && passwordFile.isFile) {
                val password = passwordFile.readText().trim()
                val signingStore = KeyStore.getInstance("PKCS12").apply {
                    keyStore.inputStream().use { load(it, password.toCharArray()) }
                }
                val certificate = signingStore.getCertificate("codexmeter")
                    ?: throw GradleException("The fixed signing alias codexmeter is missing")
                val actualCertificateSha = MessageDigest.getInstance("SHA-256")
                    .digest(certificate.encoded).joinToString("") { "%02x".format(it) }
                val expectedCertificateSha = rootProject.file("ci/phone-signing-certificate.sha256")
                    .readText().trim()
                if (actualCertificateSha != expectedCertificateSha) {
                    throw GradleException("Phone release signing certificate does not match the fixed identity")
                }
                storeFile = keyStore
                storeType = "PKCS12"
                storePassword = password
                keyAlias = "codexmeter"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("localRelease")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE*")
    }

    lint {
        baseline = file("lint-baseline.xml")
    }
}

// AGP omits validateSigningRelease when the configuration is empty, so guard packaging.
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst {
        val signingDir = rootProject.file(".local-signing")
        if (!signingDir.resolve("codex-meter-local.p12").isFile ||
            !signingDir.resolve("password").isFile) {
            throw GradleException("Fixed signing material is missing; restore android/.local-signing from the signing backup")
        }
    }
}

configurations.configureEach {
    exclude(group = "androidx.core", module = "core")
    exclude(group = "androidx.core", module = "core-ktx")
    exclude(group = "androidx.appcompat", module = "appcompat")
    exclude(group = "androidx.fragment", module = "fragment")
    exclude(group = "androidx.recyclerview", module = "recyclerview")
    exclude(group = "androidx.preference", module = "preference")
    exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
    exclude(group = "androidx.customview", module = "customview")
    exclude(group = "androidx.drawerlayout", module = "drawerlayout")
    exclude(group = "androidx.viewpager", module = "viewpager")
    exclude(group = "androidx.viewpager2", module = "viewpager2")
    exclude(group = "com.google.android.material", module = "material")
}

dependencies {
    implementation(project(":shared"))
    implementation("io.github.tribalfs:oneui-design:0.9.14+oneui8")
    implementation("io.github.oneuiproject:icons:1.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
