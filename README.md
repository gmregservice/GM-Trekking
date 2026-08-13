# GM-Trekking

App Android per pianificare percorsi di trekking da tracciati GPX, seguirli con navigazione GPS in tempo reale, e trovare luoghi utili (ristoranti, bar, hotel, ostelli, rifugi) lungo il percorso.

Piano di sviluppo completo: [`docs/PIANO_SVILUPPO.md`](docs/PIANO_SVILUPPO.md).

## Stato del progetto

Scheletro iniziale: struttura Gradle, schermate Compose (Home, import GPX, navigazione, luoghi utili), motore di navigazione, integrazione mappe (MapLibre) e dati luoghi utili (OpenStreetMap / Overpass API). Non ancora testato su dispositivo reale — vedi "Cosa verificare per primo" più sotto.

## Come funziona la build automatica (GitHub Actions)

Ad ogni push sul branch `main` (e ad ogni pull request), il workflow [`.github/workflows/android-build.yml`](.github/workflows/android-build.yml) compila automaticamente un APK di debug, installabile subito su un telefono Android — non serve Android Studio per ottenerlo.

Per scaricarlo:

1. Vai alla scheda **Actions** del repository su GitHub.
2. Apri la run più recente di "Build APK" (quella legata al tuo ultimo push).
3. In fondo alla pagina della run, sotto **Artifacts**, scarica `gm-trekking-debug-apk`.
4. Estrai lo zip: dentro trovi il file `.apk`, già firmato con la chiave di debug automatica — puoi installarlo direttamente sul telefono (serve abilitare "Origini sconosciute"/"Installa app sconosciute" nelle impostazioni Android).

Per generare anche una APK di release (non firmata, non installabile direttamente — serve prima firmarla): vai su **Actions → Build APK → Run workflow**, spunta "Compila anche una APK di release".

## Aprire il progetto in Android Studio

1. Apri Android Studio → **Open** → seleziona la cartella `GM-Trekking`.
2. Alla prima apertura, Android Studio troverà `gradle/wrapper/gradle-wrapper.properties` ma non il file `gradle-wrapper.jar` (non incluso in questo scheletro, è un binario — vedi nota in `gradlew`). In genere Android Studio lo rigenera da solo al primo sync; se non lo fa, apri un terminale nella cartella del progetto ed esegui:
   ```
   gradle wrapper --gradle-version 9.7.0
   ```
   (richiede Gradle già installato una volta in locale, anche solo per questo comando).
3. Lascia che Android Studio scarichi le dipendenze e sincronizzi il progetto. Se propone un "Upgrade Assistant" per Kotlin/AGP/Compose, è normale: le versioni in questo scheletro sono state verificate ad agosto 2026, ma è comunque sicuro accettare gli aggiornamenti proposti dall'IDE.
4. Esegui l'app su un emulatore o un dispositivo reale con il pulsante ▶️ Run.

## Cosa verificare per primo (aree meno testate)

Questo scheletro è stato scritto senza poter compilare in un ambiente con SDK Android e Gradle disponibili — è corretto dal punto di vista della struttura e della logica, ma la prima apertura in Android Studio potrebbe richiedere piccoli aggiustamenti. In ordine di probabilità:

1. **Integrazione mappa (`ui/screens/trailnavigation/TrekMapView.kt`)**: usa lo stile dimostrativo pubblico di MapLibre (`demotiles.maplibre.org`), pensato solo per test e con pochissimo dettaglio cartografico. Prima di qualunque uso reale va sostituito con uno stile mappa vero (OpenFreeMap, MapTiler, Stadia Maps, o un servizio self-hosted).
2. **Versioni delle dipendenze** in `app/build.gradle.kts`: verificate tramite ricerca ad agosto 2026, ma può darsi che Android Studio proponga versioni più recenti alla prima apertura — va bene accettarle.
3. **Permessi di localizzazione in background**: il flusso attuale richiede solo il permesso di posizione in primo piano prima di avviare la navigazione. Il permesso "sempre" (`ACCESS_BACKGROUND_LOCATION`, dichiarato nel manifest) va richiesto separatamente con un flusso dedicato — non ancora implementato nella UI — prima che il tracciamento continui in modo affidabile a schermo spento.
4. **Nessun test automatico ancora scritto** (le dipendenze di test sono già in `app/build.gradle.kts`, pronte per quando si aggiungeranno).

### Problemi già incontrati e risolti in CI

- **"The 'org.jetbrains.kotlin.android' plugin is no longer required" (build fallita su GitHub Actions)**: con AGP 9.0+ il supporto Kotlin è integrato nel plugin Android stesso; applicare anche il vecchio plugin `org.jetbrains.kotlin.android` fa fallire la build. Risolto rimuovendolo da `build.gradle.kts` (root) e `app/build.gradle.kts`, lasciando solo i plugin `org.jetbrains.kotlin.plugin.compose` e `org.jetbrains.kotlin.plugin.serialization`, che restano necessari. Dettagli: [guida ufficiale alla migrazione](https://developer.android.com/build/migrate-to-built-in-kotlin).

## Struttura del progetto

```
app/src/main/java/com/gmtrekking/app/
├── MainActivity.kt, GMTrekkingApp.kt
├── ui/
│   ├── theme/            Tema grafico (bianco, vedi nota sotto)
│   ├── navigation/        Grafo di navigazione tra le schermate (Compose Navigation)
│   └── screens/
│       ├── home/          Schermata iniziale
│       ├── importtrail/    Import file GPX
│       ├── trailnavigation/ Navigazione GPS: freccia direzionale, mappa, zoom automatico
│       └── places/         Luoghi utili con filtro per categoria
├── data/
│   ├── gpx/               Modello e parser dei file GPX
│   ├── navigation/         Motore di navigazione (calcolo fuori-percorso, direzione)
│   └── poi/                Modelli e client Overpass API per i luoghi utili
└── location/               Servizio GPS in foreground + gestione permessi
```

## Nota sul tema grafico

Su richiesta esplicita, l'app usa sempre un tema bianco (non segue la modalità scura di sistema) — vedi `ui/theme/Theme.kt` e `ui/theme/Color.kt`.

## Prossimi passi

Vedi [`docs/PIANO_SVILUPPO.md`](docs/PIANO_SVILUPPO.md) per fasi, milestone e rischi da monitorare.
