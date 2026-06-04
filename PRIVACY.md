# Datenschutzerklaerung fuer PDF Scanner

Stand: 4. Juni 2026

Diese Datenschutzerklaerung gilt fuer die Android-App **PDF Scanner** mit dem Paketnamen `info.meuse24.pdf_scanner`.

Anbieter: Guenther Meusburger  
Kontakt: guenther.meusburger@gmail.com  
Quellcode: https://github.com/meuse24/PDF_Scanner

## 1. Kurzfassung

Die App speichert gescannte Dokumente grundsaetzlich lokal auf dem Geraet. Es gibt kein Benutzerkonto, keine Werbung, kein eigenes Tracking, kein eigenes Backend und keine Uebermittlung von Scans an Server des Entwicklers.

Fuer die Scan-Funktion wird der **Google ML Kit Document Scanner** ueber **Google Play Services** verwendet. Laut Google erfolgt die Verarbeitung der Eingabedaten und der Scan-Ergebnisse auf dem Geraet. Google weist jedoch darauf hin, dass ML Kit gelegentlich Server fuer Updates und Kompatibilitaetsinformationen kontaktieren und Leistungs- bzw. Nutzungsmetriken an Google uebermitteln kann.

Die App kann auf ausdrueckliche Aktion des Nutzers passwortgeschuetzte Backup-Dateien erstellen und wiederherstellen. Diese Backups verlassen die App nur, wenn der Nutzer ein Ziel ueber den Android-Systemdialog auswaehlt. Ohne das vom Nutzer gewaehlte Backup-Passwort ist eine Wiederherstellung technisch nicht moeglich.

## 2. Welche Daten in der App verarbeitet werden

Die App verarbeitet ausschliesslich Daten, die fuer die Dokumentenverwaltung auf dem Geraet erforderlich sind. Dazu gehoeren insbesondere:

- gescannte PDF-Dateien
- optional erzeugte Vorschaubilder der ersten Seite
- Dateiname
- lokaler Dateipfad
- Erstellungszeitpunkt
- Seitenanzahl
- Dateigroesse
- optional OCR-Text, OCR-Qualitaetsdaten, OCR-Sprache und seitenbezogene OCR-Daten
- optionale Tags, Ordnerzuordnung, Favoritenstatus und Papierkorbstatus

Diese Informationen werden lokal im App-Speicher und in einer lokalen Datenbank auf dem Geraet abgelegt, damit die Ablage, Vorschau, Freigabe, der Export und das Loeschen von Scans funktionieren.

## 3. Wo die Daten gespeichert werden

Gescannte PDFs und Vorschaubilder werden im app-internen Speicher gespeichert. Zusaetzlich speichert die App Metadaten in einer lokalen Room-Datenbank.

Nach der aktuellen Implementierung sind die gescannten PDFs und die lokale Datenbank ausserdem explizit von Android-Cloud-Backups und vom Device-to-Device-Transfer ausgeschlossen.

## 4. Netzwerk, Werbung, Konten und Berechtigungen

- Die App nutzt kein eigenes Backend und lädt Dokumente nicht auf Server des Entwicklers hoch. Durch Google Play Services / ML Kit können im gemergten App-Manifest Netzwerkberechtigungen enthalten sein, etwa für Modell-, Kompatibilitäts- und Diagnosefunktionen.
- Die App enthaelt keine Werbung.
- Die App verwendet kein eigenes Analytics- oder Crash-Reporting-System.
- Die App verlangt kein Benutzerkonto und kein Abonnement.
- Die App fordert keine Kamera-, Kontakt-, Mikrofon- oder Standortberechtigung an.

Der eigentliche Scan-Vorgang wird ueber Google ML Kit Document Scanner gestartet, ohne dass die App selbst eine Kamera-Berechtigung anfordert.

## 5. Einsatz von Google ML Kit Document Scanner und Google Play Services

Die App nutzt fuer das Scannen von Dokumenten die von Google bereitgestellte Komponente **ML Kit Document Scanner**, die ueber **Google Play Services** bereitgestellt wird.

Nach den offiziellen ML-Kit-Hinweisen von Google findet die Verarbeitung der Eingabedaten wie Bilder und Scan-Ergebnisse vollstaendig auf dem Geraet statt; diese Daten werden nicht an Google-Server gesendet. Google weist jedoch ebenfalls darauf hin, dass ML Kit APIs gelegentlich Google-Server kontaktieren koennen, um Fehlerbehebungen, aktualisierte Modelle oder Hardware-Kompatibilitaetsinformationen zu beziehen. Ausserdem koennen Leistungs- und Nutzungsmetriken der API an Google uebermittelt werden.

Daneben kann Google Play Services gemaess den Einstellungen des Geraets und des Google-Kontos weitere Daten verarbeiten. Darauf hat der Entwickler dieser App keinen vollstaendigen Einfluss. Fuer diese Verarbeitung gelten die Datenschutzbestimmungen von Google.

