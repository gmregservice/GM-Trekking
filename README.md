# GM-Trekking

App Android per pianificare percorsi di trekking da tracciati GPX, seguirli con navigazione GPS in tempo reale, e trovare luoghi utili (ristoranti, bar, hotel, ostelli, rifugi) lungo il percorso.

Piano di sviluppo completo: [`docs/PIANO_SVILUPPO.md`](docs/PIANO_SVILUPPO.md).

## Stato del progetto

Scheletro iniziale: struttura Gradle, schermate Compose (mappa principale con posizione corrente e navigazione, luoghi utili), motore di navigazione, integrazione mappe (MapLibre) e dati luoghi utili (OpenStreetMap / Overpass API). L'app è stata installata e avviata su un dispositivo reale; alcuni problemi emersi in quel test sono già stati corretti (vedi sotto).

L'app si apre direttamente sulla mappa con la posizione corrente: caricare un percorso GPX è un'azione opzionale, non un passaggio obbligato. Una volta caricato un percorso, la stessa schermata mostra anche la navigazione (freccia direzionale, distanza, avviso di fuori percorso).

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

1. **Versioni delle dipendenze** in `app/build.gradle.kts`: verificate tramite ricerca ad agosto 2026, ma può darsi che vengano proposte versioni più recenti in futuro — va bene accettarle.
2. **Permessi di localizzazione in background**: il flusso attuale richiede solo il permesso di posizione in primo piano prima di avviare la navigazione. Il permesso "sempre" (`ACCESS_BACKGROUND_LOCATION`, dichiarato nel manifest) va richiesto separatamente con un flusso dedicato — non ancora implementato nella UI — prima che il tracciamento continui in modo affidabile a schermo spento.
3. **Nessun test automatico ancora scritto** (le dipendenze di test sono già in `app/build.gradle.kts`, pronte per quando si aggiungeranno).

### Problemi già incontrati e risolti in CI

