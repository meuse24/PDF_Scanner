# Feature-Plan: Lokaler Wi-Fi-Transfer (PC-Sync)

## Umsetzungsstatus

**Status: geplant, nicht umgesetzt.** Dieser Plan bewertet den vom Nutzer vorgeschlagenen Ansatz (NanoHTTPD + Token-in-URL + Foreground Service) und korrigiert ihn an mehreren Stellen, bevor eine Umsetzung beginnt.

## Kurzfazit

Machbar, aber **kein kleines Feature** — realistisch **10–14 Personentage**, also spürbar mehr als der ursprüngliche Vorschlag suggeriert. Der Mehraufwand steckt nicht im HTTP-Server selbst, sondern darin, dass dies das **erste netzwerkfähige Feature der App** ist (bisher keine `INTERNET`-Permission, keine Foreground-Services, kein Login/Session-Konzept) und PDF-Inhalte aktiv im LAN exponiert werden — das berührt Sicherheitsmodell, App-Lock-Semantik, Datenschutzerklärung und Play-Store-Deklarationen, nicht nur Code.

Der Vorschlag des Nutzers ist im Kern richtig (lokaler Server statt Cloud-Relay ist die richtige Idee für „kein Kabel, keine Zusatzsoftware"), aber der konkrete Beispielcode hat mehrere Lücken, die unten im Detail benannt und korrigiert werden.

## Bewertung des vorgeschlagenen Ansatzes

### Was übernommen wird
- Grundidee: eingebetteter lokaler HTTP-Server, PC greift per Browser über die LAN-IP zu — kein Kabel, keine Zusatzsoftware am PC. Das ist der richtige Architekturansatz.
- Foreground Service mit Pflicht-Notification, damit der Server bei gesperrtem Bildschirm weiterläuft.
- Grundgedanke „Token-Schutz, da kein valides TLS-Zertifikat für eine private IP möglich ist".

### Was korrigiert wird

**1. Bibliothek: Ktor (server-cio) statt NanoHTTPD.**
Der Vorschlag nennt Upload *und* Download als Ziel ("PDFs per WLAN herunter- und hochladen"), der Beispielcode implementiert aber nur Download. NanoHTTPD kann Multipart-Uploads zwar parsen, aber nur über eine ältere, manuelle API (`session.parseBody(files)`), die Requests synchron im Aufruf-Thread blockiert — jede Route müsste dann über `runBlocking` in die coroutine-basierten UseCases/Repositories der App hineinbrücken, was dem Rest der Codebasis (durchgehend `suspend`, `DispatcherProvider`) widerspricht. Ktor mit dem CIO-Engine (reines Kotlin, kein Netty/keine nativen Libs, Android-tauglich) bietet:
- native `suspend`-Routen → direkter Aufruf von `ImportFileUseCase`, `DocumentRepository` etc. ohne Bridging.
- `receiveMultipart()` für robuste, streamende Uploads (kein Vollladen in den Speicher).
- `io.ktor:ktor-server-test-host` für **JVM-Unit-Tests der Routen ohne Gerät** — passt zur bestehenden Teststrategie des Projekts (JVM-Tests bevorzugt, siehe `CLAUDE.md`), während NanoHTTPD echte Socket-Bindings braucht und praktisch nur instrumentiert testbar ist.
- aktive Wartung (JetBrains) vs. NanoHTTPD (seit Jahren nur sporadische Releases).
Mehrkosten: etwas mehr Abhängigkeits-Fussabdruck als NanoHTTPD — angesichts der bereits vorhandenen Größe des Projekts (ML Kit, PdfBox-Android) nicht ausschlaggebend.

**2. Sicherheitsmodell: Token-in-jeder-URL ist die schwächere Variante.**
Der Vorschlag baut das Token in jeden Link ein (`/t/A7F9/download/...`). Das landet in Browser-Verlauf, Autovervollständigung und (falls je ein externer Link/Ressource eingebunden würde) im `Referer`-Header. Außerdem ist das Beispiel im Code inkonsistent: Der Fließtext beschreibt ein „dynamisches Einmal-Token", die Startfunktion im Beispielcode verwendet aber `val token = "A7F9"` fest verdrahtet.
Korrigierter Ansatz:
- Root-URL bleibt ohne Token (`http://<ip>:8080/`) und zeigt ein PIN-Eingabeformular.
- Erfolgreiche PIN-Eingabe setzt ein zufälliges Session-Cookie (128 Bit, `HttpOnly`), das für Folge-Requests gilt — kein Secret mehr in sichtbaren URLs/Links.
- PIN wird **pro Server-Start neu zufällig erzeugt** (kryptografisch, `SecureRandom`), nicht hartkodiert.
- **PIN-Länge: 4 Ziffern** (Entscheidung: Merkbarkeit hat Vorrang vor Entropie). Das ergibt nur 10.000 Kombinationen — deutlich weniger als der ursprünglich angedachte 4-stellige alphanumerische Code. Das Rate-Limiting muss deshalb strenger ausfallen als bei höherer Entropie:
  - Max. 5 Fehlversuche pro IP, danach 30 s Sperre; die Sperrzeit verdoppelt sich pro weiterem Fehlversuchs-Block (exponentielles Backoff: 30 s → 60 s → 120 s …) statt linear weiterzuzählen.
  - Zusätzlich harte Obergrenze: nach 20 Fehlversuchen insgesamt stoppt der Server sich selbst und muss vom Nutzer manuell mit einer neuen PIN neu gestartet werden (verhindert stundenlanges automatisiertes Durchprobieren der 10.000 Kombinationen).
  - Die kurze Session-Lebensdauer (15 Min. Inaktivität) bleibt zusätzlich bestehen, damit ein erratener PIN nicht dauerhaft gültig ist.
- Session läuft nach Inaktivität ab (z. B. 15 Minuten) und in jedem Fall beim Stoppen des Servers.

**3. Dateizugriff: nicht das Dateisystem, sondern `DocumentRepository` als Quelle der Wahrheit.**
`documentDir.listFiles()` im Vorschlag würde auch **bereits in den Papierkorb verschobene** Dokumente auflisten (Soft-Delete legt die Datei laut `CLAUDE.md` nur in den Trash-Zustand, physisch bleibt sie bis zum Purge bestehen) — das widerspricht der Nutzererwartung nach dem Löschen. Zusätzlich landen im `scans/`-Verzeichnis auch Thumbnails (`.jpg`) und ggf. temporäre Dateien.
Korrigierter Ansatz: Die Web-Oberfläche listet ausschließlich Einträge aus `DocumentRepository.getAllScans()` (gefiltert auf nicht gelöschte Dokumente) und referenziert sie über ihre **DB-ID**, nicht über den rohen Dateinamen. Das eliminiert nebenbei jedes Path-Traversal-Risiko, weil nie ein Client-String direkt in einen Dateipfad übersetzt wird.

**4. Encoding/Escaping fehlt im Beispiel.**
- Dateinamen werden im Beispiel ungeschützt in `href`-Attribute interpoliert (`<a href="...${file.name}">`). Da importierte PDF-Namen vom Nutzer/Quell-App frei wählbar sind, können sie Anführungszeichen/`<`/`>` enthalten → HTML-Injection in der eigenen Web-Oberfläche. Muss über `TextUtils.htmlEncode()` (oder Ktor's HTML-DSL, die automatisch escaped) laufen.
- `Content-Disposition: attachment; filename="…"` mit rohem Namen mangelt Umlaute/Nicht-ASCII-Namen in manchen Browsern. Korrekt: `filename*=UTF-8''<percent-encoded>` (RFC 6266) zusätzlich zum ASCII-Fallback.

**5. IP-Ermittlung: `WifiManager.connectionInfo.ipAddress` ist der falsche Weg.**
Diese API ist auf neueren Android-Versionen unzuverlässig (teils `0`, teils zusätzliche Permissions je nach Hersteller/Version nötig) und funktioniert nur im klassischen Wi-Fi-Stationsmodus, nicht z. B. über einen mobilen Hotspot. Korrigierter Ansatz: lokale IPv4-Adresse über `NetworkInterface.getNetworkInterfaces()` ermitteln (erste nicht-Loopback-, nicht-virtuelle Adresse aus einem privaten Bereich, RFC 1918) — funktioniert unabhängig vom Verbindungstyp und braucht **keine** `ACCESS_WIFI_STATE`/Standort-Permission.

**6. App-Lock-Interaktion fehlt komplett.**
`CLAUDE.md` definiert App-Lock explizit als reines UI-Gate, keine Verschlüsselung. Ein laufender, tokenbasiert erreichbarer HTTP-Server würde diesen Schutz faktisch aushebeln: Ein Angreifer mit der PIN käme an alle Dokumente, ganz ohne die App-Sperre zu passieren. Der Plan sieht deshalb vor:
- „Mit PC verbinden" ist nur erreichbar, wenn die App gerade entsperrt ist.
- Der Server **stoppt automatisch**, sobald `AppLockManager.isLocked` auf `true` wechselt (bestehender `StateFlow`, kein neuer Mechanismus nötig).

**7. Foreground-Service-Details fehlen bzw. sind für Android 14 unvollständig.**
- `foregroundServiceType="dataSync"` muss im Manifest gesetzt werden (ab API 34 Pflicht, sonst `MissingForegroundServiceTypeException` zur Laufzeit).
- Die Notification sollte einen **Stop-Action-Button** enthalten (nicht nur „App öffnen zum Stoppen").
- Auto-Stop nach Inaktivitäts-Timeout (keine Requests seit z. B. 20 Minuten) begrenzt Angriffsfenster und Akkuverbrauch zusätzlich zum manuellen Stop.
- OEM-Fragmentierung (Samsung/Xiaomi killen Foreground Services teils aggressiv) ist ein bekanntes Risiko und sollte auf realer Hardware mehrerer Hersteller getestet werden, nicht nur im Emulator.

**8. Erstmalige Netzwerk-Exposition: Datenschutz & Play Store.**
Die App hat aktuell **keine** `INTERNET`-Permission im Manifest — dieses Feature ist der erste Fall, in dem Dokumentinhalte über das Netzwerk erreichbar werden. Das erfordert:
- Ergänzung in `docs/privacy-policy.html`, Abschnitt 4 („Network, advertising, accounts, and permissions" / „Netzwerk, Werbung, Konten und Berechtigungen"): lokaler Server, kein Cloud-Relay, PIN-geschützt, zeitlich befristet, keine Datenübertragung außerhalb des lokalen Netzes.
- Prüfung der Play-Console-Data-Safety-Angaben (auch wenn keine Google-/Drittanbieter-Server involviert sind, sollte das Feature dort dokumentiert werden — Formulierung mit dem Play-Console-Verantwortlichen abstimmen).
- Ein klar sichtbarer Erstnutzungs-Hinweis in der App selbst (kein verstecktes Kleingedrucktes), bevor der Server zum ersten Mal gestartet wird.

## Architektur-Einordnung (folgt bestehenden Schichten-Regeln)

| Schicht | Neue/geänderte Artefakte |
|---|---|
| `domain/gateway/` | neuer Port `LocalSyncServer`: `start(): LocalSyncSession`, `stop()`, `sessionState: StateFlow<LocalSyncState>` (`Stopped`, `Starting`, `Running(url, pin, connectedClients)`, `Error`) — frameworkfrei, kennt weder Ktor noch Android |
| `domain/model/` | `LocalSyncState`, `LocalSyncSession` (ip, port, pin, startedAt) |
| `util/` (bzw. neues Unterpaket `util/sync/`) | `KtorLocalSyncServer.kt` — Ktor-CIO-Implementierung von `LocalSyncServer`; Routing, PIN-Login, Session-Cookies, Rate-Limiting, HTML-Rendering (escaped), Multipart-Upload → temporäre Datei → bestehender `ImportFileUseCase` |
| `util/` | `LocalNetworkAddress.kt` — `NetworkInterface`-basierte IPv4-Ermittlung (kein `WifiManager`) |
| `util/` | `LocalSyncService.kt` — Foreground Service (`dataSync`), Notification mit Stop-Action, beobachtet `AppLockManager.isLocked` und Inaktivitäts-Timeout, stoppt Server entsprechend |
| `domain/usecase/` | keine neue Fachlogik-UseCases nötig — Wiederverwendung von `ImportFileUseCase` (Upload) und `DocumentRepository.getAllScans()` (Liste, gefiltert auf nicht gelöscht) |
| `ui/sync/` | `LocalSyncScreen.kt`, `LocalSyncViewModel.kt` — Start/Stop-Button, große URL-/PIN-Anzeige, laufender Status, Fehleranzeige (kein WLAN, Server-Startfehler) |
| `ui/navigation/` | neue Route `Screen.LocalSync`, Einstiegspunkt im Hauptmenü/Drawer (`AppDrawerContent.kt`, neben Ordner/Papierkorb) — nicht aus dem Viewer, da das Feature das ganze Archiv betrifft, nicht ein Einzeldokument |
| `di/` | `LocalSyncModule`: Bindung `LocalSyncServer` → `KtorLocalSyncServer` |
| Manifest | `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` (Runtime-Anfrage ab API 33); `<service>`-Eintrag mit `foregroundServiceType="dataSync"` |
| Strings | `strings_localsync.xml` in allen 10 Locales (Button „Mit PC verbinden", Statusanzeigen, Fehlermeldungen, Erstnutzungs-Hinweis) |
| Doku | `docs/privacy-policy.html` Abschnitt 4 ergänzen; `docs/Bedienungsanleitung.md` neuer Abschnitt |

## Sicherheitskonzept (überarbeitet)

1. Server bindet auf allen lokalen Interfaces, aber es existiert **keine** öffentlich erreichbare Route ohne Session-Cookie außer `/` (PIN-Formular) und `/login` (POST).
2. PIN: 4-stellig, numerisch, pro Serverstart neu (`SecureRandom`), nur im Notification-Text/UI sichtbar, nie geloggt.
3. Rate-Limiting auf `/login`: exponentielles Backoff ab dem 5. Fehlversuch pro Client-IP (30 s → 60 s → 120 s …), harter Server-Stopp nach 20 Fehlversuchen insgesamt; Zähler persistiert nur in-memory für die Serverlaufzeit.
4. Session-Cookie: 128 Bit Zufallswert, `HttpOnly`, `SameSite=Strict`; Ablauf nach 15 Minuten Inaktivität oder Serverstopp.
5. Downloads/Uploads ausschließlich über DB-IDs aus `DocumentRepository`, nie über rohe Dateisystempfade oder Client-übergebene Dateinamen.
6. Upload: Content-Type- und Magic-Bytes-Prüfung (`%PDF-`), **Größenlimit 25 MB pro PDF-Datei** (serverseitig hart durchgesetzt, Request wird abgebrochen sobald die Grenze überschritten wird — kein Vollladen großer Dateien in den Speicher, um das Limit zu prüfen), Ziel immer `storageProvider.tempDir()` → danach regulärer `ImportFileUseCase`-Pfad (inkl. Deduplizierung über `resolveUniqueFilename`, Thumbnail-Erzeugung, optionales AutoTagging — nichts davon muss neu gebaut werden).
7. Server stoppt automatisch bei App-Lock-Aktivierung, nach Inaktivitäts-Timeout und beim expliziten Stop; kein automatischer Neustart nach App-Kill.
8. Kein HTTPS in v1 (technisch für private IPs ohne eigene CA nicht sauber lösbar) — dieser Trade-off wird in der Datenschutzerklärung und im Erstnutzungs-Hinweis explizit benannt, nicht verschwiegen.

## Phasenplan

**Phase 1 — Grundgerüst & IP-/Netzwerk-Layer (1,5 Tage)**
- `LocalSyncServer`-Port, `LocalNetworkAddress` (NetworkInterface-Scan), Domain-Modelle.
- Unit-Tests für IP-Auswahl-Logik (mehrere Interfaces, keine Wi-Fi-Verbindung, VPN aktiv).

**Phase 2 — Ktor-Server: Auth & Listing (2–2,5 Tage)**
- PIN-Login, Session-Cookie, Rate-Limiting.
- Dokumentliste aus `DocumentRepository` (nur nicht gelöschte), escaped HTML-Rendering.
- Routen-Tests via `ktor-server-test-host` (kein Gerät nötig).

**Phase 3 — Download & Upload (2–2,5 Tage)**
- Download-Route über DB-ID, korrektes `Content-Disposition` (RFC 6266, Unicode-Dateinamen).
- Upload-Route: Multipart → Temp-Datei → `ImportFileUseCase`, Validierung (Typ, Magic Bytes, 25-MB-Limit pro Datei).
- Instrumentation-Test: echter Upload/Download-Roundtrip über `localhost` auf Testgerät.

**Phase 4 — Foreground Service & Lifecycle (2 Tage)**
- `LocalSyncService` mit Notification (inkl. Stop-Action), `foregroundServiceType="dataSync"`.
- Kopplung an `AppLockManager.isLocked`, Inaktivitäts-Timeout.
- `POST_NOTIFICATIONS`-Runtime-Permission-Flow (API 33+).

**Phase 5 — UI & Navigation (1,5–2 Tage)**
- `LocalSyncScreen`/`LocalSyncViewModel`, Einstiegspunkt im Hauptmenü/Drawer (`AppDrawerContent.kt`).
- Erstnutzungs-Hinweis-Dialog vor erstem Serverstart.
- Fehlerzustände (kein WLAN, Port belegt, Berechtigung verweigert) nach bestehendem `_error`/`_success`-Pattern.

**Phase 6 — Lokalisierung & Dokumentation (1 Tag)**
- `strings_localsync.xml` in 10 Sprachen.
- `docs/privacy-policy.html` Abschnitt 4 ergänzen (DE/EN), `docs/Bedienungsanleitung.md` erweitern.
- Play-Console-Data-Safety-Eintrag prüfen/abstimmen.

**Phase 7 — Härtung & Geräte-Tests (1–1,5 Tage)**
- Mehrere OEM-Geräte (Samsung/Xiaomi/Pixel) für Foreground-Service-Zuverlässigkeit testen.
- Brute-Force-/Rate-Limit-Verhalten manuell verifizieren.
- Verhalten bei Netzwerkwechsel (Wi-Fi→aus, Hotspot an/aus) während laufender Session prüfen.

## Bewusst außerhalb des Scopes (v1)

- **HTTPS/TLS** für die lokale Verbindung — technisch ohne echtes Zertifikat nicht sauber lösbar, PIN+Session-Modell ist der Kompromiss für v1.
- **QR-Code-Pairing** — bringt für ein PC-Browser-Ziel (kein Kamera-Input am typischen PC) keinen echten Mehrwert gegenüber manueller PIN-Eingabe; kann später ergänzt werden, falls Nutzer-Feedback das nahelegt.
- **Ordnerstruktur/Favoriten/Papierkorb-Navigation** in der Web-Oberfläche — v1 zeigt eine flache Liste aller aktiven Dokumente, keine Nachbildung der vollständigen App-Navigation.
- **Mehrere gleichzeitige PC-Verbindungen mit granularen Rechten** — v1 kennt nur eine gemeinsame Session pro Serverlauf, keine Multi-User-Verwaltung.
- **Automatischer Cloud-Fallback**, falls kein gemeinsames WLAN besteht — bewusst rein lokal, kein Relay-Server.

## Entschiedene Priorisierungsfragen

1. Ktor (CIO) statt NanoHTTPD — bessere Testbarkeit und natives `suspend`, obwohl minimal größerer Dependency-Fussabdruck.
2. PIN+Session-Cookie statt Token-in-jeder-URL — schließt Leak-Risiko über Browser-Verlauf/Referrer.
3. Feature ist frei verfügbar; keine Premium/Billing-Kopplung (analog zur Entscheidung im AcroForm-Plan).
4. Automatischer Server-Stopp bei App-Lock hat Vorrang vor Nutzerkomfort — Sicherheitskonsistenz mit dem bestehenden App-Lock-Modell wiegt schwerer als eine durchgehende PC-Verbindung über eine Sperre hinweg.
5. **PIN-Länge: 4 Ziffern**, damit sie sich der Nutzer beim Blick aufs Handy merken/am PC eintippen kann, ohne hin- und herzuschauen. Ausgleich für die dadurch geringere Entropie: exponentielles Backoff + harter Server-Stopp nach 20 Fehlversuchen (siehe Sicherheitskonzept).
6. **Upload-Limit: 25 MB pro PDF-Datei**, serverseitig hart durchgesetzt (Streaming-Check, kein Vollladen vor der Prüfung).
7. **Einstiegspunkt: Hauptmenü/Drawer** (`AppDrawerContent.kt`, neben Ordner/Papierkorb) statt Settings — direkt sichtbar und schnell erreichbar. Konsequenz: Der Drawer-Eintrag sollte den laufenden Status (z. B. kleiner grüner Punkt/„aktiv"-Badge) anzeigen, damit eine offene PC-Verbindung nicht unbemerkt im Hintergrund bleibt.

## Offene Fragen (bitte vor Implementierungsstart klären)

Keine offenen Priorisierungsfragen mehr — alle drei ursprünglich offenen Punkte (PIN-Länge, Upload-Limit, Einstiegspunkt) sind oben unter „Entschiedene Priorisierungsfragen" (5.–7.) aufgelöst.
