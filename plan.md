# Implementierungsplan: Überarbeitete Menüstruktur

Stand: 2026-03-27 | Basis: planClaude.md + planGemini.md + planCodex.md + Codeanalyse

---

## Zusammenfassung der Findings

Alle drei Analysen sind sich in den Kernpunkten einig:

| Problem | Schwere | Konsens |
|---------|---------|---------|
| Kaskadierte Submenüs in `ScanItem` (kein M3) | Kritisch | Alle 3 |
| `ScanAction.Highlight` nicht im Menü erreichbar | Kritisch | Claude + Codex |
| `ManageSearch`-Icon für gegensätzliche Operationen | Hoch | Claude + Codex |
| BulkActionBar icon-only ohne Labels | Hoch | Alle 3 |
| Drawer: "Scanner starten" redundant zum FAB | Mittel | Alle 3 |
| Drawer: Kein App-Header | Mittel | Claude + Gemini |
| SelectionTitleBar: "2/10" mehrdeutig | Niedrig | Claude + Codex |
| Drawer-Geste auf Edit-Screens aktiv | Niedrig | Codex |

---

## Scope und Nicht-Scope

**Im Scope (dieser Plan):**
- A: `ScanItem.kt` — DropdownMenu → ModalBottomSheet mit Sektionen
- B: `AppNavigation.kt` — Drawer-Header + "Scanner starten" entfernen + Drawer-Geste einschränken
- C: `BulkActionBar.kt` — Icon + Label-Layout
- D: `SelectionTitleBar.kt` — "X ausgewählt" statt "X/Y"
- E: `HomeScreen.kt` — `Highlight`-Navigation anschließen + `onNavigateToHighlight` ergänzen

**Explizit nicht im Scope (zu invasiv / separates Ticket):**
- Long-Press für Auswahlmodus (ändert Kern-UX-Konvention, braucht eigene Planungsrunde)
- Checkboxen nur im Auswahlmodus zeigen (hängt mit Long-Press zusammen)
- Drawer vollständig durch TopAppBar-Overflow ersetzen (Codex-Vorschlag; zu groß für dieses Increment)

---

## Schritt A — ScanItem: DropdownMenus → ModalBottomSheet

### Datei: `ui/home/components/ScanItem.kt`

### Zielzustand (Struktur des Bottom Sheet)

```
╔══════════════════════════════════════╗
║  [Handle]                            ║
║  Dateiname.pdf                       ║
║  3 Seiten · 1,4 MB                   ║
╠══════════════════════════════════════╣
║  BEARBEITEN                          ║  ← SectionHeader (non-clickable)
║  🖌️  Markieren                       ║  ← NEU (war bisher fehlend!)
║  ✏️  Annotieren                      ║
║  🖊️  Unterschreiben                  ║
╠══════════════════════════════════════╣
║  SEITEN                              ║
║  🔄  Drehen                          ║  ← verschoben aus Hauptmenü
║  ↕️  Neu anordnen                    ║  ← disabled wenn pageCount < 2
║  ✂️  Aufteilen                       ║  ← disabled wenn pageCount < 2
║  🗑️  Seiten löschen                  ║  ← disabled wenn pageCount < 2
║  📄  Seiten extrahieren              ║  ← disabled wenn pageCount < 2
║  📋  Seiten duplizieren              ║
╠══════════════════════════════════════╣
║  AUSGABE                             ║
║  🔢  Seitennummern                   ║
║  💧  Textwasserzeichen               ║
║  🖼️  Als JPG exportieren             ║
║  📦  Komprimieren                    ║  ← disabled wenn encrypted || searchable
╠══════════════════════════════════════╣
║  SCHUTZ                              ║
║  🔒  PDF schützen                    ║  ← disabled wenn encrypted
║  🚫  Nutzung einschränken            ║  ← disabled wenn encrypted
║  🔓  PDF entsperren                  ║  ← disabled wenn !encrypted
║  🗝️  Passwort entfernen              ║  ← disabled wenn !encrypted
╠══════════════════════════════════════╣
║  OCR                                 ║
║  🔍✗  Textebene entfernen            ║  ← raus aus Schutz-Sektion; Icon: FindInPage (nicht ManageSearch)
║       (nur sichtbar wenn searchable) ║  ← ausblenden wenn !searchable
╚══════════════════════════════════════╝
```

