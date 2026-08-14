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
- [x] Mappa reale sostituita allo stile dimostrativo: fuori casa la mappa restava vuota (solo colore di sfondo, nessun dettaglio) perché lo stile demo di MapLibre non ha dati oltre i confini nazionali — sostituito con OpenFreeMap (`liberty`)

## Fase 1 — MVP (obiettivo: 4-6 mesi, team 1-2 sviluppatori)

Funzionalità minime per un prodotto usabile e sicuro:

1. **Import GPX (opzionale)**: caricamento di un file `.gpx` dal dispositivo, parsing e visualizzazione del tracciato su mappa offline. L'app si apre comunque mostrando la posizione corrente sulla mappa anche senza un percorso caricato: caricarne uno è un'azione che l'utente sceglie di fare, non un passaggio obbligato.
2. **Navigazione GPS sul tracciato**: posizione utente in tempo reale sovrapposta al tracciato, calcolo continuo della distanza dal percorso, avviso chiaro di deviazione, freccia direzionale e zoom automatico nei punti critici (vedi principio guida sopra). [x] Pulsante "Ricentra" sulla mappa: dopo aver scorso la mappa (es. per vedere cosa c'è più avanti lungo il percorso), riporta la visualizzazione sulla posizione corrente senza doverla cercare manualmente.
3. **Mappe offline**: download dell'area del percorso caricato prima di partire, per garantire funzionamento senza copertura cellulare.
   - **Fattibilità "intera Italia" verificata e scartata (agosto 2026)**: chiesto se scaricare tutta la mappa italiana in locale fosse fattibile o troppo pesante. Distinzione chiave: l'estratto OSM grezzo (Geofabrik, `italy-latest.osm.pbf`) pesa 2,1 GB ma non è utilizzabile direttamente da MapLibre — è il dato sorgente, non le tile. Le tile vettoriali pronte (formato MBTiles) per un paese delle dimensioni dell'Italia, ai livelli di zoom utili in escursione, si stimano nell'ordine di 200-400 MB — spazio trascurabile su un telefono moderno. Il problema non è lo spazio ma il metodo: l'`OfflineManager` di MapLibre è pensato per un bounding box (un'area), non per scaricare un intero paese tile per tile dal server pubblico OpenFreeMap — farlo così rischierebbe migliaia di richieste e un blocco per uso anomalo (stesso tipo di problema anti-bot già visto con Overpass). Un pacchetto "Italia intera" pre-costruito e distribuito da noi sarebbe tecnicamente possibile ma aggiungerebbe un'infrastruttura di generazione/hosting/aggiornamento sproporzionata rispetto al bisogno reale.
   - **Decisione**: si resta sull'idea iniziale — download dell'area del percorso (bounding box con margine) prima di partire, via `OfflineManager` di MapLibre, senza pacchetti nazionali precostituiti.
   - **Non è specifico per l'Italia**: la fonte dati (OpenStreetMap) e lo stile (OpenFreeMap `liberty`) sono globali, non limitati a un paese, e l'`OfflineManager` scarica un'area in base a coordinate (bounding box), non in base a confini nazionali. La stessa funzionalità di download area-per-area funziona quindi ovunque nel mondo ci sia copertura OSM, senza alcuna modifica al codice: un percorso in Francia, Svizzera o altrove si scarica offline con la stessa identica logica di uno in Italia.
4. **Luoghi utili**: elenco di ristoranti, bar, trattorie, hotel, ostelli, rifugi nei dintorni del tracciato o della posizione corrente, con filtro per categoria (tutti / ristoranti / bar / trattorie / hotel / ostelli / rifugi), dati da OpenStreetMap (Overpass API).
5. **Gestione batteria**: indicatore di consumo stimato, modalità a basso consumo (riduzione frequenza GPS quando il movimento è lineare).

Criterio di uscita dalla fase: un utente non esperto riesce a caricare un percorso, seguirlo dall'inizio alla fine restando orientato nei punti critici, e trovare un posto dove mangiare o dormire lungo la strada, tutto offline.

## Fase 2 — Consolidamento (2-3 mesi dopo l'MVP)

- Cronologia dei percorsi seguiti, con statistiche (distanza, dislivello, tempo).
- Profilo altimetrico del tracciato.
- Guida vocale opzionale per i cambi di direzione.
- Cache locale dei luoghi utili (per rendere disponibile l'elenco POI anche offline dopo il primo download di un'area).
- Ricerca luoghi utili per raggio dalla posizione corrente, oltre che lungo il tracciato.

### Richieste utente da sviluppare — dopo aver testato adeguatamente la versione attuale

Annotate su richiesta esplicita (agosto 2026). Da affrontare in quest'ordine indicativo, dopo il test sul campo del flusso base (import GPX opzionale, navigazione, luoghi utili):

1. **Tracciamento del percorso effettuato** — [x] **implementato (v1.11)**: registrazione del cammino realmente fatto, indipendente da un percorso GPX caricato come guida (funziona sia seguendo una traccia sia partendo semplicemente con "Avvia registrazione"). Pulsanti Avvia/Pausa/Riprendi/Termina in `TrackingControls.kt`; motore di registrazione in `data/tracking/TrekRecorder.kt`; salvataggio in `data/tracking/ActivityStorage.kt`. Al termine salva tempo totale, tempo in movimento, distanza in km e dislivello.
   - **Pulsante di pausa**: [x] fatto — interrompe il conteggio di tempo/distanza durante una sosta, senza fermare la registrazione dell'intero percorso.
   - **Avviso automatico "sei ancora in pausa?"**: [x] fatto — se durante la pausa il GPS rileva uno spostamento oltre una soglia (30 m) rispetto al punto in cui è scattata la pausa, compare un avviso a schermo per ricordare di premere "Riprendi".
   - **Scelte deliberate per contenere il rischio di questo incremento** (da rivedere se servisse di più):
     - **Persistenza a file JSON, non Room**: Room richiede un nuovo plugin Gradle (KSP o kapt) per l'elaborazione delle annotazioni, non ancora presente nel progetto — rischio di un'altra incompatibilità di versioni da scoprire solo in CI, come già successo con AGP/Kotlin. Un file JSON nella cartella privata dell'app (`kotlinx.serialization`, già una dipendenza) basta per il volume di dati atteso. Room resta l'opzione se in futuro servissero query più ricche o il volume crescesse molto.
     - **Dislivello dal solo GPS, senza barometro**: mitigato con una soglia minima per segmento (3 m) per limitare il rumore verticale tipico del GPS (vedi "Spunti dalla ricerca competitiva" sopra), ma resta meno preciso di un approccio ibrido con barometro — miglioramento futuro, non incluso ora.
     - **"Tempo in movimento" approssimato**: oggi è "tempo totale meno le pause manuali", non un vero rilevamento automatico dei tratti fermi (es. una sosta per una foto senza premere "Pausa" viene comunque contata come tempo in movimento) — un affinamento possibile in futuro, se si rivelasse importante dopo l'uso reale.
   - **Non ancora fatto in questa versione**: nessuna schermata per rivedere l'elenco dei percorsi salvati (i dati vengono scritti su file ma non c'è ancora una "Cronologia" per consultarli in app) — i dati non vanno persi, ma prima di essere davvero utili serve un punto per vederli. Vedi punto 2 qui sotto.
   - **Conteggio passi — arricchimento pianificato (agosto 2026, non ancora implementato)**: fattibile leggendo il sensore hardware `Sensor.TYPE_STEP_COUNTER` tramite `SensorManager` (stessa famiglia di API di sistema già usata per GPS/localizzazione, nessuna nuova dipendenza esterna), con permesso `ACTIVITY_RECOGNITION` da richiedere a runtime su Android 10+. Scartata l'alternativa Health Connect/Google Fit come prima scelta: Google Fit è in dismissione entro fine 2026, e Health Connect è pensata per sincronizzare dati tra più app salute — più pesante di quanto serva per registrare solo i passi di una singola attività già tracciata in app. Da salvare insieme a distanza/tempo/dislivello in `CompletedActivity`, aggiornato in `TrekRecorder.kt` come le altre metriche. Da sviluppare quando si procederà.
2. **Cronologia percorsi**: schermata per rivedere l'elenco dei percorsi salvati dalla registrazione del punto 1. Due livelli:
   - **Elenco**: data, distanza, tempo in movimento, dislivello per ciascun percorso salvato.
   - **Dettaglio**: aprendo un percorso dall'elenco, il tracciato disegnato sulla mappa (non solo i numeri) — riusando `TrekMapView.kt` in modalità "sola lettura" (senza freccia di navigazione né avviso fuori percorso), dato che ogni `CompletedActivity` contiene già l'intera lista di punti GPS. Scelta in linea con le app concorrenti viste nella ricerca (Wikiloc, Komoot, Gaia GPS, AllTrails), che mostrano tutte il percorso sulla mappa nella cronologia.
   - I dati sono già scritti su file da `ActivityStorage.kt` (`loadAll()` già pronta per leggerli) — manca solo la schermata. Punto naturale d'ingresso: una nuova voce/icona nella barra in alto di `MainMapScreen.kt`, verso una nuova `ActivityHistoryScreen` (elenco) + `ActivityDetailScreen` (dettaglio con mappa).
3. **Foto geolocalizzate sul percorso**: possibilità di scattare una foto dall'interno dell'app durante il cammino; la foto viene associata al punto esatto in cui è stata scattata e il percorso salvato mostra un'indicazione visiva (es. icona sulla mappa) nei punti in cui sono state scattate una o più foto.
4. **Due tipi di note**:
   - **Nota generale del percorso**: una sola nota testuale libera associata all'intero percorso (es. meteo del giorno, compagni di cammino, condizioni generali).
   - **Nota puntuale geolocalizzata**: come le foto, ma testuale — dalla mappa si crea una nota legata a un punto preciso (es. tratto pericoloso o mal segnalato), salvata con la posizione e evidenziata sulla mappa.
5. **Scoperta cammini nelle vicinanze, con download GPX**: dalla posizione corrente, mostrare un elenco di percorsi che partono da lì o nei dintorni, con punto di arrivo, km, tempo previsto e livello di difficoltà, e possibilità di scaricare la traccia GPX corrispondente per usarla nell'app.
   - **Fattibilità verificata (agosto 2026)**: realizzabile senza costi, riusando la stessa fonte dati già scelta per i luoghi utili. Wikiloc e Komoot non hanno un'API pubblica per sviluppatori terzi (solo Wikiloc conferma esplicitamente l'assenza), quindi non sono utilizzabili come fonte diretta in app. La strada percorribile è **OpenStreetMap tramite Overpass API**: i sentieri sono mappati come relazioni `route=hiking`, spesso con il tag `sac_scale` per la difficoltà (7 livelli, da "escursionismo" a "alpinismo impegnativo") e con la geometria completa del tracciato, da cui si possono ricavare via codice sia la distanza totale sia il punto di arrivo (per generare noi stessi il file GPX, senza bisogno di scaricarlo da terzi). Il tempo previsto non è quasi mai presente nei dati OSM: va stimato dall'app con una formula standard (tipo Naismith, basata su distanza e dislivello), non recuperato da un tag.
   - **Alternativa da valutare**: **Waymarked Trails** (waymarkedtrails.org) espone un'API REST pubblica e open source (`hiking.waymarkedtrails.org/api/v1`) costruita apposta sugli stessi dati OSM — potrebbe semplificare la query "sentieri vicino a un punto" rispetto a scrivere query Overpass da zero; da verificare in dettaglio la copertura e i limiti d'uso quando si arriverà a questo punto.
   - **Limite noto**: la copertura del tag `sac_scale` non è uniforme in tutta Italia — alcuni sentieri (anche CAI) potrebbero non avere la difficoltà indicata su OSM. Va previsto un caso "difficoltà non disponibile" nell'elenco, non un valore inventato.
