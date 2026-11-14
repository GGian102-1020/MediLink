import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Debug usa adb reverse. La variante release se puede DISTRIBUIR sin un servidor
// para la entrega universitaria; funciones online necesitan API luego.
// La firma privada sigue siendo obligatoria (nunca claves en Git).
val releaseApiUrl = providers.environmentVariable("MEDILINK_API_BASE_URL").orNull?.trim().orEmpty()
val releaseStoreFile = providers.environmentVariable("MEDILINK_RELEASE_KEYSTORE_FILE").orNull.orEmpty()
val releaseStorePassword = providers.environmentVariable("MEDILINK_RELEASE_STORE_PASSWORD").orNull.orEmpty()
val releaseKeyAlias = providers.environmentVariable("MEDILINK_RELEASE_KEY_ALIAS").orNull.orEmpty()
val releaseKeyPassword = providers.environmentVariable("MEDILINK_RELEASE_KEY_PASSWORD").orNull.orEmpty()
val hasSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it.isNotBlank() }

fun isPublicHttpsApiBase(raw: String): Boolean = try {
    val uri = URI(raw)
    val host = uri.host?.lowercase().orEmpty()
    uri.scheme.equals("https", ignoreCase = true) &&
        host.contains('.') &&
        !host.matches(Regex("[0-9.]+")) &&
        listOf(".invalid", ".example", ".test", ".localhost", ".local", ".internal").none { host.endsWith(it) } &&
        host != "example.com" && host != "localhost" &&
        uri.userInfo == null && uri.query == null && uri.fragment == null &&
        raw.endsWith('/')
} catch (_: Exception) { false }


android {
    namespace = "ar.edu.uade.medilink.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "ar.edu.uade.medilink.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigning) {
            create("medilinkRelease") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Solo para desarrollo local, conectado por 'adb reverse'.
            buildConfigField("String", "BASE_URL", "\"http://localhost:18081/\"")
        }
        release {
            isMinifyEnabled = false
            // Sin API configurada se compila un APK instalable de demostración;
            // .invalid es un dominio reservado que NO representa un servidor operativo.
            val url = if (isPublicHttpsApiBase(releaseApiUrl)) releaseApiUrl else "https://api.example.invalid/"
            buildConfigField("String", "BASE_URL", "\"${url.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            if (hasSigning) signingConfig = signingConfigs.getByName("medilinkRelease")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// Firma obligatoria también para builds locales. No se exige servidor externo:
// la edición universitaria se puede instalar sin conexión, pero sus operaciones
// online (login, catálogo, reservas) requieren una API disponible.
// Si se configura una API, la URL debe ser HTTPS válida; nunca se hace fallback
// silencioso desde una URL mal escrita hacia el modo sin servidor.
val validateReleaseDistribution = tasks.register("validateReleaseDistribution") {
    doLast {
        check(releaseApiUrl.isBlank() || isPublicHttpsApiBase(releaseApiUrl)) {
            "MEDILINK_API_BASE_URL (si se configura) debe ser HTTPS, valida y terminar en /"
        }
        check(hasSigning && file(releaseStoreFile).isFile) {
            "Faltan MEDILINK_RELEASE_KEYSTORE_FILE / STORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD"
        }
    }
}
tasks.matching { it.name in setOf("preReleaseBuild", "assembleRelease", "bundleRelease") }.configureEach {
    dependsOn(validateReleaseDistribution)
}