### Implementierungsdetails

#### 1. State-Änderung
```kotlin
// ALT:
var menuExpanded    by remember { mutableStateOf(false) }
var subMenuPages    by remember { mutableStateOf(false) }
var subMenuSecurity by remember { mutableStateOf(false) }

// NEU:
var sheetVisible by remember { mutableStateOf(false) }
```

#### 2. MoreVert-Button bleibt, öffnet jetzt Sheet statt DropdownMenu
```kotlin
IconButton(onClick = { sheetVisible = true }) {
    Icon(Icons.Default.MoreVert, contentDescription = null)
}
```

#### 3. ModalBottomSheet außerhalb der Card (direkt in Box)
```kotlin
if (sheetVisible) {
    ModalBottomSheet(
        onDismissRequest = { sheetVisible = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        // Sheet-Inhalt (scrollbar via Column in verticalScroll)
        PdfActionSheet(
            record    = record,
            onAction  = { sheetVisible = false; onAction(it) },
            onDismiss = { sheetVisible = false }
        )
    }
}
```

#### 4. `PdfActionSheet` als eigener interner Composable in `ScanItem.kt`
```kotlin
@Composable
private fun PdfActionSheet(
    record:   ScanRecord,
    onAction: (ScanAction) -> Unit,
    onDismiss: () -> Unit
) {
    val notEncrypted = !record.isEncrypted
    val multiPage    = record.pageCount >= 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // Sheet-Header: Dateiname + Metadaten
        SheetHeader(record)

        // Sektion: BEARBEITEN
        SheetSection(titleRes = R.string.sheet_section_edit) {
            SheetItem(Icons.Default.Highlight,    R.string.action_highlight_pdf,  notEncrypted) { onAction(ScanAction.Highlight) }
            SheetItem(Icons.Default.BorderColor,  R.string.action_annotate_pdf,   notEncrypted) { onAction(ScanAction.Annotate) }
            SheetItem(Icons.Default.Draw,         R.string.action_sign_pdf,       notEncrypted) { onAction(ScanAction.Signature) }
        }

        // Sektion: SEITEN
        SheetSection(titleRes = R.string.sheet_section_pages) {
            SheetItem(Icons.AutoMirrored.Filled.RotateRight, R.string.action_rotate,         notEncrypted)           { onAction(ScanAction.Rotate) }
            SheetItem(Icons.Default.SwapVert,                R.string.action_reorder,        notEncrypted && multiPage) { onAction(ScanAction.Reorder) }
            SheetItem(Icons.Default.ContentCut,              R.string.action_split,          notEncrypted && multiPage) { onAction(ScanAction.Split) }
            SheetItem(Icons.Default.Delete,                  R.string.action_delete_pages,   notEncrypted && multiPage) { onAction(ScanAction.DeletePages) }
            SheetItem(Icons.Default.PictureAsPdf,            R.string.action_extract_pages,  notEncrypted && multiPage) { onAction(ScanAction.ExtractPages) }
            SheetItem(Icons.Default.ContentCopy,             R.string.action_duplicate_pages, true)                   { onAction(ScanAction.DuplicatePages) }
        }

        // Sektion: AUSGABE
        SheetSection(titleRes = R.string.sheet_section_output) {
            SheetItem(Icons.Default.FormatListNumbered,         R.string.action_page_numbers,     notEncrypted)                        { onAction(ScanAction.PageNumbers) }
            SheetItem(Icons.AutoMirrored.Filled.BrandingWatermark, R.string.action_text_watermark, notEncrypted)                       { onAction(ScanAction.TextWatermark) }
            SheetItem(Icons.Default.Image,                       R.string.action_export_as_jpg,   notEncrypted)                       { onAction(ScanAction.ExportAsJpg) }
            SheetItem(Icons.Default.Compress,                    R.string.action_compress_pdf,    notEncrypted && !record.isSearchable) { onAction(ScanAction.CompressPdf) }
        }

        // Sektion: SCHUTZ
        SheetSection(titleRes = R.string.sheet_section_security) {
            SheetItem(Icons.Default.Lock,               R.string.action_protect_pdf,      notEncrypted)         { onAction(ScanAction.ProtectPdf) }
            SheetItem(Icons.Default.AdminPanelSettings, R.string.action_restrict_usage,   notEncrypted)         { onAction(ScanAction.RestrictUsage) }
            SheetItem(Icons.Default.LockOpen,           R.string.action_unlock_pdf,       record.isEncrypted)   { onAction(ScanAction.UnlockPdf) }
            SheetItem(Icons.Default.NoEncryption,       R.string.action_remove_password,  record.isEncrypted)   { onAction(ScanAction.RemovePassword) }
        }

        // Sektion: OCR — nur anzeigen wenn searchable
        if (record.isSearchable && notEncrypted) {
            SheetSection(titleRes = R.string.sheet_section_ocr) {
                SheetItem(Icons.Default.FindInPage, R.string.action_remove_text_layer, true) { onAction(ScanAction.RemoveTextLayer) }
            }
        }
    }
}
```

