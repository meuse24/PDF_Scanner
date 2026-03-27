# Menüstruktur-Analyse: M24 PDF-Scanner

Analyse der drei Menükomponenten anhand Material 3 Best Practices und Android UX-Richtlinien.
Stand: 2026-03-27

---

## 1. Navigation Drawer (Hamburger-Menü)

### Ist-Zustand (`AppNavigation.kt`)

```
[Spacer 8dp]
[HorizontalDivider]  ← Divider direkt am Anfang, kein Header!
[Spacer 8dp]
• Ablage          (FolderOpen)
• Scanner starten (PhotoCamera)
[Divider]
• Hilfe           (Help)
• Info            (Info)
• Datenschutz     (PrivacyTip)
```

### Probleme

| # | Problem | Schwere |
|---|---------|---------|
| 1 | **Kein App-Header**: Material 3 empfiehlt einen Header-Bereich (App-Icon + Name/Subtitle) am Anfang des Drawers. Der erste Divider erscheint direkt ohne visuellen Anker – der Drawer wirkt abgehackt. | Mittel |
| 2 | **"Scanner starten" als Navigationseintrag**: Eine Aktion (Scan auslösen) ist konzeptuell kein Navigationsziel. Sie „navigiert" nirgendwo hin, sondern löst einen Intent aus. Der FAB auf dem HomeScreen macht dasselbe – damit ist die Funktion dupliziert, ohne klaren Mehrwert. | Mittel |
| 3 | **Fehlende Gruppe "Über die App"**: Hilfe, Info und Datenschutz haben unterschiedliche Zwecke (Support vs. rechtlich), werden aber ohne Trennlinie gruppiert. | Gering |
| 4 | **Zu wenig Kontext für neue Nutzer**: Ein Nutzer der App, der den Drawer erstmals öffnet, sieht nur 5 Einträge ohne App-Branding. Kein Untertitel, keine Version, kein Icon. | Gering |

### Vorschläge

```
┌─────────────────────────────────┐
│  [App-Icon]  PDF Scan           │  ← ModalDrawerSheet-Header
│              Version 1.x        │
├─────────────────────────────────┤
│  📁  Archiv           [aktiv]   │  ← primäre Navigation
├─────────────────────────────────┤
│  ❓  Hilfe                      │  ← sekundäre Navigation
│  ℹ️   Info                      │
│  🔒  Datenschutz                │
└─────────────────────────────────┘
```

