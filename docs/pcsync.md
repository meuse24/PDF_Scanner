# Umsetzungsplan: PC-Sync — CPU-/Lifecycle-Härtung und konfigurierbare Abschaltdauer

**Status:** umgesetzt (Phasen 1–4 und 6 vollständig, Phase 5 JVM-seitig; Phase 0 und die Geräte-Verifikation stehen aus — siehe § 8).
**Bezug:** Folgephase zu `docs/local-wifi-pc-sync.md` (dort Phasen 1–7, abgeschlossen). Dieses Dokument beschreibt Phase 8.
**Anlass:** Auf einem Samsung SM-A536B meldete das System „Hohe CPU-Nutzung" für PDF Scan. Gemessen: 97–100 % CPU auf einem einzelnen `DefaultDispatcher`-Thread, Foreground-Service seit 7 h 49 min aktiv, davon ~2 h 55 min CPU-Zeit — ein großer Anteil bei ausgeschaltetem Bildschirm.
**Aufwandsschätzung:** 2–3 Personentage inkl. Tests, Lokalisierung und Doku.

---

## 1. Ausgangslage

### 1.1 Zuordnung der Last

Die Zuordnung zum PC-Sync ist belegt: `LocalSyncService` ist der einzige Foreground-Service der App (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`), es existieren keine periodischen WorkManager-Jobs. Der Thread-Name passt ebenfalls — Ktor CIO arbeitet über `Dispatchers.IO`, das sich in kotlinx.coroutines den Threadpool mit `Dispatchers.Default` teilt; dessen Threads heißen immer `DefaultDispatcher-worker-N`. Ein heißer Thread dieses Namens deutet auf den CIO-Selector, nicht auf UI-, Render- oder OCR-Pfade.

### 1.2 Bestätigte Defekte (Code-Review)

| # | Ort | Defekt |
|---|---|---|
| **D1** | `LocalSyncService.kt:102` | `val idleMillis = localSyncServer.millisSinceLastActivity() ?: break` — der Port liefert `null`, sobald der State nicht `Running` ist (`KtorLocalSyncServer.kt:122`). Der Netzwerk-Watch stoppt den Server bei IP-Verlust selbständig und setzt `Stopped`. Der Watchdog bricht dann per `break` ab, **ohne `stopSelf()` aufzurufen**. Die State-Beobachtung in Zeile 96 reagiert ausschließlich auf `Error`, nicht auf `Stopped`. Es existiert somit **kein Pfad**, der den Foreground-Service beendet, wenn der Server von sich aus stoppt. Notification und Prozess bleiben unbegrenzt bestehen. → Erklärt die 7 h 49 min Laufzeit. |
| **D2** | `KtorLocalSyncServer.kt:87-90` | Der Aktivitäts-Interceptor sitzt auf `ApplicationCallPipeline.Call`, also **vor jeder Autorisierungsprüfung**. Jeder Fremd-Request setzt `lastActivityMillis` zurück: `favicon.ico`, 404er, Portscans von Router/NAS/Sicherheits-Appliances auf 8080, eine offen gelassene Browser-Seite. In einem belebten LAN kann der 20-Minuten-Timeout faktisch nie ablaufen. → Zweite, unabhängige Erklärung für die Laufzeit. |
| **D3** | `KtorLocalSyncServer.kt:149-157` | `NetworkRequest.Builder().build()` registriert **ohne Capability-Filter für alle Netze**; überwacht werden zusätzlich `onCapabilitiesChanged` und `onLinkPropertiesChanged`, die bei jedem RSSI-/Link-Speed-Update feuern — auf WLAN und Mobilfunk teils im Sekundentakt. Jedes Event ruft synchron auf dem Binder-Thread `findLocalIPv4Address()` auf: `NetworkInterface.getNetworkInterfaces()` plus pro Interface je ein `isUp`/`isLoopback`/`isVirtual` — jeweils ein eigener ioctl. Dauerhafter Syscall-Sturm, auch bei Screen-off. |
| **D4** | `LocalSyncService.kt:50` + `KtorLocalSyncServer.kt:168` | Der Service-Scope läuft auf `Dispatchers.Main.immediate`, `stopInternal()` ruft darin das **blockierende** `engine?.stop(gracePeriodMillis = 200, timeoutMillis = 1000)`. Bis zu 1,2 s UI-Block beim Trennen, ANR-Risiko. Zusätzlich fehlt jeder Guard gegen parallele `stop()`-Aufrufe aus mehreren Netzwerk-Callbacks; `stopInternal()` ist nicht synchronisiert. |
| **D5** | `KtorLocalSyncServer.kt:86` | `connectionIdleTimeoutSeconds` ist für die CIO-Engine nirgends gesetzt. Halbtote Keep-Alive-Sockets (PC im Standby, WLAN im Doze) bleiben dadurch beliebig lange in der Selector-Menge. |

### 1.3 Nicht belegt

Die eigentliche Ursache der **97–100 % Dauerlast** ist aus dem Code allein nicht bewiesen. D1 und D2 erklären die *Laufzeit*, nicht die *Last*; D3 erzeugt spürbare, aber normalerweise keine sättigende Last. Die naheliegende Hypothese ist ein NIO-Selector-Busy-Loop im CIO-Engine auf halbtoten Sockets — begünstigt durch D5. 2 h 55 min CPU auf 7 h 49 min entsprechen ~37 % Durchschnitt, also einem echten Dauerspin, nicht sporadischen Callbacks.

Phase 0 klärt das messtechnisch. Wichtig: **Die Umsetzung hängt nicht am Ergebnis** — die Fixes aus Phase 1–4 beenden den Zustand in jedem Fall, auch wenn der Spin eine andere Quelle hat. Phase 0 entscheidet nur, ob zusätzlich eine Engine-Migration (siehe § 7) nötig wird.

---

## 2. Zielbild

1. Der Foreground-Service kann **strukturell nicht länger laufen als eine harte Obergrenze**, unabhängig davon, welcher Einzelmechanismus versagt.
2. Die **Abschaltdauer bei Inaktivität ist in den Einstellungen konfigurierbar, Standard 20 Minuten** — ohne Option „nie".
3. Nur **autorisierte** Zugriffe halten die Verbindung offen; Fremdverkehr auf dem Port verlängert nichts.
4. Kein blockierender Aufruf auf dem Main-Thread, keine unsynchronisierten Nebenläufigkeitspfade.
5. Die Abschaltentscheidung ist eine **framework-freie, unit-testbare Domain-Funktion** — keine Zeitlogik mehr verstreut im Android-Service.

---

## 3. Clean-Architecture-Schnitt

Die heutige Zeit-/Abschaltlogik liegt vollständig im `LocalSyncService` (Android-Framework-Schicht) und ist damit nur instrumentiert testbar. Der Umbau zieht sie in die Domain und lässt im Service nur die Ausführung zurück.

### 3.1 Neue und geänderte Artefakte

| Schicht | Artefakt | Rolle |
|---|---|---|
| `domain/model/` | **`LocalSyncActivity.kt`** (neu) | `data class LocalSyncActivity(idleMillis, runtimeMillis, activeRequests)` — atomarer Aktivitäts-Schnappschuss des Servers. Ersetzt den heutigen Einzelwert. |
| `domain/model/` | **`LocalSyncTimeout.kt`** (neu) | Erlaubte Minutenwerte, Default, harte Obergrenze — eine Quelle der Wahrheit für UI, Persistenz und Policy. |
| `domain/model/AppSettings.kt` | geändert | `+ localSyncIdleTimeoutMinutes: Int = LocalSyncTimeout.DEFAULT_MINUTES` |
| `domain/repository/AppSettingsRepository.kt` | geändert | `+ fun updateLocalSyncIdleTimeoutMinutes(minutes: Int)` |
| `domain/common/` | **`LocalSyncShutdownPolicy.kt`** (neu) | **Pure Funktion** `evaluateLocalSyncShutdown(...): LocalSyncShutdownReason?`. Kein Android, keine Uhr, keine Coroutines — vollständig JVM-testbar. Platzierung analog zu `domain/common/LocalNetworkAddress.kt`, das aus demselben Feature bereits nach diesem Muster ausgelagert ist. |
| `domain/gateway/LocalSyncServer.kt` | geändert | `millisSinceLastActivity(): Long?` **entfällt**, ersetzt durch `activitySnapshot(): LocalSyncActivity?` (`null` = Server läuft nicht). Ein Port-Aufruf statt drei Einzelwerte, damit Idle-, Laufzeit- und Transfer-Zustand garantiert zum selben Zeitpunkt gehören. |
| `data/repository/SettingsRepository.kt` | geändert | Implementierung der neuen Update-Methode nach bestehendem Muster (`if (unverändert) return`, speichern, `_settings` setzen). |
| `util/AppSettingsPreferences.kt` | geändert | Neuer Key `local_sync_idle_timeout_minutes`, Normalisierung auf die **geschlossene** Whitelist beim Laden **und** Speichern. |
| `util/sync/KtorLocalSyncServer.kt` | geändert | Mutex, IO-Dispatcher, CIO-Idle-Timeout, gefilterter Netzwerk-Callback, Aktivitäts-Tracking mit Transferzähler. |
| `util/sync/LocalSyncRouting.kt` | geändert | Neuer Parameter `onAuthorizedActivity: () -> Unit`. |
| `util/sync/LocalSyncService.kt` | geändert | Watchdog ruft nur noch die Domain-Policy auf und führt deren Ergebnis aus; `onTimeout()` für API 35+. |
| `ui/settings/` | geändert | `SettingsScreen` + `SettingsViewModel`: Dropdown analog zu App-Lock-Timeout. |
| `ui/sync/LocalSyncScreen.kt` | geändert | Zeigt die eingestellte Abschaltdauer im laufenden Zustand. |
| `res/values*/` | geändert | Neue Strings in allen 10 Locales. |

### 3.2 Domain-Vertrag

```kotlin
// domain/model/LocalSyncTimeout.kt
object LocalSyncTimeout {
    const val DEFAULT_MINUTES = 20
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 60

    /**
     * Harte Obergrenze für einen Serverlauf, bewusst NICHT konfigurierbar.
     * Zweite, vom Idle-Timeout unabhängige Sicherung: selbst wenn Aktivität
     * dauerhaft frisch gehalten wird, endet der Lauf hier zwangsweise.
     */
    const val MAX_SESSION_RUNTIME_MINUTES = 120

    val SELECTABLE_MINUTES = listOf(5, 10, 20, 30, 60)

    /**
     * Die Persistenz darf keine Zwischenwerte (z. B. 17 Minuten) etablieren:
     * nur die im UI angebotenen Werte sind gültig. Ungültige oder alte Werte
     * fallen sicher auf den Standard zurück.
     */
    fun normalize(minutes: Int): Int =
        minutes.takeIf { it in SELECTABLE_MINUTES } ?: DEFAULT_MINUTES
}
```

```kotlin
// domain/model/LocalSyncActivity.kt
data class LocalSyncActivity(
    val idleMillis: Long,
    val runtimeMillis: Long,
    val activeRequests: Int
)
```

```kotlin
// domain/common/LocalSyncShutdownPolicy.kt
sealed interface LocalSyncShutdownReason {
    data object AppLocked : LocalSyncShutdownReason
    data object ServerNotRunning : LocalSyncShutdownReason
    data object IdleTimeout : LocalSyncShutdownReason
    data object MaxRuntimeExceeded : LocalSyncShutdownReason
}

/**
 * Entscheidet, ob der PC-Sync beendet werden muss. Rein funktional: keine Uhr,
 * kein Framework, keine Seiteneffekte — alle Zeitwerte kommen als Parameter herein.
 *
 * Reihenfolge ist bewusst: App-Lock und "Server läuft nicht mehr" schlagen vor jeder
 * Zeitbetrachtung zu; die harte Laufzeitgrenze schlägt vor dem Idle-Timeout zu, damit
 * ein laufender Transfer sie nicht aushebeln kann.
 */
fun evaluateLocalSyncShutdown(
    appLocked: Boolean,
    activity: LocalSyncActivity?,
    idleTimeoutMillis: Long,
    maxRuntimeMillis: Long
): LocalSyncShutdownReason? = when {
    appLocked -> LocalSyncShutdownReason.AppLocked
    activity == null -> LocalSyncShutdownReason.ServerNotRunning
    activity.runtimeMillis >= maxRuntimeMillis -> LocalSyncShutdownReason.MaxRuntimeExceeded
    activity.activeRequests > 0 -> null
    activity.idleMillis >= idleTimeoutMillis -> LocalSyncShutdownReason.IdleTimeout
    else -> null
}
```

Der Service reduziert sich damit auf: Schnappschuss holen → Policy fragen → bei Ergebnis ≠ `null` herunterfahren. Keine Zeitarithmetik mehr im Android-Code.

---

## 4. Phasenplan

### Phase 0 — Diagnose des CPU-Spins (0,25 Tage, parallel möglich)

Ziel: belegen, ob der heiße Thread der CIO-Selector ist. Blockiert die übrigen Phasen **nicht**.

1. PC-Sync auf dem Gerät starten, Browser-Tab am PC offen lassen, Display ausschalten, ~20 min warten.
2. PID ermitteln und Thread-Dump auslösen:
   ```bash
   adb shell pidof info.meuse24.pdf_scanner
   adb shell run-as info.meuse24.pdf_scanner kill -3 <pid>
   adb logcat -d | grep -A 40 "DefaultDispatcher-worker"
   ```
3. Ergänzend die Last pro Thread:
   ```bash
   adb shell top -H -p <pid> -n 1 -b
   ```
4. Ergebnis in § 8 dieses Dokuments protokollieren.

**Entscheidungspunkt:** Zeigt der Stack `SelectorManager`/`ActorSelectorManager`/`selectWakeup` und bleibt die Last nach Phase 1–4 bestehen, greift die Eskalation in § 7.

### Phase 1 — Domain: Modelle, Policy, Settings (0,5 Tage)

- `LocalSyncTimeout`, `LocalSyncActivity`, `LocalSyncShutdownPolicy` anlegen (Code siehe § 3.2).
- `AppSettings` um `localSyncIdleTimeoutMinutes` erweitern; `AppSettingsRepository`-Port um `updateLocalSyncIdleTimeoutMinutes`.
- `SettingsRepository` und `AppSettingsPreferences` implementieren, jeweils mit `LocalSyncTimeout.normalize()` beim Laden **und** Speichern — damit ein manipulierter oder aus einer früheren Version stammender Wert nie zu einem unbegrenzten Timeout führen kann.
- `LocalSyncServer`-Port: `millisSinceLastActivity()` durch `activitySnapshot()` ersetzen.

Domain-Grenze prüfen: keine der neuen Domain-Dateien importiert Android-, AndroidX-, Ktor- oder `util/`-Typen.

### Phase 2 — Server-Härtung (`KtorLocalSyncServer`) (0,75 Tage)

1. **Aktivitäts- und Transfer-Tracking umbauen (behebt D2):**
   - Interceptor auf `ApplicationCallPipeline.Call` **entfernen**.
   - Stattdessen meldet `localSyncRouting` Aktivität ausschließlich dann, wenn ein Request autorisiert war (gültiges Session-Cookie) oder ein Login erfolgreich war.
   - Der Transferzähler wird **nicht** global im `ApplicationCallPipeline.Monitoring` geführt: Sonst könnte auch ein nicht autorisierter Request kurzzeitig den Timeout aushebeln. Stattdessen umschließt ein Routing-Helper nur die autorisierten Upload- und Download-Handler mit `beginTransfer()`/`endTransfer()` in `try/finally`. Damit läuft ein großer Transfer nicht in den Idle-Timeout, obwohl währenddessen kein neuer Request eintrifft.
2. **Monotone Zeitbasis:** `lastActivityMillis` und ein neuer `startedElapsedRealtime` auf `SystemClock.elapsedRealtime()` umstellen. `System.currentTimeMillis()` ist anfällig für NTP-/Zeitzonensprünge, die den Timeout entweder aushebeln oder verfrüht auslösen. `LocalSyncSession.startedAt` bleibt Wall-Clock (reine Anzeige).
3. **Netzwerk-Callback entschärfen (behebt D3):**
   - `onCapabilitiesChanged` **streichen** — das ist der Spammer; ein RSSI-Update ändert keine IP-Adresse.
   - `onLost`, `onAvailable` und `onLinkPropertiesChanged` behalten.
   - `NetworkRequest` auf `addTransportType(NetworkCapabilities.TRANSPORT_WIFI)` einschränken (kein `NET_CAPABILITY_INTERNET`, damit Hotspot-/Wi-Fi-Direct-Setups weiter funktionieren).
   - Prüfung debouncen (min. 2 s zwischen zwei `findLocalIPv4Address()`-Aufrufen) und auf `serverScope` ausführen statt synchron auf dem Binder-Thread.
   - Registrierung mit eigenem `Handler` (HandlerThread), damit Callbacks den Binder-Pool nicht belasten.
4. **Nebenläufigkeit absichern (behebt D4):**
   - `Mutex` um `start()` und `stopInternal()`; `stopInternal()` wird reentranz-fest und idempotent.
   - `engine.stop(...)` in `withContext(Dispatchers.IO)` kapseln — die Implementierung kennt ihre Blockier-Eigenschaft, also gehört der Dispatcher-Wechsel hierher und nicht in den Aufrufer.
   - Grace-Periode auf `gracePeriodMillis = 500, timeoutMillis = 2000` anheben, damit ein laufender Download nicht mitten im Schreiben abreißt.
5. **CIO-Idle-Timeout setzen (adressiert D5):**
   ```kotlin
   embeddedServer(
       CIO,
       configure = {
           connectionIdleTimeoutSeconds = 60
           reuseAddress = true
       },
       port = LOCAL_SYNC_PORT,
       host = ip
   ) { ... }
   ```
   Halbtote Keep-Alive-Verbindungen werden damit zwangsweise geschlossen, statt beliebig lange in der Selector-Menge zu verbleiben.
6. `activitySnapshot()` implementieren: liefert `null`, wenn der State nicht `Running` ist, sonst Idle-Zeit, Laufzeit und `activeRequests` in einem Zug. Die zugrunde liegenden Zeitstempel und der Transferzähler werden dabei unter derselben Synchronisation wie Start/Stop gelesen bzw. in einem unveränderlichen `AtomicReference`-Zustand gehalten; drei voneinander unabhängige Atomics wären ausdrücklich **kein** konsistenter Schnappschuss.

### Phase 3 — Service-Lifecycle (`LocalSyncService`) (0,5 Tage)

1. **Watchdog auf die Domain-Policy umstellen (behebt D1):**
   - Die `break`-Zweige entfallen ersatzlos. Liefert `activitySnapshot()` `null`, ist das über `ServerNotRunning` ein **Abschaltgrund**, kein Grund die Schleife zu verlassen.
   - Der Timeout-Wert kommt pro Durchlauf aus `AppSettingsRepository.settings.value.localSyncIdleTimeoutMinutes` — eine Änderung in den Einstellungen wirkt damit auf die laufende Sitzung, ohne dass sie neu gestartet werden muss.
   - Prüfintervall bleibt 30 s.
2. **App-Lock-Beobachtung** bleibt, wird aber ebenfalls über die Policy geführt, damit es genau eine Entscheidungsstelle gibt.
3. **Zusätzlicher, vom Watchdog unabhängiger Fallback:** ein einmaliger `AlarmManager`-Weckruf auf `MAX_SESSION_RUNTIME_MINUTES`, der über einen nicht exportierten `BroadcastReceiver` den bereits laufenden Service stoppt. Begründung: Wenn der Watchdog-Coroutine-Job stirbt — exakt das Muster von D1 —, greift keine Prüfung, die nur in diesem Job lebt. Alarm wird beim regulären Stop und in `onDestroy()` gecancelt.
   - Der harte 2-Stunden-Grenzwert bleibt primär die Watchdog-Policy. Für einen *exakt* in Doze zugestellten Alarm benötigt `setExactAndAllowWhileIdle()` ab Android 12 die spezielle Berechtigung `SCHEDULE_EXACT_ALARM`; sie ist für neue Installationen standardmäßig verweigert und für eine Dokumenten-App Play-policy-sensitiv. Daher keine stillschweigende Berechtigungserweiterung: Der Plan ergänzt die Berechtigung nur nach ausdrücklicher Release-/Play-Policy-Freigabe, prüft `canScheduleExactAlarms()` und verwendet dann den exakten Alarm. Ohne Freigabe wird `setAndAllowWhileIdle()` als redundanter Best-Effort-Fallback verwendet; der In-Process-Watchdog bleibt weiterhin die verbindliche 2-Stunden-Grenze. Dieser Unterschied wird in Doku und Testprotokoll transparent festgehalten.
   - Receiver, `PendingIntent` und Alarm-Identität sind pro Service-Lauf eindeutig; ein alter Alarm darf keinen später gestarteten Lauf stoppen. Der Receiver prüft daher eine zufällige Lauf-ID gegen die aktuelle Service-Lauf-ID, bevor er `stopSelf()` auslöst.
4. **`Service.onTimeout(startId: Int, fgsType: Int)` überschreiben** (API 35+): Android 15 ruft diese Methode auf, nachdem das gemeinsame `dataSync`-Budget von sechs Stunden in 24 Stunden erschöpft ist. Der Service muss innerhalb weniger Sekunden `stopSelf()` aufrufen, sonst folgt eine `RemoteServiceException`. Der Pfad verwendet einen eigenen Grund `SystemForegroundServiceTimeout` (nicht `MaxRuntimeExceeded`, denn die 2-Stunden-Policy hat hier nicht entschieden). Die Override-Signatur ist API-35-sicher zu kapseln, z. B. durch eine API-35-Teilklasse oder `@RequiresApi(35)` auf der Methode; auf Android 14 und älter wird sie nicht aufgerufen.
5. **Kein WakeLock** — es gibt heute keinen, und es wird bewusst keiner ergänzt. Der Service soll das Gerät nicht am Schlafen hindern.
6. Scope-Frage: Der Service-Scope bleibt `Dispatchers.Main.immediate` (Lifecycle-Aufrufe wie `startForeground`/`stopForeground` gehören auf den Main-Thread); das Blockieren ist bereits in Phase 2.4 an der richtigen Stelle beseitigt.

### Phase 4 — Einstellungen-UI (0,5 Tage)

- Neue Zeile in der Gruppe **Sicherheit** (`Icons.Default.Lock`) von `SettingsScreen.kt`, direkt unter dem App-Lock-Block — thematisch verwandt, da beides den Zugriffsschutz betrifft.
- `DropdownPreference` exakt nach dem Muster von `settings_app_lock_timeout_label`:
  ```kotlin
  DropdownPreference(
      title = stringResource(R.string.settings_local_sync_timeout_label),
      value = formatLocalSyncTimeoutLabel(settings.localSyncIdleTimeoutMinutes),
      expanded = localSyncTimeoutExpanded,
      onExpandedChange = { localSyncTimeoutExpanded = it },
      options = LocalSyncTimeout.SELECTABLE_MINUTES.map { it to formatLocalSyncTimeoutLabel(it) },
      onOptionSelected = { minutes ->
          onLocalSyncIdleTimeoutMinutesChange(minutes)
          localSyncTimeoutExpanded = false
      }
  )
  ```
- Optionen: **5, 10, 20, 30, 60 Minuten**, Vorauswahl **20 Minuten**.
- **Bewusst keine Option „nie" / „unbegrenzt"** — genau dieser Zustand war der gemeldete Fehler. Die Einstellung kann den Schutz verkürzen, nie abschalten.
- Untertitel-Text erklärt die harte Obergrenze: die Verbindung endet spätestens nach 2 Stunden, unabhängig von der Auswahl.
- `LocalSyncScreen` zeigt im laufenden Zustand die eingestellte Dauer an; die Notification nennt sie ebenfalls (`local_sync_notification_text_timeout`).
- Die Zeile ist immer sichtbar (nicht wie der App-Lock-Timeout an einen Schalter gekoppelt), da PC-Sync jederzeit über den Drawer gestartet werden kann.

### Phase 5 — Tests (0,5 Tage)

**JVM-Unit-Tests (bevorzugt, siehe Teststrategie in `CLAUDE.md`):**

| Test | Fälle |
|---|---|
| `domain/common/LocalSyncShutdownPolicyTest` (neu) | App-Lock schlägt alles; `activity == null` → `ServerNotRunning`; Laufzeitgrenze schlägt vor Idle-Timeout; laufender Transfer verhindert Idle-Abschaltung; laufender Transfer verhindert die **Laufzeitgrenze nicht**; exakte Grenzwerte (`idleMillis == timeout`); Normalbetrieb → `null` |
| `domain/model/LocalSyncTimeoutTest` (neu) | `normalize()` akzeptiert exakt die fünf erlaubten Werte und fällt bei jedem anderen Wert auf 20 zurück; Default ist 20; `SELECTABLE_MINUTES` enthält exakt 5/10/20/30/60 |
| `util/sync/LocalSyncRoutingTest` (erweitern) | Unautorisierter Request meldet **keine** Aktivität; autorisierter Request und erfolgreicher Login melden Aktivität; abgelehnter Login meldet keine |
| `data/repository/SettingsRepositoryTest` bzw. `AppSettingsPreferences`-Test (erweitern) | Persistenz-Roundtrip; Clamping eines aus Preferences gelesenen Werts außerhalb des Bereichs; Default bei fehlendem Key |
| `ui/settings/SettingsViewModelTest` (erweitern, falls vorhanden) | Auswahl wird an das Repository durchgereicht |

**Instrumentation-Tests (`androidTest/`):**

| Test | Fälle |
|---|---|
| `LocalSyncLifecycleInstrumentedTest` (neu) | Mehrfaches Start/Stop hinterlässt keinen registrierten `NetworkCallback` und keine gebundene Portbelegung; paralleles `stop()` aus zwei Coroutines wirft nicht und lässt keinen Engine-Rest zurück (Regression zu D4); ausgelöster Alarm mit falscher Lauf-ID beendet keinen neuen Lauf, korrekte Lauf-ID beendet ihn |
| `LocalSyncUploadInstrumentedTest` (erweitern) | Aktivitätsmeldung während eines laufenden Uploads: `activitySnapshot().activeRequests > 0`; nicht autorisierter Request erhöht den Transferzähler nicht |

**Manuelle Verifikation auf dem SM-A536B:**
1. PC-Sync starten, Timeout in den Einstellungen auf 5 Minuten stellen, Display aus → Service muss nach spätestens ~5,5 Minuten verschwunden sein (Notification weg).
2. Während laufender Sitzung WLAN abschalten → Service muss sich beenden (D1-Regression).
3. Portscan von einem zweiten Gerät während der Wartezeit → darf den Timeout **nicht** verlängern (D2-Regression).
4. 30-minütige Sitzung mit offenem Browser-Tab am PC, Display aus, danach `adb shell top -H -p <pid>` → keine gesättigte CPU (D3/D5).
5. Android-15-Emulation des Systemlimits: `adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS info.meuse24.pdf_scanner`, dann `adb shell device_config put activity_manager data_sync_fgs_timeout_duration <kurze-testdauer>`; verifizieren, dass `onTimeout(startId, fgsType)` den Service ohne Crash beendet. Anschließend beide Testschalter zurücksetzen.

### Phase 6 — Lokalisierung und Dokumentation (0,25 Tage)

**Neue Strings, alle 10 Locales** (`values/`, `-de`, `-es`, `-fr`, `-pt`, `-zh-rCN`, `-ar`, `-ja`, `-ru`, `-hi`):

| Key | Datei | Zweck |
|---|---|---|
| `settings_local_sync_timeout_label` | `strings_localsync.xml` | Zeilentitel in den Einstellungen |
| `settings_local_sync_timeout_minutes` | `strings_localsync.xml` | Wertformat („%1$d Minuten") |
| `settings_local_sync_timeout_hint` | `strings_localsync.xml` | Hinweis auf die 2-Stunden-Obergrenze |
| `local_sync_running_timeout_hint` | `strings_localsync.xml` | Anzeige im laufenden Zustand des `LocalSyncScreen` |
| `local_sync_notification_text_timeout` | `strings_localsync.xml` | Notification-Text inkl. Abschaltdauer |

Die Settings-Strings gehören nach `strings_localsync.xml` (Feature-Datei) statt in die allgemeine `strings.xml` — konsistent mit der Regel „Feature-Strings in `strings_<feature>.xml`". Keine Literal-Strings in Kotlin.

**Dokumentation:**
- `docs/local-wifi-pc-sync.md`: neuer Abschnitt „Phase 8 — CPU-/Lifecycle-Härtung" im Fortschrittsprotokoll; der Absatz zum bekannten **Android-15**-Randfall (Zeile 212) ist zu korrigieren — die Annahme „der eigene Inaktivitäts-Timeout greift in der Praxis immer vorher" war nachweislich falsch. Dokumentieren: Android 15 meldet nach sechs Stunden `dataSync`-Budget `onTimeout(startId, fgsType)`; der Service behandelt diesen Callback und beendet sich sauber.
- `docs/Bedienungsanleitung.md`: Abschnitt „PC-Verbindung" um die neue Einstellung und die harte Obergrenze ergänzen.
- `CLAUDE.md`: `AppSettings`-Aufzählung um `localSyncIdleTimeoutMinutes` erweitern; unter den Architektur-Regeln ergänzen, dass die PC-Sync-Abschaltung ausschließlich über `evaluateLocalSyncShutdown` entschieden wird.
- `docs/privacy-policy.html` ist inhaltlich **nicht** betroffen — der Datenfluss ändert sich nicht, nur die Laufzeitbegrenzung wird strenger. Die bestehende Aussage „stoppt automatisch nach Inaktivität" bleibt korrekt; prüfen, ob dort eine feste Minutenzahl steht, die dann durch „konfigurierbar, Standard 20 Minuten" zu ersetzen wäre.

---

## 5. Reihenfolge und Verifikation

```bash
./gradlew :app:compileDebugKotlin
./gradlew testDebugUnitTest
./gradlew lint
./gradlew assembleDebug
./gradlew --% connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=info.meuse24.pdf_scanner.util.sync.LocalSyncLifecycleInstrumentedTest
```

Phasen 1 → 2 → 3 sind voneinander abhängig (Port-Signatur ändert sich in Phase 1 und wird in 2/3 genutzt). Phase 4 hängt an Phase 1. Phase 0 läuft unabhängig.

Nach `installDebug` die App per ADB starten:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n info.meuse24.pdf_scanner/.MainActivity
```

---

## 6. Bewusst außerhalb des Scopes

- **Migration der Nutzer-Einstellung aus einem Altwert** — die Einstellung existiert noch nicht; fehlender Key ergibt den Default 20.
- **Persistierung des Sync-Zustands über App-Kills hinweg** — der Service bleibt `START_NOT_STICKY`; ein automatischer Neustart würde dem Sicherheitsmodell widersprechen.
- **Konfigurierbarkeit der harten Laufzeitgrenze** — bewusst als Konstante, damit sie nicht wegkonfiguriert werden kann.
- **Ohne Release-Freigabe keine neue Exact-Alarm-Sonderberechtigung** — `SCHEDULE_EXACT_ALARM` ist für diesen Anwendungsfall nicht stillschweigend hinzuzufügen. Der Alarm-Fallback bleibt ohne diese Freigabe absichtlich best effort; der Watchdog setzt die eigentliche Zwei-Stunden-Policy durch.
- **HTTPS/TLS** — unverändert außerhalb des Scopes, siehe `docs/local-wifi-pc-sync.md`.

---

## 7. Eskalationspfad, falls die Last nach Phase 1–4 bestehen bleibt

Nur relevant, wenn Phase 0 einen Selector-Spin belegt **und** dieser das gesetzte `connectionIdleTimeoutSeconds` überlebt. In dieser Reihenfolge:

1. **Ktor-Version prüfen** — aktuell 3.5.1 (`gradle/libs.versions.toml:33`). Changelog auf CIO-Selector-Fixes durchsehen, ggf. Patch-Update.
2. **Eigener Engine-Dispatcher** — CIO über `embeddedServer(..., parentCoroutineContext = ...)` an einen dedizierten, auf 2 Threads begrenzten Executor binden. Löst den Spin nicht, begrenzt aber den Schaden auf einen Thread und entlastet den gemeinsamen `Dispatchers.Default`-Pool, der sonst auch OCR und PDF-Rendering trägt.
3. **Engine-Wechsel auf Netty** — deutlich größerer Dependency-Fußabdruck und schlechter für die APK-Größe (siehe `apk-size-optimization-report.md`), daher letzte Option.

Ohne Beleg aus Phase 0 wird keiner dieser Schritte umgesetzt.

---

## 8. Diagnose- und Fortschrittsprotokoll

### Phase 0 — Diagnose
- Status: **offen** — der PC-Sync war zum Zeitpunkt der Umsetzung bereits beendet, der Zustand also nicht mehr reproduzierbar. Der Thread-Dump steht weiterhin aus und ist beim nächsten Auftreten nachzuholen.
- Konsequenz: Die Spin-Hypothese bleibt unbestätigt. Phase 1–4 wurden wie geplant unabhängig davon umgesetzt; sie beenden den Zustand in jedem Fall über die Laufzeitgrenze, auch wenn die Last eine andere Quelle hat.

### Phase 1 — Domain: **abgeschlossen**
- `domain/model/LocalSyncTimeout.kt`, `domain/model/LocalSyncActivity.kt`, `domain/common/LocalSyncShutdownPolicy.kt` neu.
- `AppSettings.localSyncIdleTimeoutMinutes` (Standard 20), `AppSettingsRepository.updateLocalSyncIdleTimeoutMinutes`.
- `LocalSyncServer.millisSinceLastActivity()` → `activitySnapshot(): LocalSyncActivity?`.
- Domain-Grenze eingehalten: keine der neuen Domain-Dateien importiert Android-, AndroidX-, Ktor- oder `util/`-Typen.

### Phase 2 — Server-Härtung: **abgeschlossen**
- `LocalSyncActivityTracker` (neu) ersetzt den Pipeline-Interceptor; nur autorisierte Routen melden Aktivität, Upload/Download zusätzlich als laufender Transfer über `withTransfer { }`.
- Aktivitätszustand als ein atomar getauschtes `ActivityState`, damit `activitySnapshot()` einen konsistenten Zeitpunkt liefert; Zeitbasis `SystemClock.elapsedRealtime()`.
- Netzwerk-Callback: `TRANSPORT_WIFI`-Filter, `onCapabilitiesChanged` gestrichen, 2-s-Debounce, eigener `HandlerThread`, Interface-Scan auf `Dispatchers.IO`.
- `Mutex` um Start/Stop, `engine.stop()` in `withContext(Dispatchers.IO)`, Grace-Periode 500/2000 ms.
- `connectionIdleTimeoutSeconds = 60`, `reuseAddress = true`.
- **Planabweichung (technisch erzwungen):** Ktor 3.5.1 besitzt keine `embeddedServer(factory, port, host, configure, module)`-Überladung — die `port`/`host`-Varianten kennen nur `watchPaths`. Verifiziert per `javap` gegen `ktor-server-core-jvm-3.5.1.jar`. Der Server wird deshalb über `applicationEnvironment { }` plus `configure { … connector { port; host } }` aufgesetzt; nur so ist die Engine-Konfiguration überhaupt erreichbar.

#### Nachtrag aus dem Code-Review: veralteter Netzwerk-Check (P2)

`scheduleBoundAddressCheck()` kopierte die gebundene Adresse in eine lokale Variable, führte dann den vergleichsweise langsamen `findLocalIPv4Address()`-Scan auf `Dispatchers.IO` aus und rief anschließend **bedingungslos** `stop()` auf, wenn die Adresse abwich. Stoppt der Nutzer den Sync während des laufenden Scans und startet ihn neu, gehört das Urteil zum alten Lauf — der `stop()`-Aufruf beendet dann die frisch gestartete Sitzung. Enges Zeitfenster, aber realistisch bei einem Netzwerkwechsel, weil genau dann sowohl der Callback feuert als auch der Nutzer typischerweise neu verbindet.

Behoben über eine Generation pro Lauf: `boundIp: String?` wurde durch `AtomicReference<BoundRun?>` ersetzt (`BoundRun(generation, ipAddress)` — Adresse und Generation als ein atomar getauschter Wert, gleiche Technik wie beim Aktivitäts-Schnappschuss). Der Check merkt sich die Generation, für die er gestartet wurde, und verwirft sein Ergebnis unter dem `lifecycleMutex`, wenn inzwischen ein anderer Lauf aktiv ist. Die Entscheidung liegt in der reinen Funktion `isStaleAddressCheck(checkedRun, currentRun)`, abgedeckt von `StaleAddressCheckTest` (4 Fälle, inkl. Neustart auf derselben Adresse).

### Phase 3 — Service-Lifecycle: **abgeschlossen**
- Watchdog, App-Lock-Flow und Server-State-Flow rufen dieselbe `evaluateAndMaybeShutdown()`-Funktion, die ausschließlich `evaluateLocalSyncShutdown` befragt. Kein `break` mehr, `ServerNotRunning` beendet den Service.
- `ServerNotRunning` fährt bewusst ohne `localSyncServer.stop()` herunter, damit ein `Error`-State für die UI sichtbar bleibt.
- Laufzeit-Alarm mit Lauf-ID-Schutz, `Service.onTimeout(startId, fgsType)` für Android 15.
- **Planabweichung:** Der Alarm nutzt `PendingIntent.getService` mit Lauf-ID-Extra statt eines eigenen `BroadcastReceiver` — gleiche Wirkung, kein zusätzlicher Manifest-Eintrag, keine zweite Angriffsfläche. `SCHEDULE_EXACT_ALARM` wurde wie geplant **nicht** ergänzt; der Alarm ist `setAndAllowWhileIdle` und damit best effort.
- Kein WakeLock ergänzt.

### Phase 4 — Einstellungen-UI: **abgeschlossen**
- Dropdown in der Gruppe „Sicherheit", immer sichtbar, Optionen 5/10/20/30/60, Vorauswahl 20, keine Option „nie".
- `DropdownPreference` um optionalen `supportingText` erweitert (Hinweis auf die 2-Stunden-Obergrenze) — wiederverwendbar statt einer Sonderkomponente.
- `LocalSyncScreen` zeigt die eingestellte Dauer im laufenden Zustand; Notification nennt sie ebenfalls.

### Phase 5 — Tests: **abgeschlossen (JVM), Instrumentation offen**
- JVM, alle grün: `LocalSyncShutdownPolicyTest` (10), `LocalSyncTimeoutTest` (6), `LocalSyncStopRequestTest` (4), `LocalSyncRoutingTest` 12 → 17 Fälle. Volle `testDebugUnitTest`-Suite grün.
- **Planabweichung:** Der Lauf-ID-Schutz wurde als reine Funktion `shouldApplyStopRequest()` herausgezogen und im JVM-Test abgedeckt, statt ihn instrumentiert über den Hilt-Service zu prüfen — schneller, deterministischer, gleiche Aussage.
- **Planabweichung:** „Transferzähler während eines Uploads" liegt im JVM-`LocalSyncRoutingTest` (via `RecordingActivityTracker`) statt im Instrumentation-Test; dort ist auch der Fehlerfall („eine abgelehnte Datei gibt den Zähler wieder frei") prüfbar.
- Instrumentation auf dem SM-A536B, grün: `LocalSyncLifecycleInstrumentedTest` (5 Fälle: konsistenter Schnappschuss, Null-Schnappschuss nach Stop, wiederholtes Start/Stop, parallele Stops, Stop ohne Start) und `LocalSyncUploadInstrumentedTest` (2 Fälle, Regression).
- **End-to-End-Verifikation auf dem SM-A536B (belegt D1 und D2 gemeinsam):** Abschaltzeit auf 5 Minuten gestellt, PC-Sync gestartet, danach im Minutentakt ein **unautorisierter** Request (`GET /`, HTTP 200) vom PC — genau das Muster, das den Inaktivitäts-Timer vorher dauerhaft frisch gehalten hätte.

  | t | unautorisierter Request | laufende `ServiceRecord`s |
  |---|---|---|
  | 61 s | 200 | 1 |
  | 121 s | 200 | 1 |
  | 181 s | 200 | 1 |
  | 242 s | 200 | 1 |
  | 305 s | nicht erreichbar | 0 |
  | 368 s | nicht erreichbar | 0 |

  Abschaltung zwischen 242 s und 305 s, also im erwarteten Fenster (300 s Timeout + bis zu 30 s Prüfintervall). Entscheidend: Die vier beantworteten Fremd-Requests haben den Timeout **nicht** verlängert (D2), und mit dem Server verschwand auch der Foreground-Service selbst (`ServiceRecord`-Zähler 1 → 0, D1).