#### 5. Hilfsfunktionen `SheetHeader`, `SheetSection`, `SheetItem`
```kotlin
@Composable
private fun SheetHeader(record: ScanRecord) {
    // Dateiname + "X Seiten · Y MB" als Subtitle
    // Padding: horizontal 24dp, vertical 12dp, bottom-Divider
}

@Composable
private fun SheetSection(titleRes: Int, content: @Composable ColumnScope.() -> Unit) {
    // Non-clickable Label in bodySmall, primaryContainer-Farbe
    // + Inhalt
}

@Composable
private fun SheetItem(icon: ImageVector, textRes: Int, enabled: Boolean, onClick: () -> Unit) {
    // ListItem-ähnliches Layout: Icon (24dp) + Text + clickable
    // wenn !enabled: alpha = 0.38f (M3-Disabled-Convention), onClick = {}
    // Höhe: 48dp (M3 touch target)
    // Padding: horizontal 24dp
}
```

#### Icon-Korrekturen
| Aktion | Altes Icon | Neues Icon | Grund |
|--------|-----------|-----------|-------|
| Textebene entfernen | `ManageSearch` | `FindInPage` | `ManageSearch` = "durchsuchbar machen" → Konflikt |
| Markieren | *(fehlte)* | `Highlight` (oder `Edit` aus material-icons-extended) | Neue Aktion |

---

## Schritt B — AppNavigation: Drawer-Header + Vereinfachung

### Datei: `ui/navigation/AppNavigation.kt`

#### B.1 "Scanner starten" entfernen
- Den `DrawerItem`-Block für `nav_start_scanner` und `PhotoCamera`-Icon ersatzlos löschen.
- Der FAB auf `Screen.Ablage` übernimmt diese Funktion vollständig.
- `scanTrigger`-State bleibt erhalten (wird nur noch vom FAB gesetzt).

#### B.2 App-Header in den Drawer einfügen
```kotlin
ModalDrawerSheet(
    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
) {
    // NEU: App-Header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter            = painterResource(R.drawable.app_icon),
            contentDescription = null,
            modifier           = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                stringResource(R.string.app_name),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.drawer_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    Spacer(Modifier.height(8.dp))

    // Ablage
    DrawerItem(Icons.Default.FolderOpen, stringResource(R.string.nav_archive), ...) { ... }

    Spacer(Modifier.height(8.dp))
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    Spacer(Modifier.height(8.dp))

    // Hilfe / Info / Datenschutz
    DrawerItem(...)
    DrawerItem(...)
    DrawerItem(...)
}
```

#### B.3 Drawer-Geste auf alle Edit-Screens deaktivieren
```kotlin
// ALT:
gesturesEnabled = currentRoute?.startsWith("annotate/") != true

// NEU: Geste nur auf Top-Level-Screen (Ablage, Help, Info, Privacy)
gesturesEnabled = currentRoute == Screen.Ablage.route
    || currentRoute == Screen.Help.route
    || currentRoute == Screen.Info.route
    || currentRoute == Screen.Privacy.route
    || currentRoute == null
```

#### B.4 `onNavigateToHighlight` in `HomeScreen`-Aufruf ergänzen
```kotlin
composable(Screen.Ablage.route) {
    HomeScreen(
        ...
        onNavigateToAnnotate  = { scanId -> navController.navigate(Screen.Annotate.createRoute(scanId)) },
        onNavigateToHighlight = { scanId -> navController.navigate(Screen.Highlight.createRoute(scanId)) }  // NEU
    )
}
```

---

## Schritt C — BulkActionBar: Icon + Text-Labels

### Datei: `ui/home/components/BulkActionBar.kt`