Offizielle Informationen:

- https://developers.google.com/ml-kit/terms
- https://support.google.com/android/answer/10546414
- https://policies.google.com/privacy

## 6. Teilen und Exportieren von Dateien

Die App kann Scans auf ausdrueckliche Aktion des Nutzers an andere Apps weitergeben oder in den Download-Bereich des Geraets exportieren.

- Beim **Teilen** wird die Datei an die vom Nutzer ausgewaehlte Ziel-App uebergeben.
- Beim **Export** wird eine Kopie der Datei in den Download-Bereich des Geraets geschrieben.

Ab diesem Zeitpunkt richtet sich die weitere Verarbeitung nach der Datenschutzerklaerung der jeweils genutzten Ziel-App, des Dienstes oder des Geraeteherstellers.

## 7. Verschluesselte Backups

Die App kann auf ausdrueckliche Aktion des Nutzers eine verschluesselte Backup-Datei mit der Endung `.m24backup` erstellen. Der Nutzer waehlt dabei selbst den Speicherort ueber den Android-Systemdialog und vergibt ein Backup-Passwort.

Das Backup enthaelt je nach gewaehlten Optionen Dokumentdateien, Vorschaubilder, OCR-Text, OCR-Metadaten, Tags, Ordner, Favoritenstatus und optional Papierkorb-Dokumente. Diese Inhalte liegen im Backup nur im verschluesselten Payload. Der unverschluesselte Header enthaelt technische Format- und Schluesselableitungsdaten, aber keine Dokumentnamen, OCR-Texte, Tags oder Ordnernamen.

Das Backup-Passwort kann nicht wiederhergestellt oder zurueckgesetzt werden. Wenn es verloren geht, kann die Backup-Datei technisch nicht entschluesselt werden. Die App nutzt Standard-Kryptografie fuer Datei- und Backup-Schutz: Argon2id fuer die passwortbasierte Schluesselableitung, AES-256-GCM fuer das Wrapping des zufaelligen Backup-Keysets und Tink StreamingAead fuer den verschluesselten Payload. Dies ist nutzergesteuerter Datei- und Backup-Schutz; die App bietet keinen allgemeinen Kryptografie-Dienst.

Beim Wiederherstellen wird das Backup zunaechst in ein temporaeres App-Cache-Verzeichnis entschluesselt und validiert. Dieses Staging-Verzeichnis wird bei Fehlern, Abbruch und nach erfolgreichem Import geloescht. Nach erfolgreicher Wiederherstellung liegen die Dokumente wieder in der normalen lokalen App-Ablage; das Backup schuetzt also die exportierte Datei, nicht automatisch die wiederhergestellten lokalen App-Daten.

Per-Datei-Checksummen im Backup dienen der Fehlererkennung bei Kopie, ZIP-Verarbeitung und Implementierungsfehlern. Die kryptografische Integritaet wird durch die authentifizierte Verschluesselung bereitgestellt.

## 8. Speicherdauer

Lokal gespeicherte Scans, Vorschaubilder und Metadaten bleiben auf dem Geraet, bis sie durch den Nutzer in der App geloescht oder die App deinstalliert wird. Beim Loeschen in der App werden Dokumente zunaechst lokal in den Papierkorb verschoben und dort bis zu 30 Tage aufbewahrt. Sie werden frueher entfernt, wenn der Nutzer sie endgueltig loescht oder den Papierkorb leert. Exportierte oder geteilte Kopien koennen ausserhalb der App weiter bestehen.

Temporaere entschluesselte Restore-Dateien werden nach Fehlern, Abbruch oder erfolgreichem Restore geloescht. Bereits exportierte Backup-Dateien bleiben an dem vom Nutzer gewaehlten Speicherort bestehen, bis der Nutzer sie dort loescht.

## 9. Rechtsgrundlagen nach DSGVO

Soweit personenbezogene Daten verarbeitet werden, erfolgt dies zur Bereitstellung der vom Nutzer angeforderten App-Funktionen gemaess Art. 6 Abs. 1 lit. b DSGVO sowie auf Grundlage des berechtigten Interesses an einer stabilen, sicheren und kompatiblen Bereitstellung der App gemaess Art. 6 Abs. 1 lit. f DSGVO.

## 10. Rechte betroffener Personen

Sofern die Voraussetzungen der DSGVO vorliegen, bestehen insbesondere Rechte auf Auskunft, Berichtigung, Loeschung, Einschraenkung der Verarbeitung, Widerspruch sowie Datenuebertragbarkeit. Anfragen koennen an die oben genannte Kontaktadresse gerichtet werden.

## 11. Aenderungen dieser Datenschutzerklaerung

Diese Datenschutzerklaerung kann angepasst werden, wenn sich die App, ihre Funktionen oder die rechtlichen Anforderungen aendern. Massgeblich ist die jeweils veroeffentlichte Fassung.
