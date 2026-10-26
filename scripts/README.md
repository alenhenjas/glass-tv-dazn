# Script di installazione · DAZN TV Box (Sagemcom DTIW3930)

Kit per Windows: doppio clic sugli script nell'ordine. Ogni modifica è reversibile con `99_ripristino.bat`.

| Script | Cosa fa |
|---|---|
| `01_adb.bat` | verifica ADB o scarica i platform-tools ufficiali di Google |
| `02_connetti.bat` | collegamento al box via rete (salva l'IP in `ip.txt`) |
| `03_analisi.bat` | report completo del dispositivo in `analisi.txt` |
| `04_ottimizza.bat` | animazioni rapide, niente scansioni in background, HDMI-CEC, pulizia cache, compilazione AOT — con backup automatico dei valori originali |
| `05_debloat.bat` | disattiva le app elencate in `debloat.txt` (`pm disable-user`, reversibile) |
| `06_installa_glass.bat` | installa Glass TV, concede i permessi, attiva il servizio e lo imposta come home |
| `07_riattiva_servizio.bat` | riattiva il servizio dopo un aggiornamento di Glass |
| `99_ripristino.bat` | riattiva tutte le app, ripristina le impostazioni e la home originale |

---
Glass TV · © 2026 Alen Henjas
