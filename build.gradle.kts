// Versioni verificate tramite ricerca ad agosto 2026.
// Se Android Studio propone un aggiornamento (Upgrade Assistant) alla prima apertura
// del progetto, è normale e sicuro accettarlo.
//
// NOTA: dalla versione 9.0, AGP include il supporto Kotlin "built-in" ed il
// plugin "org.jetbrains.kotlin.android" non va più applicato (causa un errore
// di build se presente). I plugin per Compose e per la serializzazione restano
// necessari: non sono sostituiti dal supporto Kotlin integrato in AGP.
// https://developer.android.com/build/migrate-to-built-in-kotlin
plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
}