6. **Luoghi utili: distanza, navigazione e telefono cliccabile**: nell'elenco "Luoghi utili", mostrare accanto al nome la distanza dalla posizione corrente (già calcolabile: `Poi` ha latitudine/longitudine, la posizione corrente è già disponibile dove viene mostrato l'elenco); selezionando un luogo, poter avviare la navigazione verso quel punto — probabilmente riusando `NavigationEngine`/`TrekMapView` già scritti per i tracciati GPX, ma con un "percorso" di un solo punto di arrivo invece di un tracciato importato. Inoltre: rendere visibile il numero di telefono nell'elenco quando presente nei dati (`Poi.phone` è già estratto da Overpass ma non ancora mostrato in `PoiListItem`, vedi `PlacesScreen.kt`), e renderlo cliccabile per aprire il tastierino di chiamata del telefono già compilato (`Intent.ACTION_DIAL` — non serve il permesso `CALL_PHONE`, apre solo il tastierino, non chiama automaticamente). Annotato su richiesta esplicita (agosto 2026), non ancora iniziato.
7. **Pulsante Emergenza**: invio rapido di una richiesta d'aiuto con le coordinate GPS a numeri preimpostati, sia via SMS che via WhatsApp.
   - **Due schermate dedicate** (non un pulsante nella schermata principale, apposta per non rischiare di premerlo per errore):
     - **"Emergenza"**: contiene solo il pulsante che invia effettivamente la richiesta d'aiuto.
     - **"Impostazioni"**: dove inserire i numeri di telefono da contattare in caso di emergenza. Pensata fin da subito come contenitore generale per qualunque dato da preimpostare in futuro (non solo i numeri di emergenza) — se altre funzionalità future avranno bisogno di configurazione, va tutto qui, non sparso in schermate diverse.
   - **SMS**: invio automatico e diretto (via `SmsManager`, permesso `SEND_SMS` da richiedere una volta) a tutti i numeri impostati, con le coordinate GPS. Funziona anche con solo segnale telefonico minimo, senza bisogno di dati — canale principale per l'affidabilità in montagna (vedi discussione fattibilità sotto).
   - **WhatsApp**: predisposto in parallelo, con il limite tecnico già emerso in fase di analisi — WhatsApp non ha un'API per l'invio automatico silenzioso per utenti privati, quindi l'app può solo aprire WhatsApp con il messaggio (testo + coordinate) già scritto verso il numero giusto (via intent, `api.whatsapp.com/send?phone=...`); l'ultimo tocco su "Invia" dentro WhatsApp resta dell'utente, per ciascun contatto. Da trattare come canale secondario/di comodo, non come sostituto dell'SMS.
   - **Fattibilità (agosto 2026)**: confermata per l'SMS (nessuna restrizione rilevante: l'app non passa dallo store di Google Play, quindi non si applicano le limitazioni sui permessi SMS previste per le app pubblicate lì). Nessuna delle grandi app di trekking generaliste confrontate (Komoot, AllTrails, Wikiloc, Gaia GPS) ha questa funzione; esiste invece in app di sicurezza dedicate (es. SafeBeacon, My SOS Family — SOS con un tap, SMS con coordinate GPS a contatti preimpostati), quindi è un pattern precedente valido anche se non tra le app di trekking pure. Per zone davvero senza copertura cellulare, il vero standard resta un dispositivo satellitare dedicato (Garmin inReach, Spot, ZOLEO) — fuori dallo scopo di un'app per smartphone, da menzionare come limite onesto della funzionalità, non da promettere come sostituto.