- Persistenz auf dem Gerät bestätigt: `shared_prefs/app_settings.xml` enthält nach der Auswahl `<int name="local_sync_idle_timeout_minutes" value="5" />`. **Hinweis:** Das Testgerät steht dadurch noch auf 5 Minuten; für den Normalbetrieb in den Einstellungen auf 20 zurückstellen.
- Voraussetzung dafür war die Deinstallation der Play-signierten Produktionsversion (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` bei abweichender Signatur) — auf ausdrückliche Freigabe des Nutzers, mit Verlust der Gerätedaten. `adb uninstall` meldete dabei `DELETE_FAILED_INTERNAL_ERROR`, entfernte das Paket aber trotzdem (bestätigt über leeres `pm path`); Ursache ist der zu dem Zeitpunkt noch laufende Foreground-Service.

### Phase 6 — Lokalisierung und Dokumentation: **abgeschlossen**
- 5 neue Strings in allen 10 Locales; der ungenutzt gewordene `local_sync_notification_text` wurde durch `local_sync_notification_text_timeout` ersetzt.
- `docs/local-wifi-pc-sync.md` um „Phase 8 — CPU-/Lifecycle-Härtung" ergänzt, `docs/Bedienungsanleitung.md` um Einstellung und Obergrenze, `CLAUDE.md` um die PC-Sync-Architekturregel.
- `docs/privacy-policy.html` geprüft: nennt keine feste Minutenzahl („after a period of inactivity"), bleibt inhaltlich korrekt — keine Änderung nötig.

### Mitbehoben: vorbestehender Lint-Blocker
`./gradlew lintDebug` schlug mit `NonObservableLocale` fehl — vorbestehend, nicht aus dieser Änderung. Lint meldet nur den jeweils ersten Fund pro Lauf; tatsächlich betraf die Regel neun Composables. Auf Freigabe des Nutzers alle umgestellt auf `LocalResources.current.configuration.locales[0]` statt `Locale.getDefault()`:
`DocumentEditSheet`, `HomeScreen` (inkl. vier `SimpleDateFormat`-Aufrufe für Import-/Scan-/Merge-Dateinamen), `SettingsScreen`, `TableExportScreen`, `TranslationReviewScreen`, `RedactScreen`, `OcrReviewScreen`, `ImagesToPdfScreen`, `ScanItem`.
Nicht angefasst: `HomeViewModel` und `OcrLanguageOptions.defaultOcrLanguage()` — keine Composables, dort ist `Locale.getDefault()` korrekt. `lintDebug` ist danach grün.

Nebeneffekt (gewollt): Diese Screens folgen jetzt einer per In-App-Sprachwahl abweichenden Locale statt der System-Locale — dieselbe Quelle, aus der `stringResource` die Texte zieht.