#### Layout-Änderung: Icon + Label unter jedem Button

```kotlin
// Jeder Button wird zu einem Column(Icon + Text) in einem IconButton-ähnlichen Box
@Composable
private fun BulkAction(
    icon:        ImageVector,
    labelRes:    Int,
    enabled:     Boolean = true,
    tint:        Color   = LocalContentColor.current,
    onClick:     () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.38f)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(labelRes),
            style    = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color    = tint
        )
    }
}
```

#### Icon-Korrekturen in BulkActionBar
| Funktion | Altes Icon | Neues Icon | Grund |
|----------|-----------|-----------|-------|
| Zusammenführen | `CallMerge` | `MergeType` | Semantisch passender für PDF-Merge |
| Durchsuchbar machen | `ManageSearch` | `FindInPage` | Gleicher Konflikt wie in ScanItem — FindInPage ist neutral |

#### Neue Signatur (Parameter unverändert, nur Layout)
```kotlin
@Composable
internal fun BulkActionBar(
    onShare:               () -> Unit,
    onExport:              () -> Unit,
    onExtractTexts:        () -> Unit,
    onMakeSearchable:      () -> Unit,
    onMerge:               () -> Unit,
    onDelete:              () -> Unit,
    extractEnabled:        Boolean  = true,
    makeSearchableEnabled: Boolean  = true,
    mergeEnabled:          Boolean  = false,
    modifier:              Modifier = Modifier
)
```

---

## Schritt D — SelectionTitleBar: Klarerer Text

### Datei: `ui/home/components/SelectionTitleBar.kt`

```kotlin
// ALT: stringResource(R.string.selection_count_fraction, count, total)
// → gibt "2/10" aus → mehrdeutig

// NEU: neuer String "selection_count_label" → "2 ausgewählt"
Text(
    stringResource(R.string.selection_count_label, count),
    style      = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold
)
```

String-Ressource: `selection_count_label` = `"%1$d ausgewählt"` (+ alle 10 Locale-Dateien)

`selection_count_fraction` kann vorerst erhalten bleiben (evtl. noch anderswo verwendet — prüfen).

---

## Schritt E — HomeScreen: Highlight-Navigation anschließen

### Datei: `ui/home/HomeScreen.kt`

#### E.1 Parameter ergänzen
```kotlin
@Composable
fun HomeScreen(
    ...
    onNavigateToAnnotate:   (Long) -> Unit = {},
    onNavigateToHighlight:  (Long) -> Unit = {}   // NEU
)
```

#### E.2 ScanAction.Highlight im when-Block anschließen
```kotlin
// ALT:
ScanAction.Highlight -> {}  // leer / fehlend

// NEU:
ScanAction.Highlight -> onNavigateToHighlight(record.id)
```

---

## Benötigte neue String-Ressourcen

In alle 10 Locale-Dateien eintragen (`values/`, `-de`, `-es`, `-fr`, `-pt`, `-zh-rCN`, `-ar`, `-ja`, `-ru`, `-hi`):

| Key | Deutsch | Verwendung |
|-----|---------|-----------|
| `drawer_version` | `"Version %1$s"` | Drawer-Header |
| `sheet_section_edit` | `"Bearbeiten"` | Bottom-Sheet-Sektion |
| `sheet_section_pages` | `"Seiten"` | Bottom-Sheet-Sektion |
| `sheet_section_output` | `"Ausgabe"` | Bottom-Sheet-Sektion |
| `sheet_section_security` | `"Schutz"` | Bottom-Sheet-Sektion |
| `sheet_section_ocr` | `"OCR"` | Bottom-Sheet-Sektion |
| `selection_count_label` | `"%1$d ausgewählt"` | SelectionTitleBar |
| `action_highlight_pdf` | `"PDF markieren"` | Bottom Sheet + cd_ |
| `cd_bulk_share` | `"Teilen"` | BulkActionBar Label |
| `cd_bulk_export` | `"Exportieren"` | BulkActionBar Label |
| `cd_bulk_merge` | `"Zusammenführen"` | BulkActionBar Label |
| `cd_bulk_extract` | `"Text extrahieren"` | BulkActionBar Label |
| `cd_bulk_searchable` | `"Durchsuchbar"` | BulkActionBar Label |
| `cd_bulk_delete` | `"Löschen"` | BulkActionBar Label |

