# Analyse und Vorschläge zur Menüstruktur

## Aktueller Stand

### 1. Hamburger-Menü (Navigation Drawer)
- **Inhalt:** Archiv, Scanner starten, Hilfe, Info, Datenschutz.
- **Kritik:** 
    - Das Menü wirkt sehr leer.
    - "Scanner starten" ist eine Primäraktion, die bereits durch den Floating Action Button (FAB) prominent vertreten ist. Die Aufnahme in das Navigationsmenü ist redundant und entspricht nicht dem Standard für Navigations-Drawer (die primär der Navigation zwischen App-Bereichen dienen).

### 2. Auswahl-Leiste (Bulk-Funktionen)
- **Inhalt:** Teilen, Exportieren, Zusammenfügen (Merge), Text extrahieren, Durchsuchbar machen (OCR), Löschen.
- **Kritik:**
    - 6 Icons in einer Reihe ohne Textlabels können für Nutzer schwer verständlich sein (z.B. ist das Icon für "Durchsuchbar machen" nicht selbsterklärend).
    - Auf kleinen Bildschirmen wirkt die Leiste gedrängt.

### 3. Dreipunkte-Menü (Einzel-PDF Aktionen)
- **Inhalt:** 17 verschiedene Aktionen, verteilt auf ein Hauptmenü und zwei Untermenüs (Seitenstruktur, Schutz & Passwort).
- **Kritik:**
    - Das Menü ist überladen. 7 Top-Level Einträge plus Untermenüs führen zu einer langen Liste, die auf mobilen Geräten schwer zu überblicken ist.
    - Die Verschachtelung in Untermenüs erfordert mehr Klicks.

---

## Vorschläge nach Best Practices (Material 3)

### 1. Hamburger-Menü (Navigation Drawer)
- **Fokus auf Navigation und App-Verwaltung:**
    - Entfernen von "Scanner starten" aus dem Hauptbereich (der FAB reicht völlig aus).
    - Gruppierung der Einträge:
        - **Haupt:** Archiv (Ablage).
        - **Support & Rechtliches:** Hilfe, Info, Datenschutz.
    - Hinzufügen eines "Einstellungen" (Settings) Punktes (falls zukünftig geplant), um den Platz sinnvoll zu nutzen.

### 2. Auswahl-Leiste (Bulk-Funktionen)
- **Priorisierung und Klarheit:**
    - Verwendung eines **Bottom App Bar** Musters mit Text unter den Icons für die wichtigsten Aktionen (Teilen, Löschen, Merge).
    - Verschieben von seltener genutzten oder komplexen Aktionen (Text extrahieren, OCR) in ein "Mehr"-Overflow-Menü innerhalb der Auswahlleiste.
    - Alternativ: Eine **Contextual Action Bar (CAB)** am oberen Bildschirmrand, die den Titel "X ausgewählt" zeigt und Icons für die häufigsten Aktionen bietet.

### 3. Einzel-PDF Bearbeitung (Das "Drei-Punkte-Problem")
- **Wechsel zu einem Modal Bottom Sheet:**
    - Statt eines Dropdown-Menüs sollte ein **Modal Bottom Sheet** verwendet werden. Bottom Sheets sind auf mobilen Geräten besser erreichbar (Daumen-Zone) und bieten Platz für Icons mit klaren Textbeschreibungen.
- **Kategorisierung der Aktionen (ähnlich wie in der Hilfe-Seite):**
    - **Bearbeiten:** Rotieren, Seiten verwalten (Untermenü/Gruppe: Löschen, Extrahieren, Duplizieren), Sortieren, Trennen (Split).
    - **Inhalt & Markierungen:** Unterschreiben, Annotieren, Wasserzeichen, Seitenzahlen.
    - **Datei & Sicherheit:** Komprimieren, Passwort-Schutz, Export als JPG, OCR-Ebene entfernen.
- **Visuelle Trennung:** Verwendung von horizontalen Trennlinien oder Gruppenüberschriften innerhalb des Bottom Sheets, um die 17 Aktionen logisch zu strukturieren.

## Zusammenfassung der Vorteile
- **Bessere Erreichbarkeit:** Bottom Sheets und eine aufgeräumte untere Aktionsleiste verbessern die Einhandbedienung.
- **Höhere Verständlichkeit:** Textlabels und klare Kategorien reduzieren die kognitive Last für den Nutzer.
- **Konsistenz:** Die Struktur folgt den Material 3 Guidelines und wirkt dadurch moderner und professioneller.
