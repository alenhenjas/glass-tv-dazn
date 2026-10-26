# Cronologia delle versioni

Tutte le tappe di **Glass TV**, dalla prima ottimizzazione del DAZN TV Box fino al porting sullo Xiaomi TV Box S.
Ogni versione è stata installata e verificata direttamente sul televisore, con schermate e misure di fluidità (`dumpsys gfxinfo`).

> Progetto ideato, progettato e sviluppato da **Alen Henjas**.

---

## 5.6 — 26 ottobre 2026 · Indicatore volume "liquid glass" di nuova generazione
- Indicatore del volume ridisegnato: vetro **trasparente** (si vede la partita sotto), bordo speculare, riflesso morbido, altoparlante bianco con onde che si accendono gradualmente e **barra a capsula** in vetro con bagliore.
- Il livello scorre in modo fluido verso il nuovo valore (animazione da 160 ms).
- Finestra più piccola e più bassa: copre meno l'immagine.

## 5.5 — 26 ottobre 2026 · Volume dal telecomando Xiaomi
- Scoperto, analizzando il codice di sistema, che sullo Xiaomi i tasti volume vengono consumati dal window manager prima del filtro di accessibilità.
- Glass ora reagisce al **cambio di volume di sistema** (`VOLUME_CHANGED_ACTION`) e mostra il proprio indicatore anche quando il volume si regola dal telecomando.

## 5.4 — 26 ottobre 2026 · Video reale e indicatore volume in stile Apple
- Il popup Video legge la **vera uscita HDMI** da SurfaceFlinger (`FixedSizeModeMgr: 2160p30hz`): l'interfaccia Android è sempre 1080p e prima il popup mostrava "1080p" anche in 4K.
- Lettura in background: il popup si apre subito e aggiorna la spunta appena il dato è pronto.
- Nuova voce **4K · 25 Hz** per TV e sport italiani.
- Primo indicatore volume a quadrato in basso al centro, come su iPhone, iPad e Mac.
- Diagnosi del limite 4K: la TV dichiara al massimo 2160p30 (anche come modalità Dolby Vision), quindi il limite è del collegamento HDMI e non del box.

## 5.3 — 26 ottobre 2026 · Automazione impostazioni Xiaomi
- L'automazione delle impostazioni di sistema ora cerca anche nelle **finestre sovrapposte** (la conferma "Resolution has been changed" è una finestra di tipo overlay) e visita l'intero albero dei nodi (le pagine *slice* di Android TV sfuggono alla ricerca per testo).
- Nuovo passo `~voce`: seleziona una voce per far comparire il suo pannello di anteprima e poi agisce lì.
- Orario liquid glass: le cifre vengono convertite in un **contorno unico** (`Path.op UNION`), niente più linee interne dove i tratti del carattere si sovrappongono (es. il "4").
- DNS privato Cloudflare (DNS-over-TLS) sullo Xiaomi.

## 5.1 – 5.2 — 25 ottobre 2026 · Stabilità menu
- Risolto il menu della pressione lunga che restava bloccato a schermo: con il telecomando vero partivano due pressioni lunghe (la mia e quella standard di Android) e si aprivano due menu sovrapposti. Ora il tasto OK è gestito interamente da Glass e un secondo menu non può aprirsi sopra al primo.

## 5.0 — 25 ottobre 2026 · Porting su Xiaomi TV Box S (2nd Gen)
- Stesso codice, rilevamento automatico del dispositivo (`Sys.XIAOMI`).
- Popup Video dedicato con le risoluzioni Amlogic (4K 24/25/30, 1080p 24/50/60, 720p) applicate tramite la pagina di sistema, sotto una scheda in vetro.
- Glass impostato come home al posto di Google TV (`launcherx` e `setupwraith` disattivati in modo reversibile).
- Elenco app della home predefinito per entrambi i box: le app non installate vengono saltate.
- Uscita portata da **4K 30 Hz a 1080p 60 Hz** (interfaccia a 60 fps) e poi resa selezionabile in un clic.

