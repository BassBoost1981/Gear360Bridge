# Gear360Bridge – Baustellen-App für Android – Entwurf

Stand: 2026-11-10. Status: Entwurf, noch nicht freigegeben.

## Worum es geht

Die Samsung Gear 360 (Modell 2016, SM-C200) nimmt 360°-Fotos auf, aber Samsungs Begleit-Apps sind eingestellt. Ohne sie bleibt nur, die Speicherkarte herauszunehmen. Diese App ersetzt den fehlenden Teil für einen bestimmten Zweck: **Baustellen-Dokumentation**.

Ein 360°-Foto pro Raum hält alles fest, auch die Stellen, an die man beim normalen Fotografieren nicht denkt. Dafür stellt man die Kamera in den Raum, geht hinaus, löst per Handy aus und hat das Bild danach richtig einsortiert auf dem Handy.

Genutzt wird die App vom Auftraggeber und von Kollegen, jeweils mit dem eigenen Android-Handy. Die Nutzer sind Handwerker und Bauleiter, keine Technik-Enthusiasten.

## Ziele

1. Ein Foto aus der Ferne auslösen, ohne selbst im Bild zu sein.
2. Das Bild ohne Kartenwechsel aufs Handy bekommen.
3. Jedes Bild so ablegen, dass man es nach Monaten über Baustelle, Datum, Stockwerk, Wohnung und Raum wiederfindet.
4. Bilder einer Baustelle gesammelt weitergeben.
5. (Stufe 2) Das 360°-Bild in der App ansehen und einen Ausschnitt als normales Foto speichern, zum Drucken oder Verschicken.

## Nicht-Ziele

- **Video.** Die Kamera lässt Video über die genutzte Schnittstelle nicht zu (siehe unten). Videos laufen weiter über die Speicherkarte.
- **iOS und Windows.** Eigene spätere Projekte. Der Auftraggeber hat ausdrücklich entschieden, zuerst eine vollständige, reine Android-App zu bauen.
- **Live-Vorschau.** Gibt die Kamera nicht her.
- **Fester Firmen-Speicherort, Cloud-Anbindung, Benutzerkonten.** Wo die Firma ablegt, ist offen. Die App gibt Bilder über die Teilen-Funktion von Android weiter.
- **Firmenweite Einstellungen.** Einstellungen gelten pro Handy.
- **Veröffentlichung im Play Store.** Die App wird als APK-Datei auf die Handys installiert.

## Was die App auf keinen Fall tun darf

- Ein Foto verlieren oder falsch einsortieren. Ein Bild, das im falschen Raum-Ordner liegt, ist schlimmer als gar keins.
- Den Nutzer mit Technik allein lassen. Fehlermeldungen wie „Timeout" oder „HTTP 500" sind unbrauchbar; es muss dastehen, was los ist und was man tun soll.
- Internet, Anmeldung oder Samsung-Dienste voraussetzen.
- Ein Bild von der Kamera löschen, bevor es sicher auf dem Handy gespeichert ist.

## Was an der echten Kamera geprüft wurde

Alles Folgende wurde am 9. und 10.11.2026 vom PC aus an der Kamera des Auftraggebers getestet (Firmware `C200GLU0API1`). Der Builder kann sich darauf verlassen.