- **"Scanner starten" entfernen** – der FAB ist dafür zuständig und ausreichend prominent. Im Drawer wirkt es wie eine Aktion zweiter Klasse. Falls gewünscht, als `ExtendedFloatingActionButton` im Drawer darstellen (M3-Pattern „Promoted Action").
- **App-Header hinzufügen**: `ModalDrawerSheet` erlaubt beliebigen Composable-Content oben – App-Icon, Name, Versionsnummer platzieren.
- **Gruppen explizit benennen** oder mit Abschnitts-Labels versehen (M3: `NavigationDrawerItem` mit `label`-Sektion), statt nur einen Divider zwischen primärer und sekundärer Navigation.

---

## 2. Dreipunkte-Menü (ScanItem – Einzeldokument)

### Ist-Zustand (`ScanItem.kt`)

**Hauptmenü (7 direkte Einträge + 2 Submenü-Öffner):**
```
• Drehen              (RotateRight)        — disabled wenn verschlüsselt
• Seitennummern       (FormatListNumbered) — disabled wenn verschlüsselt
• Textwasserzeichen   (BrandingWatermark)  — disabled wenn verschlüsselt
• PDF unterschreiben  (Draw)               — disabled wenn verschlüsselt
• PDF annotieren      (BorderColor)        — disabled wenn verschlüsselt
• PDF komprimieren    (Compress)           — disabled wenn verschlüsselt ODER searchable
• Als JPG exportieren (Image)              — disabled wenn verschlüsselt
[Divider]
→ Seitenstruktur      (Layers, Pfeil)      — disabled wenn verschlüsselt
→ Schutz & Passwort   (Security, Pfeil)
```

**Submenü „Seitenstruktur" (5 Einträge):**
```
• Aufteilen           (ContentCut)         — disabled wenn pageCount < 2
• Neuanordnen         (SwapVert)           — disabled wenn pageCount < 2
• Seiten löschen      (Delete)             — disabled wenn pageCount < 2
• Seiten extrahieren  (PictureAsPdf)       — disabled wenn pageCount < 2
• Seiten duplizieren  (ContentCopy)
```

**Submenü „Schutz & Passwort" (5 Einträge):**
```
• PDF schützen        (Lock)               — disabled wenn verschlüsselt
• Nutzung einschränken(AdminPanelSettings) — disabled wenn verschlüsselt
• PDF entsperren      (LockOpen)           — disabled wenn NICHT verschlüsselt
• Passwort entfernen  (NoEncryption)       — disabled wenn NICHT verschlüsselt
[Divider]
• Textebene entfernen (ManageSearch)       — disabled wenn !searchable || verschlüsselt
```

### Probleme

| # | Problem | Schwere |
|---|---------|---------|
| 1 | **Kaskadierendes Submenü-Pattern**: Material 3 kennt keine nativen kaskadierten Dropdown-Menüs. Das aktuelle System öffnet ein zweites `DropdownMenu` als separates Overlay – dieses positioniert sich nicht relativ zum Elterneintrag, sondern unvorhersehbar. Nutzertests zeigen regelmäßig Verwirrung bei mehrstufigen Menüs auf kleinen Screens. | Hoch |
| 2 | **"Drehen" im Hauptmenü, alle anderen Seiten-Aktionen im Submenü**: `Drehen` ist konzeptuell eine Seitenstruktur-Aktion, gehört aber ins Hauptmenü, während Split/Reorder/Delete im Submenü vergraben sind. Inkonsistente Kategorisierung verwirrt Nutzer. | Mittel |
| 3 | **"Textebene entfernen" im Sicherheits-Submenü**: Diese Aktion hat inhaltlich nichts mit Passwortschutz zu tun. Sie ist nur für OCR-durchsuchbare PDFs relevant und gehört eher in eine "Bearbeiten"-Kategorie. | Mittel |
| 4 | **`ScanAction.Highlight` fehlt im Menü**: Das sealed interface definiert `Highlight`, aber kein Menüeintrag ruft diese Aktion auf. Der `HighlightScreen` ist vollständig implementiert, aber für Nutzer über das Dreipunkte-Menü nicht erreichbar. | Hoch |
| 5 | **Gleiche Icons für entgegengesetzte Aktionen**: `ManageSearch` steht im Einzelmenü für „Textebene entfernen" und in der BulkActionBar für „Durchsuchbar machen" – exakt gegensätzliche Operationen mit demselben Icon. | Mittel |
| 6 | **Disabled-Einträge ohne Erklärung**: Z.B. „Komprimieren" ist grau wenn `isSearchable`, aber der Nutzer weiß nicht warum. Ein `Tooltip` (M3 `TooltipBox`) oder ein erläuternder Untertitel würde helfen. | Gering |
| 7 | **Zu viele Einträge im Hauptmenü**: 7 sichtbare Items + 2 Submenü-Öffner = 9 Einträge. M3-Empfehlung: max. 5–7 Items in einem DropdownMenu, danach besser ein Bottom Sheet oder separater Screen. | Mittel |
| 8 | **"Als JPG exportieren" kategorisch falsch platziert**: Export-Aktionen gehören konzeptuell zu Share/Export (wie in der BulkActionBar), nicht zu Bearbeitungsaktionen wie Wasserzeichen oder Signatur. | Gering |

### Empfohlene Neustruktur

**Option A – Flaches Menü mit klaren Gruppen (Bottom Sheet):**

Statt Cascading-Submenüs ein `ModalBottomSheet` mit gruppierten Abschnitten verwenden:

```
╔══════════════════════════════╗
║  [Handle]                    ║
║  Dateiname.pdf               ║
╠══════════════════════════════╣
║  BEARBEITEN                  ║
║  ✏️  Annotieren               ║
║  🖊️  Unterschreiben           ║
║  🖌️  Markieren (Highlight)    ║  ← fehlt aktuell!
║  💧  Wasserzeichen            ║
╠══════════════════════════════╣
║  SEITEN                      ║
║  🔄  Drehen                   ║
║  ↕️  Neuanordnen              ║
║  ✂️  Aufteilen                ║
║  🗑️  Seiten löschen           ║
║  📄  Seiten extrahieren       ║
╠══════════════════════════════╣
║  DOKUMENT                    ║
║  🔢  Seitennummern            ║
║  📦  Komprimieren             ║
║  🖼️  Als JPG exportieren      ║
╠══════════════════════════════╣
║  SCHUTZ                      ║
║  🔒  PDF schützen             ║
║  🔓  Entsperren / Passwort    ║
║  🚫  Nutzung einschränken     ║
╚══════════════════════════════╝
```

**Option B – Dropdown mit max. 2 flachen Kategorien:**

Falls Bottom Sheet nicht gewünscht: Dropdown-Menü auf max. 7 Einträge reduzieren, Kategorien als `DropdownMenuItem` mit `enabled=false` als nicht-anklickbare Section-Header darstellen (M3-Muster).

```
[Bearbeiten]          ← non-clickable Header
• Annotieren
• Unterschreiben
• Markieren           ← fehlt aktuell!
[Seiten]              ← non-clickable Header
• Drehen
• Aufteilen / Mehr…
[Dokument & Schutz]   ← non-clickable Header
• Komprimieren
• Schutz / Mehr…
```

---

## 3. BulkActionBar (Mehrfachauswahl)

### Ist-Zustand (`BulkActionBar.kt`)

```
[Share] [Download] [CallMerge] [TextSnippet] [ManageSearch] [Delete🔴]
```

6 Icon-only-Buttons, gleichmäßig verteilt (`SpaceEvenly`), kein Label-Text.

### Probleme

| # | Problem | Schwere |
|---|---------|---------|
| 1 | **Keine sichtbaren Labels**: Reine Icon-Leisten sind für neue Nutzer schwer verständlich. M3 empfiehlt bei BottomBar-ähnlichen Strukturen Text-Labels unterhalb der Icons (analog `NavigationBar`). Bei begrenztem Platz zumindest Tooltips via `TooltipBox`. | Hoch |
| 2 | **Share vs. Export unklar**: Für viele Nutzer ist der Unterschied zwischen „Teilen" (Share-Intent) und „Exportieren" (MediaStore Downloads) nicht sofort ersichtlich. Beide Icons (Share, Download) sind nebeneinander ohne Kontext. | Mittel |
| 3 | **CallMerge-Icon für „Zusammenführen"**: Das `CallMerge`-Icon (eigentlich für Anruf-Zusammenführung) ist für PDF-Merge semantisch irreführend. Besser: `MergeType`, `LibraryAdd` oder ein expliziteres PDF-spezifisches Icon. | Gering |
| 4 | **ManageSearch = „Durchsuchbar machen"** vs. **ManageSearch = „Textebene entfernen"**: Dasselbe Icon für entgegengesetzte Funktionen (s. auch Punkt 5 bei Dreipunkte-Menü). | Mittel |
| 5 | **Merge immer sichtbar, oft disabled**: Der Merge-Button ist bei Einzelauswahl disabled. Besser: erst ab 2 ausgewählten Dokumenten einblenden (oder mit klarem Tooltip „Mindestens 2 PDFs auswählen"). | Gering |
| 6 | **Keine Tooltips**: Android/Compose unterstützt `TooltipBox` (M3). Long-Press auf einen Icon-Button sollte den Funktionsname einblenden. | Mittel |

### Vorschlag

```
╔═══════════════════════════════════════════════════════╗
║                                                       ║
║  [Share]   [Export]   [Merge]   [OCR]   [🔍+]  [🗑️] ║
║  Teilen   Exportieren Zusammen  Text    Such-  Lösch. ║
║                       führen    extrah. bar          ║
║                                                       ║
╚═══════════════════════════════════════════════════════╝
```

- **Text-Labels** unter jedem Icon hinzufügen (kurz, 1–2 Wörter)
- **Share und Export** mit Tooltip-Erklärung versehen ODER in einen einzelnen Button mit Dialog zusammenfassen: „Ausgabe" → Dialog: Teilen / In Downloads speichern
- **Merge-Icon** ersetzen: `MergeType` aus Material Icons ist semantisch passender
- **ManageSearch für OCR/Searchable** durch ein eindeutigeres Icon ersetzen, z.B. `FindInPage` oder `PageSearch`

---

## 4. SelectionTitleBar

### Ist-Zustand (`SelectionTitleBar.kt`)

```
[✕]    "2/10"    [SelectAll]
```

### Probleme

| # | Problem | Schwere |
|---|---------|---------|
| 1 | **"2/10" ist mehrdeutig**: Ohne Kontext könnte es „Seite 2 von 10" oder „2 ausgewählt von 10" bedeuten. Eine Beschriftung wie „2 ausgewählt" wäre klarer. | Gering |
| 2 | **Kein "Auswahl umkehren"** (Invert): Bei großen Archiven ist die Möglichkeit, die aktuelle Auswahl umzukehren (alle bisher NICHT ausgewählten auswählen), ein häufiger Wunsch. | Gering |
| 3 | **SelectAll ohne visuelles Feedback**: Wenn bereits alle ausgewählt sind, sollte das Icon zu „DeselectAll" wechseln (oder zumindest deaktiviert sein). | Gering |

---

## 5. Übergreifende Probleme

| # | Problem | Betroffene Komponente | Schwere |
|---|---------|-----------------------|---------|
| 1 | **`ScanAction.Highlight` nicht erreichbar**: Vollständig implementierter Screen, aber kein Menüeintrag. | ScanItem Hauptmenü | Hoch |
| 2 | **Inkonsistente Icon-Bedeutung**: `ManageSearch` bedeutet je nach Kontext „Textebene entfernen" oder „Durchsuchbar machen". | ScanItem + BulkActionBar | Mittel |
| 3 | **Keine Tooltips / Long-Press-Labels**: Weder in der BulkActionBar noch im Hamburger-Menü gibt es Hilfetexte beim Long-Press. | Alle | Mittel |
| 4 | **Kein visuelles Feedback für Disabled-Gründe**: Grau = „nicht verfügbar", aber warum? Verschlüsselt? Falsche Seitenzahl? OCR-Status? | ScanItem | Gering |
| 5 | **Kaskadierte Submenüs (nicht M3-konform)**: Zweistufige `DropdownMenu`-Kaskade positioniert sich unkontrolliert und widerspricht dem M3-Pattern. | ScanItem Submenüs | Hoch |

---

## 6. Prioritisierte Umsetzungsempfehlungen

### Priorität 1 – Kritisch

1. **`ScanAction.Highlight` in das Dreipunkte-Menü aufnehmen** (zwischen Annotieren und Unterschreiben)
2. **Icon-Konflikt `ManageSearch` auflösen** – unterschiedliche Icons für „Textebene entfernen" und „Durchsuchbar machen" verwenden
3. **Kaskadierte Submenüs ersetzen** durch Bottom Sheet oder flache Gruppen mit Section-Headern

### Priorität 2 – Wichtig

4. **BulkActionBar mit Text-Labels** versehen (oder Tooltips via `TooltipBox`)
5. **App-Header im Navigation Drawer** hinzufügen (Icon + Name + Version)
6. **"Scanner starten" aus dem Drawer entfernen** – FAB ist dafür zuständig
7. **Kategorisierung im Dreipunkte-Menü** bereinigen: „Drehen" ins Seiten-Submenü, „Textebene entfernen" aus dem Sicherheits-Submenü heraus

### Priorität 3 – Verbesserung

8. **`CallMerge` durch `MergeType`** ersetzen
9. **Disabled-Zustände mit Tooltip erklären** (`TooltipBox` aus M3)
10. **SelectAll/DeselectAll** in der SelectionTitleBar kontextabhängig wechseln
11. **Share vs. Export** in der BulkActionBar klarer unterscheiden (Tooltip oder Dialog)
