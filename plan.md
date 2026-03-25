# Implementierungsplan: Volltextsuche & On-Device Auto-Tagging

Dieses Dokument beschreibt die Strategie zur Implementierung einer globalen Suche und eines intelligenten, datenschutzkonformen Tagging-Systems.

## 1. Übersicht
*   **Volltextsuche (FTS)**: Ermöglicht das Durchsuchen aller gescannten Dokumente nach Schlagworten.
*   **Auto-Tagging**: Erkennt automatisch Entitäten (Daten, Beträge, IBANs) und kategorisiert Dokumente (z.B. "Rechnung"), ohne Daten in die Cloud zu senden.

## 2. Datenschutz-Prüfung (Privacy-First)
Die Implementierung nutzt ausschließlich **Google ML Kit (On-Device)**. 
*   Die Text-Erkennung (OCR) und Entitäten-Extraktion finden lokal auf dem Smartphone statt.
*   Es werden **keine** Bild- oder Textdaten an externe Server übertragen.
*   Dies entspricht strikt der Privacy Policy der App.

## 3. Architektur-Änderungen

### 3.1. Datenbank (Room FTS5)
Erweiterung der `AppDatabase.kt` um eine virtuelle Tabelle für die Suche:
```kotlin
@Fts4(contentEntity = ScanRecord::class) // FTS4/5 für Room
@Entity(tableName = "scans_fts")
data class ScanFts(
    val filename: String,
    val extractedText: String
)
```
*   **ScanDao**: Neue Methoden für `MATCH`-Abfragen über alle Dokumente.

### 3.2. OCR-Prozess (SearchablePdfBuilder.kt)
*   Anpassung von `makeSearchable`: Die Methode gibt nun den gesamten extrahierten Text als String zurück.
*   Dieser Text wird beim Speichern des `ScanRecord` in die FTS-Tabelle eingetragen.

### 3.3. Auto-Tagging Engine (Neu)
*   **AutoTagUseCase**: Nutzt ML Kit `EntityExtraction`, um im extrahierten Text nach Mustern zu suchen:
    *   **Datum**: Für chronologische Sortierung.
    *   **IBAN/Zahlungsdaten**: Indikator für "Rechnung".
    *   **Adressen/E-Mails**: Identifikation von Absendern.
*   **KeywordClassifier**: Ein lokaler Service, der basierend auf Schlagworten (z.B. "Versicherung", "Vertrag", "Kündigung") Tags vorschlägt.

### 3.4. UI-Integration
*   **HomeScreen**: Integration einer Suchleiste in der TopAppBar.
*   **ScanPreviewCard**: Anzeige der automatisch generierten Tags (z.B. kleine Chips unter dem Dateinamen).
*   **SearchViewModel**: Ein spezialisiertes ViewModel (oder Erweiterung des HomeViewModels) für die asynchrone Suche.

## 4. Implementierungsschritte

### Schritt 1: FTS-Datenbank
1.  Abhängigkeiten für Room FTS in `build.gradle.kts` prüfen.
2.  `ScanFts` Entity erstellen und `ScanDao` um Suchfunktionen erweitern.
3.  Datenbank-Migration (oder destruktives Update für die Entwicklungsphase).

### Schritt 2: Text-Extraktion & Indizierung
1.  `SearchablePdfBuilder` so umbauen, dass er den gesammelten Text aller Seiten nach Abschluss des OCR zurückgibt.
2.  `ImportScanUseCase` und `MakeSearchableUseCase` aktualisieren, um diesen Text in die Datenbank zu schreiben.

### Schritt 3: On-Device Klassifizierung (Auto-Tagging)
1.  Hinzufügen der ML Kit Entity Extraction Abhängigkeit.
2.  Erstellen eines `TaggingService`:
    *   Input: Extrahierter Text.
    *   Logik: ML Kit Entity Extraction + Regex für IBAN + Keyword-Mapping.
    *   Output: Liste von Tags (z.B. ["Rechnung", "Finanzen"]).
3.  Speichern der Tags in einer neuen Spalte `tags` (kommagetrennter String oder Relation) im `ScanRecord`.

### Schritt 4: Such-UI
1.  Suchleiste in der `HomeScreen` implementieren.
2.  Echtzeit-Filterung der Liste basierend auf der FTS-Abfrage (`SELECT ... FROM scans_fts WHERE extractedText MATCH :query`).

### Schritt 5: Dokumentation & Rechtliches
1.  **Hilfe-System**: Ergänzung von `HelpScreen.kt` um ein neues Kapitel "Suche & Automatisierung". Erklärung der Volltextsuche und wie Auto-Tags entstehen.
2.  **Datenschutz**: Update von `PrivacyScreen.kt` um einen 5. Punkt "On-Device Intelligenz". Expliziter Hinweis, dass ML Kit für Auto-Tagging lokal arbeitet und keine Dokumenteninhalte hochgeladen werden.
3.  **Strings**: Lokalisierung der neuen Begriffe in `strings.xml` (und `strings-de.xml`).

## 5. Testplan
*   **Privacy-Audit**: Verifizieren (via Logcat/Network Profiler), dass keine Daten während der Extraktion das Gerät verlassen.
*   **Performance**: Messen der Indizierungszeit bei 10+ Seiten (Ziel: < 2s zusätzlich zum OCR).
*   **Qualität**: Testen der Suche mit Tippfehlern (FTS Such-Präfixe) und der Genauigkeit der Tags bei verschiedenen Dokumententypen (Rechnungen, Briefe).

---
*Erstellt von Gemini CLI - Strategische Feature-Planung*