#### Spunti dalla ricerca competitiva (Komoot, AllTrails, Wikiloc, Gaia GPS — agosto 2026)

- **Tempo totale vs tempo in movimento**: Wikiloc mostra entrambi separatamente. Più utile del solo tempo totale (non gonfiato dalle soste) — da adottare per il punto 1.
- **Dislivello — barometro + GPS ibrido**: il solo GPS sovrastima parecchio il dislivello (rumore verticale, es. +90m su un tratto pianeggiante); un sensore barometrico (se presente sul telefono, `SensorManager.TYPE_PRESSURE`) è molto più preciso ma va calibrato sull'altitudine GPS di partenza e deriva con il meteo. Approccio consigliato: usare il barometro quando disponibile, calibrato a inizio percorso, con fallback al solo GPS (filtrato/smussato) se assente.
- **Registrazione libera, non solo su GPX**: Gaia GPS, HiiKER, Footpath e altre permettono di premere "Avvia" e registrare senza una traccia precaricata — esattamente il caso "solo partenza/arrivo" del punto 1, non un'eccezione da gestire a parte.
- **Foto come caso particolare di waypoint**: Gaia GPS non tratta le foto come una funzione separata dalle note puntuali — una foto è un waypoint con un'icona e, opzionalmente, un'immagine allegata. Conviene modellare punto 2 e la nota puntuale del punto 3 sulla stessa struttura dati (stesso "punto sul percorso", con testo e/o foto opzionali), invece di due sistemi paralleli.
- **Icone per categoria sui punti**: Gaia GPS usa icone predefinite per tipo di punto (pericolo, acqua, bivio, campeggio...) invece di un unico marker generico — rende la mappa leggibile a colpo d'occhio ed è coerente con il principio guida "sicurezza e chiarezza" già alla base del progetto.
- **Nota generale del percorso — occasione di differenziazione**: AllTrails delega esplicitamente il diario di viaggio (meteo, compagni, sensazioni) a un'app esterna, non lo integra. Farlo bene direttamente in GM-Trekking (punto 3) non è solo "mettersi alla pari" con la concorrenza ma un vero punto di distinzione.

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
- **Numero di versione**: la versione attuale del progetto è la **1.11** (`versionName` in `app/build.gradle.kts`; aggiornata con la registrazione del cammino effettuato — Avvia/Pausa/Riprendi/Termina). Ad ogni modifica rilasciata, aggiornare `versionCode` (+1) e `versionName`. Il numero deve anche essere mostrato nell'interfaccia dell'app (non ancora fatto): `buildFeatures.buildConfig = true` è già abilitato nel Gradle, quindi basterà leggere `BuildConfig.VERSION_NAME` — punto d'ingresso naturale: un piccolo testo nella barra in alto di `MainMapScreen.kt`, o una futura schermata "Informazioni".
- **Messaggi di commit, sempre con la versione davanti**: ogni commit deve iniziare con `vX.Y - descrizione` (es. `v1.11 - ...`), così su GitHub si vede subito a quale versione appartiene ogni modifica. Vale anche per i commit che toccano solo la documentazione (piano, README) senza rilasciare una nuova build: in quel caso si usa il numero di versione corrente (l'ultimo rilasciato), senza incrementarlo. Standard da rispettare sempre, non solo per i cambi di codice.
- **Workflow di sviluppo**: l'utente non usa Android Studio, solo GitHub (push → GitHub Actions compila l'APK → installazione diretta sul telefono). Le richieste di log/diagnostica vanno quindi pensate senza presumere accesso a Logcat/adb — da qui la schermata di crash in-app (`crash/CrashHandler.kt`).
- **Ogni schermata deve avere un modo esplicito di tornare alla mappa principale**: freccia/pulsante indietro visibile in alto, non affidarsi solo al gesto/pulsante di sistema — coerente con il principio di chiarezza per utenti non esperti già alla base del progetto. Richiesto esplicitamente (agosto 2026): vale per tutte le schermate, comprese quelle già esistenti (`PlacesScreen.kt` oggi non ce l'ha) e tutte quelle future (Cronologia, Emergenza, Impostazioni, ecc.). Da applicare quando si riprenderà in mano la navigazione tra schermate (`ui/navigation/AppNavHost.kt`), non solo alle nuove schermate create da qui in avanti.

## Rischi da monitorare (ripresi dall'analisi di fattibilità)

- Qualità/copertura disomogenea dei dati OpenStreetMap in alcune aree montane: mitigare permettendo segnalazioni dagli utenti in Fase 3.
- Consumo batteria durante tracciamento lungo: testare su più dispositivi reali, non solo emulatore.
- Precisione GPS in bosco/valli strette: la soglia di tolleranza per l'avviso di fuori percorso deve essere calibrabile e testata sul campo, non solo a tavolino.
- Versioni delle dipendenze (Kotlin, Compose, MapLibre) verificate ad agosto 2026: se Android Studio propone aggiornamenti (Upgrade Assistant), è normale e sicuro accettarli.
- **Dipendenza da infrastruttura Overpass API pubblica**: il mirror usato oggi (`overpass.kumi.systems`) potrebbe in futuro applicare gli stessi filtri anti-bot del server principale, o diventare instabile/sovraccarico — è successo al server principale nel 2026 (vedi problema HTTP 406 sopra), e anche il mirror ha già mostrato un HTTP 504 Gateway Timeout su query pesanti (mitigato ottimizzando la query, v1.10). Da tenere d'occhio; se si ripresenta ancora, prima cosa da controllare è il dettaglio tecnico dell'errore in "Luoghi utili" (già mostrato in UI dalla v1.6). Soluzione strutturale a lungo termine, se il problema continua a ripetersi: scaricare periodicamente un estratto `.osm.pbf` (es. da Geofabrik) e servire i luoghi utili da una copia propria dei dati invece che da un server Overpass pubblico condiviso.