> Die bestehenden `cd_share`, `cd_merge` usw. bleiben für Rückwärtskompatibilität erhalten.

---

## Betroffene Dateien (Übersicht)

| Datei | Art der Änderung |
|-------|-----------------|
| `ui/home/components/ScanItem.kt` | Komplett-Refactor: 3 DropdownMenus → ModalBottomSheet |
| `ui/home/components/BulkActionBar.kt` | Icon-Korrekturen + Label-Layout |
| `ui/home/components/SelectionTitleBar.kt` | Text-Änderung (1 String) |
| `ui/home/HomeScreen.kt` | +Parameter `onNavigateToHighlight` + when-Branch |
| `ui/navigation/AppNavigation.kt` | Drawer-Header + "Scanner starten" weg + gesturesEnabled + Highlight-Route |
| `res/values*/strings.xml` (10 Dateien) | ~14 neue Strings |

---

## Reihenfolge der Implementierung

```
1. Strings ergänzen (alle 10 Locales) — Blocker für alle anderen Schritte
2. Schritt D: SelectionTitleBar (trivial, 2 Zeilen)
3. Schritt C: BulkActionBar (Icon-Fix + Layout)
4. Schritt B: AppNavigation (Header + "Scanner starten" weg + gesturesEnabled)
5. Schritt E: HomeScreen (Parameter + when-Branch)
6. Schritt A: ScanItem (Bottom Sheet — größte Änderung, zuletzt)
```

---

## Tests

### Unit-Tests: keine neuen erforderlich
- Bestehende ViewModel-Tests bleiben unberührt (keine Logikänderung)
- `HighlightPdfWorkflowTest` / `AnnotatePdfWorkflowTest` bleiben grün

### Manuelle Smoke-Tests nach Implementierung
- [ ] MoreVert → Sheet öffnet sich, alle 5 Sektionen sichtbar
- [ ] "PDF markieren" öffnet HighlightScreen
- [ ] "Textebene entfernen" nur sichtbar bei `isSearchable=true`
- [ ] Disabled-Items (encrypted, single-page) korrekt ausgegraut
- [ ] Drawer: Header mit App-Icon + Version sichtbar; kein "Scanner starten"
- [ ] Drawer-Geste auf Bearbeitungs-Screens inaktiv (z.B. SplitScreen)
- [ ] BulkActionBar: alle 6 Labels lesbar auf kleinem Screen (Pixel 6a)
- [ ] SelectionTitleBar: "3 ausgewählt" statt "3/10"
- [ ] `CallMerge` → `MergeType` Icon sichtbar geändert
- [ ] `ManageSearch` nirgends mehr für gegensätzliche Aktionen

---

## Risiken und Gegenmaßnahmen

| Risiko | Gegenmaßnahme |
|--------|---------------|
| `ModalBottomSheet` auf API 29 — `ExperimentalMaterial3Api` | Annotation `@OptIn(ExperimentalMaterial3Api::class)` in `ScanItem.kt`; in Compose BOM 2026.03.00 stabil |
| Bottom Sheet zu lang auf kleinen Screens | `verticalScroll(rememberScrollState())` innerhalb der `Column` |
| `FindInPage` nicht in `material-icons-core` | Icon aus `material-icons-extended` (bereits als Transitivdependenz via Compose BOM vorhanden) |
| `app_icon.png` im Drawer — Pfad prüfen | `R.drawable.app_icon` existiert bereits laut git status |
| `MergeType` nicht in `material-icons-core` | Ebenfalls aus `extended`; alternativ `CallMerge` behalten und nur Text korrigieren |

---

## Abgrenzung zu späteren Schritten (Out of Scope)

Diese Punkte sind dokumentiert, aber bewusst nicht Teil dieses Plans:

- **Long-Press statt Checkbox für Auswahlmodus**: Ändert das Kern-Interaktionsmodell; erfordert eigene UX-Abstimmung
- **Checkboxen nur im Auswahlmodus**: Abhängig von Long-Press-Entscheidung
- **Drawer durch TopAppBar-Overflow ersetzen**: Strukturelle Navigation; eigenes Ticket wenn App wächst
- **Tooltips via `TooltipBox`**: Nice-to-have; kann nach diesem Batch ergänzt werden
- **`Komprimieren` ausblenden statt disabled**: Verhaltensänderung; nutzer könnten es vermissen