- **"The 'org.jetbrains.kotlin.android' plugin is no longer required" (build fallita su GitHub Actions)**: con AGP 9.0+ il supporto Kotlin è integrato nel plugin Android stesso; applicare anche il vecchio plugin `org.jetbrains.kotlin.android` fa fallire la build. Risolto rimuovendolo da `build.gradle.kts` (root) e `app/build.gradle.kts`, lasciando solo i plugin `org.jetbrains.kotlin.plugin.compose` e `org.jetbrains.kotlin.plugin.serialization`, che restano necessari. Dettagli: [guida ufficiale alla migrazione](https://developer.android.com/build/migrate-to-built-in-kotlin).
- **"This material API is experimental" su `TopAppBar` (errore di compilazione Kotlin)**: `TopAppBar` di Material3 è contrassegnata `@ExperimentalMaterial3Api` e senza un opt-in esplicito il compilatore la tratta come errore, non solo come avviso. Risolto aggiungendo l'opt-in a livello di modulo in `app/build.gradle.kts` (`kotlin { compilerOptions { freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api") } }`), invece di annotare ogni singola schermata.
- **"Cannot access 'val RowColumnParentData?.weight: Float': it is internal in file"**: causato da un import esplicito sbagliato, `import androidx.compose.foundation.layout.weight`. Il modificatore `weight()` non è una funzione top-level importabile per nome: è un membro delle interfacce `ColumnScope`/`RowScope` di Compose, disponibile automaticamente dentro un blocco `Column { }` / `Row { }` senza bisogno di import. Quell'import esplicito faceva risolvere il simbolo sbagliato (una proprietà interna di Compose con lo stesso nome). Risolto rimuovendo l'import da `PlacesScreen.kt` e `TrailNavigationScreen.kt`.

### Problemi riscontrati su dispositivo reale (dopo l'installazione)

- **L'app si chiude (crash) al caricamento di un tracciato GPX**: causato dall'assenza dell'inizializzazione di MapLibre. La libreria richiede una chiamata a `MapLibre.getInstance(context)` prima che qualunque `MapView` venga creata — mancava del tutto. Poiché caricare un GPX portava subito alla schermata di navigazione (che crea la mappa), il crash comparirebbe in quel momento. Risolto aggiungendo l'inizializzazione in `GMTrekkingApp.kt` (`onCreate`), l'unico punto garantito ad essere eseguito prima di qualsiasi schermata.
- **Fuori casa la mappa resta vuota (solo sfondo colorato, nessuna via/terreno, posizione poco visibile)**: causato dallo stile dimostrativo di MapLibre (`demotiles.maplibre.org`), che contiene solo confini nazionali a bassissimo dettaglio — ai livelli di zoom usati dall'app (15+) non c'era alcun dato reale da disegnare in nessuna zona del mondo, solo il colore di sfondo dello stile. Non era emerso nei test precedenti perché fatti al chiuso, senza una posizione GPS reale da inquadrare. Risolto sostituendo lo stile con quello di [OpenFreeMap](https://openfreemap.org/) (`liberty`, gratuito, senza chiave API, dati OpenStreetMap con vero dettaglio cartografico) in `TrekMapView.kt`.
- **Luoghi utili: "Dettaglio tecnico: HttpException: HTTP 406 Not Acceptable"**: grazie al dettaglio tecnico aggiunto in v1.6 (vedi sotto), individuato con certezza invece che per ipotesi. Non è un problema di connessione né di query: l'endpoint pubblico principale di Overpass API (`overpass-api.de`) ha iniziato a filtrare in modo aggressivo il traffico che sembra automatizzato (misura anti-scraping, soprattutto contro bot che addestrano modelli AI), rifiutando con HTTP 406 le richieste con lo User-Agent generico di default di OkHttp e senza un header `Accept` esplicito — capita anche a client legittimi come questo, non solo a scraper veri (problema noto e attuale, confermato da segnalazioni di altri sviluppatori nell'aprile-agosto 2026: [drolbr/Overpass-API#791](https://github.com/drolbr/Overpass-API/issues/791)). Risolto in `OverpassApiService.kt`: passaggio al mirror pubblico `overpass.kumi.systems` (stesso protocollo, gratuito, nessuna chiave, non applica gli stessi filtri) e aggiunta di uno User-Agent descrittivo e di un header `Accept` espliciti su ogni richiesta.
- **Prima ipotesi scartata — non era un problema di segnale/copertura**: inizialmente si era pensato a una connessione debole sul posto (mappa e luoghi utili sembravano falliti insieme), ma la mappa si è poi rivelata funzionante correttamente una volta installata la versione con lo stile OpenFreeMap: il problema residuo era solo ed esclusivamente l'HTTP 406 di Overpass API descritto sopra.
- **Luoghi utili, dopo il fix dell'HTTP 406: "Dettaglio tecnico: SocketTimeoutException: timeout"**: la query Overpass dichiara essa stessa `[timeout:25]` (25 secondi) lato server (vedi `OverpassQueryBuilder`), ma il client Retrofit/OkHttp usava i timeout di default (10 secondi) — quindi la richiesta veniva abbandonata dal telefono ben prima che il server avesse anche solo il tempo di elaborarla, sempre e comunque, indipendentemente dalla qualità della connessione. Risolto in `OverpassApiService.kt` configurando un `OkHttpClient` con timeout più ampi (35s in lettura, oltre il margine dei 25s dichiarati nella query).
- **Luoghi utili, dopo i due fix precedenti: "Dettaglio tecnico: HttpException: HTTP 504 Gateway Timeout"**: stavolta la richiesta arriva al server (non più un timeout lato client), ma il server stesso impiega troppo tempo a rispondere. Causa: la query per la categoria "Tutti" ripeteva il filtro spaziale `around` (la parte più costosa, una ricerca per raggio) una volta per ogni singola coppia chiave/valore OSM — fino a 16 filtri `around` separati in un'unica query. Risolto in `OverpassQueryBuilder.kt` raggruppando i valori per chiave OSM (es. tutti i `tourism=...` insieme) in un solo filtro con espressione regolare, riducendo a 4 i filtri `around` per la query più pesante (categoria "Tutti"): stesso risultato, query più leggera per il server.

### Modifiche al flusso dell'app

- **Caricamento GPX reso opzionale**: inizialmente l'app obbligava a caricare un percorso GPX prima di mostrare qualunque cosa (schermata Home → Import GPX → Navigazione). Su richiesta, il flusso è stato cambiato: l'app si apre direttamente su `MainMapScreen` (`ui/screens/trailnavigation/MainMapScreen.kt`), che mostra subito la posizione corrente sulla mappa e offre il caricamento di un GPX come azione facoltativa, disponibile in ogni momento (pulsante "Carica un percorso GPX", sostituito da "Cambia percorso"/"Rimuovi percorso" quando un tracciato è già caricato). Le vecchie schermate `HomeScreen` e `ImportTrailScreen` sono state rimosse (la logica di scelta/parsing del file GPX è ora inline in `MainMapScreen.kt`); `TrailNavigationScreen` è stata sostituita da `MainMapScreen`, che gestisce sia lo stato "solo posizione" sia lo stato "percorso caricato, navigazione attiva".

### Schermata di crash in-app

Dato che l'app viene compilata e distribuita solo tramite GitHub Actions, senza Android Studio, non c'è modo di leggere Logcat in caso di crash. È stato aggiunto `crash/CrashHandler.kt`, installato in `GMTrekkingApp.onCreate`: se l'app va in crash, invece del messaggio generico di sistema si apre una schermata (`crash/CrashReportActivity.kt`, in un processo separato per restare affidabile anche se il processo principale è compromesso) che mostra il testo completo dell'errore, con un pulsante "Condividi" per mandarmelo direttamente. Se l'app si chiude di nuovo in modo anomalo, questa schermata dovrebbe comparire al posto del crash silenzioso — se anche questo non succede, è un segnale che vale la pena approfondire a parte.

### Firma APK di debug instabile tra una build e l'altra (bug reale riscontrato)

Dopo aver installato una prima APK compilata da GitHub Actions, le build successive sembravano "non installarsi": l'app apriva sempre la vecchia versione, senza errori visibili. Causa: GitHub Actions esegue ogni build su una macchina virtuale nuova ed effimera; senza una keystore di debug esplicita, Gradle ne genera una casuale ad ogni run, quindi ogni APK aveva una firma diversa. Android rifiuta di installare un'APK con firma diversa sopra un'app già presente con lo stesso nome pacchetto — a seconda del telefono, questo può dare un errore poco visibile o venire ignorato, lasciando l'app vecchia al suo posto senza che sia ovvio perché.

Risolto committando una keystore di debug fissa (`keystore/debug.keystore`, non sensibile: firma solo build di debug, non pubblicabili su Play Store) e collegandola esplicitamente in `app/build.gradle.kts` (`signingConfigs.debug`). Da questa build in poi (versione 1.3), tutte le APK di debug avranno sempre la stessa firma e si aggiorneranno normalmente una sopra l'altra.

**Importante, solo questa volta**: la nuova APK (1.3) ha una firma diversa da qualunque versione precedente installata sul telefono (che usava una delle firme casuali generate prima del fix). Per installarla va prima disinstallata manualmente l'app attuale (tieni premuto sull'icona → Disinstalla), poi installata la nuova APK da zero. Dalle prossime build in poi non servirà più: basterà installare la nuova APK sopra quella vecchia, come un aggiornamento normale.

## Struttura del progetto

```
keystore/debug.keystore   Firma di debug fissa per le build CI (vedi sopra)
app/src/main/java/com/gmtrekking/app/
├── MainActivity.kt, GMTrekkingApp.kt
├── crash/                 Gestore di crash: mostra l'errore a schermo invece di chiudersi silenziosamente
├── ui/
│   ├── theme/            Tema grafico (bianco, vedi nota sotto)
│   ├── navigation/        Grafo di navigazione tra le schermate (Compose Navigation)
│   └── screens/
│       ├── trailnavigation/ Schermata principale: mappa con posizione corrente,
│       │                    import GPX opzionale, navigazione (freccia, zoom automatico)
│       └── places/         Luoghi utili con filtro per categoria
├── data/
│   ├── gpx/               Modello, parser dei file GPX e stato del percorso caricato
│   ├── navigation/         Motore di navigazione (calcolo fuori-percorso, direzione)
│   └── poi/                Modelli e client Overpass API per i luoghi utili
└── location/               Servizio GPS in foreground + gestione permessi
```

## Nota sul tema grafico

Su richiesta esplicita, l'app usa sempre un tema bianco (non segue la modalità scura di sistema) — vedi `ui/theme/Theme.kt` e `ui/theme/Color.kt`.

## Prossimi passi

Vedi [`docs/PIANO_SVILUPPO.md`](docs/PIANO_SVILUPPO.md) per fasi, milestone e rischi da monitorare.