**So kommt die Kamera in den richtigen Modus:** Menü-Taste gedrückt halten, mit der Menü-Taste bis „Google Street View" blättern, mit der Auslöser-Taste bestätigen. Im Display erscheint ein „G" neben dem Kamera-Symbol. Die Kamera öffnet dann ein eigenes WLAN (Name beginnt mit „Gear 360") ohne Internet. Das Passwort dafür zeigt die Kamera in ihrem Display an; die Anleitung in der App muss diesen Schritt enthalten. Der Modus lässt sich nicht aus der Ferne einschalten. Ob er einen Neustart der Kamera übersteht, ist ungeprüft; anzunehmen ist nein.

**Schnittstelle:** Open Spherical Camera API, Version 1, einfaches HTTP ohne Verschlüsselung unter `http://192.168.107.1` (Port 80).

| Aufruf | Ergebnis |
|---|---|
| `GET /osc/info` | Hersteller, Modell „GEAR 360", Seriennummer, Firmware, Liste der Endpunkte |
| `POST /osc/state` | `batteryLevel` als Zahl 0–1 (gemessen 0.7 und 1.0, vermutlich grobe Stufen), `sessionId` |
| `camera.startSession` | liefert `sessionId` (z. B. `SID_0216`) und `timeout: 300` Sekunden |
| `camera.getOptions` | liefert nur `captureMode: "image"`, `captureModeSupport: ["image"]`, `hdr: true`, `hdrSupport: true`, `iso: "auto"`, `whiteBalance: "auto"`. Alle anderen abgefragten Optionen werden stillschweigend weggelassen. |
| `camera.takePicture` | antwortet sofort mit `state: "inProgress"` und `id`. Der Fortschrittswert bleibt bei 0. |
| `POST /osc/commands/status` mit `{"id": …}` | nach etwa 10 Sekunden `state: "done"` mit `results.fileUri` (z. B. `/mnt/mmc/DCIM/100PHOTO/SAM_0005.JPG`) |
| `camera.getImage` mit `fileUri` | liefert das JPEG direkt als Antwort, `Content-Type: image/jpeg`, 8,3 MB in 1,2 Sekunden |
| `camera.listImages` (`entryCount`, `maxSize`, `includeThumb: false`) | Liste mit `name`, `uri`, `size`, `dateTimeZone`, `width` und – Tippfehler der Firmware – `heigh` statt `height` |
| `camera.closeSession` | `state: "done"` |

Alle Kommandos gehen als `POST /osc/commands/execute` mit JSON `{"name": …, "parameters": {…}}`.

**Das Bild:** 7200 × 3600 Pixel, bereits fertig zu einem 360°-Bild zusammengesetzt (equirectangular) und im Bild selbst als solches gekennzeichnet (`GPano:ProjectionType = equirectangular`). Ein eigenes Zusammensetzen ist nicht nötig.

**Was die Kamera nicht hergibt:**
- Nur Fotos, kein Video.
- Kein freier Speicherplatz, keine Zahl verbleibender Bilder (`remainingPictures`, `remainingSpace`, `totalSpace` kommen leer zurück).
- Kein Selbstauslöser (`exposureDelay` kommt leer zurück).
- Die Uhr der Kamera ist verstellt (Testfoto trägt den 1.1.2016). Dem Kamera-Datum ist nicht zu trauen.

**Ungeprüft:** `camera.delete`, `camera.setOptions` (etwa zum Stellen der Uhr), Verhalten bei abgelaufener Sitzung, Verhalten bei mehreren gleichzeitig verbundenen Handys.

## Wie die App sich verhält

### Verbinden

Der Nutzer bringt die Kamera per Tasten in den Street-View-Modus und verbindet das Handy in den WLAN-Einstellungen von Android mit dem Kamera-WLAN. Die App zeigt beim Start und immer dann, wenn sie die Kamera nicht erreicht, eine kurze bebilderte Anleitung für diese Schritte, mit einem Knopf, der die WLAN-Einstellungen öffnet.

Sobald die Kamera erreichbar ist, zeigt die App „Verbunden" und den Akkustand in Prozent. Der Akkustand wird regelmäßig aktualisiert.

Weil das Kamera-WLAN kein Internet hat, schicken Android-Handys Daten gern weiter übers Mobilfunknetz und damit an der Kamera vorbei. Die App muss ihre Kamera-Anfragen ausdrücklich über die WLAN-Verbindung schicken. Unverschlüsseltes HTTP muss für die Kamera-Adresse erlaubt werden.

### Fotografieren

Vor dem Auslösen ist sichtbar, wohin das nächste Bild einsortiert wird (Baustelle, Stockwerk, Wohnung, Raum, je nach Einstellung). Der Nutzer wählt den Raum und drückt den Auslöser.

- **Wartezeit zum Rausgehen:** einstellbar, die App zählt sichtbar und hörbar herunter und löst dann aus. Die Wartezeit läuft in der App, weil die Kamera keinen Selbstauslöser anbietet. Vorschlag für die Auswahl: 0, 5, 10, 20 Sekunden, Voreinstellung 10.
- Während die Kamera arbeitet (rund 10 Sekunden), zeigt die App das an. Danach holt sie das Bild automatisch aufs Handy und speichert es am richtigen Ort.
- Danach zeigt die App das Bild klein an, damit man sieht, dass es geklappt hat, mit der Möglichkeit, es zu verwerfen und neu aufzunehmen.
- Das Auslösen muss auch funktionieren, wenn das Handy-Display zwischendurch ausgeht oder die App in den Hintergrund gerät: Ein ausgelöstes Bild wird zu Ende geholt und gespeichert.

Wenn das Herunterladen scheitert, bleibt das Bild auf der Kamera. Die App merkt sich, wohin es gehört, und holt es nach, sobald die Verbindung wieder steht.

**Eine Kamera, mehrere Handys.** Laut Auftraggeber gibt es genau eine Kamera (die getestete), die von mehreren Kollegen mit ihren eigenen Handys benutzt wird. Daraus folgt:

- Auf der Karte liegen Bilder verschiedener Handys durcheinander, dazu solche, die per Kamera-Taste entstanden sind. Die App holt nur Bilder nach, die sie selbst ausgelöst hat; sie merkt sich dafür die von der Kamera gemeldete Datei jedes eigenen Auslösens. Fremde Bilder fasst sie nicht an und sortiert sie nicht ein.
- Die Dateinamen der Kamera können sich nach dem Formatieren der Karte wiederholen. Ein gemerkter Dateiname allein reicht deshalb nicht, um ein Bild sicher als das eigene zu erkennen.
- Andere Kameras oder das Modell von 2017 müssen nicht unterstützt werden.
- Entscheidung des Entwurfs: Gedacht ist die Benutzung nacheinander, ein Handy zur Zeit. Was passiert, wenn zwei Handys gleichzeitig verbunden sind, ist ungeprüft und wird während der Umsetzung an der Kamera getestet. Die App muss in diesem Fall verständlich reagieren und darf kein Bild falsch zuordnen.

### Einsortieren

Jedes Bild gehört zu einer **Baustelle** (frei benannt, z. B. Adresse). Dazu kommen wahlweise:

- **Datum** – automatisch aus der Handy-Uhr, nie von der Kamera
- **Stockwerk** – z. B. KG, EG, 1. OG
- **Wohnungsnummer** – für Wohnhäuser
- **Raum** – z. B. Bad, Küche, Flur

In den Einstellungen legt der Nutzer per Häkchen fest, welche dieser Angaben verwendet werden. Abgehakte Angaben werden beim Fotografieren abgefragt und erscheinen in der Ordnerstruktur, die anderen entfallen. Ebenfalls in den Einstellungen wählbar: ob das Datum **über** oder **unter** der Baustelle liegt.

```
Musterstraße 12 / 2026-11-10 / 2. OG / Whg 5 / Bad.jpg      (Datum unter der Baustelle)
2026-11-10 / Musterstraße 12 / 2. OG / Whg 5 / Bad.jpg      (Datum über der Baustelle)
```

Das alles hat der Auftraggeber so festgelegt. Die folgenden Punkte sind Vorschläge des Entwurfs, vom Auftraggeber als „sieht gut aus" bestätigt:

- Baustelle, Stockwerk und Wohnung bleiben eingestellt, bis man sie ändert. Pro Foto gibt man nur den Raum an.
- Räume wählt man aus einer Liste üblicher Namen oder tippt einen eigenen ein; eigene Namen merkt sich die App.
- Mehrere Fotos im selben Raum werden durchnummeriert (`Bad_1.jpg`, `Bad_2.jpg`).

### Ablegen und Weitergeben

Die Bilder liegen auf dem Handy in einem eigenen Ordner unter „Bilder", so dass sie auch in der Galerie und im Dateimanager sichtbar sind und eine Deinstallation der App überstehen. Die 360°-Kennzeichnung im Bild bleibt erhalten, damit andere Programme es als 360°-Bild erkennen. Das Aufnahmedatum im Bild wird auf die Handy-Zeit gesetzt.

In der App gibt es eine Übersicht der Baustellen mit ihren Bildern.

**Nachträglich korrigieren (vom Auftraggeber festgelegt, Teil von Stufe 1):** In der Übersicht lässt sich ein Bild in einen anderen Raum, ein anderes Stockwerk, eine andere Wohnung oder eine andere Baustelle verschieben. Der häufigste Fehler wird sein, dass jemand das Umstellen von Stockwerk oder Wohnung vergisst und es erst einige Räume später merkt; deshalb muss sich auch eine Auswahl mehrerer Bilder auf einmal verschieben lassen. Ordner, Dateiname und Nummerierung passen sich dabei an. Entscheidung des Entwurfs: Umbenennen und Löschen einzelner Bilder gehören ebenfalls dazu, Löschen mit Rückfrage.

Eine ganze Baustelle (oder ein Tag, ein Stockwerk) lässt sich über die Teilen-Funktion von Android weitergeben, wobei die Ordnerstruktur erhalten bleiben muss.

### Stufe 2: Ansehen und Ausschnitte

Nach der Kernfunktion, als eigener Abschnitt der Umsetzung:

- 360°-Bild in der App öffnen und sich per Wischen darin umsehen, mit Zoom.
- Den gerade sichtbaren Ausschnitt als normales, entzerrtes Foto speichern. Es landet neben dem 360°-Bild im selben Ordner und lässt sich teilen und drucken.

## Reihenfolge

Der Auftraggeber hat gesagt, am wichtigsten sei eine Android-App, mit der man Fotos machen kann. Daraus folgt:

1. **Stufe 1 – Fotografieren:** verbinden, Akku, fernauslösen mit Wartezeit, herunterladen, einsortieren, Übersicht mit nachträglichem Verschieben, teilen.
2. **Stufe 2 – Ansehen:** 360°-Betrachter und Ausschnitt-Export.

Stufe 1 muss für sich allein auf der Baustelle benutzbar sein.

## Entscheidungen zur Machart

Vom Auftraggeber:
- Reine Android-App, zuerst und vollständig.
- **Name: Gear360Bridge.** Das Projekt gehört in das GitHub-Repository https://github.com/BassBoost1981/Gear360Bridge.

Vom Entwurf getroffen (nicht vom Auftraggeber):
- **Kotlin, native Android-App.** Eine geteilte Codebasis für iOS wurde angeboten; der Auftraggeber wollte zuerst Android komplett.
- **Oberfläche auf Deutsch.**
- **Installation als APK**, kein Store.
- **Löschen von der Kamera nach dem Herunterladen** ist nicht Teil von Stufe 1, weil `camera.delete` ungeprüft ist. Bei gut 8 MB pro Bild reicht eine 128-GB-Karte für viele tausend Fotos.
- **Bilder der Anleitung** (welche Taste, wie lange drücken, wo das Passwort steht) entstehen als einfache Zeichnungen während der Umsetzung; der Auftraggeber liefert dafür nichts zu.

## Rahmenbedingungen

- Auf dem Entwicklungs-PC (Windows 11) sind weder Java noch das Android-SDK installiert. Die Werkzeuge müssen eingerichtet werden; das Installieren braucht die Zustimmung des Auftraggebers.
- Das lokale Projektverzeichnis ist noch kein Git-Repository und noch nicht mit dem GitHub-Repository verbunden. In welchem Zustand das GitHub-Repository ist (leer oder mit Inhalt), ist ungeprüft.
- Zum Testen stehen die echte Kamera und das Android-Handy des Auftraggebers (Samsung Galaxy S25 Ultra) zur Verfügung. Während der PC am Kamera-WLAN hängt, hat er je nach Anschluss kein Internet.
- Die Kamera braucht rund 10 Sekunden pro Bild; schnelle Serien sind kein Ziel.
- Ältere Handys der Kollegen sind möglich; welche Android-Versionen dort laufen, ist unbekannt.
- Als Anschauungsmaterial existiert [gear360-revive](https://github.com/arellaeast/gear360-revive) (MIT-Lizenz), eine Android-App für dieselbe Kamera und Schnittstelle, laut eigener Beschreibung nie kompiliert und nie an echter Hardware getestet. Die eigenen Messungen oben haben Vorrang.

## Dem Builder überlassen

- Aufbau der App, Bibliotheken, Speicherung der Baustellen- und Einstellungsdaten.
- Niedrigste unterstützte Android-Version (so niedrig, wie es ohne Mehraufwand geht).
- Gestaltung der Bildschirme, solange sie mit Handschuhen und im Baustellen-Alltag bedienbar sind: große Knöpfe, wenige Schritte bis zum Auslösen.
- In welcher Form geteilt wird (einzelne Dateien oder ein gepacktes Archiv), solange die Ordnerstruktur beim Empfänger ankommt.
- Umgang mit der Sitzung der Kamera (Ablauf nach 300 Sekunden).
- Wie die Kamera-Anbindung ohne Kamera getestet wird; die oben dokumentierten Antworten taugen als Vorlage für eine Attrappe.
- Technik für Betrachter und Ausschnitt-Export in Stufe 2.

## Woran man erkennt, dass es fertig ist

Stufe 1: Ein Kollege, der die App noch nie gesehen hat, bekommt Kamera und Handy in die Hand, folgt der Anleitung in der App, fotografiert drei Räume einer Wohnung aus dem Flur heraus und findet danach drei richtig benannte 360°-Bilder im richtigen Ordner auf dem Handy, mit heutigem Datum, und kann sie als Baustelle weiterschicken.

Stufe 2: In einem dieser Bilder dreht er sich zu einer Wand, speichert den Ausschnitt und hat ein normales Foto, das sich drucken lässt.
