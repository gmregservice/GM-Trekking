// Da AGP 9.0 il supporto Kotlin è integrato (built-in): niente più
// "org.jetbrains.kotlin.android" qui, altrimenti la build fallisce.
// I plugin Compose e serialization restano necessari.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.gmtrekking.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gmtrekking.app"
        minSdk = 26
        targetSdk = 37
        // Tenere aggiornati ad ogni modifica rilasciata (vedi docs/PIANO_SVILUPPO.md,
        // sezione "Note operative"). versionCode: intero progressivo, +1 ad ogni
        // release (usato da Android per capire se una APK è più recente di un'altra).
        // versionName: numero visibile all'utente (vedi anche BuildConfig.VERSION_NAME,
        // da mostrare nell'app — pianificato, non ancora implementato in UI).
        versionCode = 28
        versionName = "1.28"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Keystore di debug FISSA, committata in keystore/debug.keystore.
        // Necessaria perché GitHub Actions esegue ogni build su una macchina
        // nuova ed effimera: senza questa keystore esplicita, Gradle ne
        // genera una casuale e diversa ad ogni run. Con una firma diversa,
        // Android rifiuta di installare la nuova APK sopra quella già
        // presente sul telefono (bug reale riscontrato: dopo aver installato
        // una build, quelle successive sembravano "non installarsi" e
        // l'app restava quella vecchia, senza un errore chiaro a schermo).
        // Usando sempre questa stessa keystore, la firma resta identica tra
        // una build e l'altra e l'installazione aggiorna l'app normalmente.
        // NOTA: il plugin Android crea già da solo una signingConfig "debug"
        // di default (con la keystore casuale ~/.android/debug.keystore) —
        // create("debug") fallisce la build ("SigningConfig ... already
        // exists", errore reale riscontrato in CI). Va usato getByName per
        // modificare quella già esistente, non crearne una nuova.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // da abilitare (con proguard-rules.pro) prima della pubblicazione
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Necessario per leggere BuildConfig.VERSION_NAME dal codice (dalla versione 8
        // di AGP in poi la generazione di BuildConfig è disattivata di default).
        // Usato per mostrare il numero di versione nell'app (vedi Note operative).
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// DSL moderna per fissare il JDK di compilazione Kotlin (sostituisce il
// vecchio blocco android.kotlinOptions.jvmTarget, deprecato nelle versioni
// recenti del plugin Kotlin).
kotlin {
    jvmToolchain(17)
    compilerOptions {
        // TopAppBar (Material3) è contrassegnata @ExperimentalMaterial3Api: senza
        // questo opt-in a livello di modulo, ogni schermata che la usa fallisce la
        // compilazione (errore reale riscontrato in CI). L'opt-in qui evita di dover
        // annotare @OptIn(ExperimentalMaterial3Api::class) su ogni singola funzione.
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    // --- Jetpack Compose (versioni allineate dal BOM) ---
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")

    // --- Localizzazione ---
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // --- Mappe: MapLibre (open source, nessun costo per volume) ---
    implementation("org.maplibre.gl:android-sdk:13.4.1")
    // Classi GeoJSON (Point, LineString, Feature) usate da TrekMapView: libreria
    // separata dal core SDK, versionata a parte.
    implementation("org.maplibre.gl:android-sdk-geojson:6.0.1")

    // --- Rete: Overpass API (luoghi utili) ---
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // NOTA: il parsing dei file GPX (data/gpx/GpxParser.kt) usa XmlPullParser,
    // incluso nella piattaforma Android: nessuna libreria XML esterna necessaria.

    // NOTA: la cache locale offline dei luoghi utili (Room) è pianificata per la Fase 2
    // del piano di sviluppo (docs/PIANO_SVILUPPO.md) e non è ancora cablata in questo
    // scheletro, per tenere al minimo le dipendenze del primo build.

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