## 4.6 — 25 ottobre 2026 · Riavvio e orario personalizzabile
- Pulsante **Riavvia** accanto a Spegni: apre il menu di accensione di sistema e preme "Riavvia" in automatico.
- **Orario personalizzabile** (OK o pressione lunga sull'orario): colore (12 tinte + "dallo sfondo"), stile (pieno, liquid glass, vetro trasparente, contorno), opacità, carattere, dimensione, data on/off. Anteprima dal vivo in un popup laterale.
- Selettori "‹ valore ›" nei popup (sinistra/destra per cambiare).
- Il focus non esce mai dai popup (ricerca della voce successiva limitata al pannello).

## 4.4 – 4.5 — 25 ottobre 2026 · Motore liquid glass più veloce
- Ombre calcolate a 1/4 di risoluzione e **condivise** tra tutti i pannelli della stessa forma (cache LRU).
- Riflessi pre-disegnati in bitmap condivise, create solo quando servono.
- Elenco delle app aperte preparato in background: il menu della pressione lunga compare all'istante.
- Animazioni più rapide (popup 110–140 ms, icone 140 ms).
- Risultato misurato sul DAZN box: 99° percentile dei frame **da 250 ms a 48 ms**, scatto massimo all'apertura dei popup da ~300 ms a ~50 ms.

## 4.3 — 25 ottobre 2026 · Spegnimento TV
- Pulsante **Spegni** in basso a sinistra con conferma in vetro: standby del box e spegnimento della TV via HDMI-CEC.

## 4.2 — 25 ottobre 2026 · Home a griglia adattiva
- La home calcola righe, colonne e dimensione delle icone in base al numero di app: tutto sempre visibile, mai scorrimento.
- Corretto il focus che usciva dai menu e lasciava il popup bloccato.

## 4.1 — 25 ottobre 2026 · Home personalizzabile
- Pressione lunga anche in **Tutte le app**.
- **Aggiungi alla Home / Rimuovi dalla Home** dal menu, con aggiornamento immediato della home.

## 4.0 — 25 ottobre 2026 · Impostazioni in vetro
- App Impostazioni in stile iPad: Wi-Fi, Bluetooth, Video, Audio, App aperte, App, Memoria, Informazioni, Data e ora.
- Browser: clic veri sulla pagina (eventi touch) per i banner dei cookie.

## 3.x — 25 ottobre 2026 · Centro di controllo
- Controlli rapidi in alto (Wi-Fi, Video, Audio, Bluetooth, Chiudi tutto) con **popup in vetro** al posto delle schermate Android.
- Wi-Fi: scansione, connessione con password, dimentica rete. Bluetooth: associazione e dispositivi. Audio: volume, formato, suoni dei tasti. Video: risoluzione e 50 Hz.
- **App aperte** in stile iPad con anteprime reali (MediaProjection) e chiusura delle app.
- Menu della pressione lunga: Apri, Chiudi app (rossa se aperta, grigia se già chiusa), Informazioni app.
- Indicatore volume in vetro.

## 2.x — 25 ottobre 2026 · Launcher e browser
- Home con YouTube, Mavex, MOVE, Browser e dock con Play Store, App aperte, Impostazioni, Tutte le app.
- **Browser** stile Chrome costruito su WebView: puntatore controllato dalle frecce con accelerazione, scorrimento, blocco pubblicità, user agent desktop, barra indirizzi in vetro.

## 1.x — 25 ottobre 2026 · Ottimizzazione e primo launcher
- Analisi completa del box via ADB, backup delle impostazioni e kit di script per Windows.
- Animazioni accorciate, scansioni in background disattivate, compilazione AOT, rimozione del bloatware (reversibile).
- Primo launcher liquid glass con sfondo personalizzato.

---
Glass TV · © 2026 Alen Henjas
