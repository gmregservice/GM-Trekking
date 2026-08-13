# GM-Trekking — Piano di sviluppo

App Android per pianificare percorsi di trekking a partire da tracciati GPX, navigare in tempo reale con il GPS e trovare luoghi utili (ristoranti, bar, hotel, ostelli, rifugi) lungo il percorso.

Riferimento: analisi di fattibilità e ricerca di mercato (documento separato, condiviso in chat). Questo piano la traduce in fasi di sviluppo concrete.

## Principio guida: sicurezza e chiarezza per utenti non esperti

Questo è il criterio prioritario di progettazione dell'intera app, non una rifinitura da aggiungere dopo. Ogni schermata di navigazione deve rispettare:

- **Indicazione di direzione grande e immediata**: freccia ben visibile verso il prossimo punto del tracciato, distanza residua in metri, nessun gergo tecnico (niente "bearing", "waypoint", "azimut" nell'interfaccia).
- **Zoom automatico nei punti critici**: la mappa si ingrandisce da sola in prossimità di bivi, incroci di sentieri o tratti con tracciati ravvicinati — lo stesso pattern dei navigatori auto agli incroci.
- **Segnalazione "fuori percorso" azionabile**: non un semplice allarme, ma un'istruzione chiara su come tornare sul tracciato.
- **Vista d'insieme vs vista di dettaglio**: overview del percorso nei tratti lineari, passaggio automatico alla vista ravvicinata nei tratti complessi.
- **Interfaccia essenziale**: pochi elementi a schermo, icone semplici, testo minimo.

Questi punti sono criteri di accettazione della fase MVP, non funzionalità opzionali.

## Fase 0 — Setup

- [x] Repository GitHub creato (`gmregservice/GM-Trekking`)
- [x] Scheletro progetto Android Kotlin + Jetpack Compose (mappa principale con posizione corrente e navigazione, Luoghi utili; motore di navigazione; integrazione MapLibre; client Overpass API; servizio di localizzazione in foreground)
- [x] Pipeline GitHub Actions per build automatica dell'APK di debug (e, su richiesta, di release non firmata)
- [x] Tema grafico bianco applicato a tutte le schermate
- [x] Primo push e primo build verde su GitHub Actions
- [x] APK installata e avviata su dispositivo reale (workflow di sviluppo: solo GitHub Actions, senza Android Studio)
- [x] Caricamento GPX reso opzionale: l'app si apre sulla mappa con la posizione corrente, non forza più il caricamento di un percorso prima di mostrare qualcosa
- [x] Schermata di crash in-app (mostra l'errore a schermo, con opzione "Condividi"): necessaria perché senza Android Studio non c'è modo di leggere Logcat
- [x] Firma APK di debug stabilizzata (`keystore/debug.keystore` fissa): prima ogni build GitHub Actions firmava l'APK con una chiave diversa e le build successive alla prima non si installavano più sopra quella vecchia

## Fase 1 — MVP (obiettivo: 4-6 mesi, team 1-2 sviluppatori)

Funzionalità minime per un prodotto usabile e sicuro:

1. **Import GPX (opzionale)**: caricamento di un file `.gpx` dal dispositivo, parsing e visualizzazione del tracciato su mappa offline. L'app si apre comunque mostrando la posizione corrente sulla mappa anche senza un percorso caricato: caricarne uno è un'azione che l'utente sceglie di fare, non un passaggio obbligato.
2. **Navigazione GPS sul tracciato**: posizione utente in tempo reale sovrapposta al tracciato, calcolo continuo della distanza dal percorso, avviso chiaro di deviazione, freccia direzionale e zoom automatico nei punti critici (vedi principio guida sopra).
3. **Mappe offline**: download dell'area del percorso caricato prima di partire, per garantire funzionamento senza copertura cellulare.
4. **Luoghi utili**: elenco di ristoranti, bar, trattorie, hotel, ostelli, rifugi nei dintorni del tracciato o della posizione corrente, con filtro per categoria (tutti / ristoranti / bar / trattorie / hotel / ostelli / rifugi), dati da OpenStreetMap (Overpass API).
5. **Gestione batteria**: indicatore di consumo stimato, modalità a basso consumo (riduzione frequenza GPS quando il movimento è lineare).

Criterio di uscita dalla fase: un utente non esperto riesce a caricare un percorso, seguirlo dall'inizio alla fine restando orientato nei punti critici, e trovare un posto dove mangiare o dormire lungo la strada, tutto offline.

## Fase 2 — Consolidamento (2-3 mesi dopo l'MVP)

- Cronologia dei percorsi seguiti, con statistiche (distanza, dislivello, tempo).
- Profilo altimetrico del tracciato.
- Guida vocale opzionale per i cambi di direzione.
- Cache locale dei luoghi utili (per rendere disponibile l'elenco POI anche offline dopo il primo download di un'area).
- Ricerca luoghi utili per raggio dalla posizione corrente, oltre che lungo il tracciato.

## Fase 3 — Community e arricchimento dati (3-4 mesi dopo la Fase 2)

- Condivisione di percorsi tra utenti.
- Segnalazioni della community su condizioni del sentiero o correzioni ai luoghi utili.
- Sincronizzazione multi-dispositivo.
- Eventuale integrazione di fonti dati POI aggiuntive (es. Google Places) per arricchire orari, recensioni, foto.
- Valutazione di un modello di monetizzazione (freemium), solo dopo aver validato l'adozione.

## Architettura tecnica

- **Piattaforma**: Android nativo, Kotlin + Jetpack Compose (scelto invece di un framework cross-platform perché il target è solo Android e la gestione fine di GPS in background/consumo batteria richiede accesso diretto alle API di sistema).
- **Mappe**: MapLibre Android SDK, tile OpenStreetMap — nessun costo di licenza legato al volume di utilizzo.
- **Dati luoghi utili**: OpenStreetMap tramite Overpass API (gratuita, nessuna chiave richiesta).
- **Localizzazione in background**: Foreground Service Android con tipo `location` dichiarato (obbligatorio da Android 14 in poi).
- **Persistenza locale**: da introdurre in Fase 2 (Room) per la cache offline dei luoghi utili; nello scheletro attuale i dati POI sono tenuti in memoria per semplicità.
- **Compilazione**: GitHub Actions genera l'APK ad ogni push sul branch `main` (vedi `.github/workflows/android-build.yml`); l'APK compilato è scaricabile dagli Artifacts della run, senza dover installare Android Studio per ottenere un file installabile.

## Note operative (indicazioni dirette dell'utente, da rispettare in ogni sessione)

- **Tema grafico**: tema bianco per le schermate dell'app. ✅ Fatto — vedi `ui/theme/Theme.kt` e `Color.kt`.
- **Coerenza ad ogni modifica**: ad ogni modifica ai file del progetto, verificare che tutto resti coerente e che non manchi nulla (riferimenti tra file, dipendenze usate ma non dichiarate o viceversa, versioni allineate) prima di considerare il passo concluso.
- **Numero di versione**: la versione attuale del progetto è la **1.4** (`versionName` in `app/build.gradle.kts`; aggiornata con il fix della firma APK di debug instabile in CI). Ad ogni modifica rilasciata, aggiornare `versionCode` (+1) e `versionName`. Il numero deve anche essere mostrato nell'interfaccia dell'app (non ancora fatto): `buildFeatures.buildConfig = true` è già abilitato nel Gradle, quindi basterà leggere `BuildConfig.VERSION_NAME` — punto d'ingresso naturale: un piccolo testo nella barra in alto di `MainMapScreen.kt`, o una futura schermata "Informazioni".
- **Workflow di sviluppo**: l'utente non usa Android Studio, solo GitHub (push → GitHub Actions compila l'APK → installazione diretta sul telefono). Le richieste di log/diagnostica vanno quindi pensate senza presumere accesso a Logcat/adb — da qui la schermata di crash in-app (`crash/CrashHandler.kt`).

## Rischi da monitorare (ripresi dall'analisi di fattibilità)

- Qualità/copertura disomogenea dei dati OpenStreetMap in alcune aree montane: mitigare permettendo segnalazioni dagli utenti in Fase 3.
- Consumo batteria durante tracciamento lungo: testare su più dispositivi reali, non solo emulatore.
- Precisione GPS in bosco/valli strette: la soglia di tolleranza per l'avviso di fuori percorso deve essere calibrabile e testata sul campo, non solo a tavolino.
- Versioni delle dipendenze (Kotlin, Compose, MapLibre) verificate ad agosto 2026: se Android Studio propone aggiornamenti (Upgrade Assistant), è normale e sicuro accettarli.
