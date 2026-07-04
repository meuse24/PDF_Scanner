# Feature-Plan: Lokaler Wi-Fi-Transfer (PC-Sync)

## Umsetzungsstatus

**Status: in Umsetzung.** Dieser Plan bewertet den vom Nutzer vorgeschlagenen Ansatz (NanoHTTPD + Token-in-URL + Foreground Service), korrigiert ihn an mehreren Stellen und wird phasenweise umgesetzt. Fortschritt siehe „Fortschrittsprotokoll" am Ende dieser Datei.

Entschiedene offene Fragen (vom Nutzer bestätigt): PIN-Länge 4 Ziffern, Upload-Limit 25 MB pro PDF, Einstiegspunkt im Hauptmenü/Drawer (siehe „Entschiedene Priorisierungsfragen" Punkte 5–7).

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

## Fortschrittsprotokoll

### Phase 1 — Grundgerüst & IP-/Netzwerk-Layer: **abgeschlossen**

- `gradle/libs.versions.toml` / `app/build.gradle.kts`: Ktor 3.4.2 ergänzt (`ktor-server-core`, `ktor-server-cio`, `ktor-server-sessions`, testweise `ktor-server-test-host`). Dependency-Auflösung verifiziert (`compileDebugKotlin`, `compileDebugUnitTestKotlin` grün).
- `domain/model/LocalSyncModels.kt`: `LocalSyncSession`, `LocalSyncState` (`Stopped`/`Starting`/`Running`/`Error`), `LocalSyncError`.
- `domain/gateway/LocalSyncServer.kt`: Port mit `state: StateFlow<LocalSyncState>`, `start()`, `stop()`.
- `domain/common/LocalNetworkAddress.kt`: IPv4-Auswahllogik als reine Funktion `selectLocalIPv4Address(List<NetworkInterfaceInfo>)` (testbar ohne echte Netzwerk-Interfaces) plus dünner Adapter `findLocalIPv4Address()` auf Basis von `java.net.NetworkInterface`.
  - **Abweichung vom ursprünglichen Plan:** in `domain/common/` statt `util/` platziert, da `java.net.NetworkInterface`/`InetAddress` reine JDK-Typen sind (kein Android-Framework-Import, analog zur bestehenden `java.io.File`-Nutzung in `domain/gateway`-Ports) und die Auswahllogik so ohne Android-Kontext unit-testbar ist.
  - Auswahlregel: bevorzugt Interface-Namen mit `wlan`/`ap`/`swlan`-Präfix (Wi-Fi/Hotspot), sonst erstes verbleibendes privates IPv4 (RFC 1918); schließt `tun`/`ppp`/`rmnet`/`ccmni`/`clat` (VPN, Mobilfunkdaten, 464xlat) explizit aus.
- Tests: `LocalNetworkAddressTest` (7 Fälle: Wi-Fi bevorzugt, Fallback ohne Wi-Fi-Namen, VPN/Mobilfunk ignoriert, kein lokales Netz, kein aktives Interface, öffentliche Adresse abgelehnt, RFC-1918-Grenzwerte) — alle grün.

### Phase 2 — Ktor-Server: Auth & Listing: **abgeschlossen**

- `util/sync/LocalSyncPin.kt`: `generateLocalSyncPin()` (4-stellig, `SecureRandom`), `String.escapeHtml()`.
- `util/sync/LoginRateLimiter.kt`: pro-Client exponentielles Backoff (30s → 60s → 120s → …, gekappt bei 2^6) ab dem 5. Fehlversuch, harter globaler Stopp nach 20 Fehlversuchen insgesamt (`LoginAttemptResult`: `Allowed`/`LockedOut`/`HardStopped`).
- `util/sync/LocalSyncSessionStore.kt`: In-Memory-Session-Store, 15 Minuten Inaktivitäts-Timeout, zufällige 128-Bit-Session-IDs.
- `util/sync/LocalSyncHtml.kt`: escapte HTML-Renderer für Login-, Sperr- und Dokumentliste-Seite (kein Templating-Framework nötig für diesen Umfang).
- `util/sync/LocalSyncRouting.kt`: `Route.localSyncRouting(...)` — als eigenständige, von `KtorLocalSyncServer` entkoppelte Funktion, damit sie über `ktor-server-test-host` ohne echten Socket/Gerät testbar ist. Routen: `GET /` (PIN-Formular), `POST /login` (PIN-Prüfung, Rate-Limiting, Cookie setzen), `GET /documents` (Session-geschützt, Liste aus `DocumentRepository.getAllScans()` — **keine zusätzliche Trash-Filterung nötig**, da die zugrunde liegende Room-Query bereits `deleted_at IS NULL` filtert; die im Plan angenommene Notwendigkeit einer manuellen Filterung war zu vorsichtig).
- `util/sync/KtorLocalSyncServer.kt`: implementiert `LocalSyncServer`-Port mit Ktor-CIO-Engine (`embeddedServer(CIO, port=8080, host="0.0.0.0")`), verdrahtet Session-Store/Rate-Limiter/PIN mit den Routen.
- `di/LocalSyncModule.kt`: Hilt-Bindung `LocalSyncServer` → `KtorLocalSyncServer`.
- Tests (alle grün, JVM, kein Gerät nötig):
  - `LoginRateLimiterTest` (6 Fälle: Schwelle, Backoff-Werte, Ablauf der Sperre, getrennte Clients, Reset bei Erfolg, harter Stopp).
  - `LocalSyncSessionStoreTest` (6 Fälle: gültig direkt nach Erzeugung, unbekannte ID, Timeout, Auffrischung bei Zugriff, `invalidateAll`, Eindeutigkeit).
  - `LocalSyncPinTest` (4 Fälle: PIN-Format, HTML-Escaping inkl. Ampersand-Reihenfolge).
  - `LocalSyncRoutingTest` (6 Fälle, **echte HTTP-Requests via `ktor-server-test-host`**: Login-Seite ohne Auth erreichbar, `/documents` ohne Cookie → 302 zu `/`, korrekte PIN setzt HttpOnly-Cookie und schaltet Liste frei, falsche PIN setzt kein Cookie, wiederholte Fehlversuche lösen Backoff-Meldung aus, globaler Fehlversuchs-Cap löst Sperr-Seite aus). Bestätigt insbesondere, dass Dateinamen mit `<`/`&`/`"` korrekt escaped werden (kein HTML-Injection-Risiko, siehe Bewertung des ursprünglichen Vorschlags Punkt 4).

### Phase 3 — Download & Upload: **abgeschlossen**

- `util/sync/LocalSyncUpload.kt`: `ByteReadChannel.copyToPdfFile(destination, maxBytes)` — liest den `%PDF-`-Magic-Header separat via `readFully()` (sauberer als ein Partial-Read-Check auf den ersten Chunk), verwirft danach non-PDF-Uploads sofort (`UploadNotAPdfException`), erzwingt das 25-MB-Limit **während des Streamens** (`UploadTooLargeException`, nie die ganze Datei im Speicher).
- `util/sync/LocalSyncRouting.kt` erweitert um:
  - `GET /documents/{id}/download` — Lookup ausschließlich über `DocumentRepository.getScansByIds(listOf(id))` (nicht über rohe Dateinamen/Pfade aus der URL — eliminiert Path-Traversal strukturell), RFC-6266-konformer `Content-Disposition`-Header (ASCII-Fallback-Dateiname **und** `filename*=UTF-8''<percent-encoded>` für Umlaute/Nicht-ASCII-Namen), 404 bei unbekannter ID oder fehlender Datei.
  - `POST /documents/upload` — Multipart-Empfang (`receiveMultipart()`), Ziel-Temp-Datei über `storageProvider.tempDir()`, danach Wiederverwendung des **bestehenden** `ImportFileUseCase` (kein eigener Persistenz-Code — Dedup, Thumbnail, AutoTag laufen wie beim normalen In-App-Import) und Löschen der Temp-Datei in `finally`.
  - `renderDocumentsPage(...)` zeigt jetzt Download-Links (per Dokument-ID) und ein Upload-Formular; erfolgreiche/fehlgeschlagene Uploads erscheinen als escapte Erfolgsmeldung auf derselben Seite.
- `KtorLocalSyncServer`/`localSyncRouting` erhalten `ImportFileUseCase` und `StorageProvider` als zusätzliche Parameter (Constructor-Injection via Hilt).
- **Testbarkeits-Erkenntnis (wichtig für spätere Phasen):** Der Upload-Erfolgspfad lässt sich nicht vollständig als JVM-Unit-Test abbilden — `Uri.fromFile(File)` ist auf dem JVM-Unit-Test-Klassenpfad ein Stub, der trotz `isReturnDefaultValues=true` zu einer `NullPointerException: fromFile(...) must not be null` führt (Kotlins Null-Assertion auf den Rückgabewert des Plattform-Aufrufs). Das ist **keine Produktionsauffälligkeit** (funktioniert auf echten Geräten normal), aber der Grund, warum es dafür `LocalSyncUploadInstrumentedTest` (Instrumentation, on-device) statt eines JVM-Tests gibt — exakt das gleiche Muster wie das PDFBoxResourceLoader/Identity-H-Problem, das in der AcroForm-Formular-Fallback-Verifikation dieser Session bereits einmal auftrat.
- Tests (JVM, alle grün):
  - `LocalSyncUploadTest` (4 Fälle: gültiges PDF byte-genau kopiert, fehlender Magic-Header abgelehnt, zu kurzer Header abgelehnt, Größenlimit ausgelöst).
  - `LocalSyncRoutingTest` erweitert um 6 weitere Fälle: Download ohne Session → Redirect, autorisierter Download mit korrektem escaptem RFC-6266-Header, Download unbekannter ID → 404, Upload einer nicht-PDF-Datei wird abgelehnt **ohne** `ImportFileUseCase` überhaupt aufzurufen (`verifyNoInteractions`). Der Upload-Erfolgsfall ist bewusst nur als Kommentar/Verweis auf den Instrumentation-Test vorhanden (siehe oben).
- Tests (Instrumentation, **geschrieben, kompiliert, aber in dieser Session nicht auf Gerät ausgeführt** — kein Gerät verbunden):
  - `LocalSyncUploadInstrumentedTest` (2 Fälle, echte `ImportFileUseCase`/`ScanRepository`/In-Memory-Room-DB/`PdfEditor`): valider Upload landet im Repository und auf der Festplatte; hochgeladenes Dokument lässt sich per Download-Route wieder abrufen. **Muss vor einem Merge/Release einmal real auf einem Gerät laufen.**
- Nebenbei behoben: `PdfEditorFormOpsTest`/`PdfFormRoundTripInstrumentedTest` aus der AcroForm-Session waren bereits mit Multi-Skript-Fallback-Fonts (Devanagari/Arabisch/CJK) aktualisiert worden (nicht Teil dieses Plans, aber beim Verifizieren mitbeobachtet).

### Phase 4 — Foreground Service & Lifecycle: **abgeschlossen**

- `domain/gateway/LocalSyncServer.kt`: Port um `fun millisSinceLastActivity(): Long?` ergänzt (null = Server läuft nicht).
- `util/sync/KtorLocalSyncServer.kt`: `intercept(ApplicationCallPipeline.Call) { ... }` auf Application-Ebene aktualisiert bei **jedem** eingehenden Request einen `AtomicLong`-Zeitstempel (nicht nur beim Login) — Grundlage für den Inaktivitäts-Timeout.
- `util/sync/LocalSyncService.kt`: neuer Foreground Service (`@AndroidEntryPoint`, Hilt-Injection von `LocalSyncServer` und `AppLockManager`):
  - Startet den Server, zeigt Notification mit URL+PIN (`NotificationCompat`, `PRIORITY_LOW`, `setOngoing(true)`), inkl. **Stop-Action-Button direkt in der Notification** (kein Öffnen der App nötig).
  - Beobachtet `AppLockManager.isLocked` (bereits bestehender `StateFlow`, keine neue Infrastruktur) und stoppt den Server automatisch bei Sperre — verhindert, dass der Hintergrund-Server die App-Sperre faktisch aushebelt (siehe Sicherheitskonzept Punkt 6 im Bewertungsteil).
  - Prüft alle 30 s `millisSinceLastActivity()` und stoppt nach 20 Minuten Inaktivität automatisch selbst (`stopSelfCompletely()`).
  - `foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` — Konstante existiert seit API 29 (verifiziert), keine Versionsprüfung nötig, da `minSdk = 29`.
- `AndroidManifest.xml`: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` ergänzt (App hatte zuvor **keine** dieser Permissions — dieses Feature ist wie angekündigt der erste netzwerk-/notification-relevante Codepfad der App), `<service>`-Eintrag mit `foregroundServiceType="dataSync"`, `exported="false"`.
- `strings_localsync.xml` für Notification-Texte (Kanalname, Titel, Text mit URL/PIN-Platzhaltern, Stop-Button) in allen 10 Locales ergänzt — Rest der Feature-Strings (Screen-UI) folgt in Phase 5/6.
- **Bekannter, bewusst nicht behandelter Android-14-Randfall:** `dataSync`-Foreground-Services werden vom System nach ca. 6 Stunden Laufzeit pro 24h-Fenster automatisch beendet (`Service.onTimeout`). Da der eigene Inaktivitäts-Timeout (20 Min.) in der Praxis immer vorher greift, ist das nur bei durchgehender aktiver Nutzung über Stunden relevant — nicht weiter behandelt, aber hier dokumentiert.
- Build/Verifikation: `compileDebugKotlin`, volle `testDebugUnitTest`-Suite, `assembleDebug` und Manifest-Merge-Check (Permissions/Service korrekt im gemergten Manifest) — alle grün. `POST_NOTIFICATIONS`-Runtime-Anfrage (API 33+) ist UI-seitig, folgt in Phase 5.

### Phase 5 — UI & Navigation: **abgeschlossen (UI ungeprüft auf echtem Gerät — kein Gerät verbunden)**

- `ui/sync/LocalSyncModels.kt`, `LocalSyncViewModel.kt`, `LocalSyncScreen.kt`, `LocalSyncStatusViewModel.kt` (schlanker Read-only-Wrapper nur für das Drawer-Badge).
- `LocalSyncViewModel` startet/stoppt **nicht** den Server direkt, sondern sendet Intents an `LocalSyncService` (`ContextCompat.startForegroundService`/`startService`) und beobachtet den gemeinsamen `LocalSyncServer.state`-`StateFlow` (Singleton) für den UI-Zustand — vermeidet doppelte Start-Logik zwischen ViewModel und Service.
- Erstnutzungs-Hinweis: `util/sync/LocalSyncFirstUseStore.kt` (eigener, schlanker SharedPreferences-Flag statt Erweiterung von `AppSettings`/`AppSettingsRepository` — bewusste Entscheidung, um das zentrale Settings-Modell nicht um ein einzelnes, feature-lokales Flag zu erweitern). Dialog erscheint einmalig vor dem ersten Start, referenziert in `LocalSyncScreen`.
- Navigation: `Screen.LocalSync` (Route `local-sync`), registriert in `AppNavHost.kt` (`infoNavGraph`, analog zu Settings/Privacy).
- Einstiegspunkt: neuer Drawer-Eintrag in `AppDrawerContent.kt` (Wifi-Icon) mit **aktiv-Badge** (kleiner gefüllter Punkt), gespeist aus `LocalSyncStatusViewModel` — zeigt live an, ob die PC-Verbindung gerade läuft, unabhängig davon, von wo sie gestartet wurde.
- Strings: alle UI-Texte (Titel, Start/Stop-Button, Anleitung, PIN-Label, Erstnutzungs-Dialog, Fehlermeldungen) in `strings_localsync.xml`, **alle 10 Locales** ergänzt (zusammen mit den Notification-Strings aus Phase 4).
- Build/Verifikation: `compileDebugKotlin`, volle `testDebugUnitTest`-Suite, `assembleDebug` — alle grün.
- **Nicht möglich in dieser Session:** Es war kein Android-Gerät/Emulator verbunden, daher konnte die Oberfläche nicht manuell in der App bedient/gesehen werden (PIN-Eingabe am PC-Browser, Drawer-Badge-Optik, Dialog-Layout etc.). Das muss vor einem Merge/Release nachgeholt werden — siehe Phase 7.

### Phase 6 — Lokalisierung & Dokumentation: **abgeschlossen**

- **Erweiterung gegenüber dem ursprünglichen Plan:** Zusätzlich zu den nativen Android-UI-Strings (Phase 4/5) wurden auch die **serverseitig gerenderten HTML-Seiten** (Login-Formular, Sperr-Seite, Archiv-/Upload-Seite, alle Fehlermeldungen) vollständig lokalisiert — auf Nutzerentscheidung hin, da hartkodiertes Deutsch dort der CLAUDE.md-Regel "keine Literal-Strings" widersprochen hätte und inkonsistent zur sonst 10-sprachigen App gewesen wäre.
  - `util/sync/LocalSyncHtml.kt` und `util/sync/LocalSyncRouting.kt` nehmen jetzt einen `ResourceProvider` entgegen (der bereits Android-Context-freie Port, kein Bruch der Testbarkeit); `KtorLocalSyncServer` injiziert und reicht ihn durch.
  - 20 weitere Strings (`local_sync_web_*`, inkl. `local_sync_web_html_lang` für das `<html lang="...">`-Attribut) in allen 10 Locales ergänzt — macht zusammen mit den Phase-4/5-Strings **38 Strings pro Locale**, Anzahl über alle 10 Dateien hinweg verifiziert (identisch 38 je Datei).
  - `LocalSyncRoutingTest` und `LocalSyncUploadInstrumentedTest` entsprechend angepasst (JVM-Tests nutzen `FakeResourceProvider` aus `testutil/`, Instrumentation-Test nutzt echten `AndroidResourceProvider`).
- `docs/privacy-policy.html`, Abschnitt 4 (EN + DE): Aussage "App fordert keine Internet-Berechtigung an" korrigiert (war ab jetzt schlicht falsch) und durch neuen Unterabschnitt "Local Wi-Fi PC connection" / "Lokale WLAN-PC-Verbindung" ersetzt — beschreibt Lokalitätsgarantie, PIN-Schutz, Rate-Limiting, automatisches Stoppen und den bewussten Verzicht auf TLS.
- `docs/Bedienungsanleitung.md`: neuer Abschnitt **„PC-Verbindung (lokaler WLAN-Transfer)"** (vor „Backup erstellen", da thematisch verwandt), Bullet in „Datenschutz auf einen Blick" ergänzt, FAQ-Antwort „Werden meine Dokumente in der Cloud gespeichert?" um den Hinweis auf die rein lokale PC-Verbindung erweitert.
- **Nicht Teil dieser Session:** Play-Console-Data-Safety-Formular-Abgleich (Play-Console-Zugriff nicht Teil dieser Umgebung) — laut plan1.md-Bewertung ohnehin nur mit dem Konto-Verantwortlichen abzustimmen, da kein Drittanbieter-/Google-Server involviert ist.
- Build/Verifikation nach Lokalisierung: `compileDebugKotlin`/`compileDebugUnitTestKotlin`/`compileDebugAndroidTestKotlin`, volle `testDebugUnitTest`-Suite, `lintDebug`, `assembleDebug` — alle grün.

### Phase 7 — Härtung & Geräte-Tests: **teilweise abgeschlossen (auf einem Gerät verifiziert, Multi-OEM-Test steht aus)**

Ein Gerät (Samsung SM-A536B, Android, Systemsprache Deutsch) war während der Session verbunden — damit konnte deutlich mehr geprüft werden als ursprünglich für diese Session erwartet:

- **Packaging-Fix:** `connectedDebugAndroidTest` schlug zunächst mit META-INF-Duplikat-Konflikten fehl (`META-INF/DEPENDENCIES` von `httpclient5`/`httpcore5`, danach `META-INF/AL2.0` von `jna`/`jna-platform`) — beides transitive Testabhängigkeiten von `ktor-server-test-host`. Behoben über einen `packaging { resources { excludes += ... } }`-Block in `app/build.gradle.kts` (betrifft nur den androidTest-APK-Build, nicht die Release-App).
- **`LocalSyncUploadInstrumentedTest` erfolgreich auf echtem Gerät ausgeführt** (beide Tests grün): Upload über den echten `ImportFileUseCase`/`ScanRepository`/`PdfEditor`-Stack, danach Download-Roundtrip. Bestätigt nebenbei, dass die Lokalisierung tatsächlich greift: Auf dem deutschsprachigen Testgerät erschien die Erfolgsmeldung korrekt als „wurde importiert" statt der in der JVM-Testsuite verwendeten englischen Fallback-Strings — die Testassertion wurde entsprechend geräteunabhängig umgebaut (baut die Erwartung über den echten `ResourceProvider` statt über einen hartkodierten String).
- **Echter End-to-End-Test über das reale LAN:** APK installiert, App gestartet, PC-Verbindung über die UI gestartet — der Windows-Rechner dieser Session konnte die reale, laufende Instanz erreichen: Login-Seite abgerufen, mit der angezeigten PIN eingeloggt (Session-Cookie korrekt gesetzt: `HttpOnly`, `SameSite=Strict`), Archiv-Seite abgerufen, falsche PIN korrekt abgelehnt — alles über `curl` gegen `http://<Geräte-IP>:8080` verifiziert, nicht nur über automatisierte Tests.
- **Echter UI-Bug gefunden und behoben:** `LocalSyncScreen` hatte eine eigene `Scaffold`+`TopAppBar`, wodurch beim Öffnen der Seite **zwei Titelleisten übereinander** erschienen. Sichtbar erst durch den Screenshot-Vergleich mit `SettingsScreen`/`PrivacyScreen` (die keine eigene Scaffold/TopAppBar haben, weil die äußere Shell in `AppNavigation.kt` bereits Zurück-Button + Titel für alle Routen im selben Nav-Graph rendert, siehe `AppBarTitle.kt`). Behoben durch Entfernen der eigenen Scaffold/TopAppBar aus `LocalSyncScreen`, Ergänzung von `Screen.LocalSync` in `AppBarTitle.kt`s Titel-Mapping, Anpassung von `AppNavHost.kt` (kein `onNavigateBack`-Parameter mehr nötig). Dieser Fehler wäre bei reiner Code-Review ohne visuellen Gerätetest wahrscheinlich unentdeckt geblieben.
- **Nutzerwunsch während der Session ergänzt:** Teilen-Button direkt neben der URL-Anzeige im laufenden Zustand (`RunningContent` in `LocalSyncScreen.kt`) — öffnet den Standard-Android-Share-Sheet (`Intent.ACTION_SEND`, `text/plain`) mit URL und PIN in einem Text, damit der Link z. B. per E-Mail oder WhatsApp an die Person am PC geschickt werden kann, ohne abzutippen. Neuer String `local_sync_share_text` in allen 10 Locales ergänzt (39 Strings/Locale, erneut geprüft).
- Drawer- **und** NavigationRail-Eintrag ergänzt (`AppNavigation.kt`) — Tablet-/Breitbildschirm-Fall war zunächst übersehen worden, da nur der Drawer-Pfad in Phase 5 getestet wurde.
- Build/Verifikation (alle grün, final auf dem Gerät + lokal): `compileDebugKotlin`, volle `testDebugUnitTest`-Suite, `connectedDebugAndroidTest` (Formular- **und** Sync-Instrumentation-Tests, kein Regressions-Schaden durch das Packaging-Update), `lintDebug`, `assembleDebug`.
- **Nicht Teil dieser Session (echtes Restrisiko, nicht nur Zeitmangel):**
  - Nur **ein** Gerät/Hersteller getestet (Samsung/One UI). Die im Bewertungsteil genannte Sorge um aggressive Foreground-Service-Terminierung durch andere OEM-Skins (Xiaomi/MIUI insbesondere) ist **nicht** verifiziert.
  - Verhalten bei Netzwerkwechsel während laufender Verbindung (WLAN aus, Hotspot an/aus) nicht getestet.
  - Rate-Limiting/Hard-Stop-Verhalten (5/20-Fehlversuche-Schwellen) nur in Unit-/Routing-Tests, nicht manuell gegen den echten Server verifiziert.
  - `POST_NOTIFICATIONS`-Laufzeit-Permission-Anfrage (API 33+) ist im Manifest deklariert, aber **kein** Runtime-Request-Flow dafür implementiert — auf dem Testgerät wurde die Notification deshalb vom System stillschweigend unterdrückt ("Suppressing notification ... by user request", siehe Logcat). Funktional unkritisch (Foreground Service läuft trotzdem weiter), aber UX-Lücke: Nutzer sehen aktuell keine Notification und damit keinen Weg, über die Notification zu stoppen, es sei denn die Permission ist bereits anderweitig erteilt.
