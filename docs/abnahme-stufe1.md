# Abnahme Stufe 1

Geprüft am 10. und 11.11.2026 mit der Kamera des Auftraggebers (SM-C200, Firmware `C200GLU0API1`) und einem Samsung Galaxy S25 Ultra (Android 17).

## An Kamera und Handy geprüft

| Fall | Ergebnis |
|---|---|
| Foto auslösen, übertragen, ablegen | In Ordnung. `p5/2026-11-11/og/Bad.jpg`, 9,4 MB, 360°-Kennzeichnung im Bild, Aufnahmedatum aus der Handy-Uhr. |
| Zwei Fotos im selben Raum | In Ordnung. `Küche.jpg`, `Küche_2.jpg`. |
| Display während der Aufnahme aus | In Ordnung, das Bild kommt an. |
| Kamera nach dem Auslösen ausgeschaltet | Meldung „… konnte nicht übertragen werden …" erscheint; nach dem Wiederverbinden wurde das Bild nachgeholt. |
| Raum umbenennen | In Ordnung. `Foto.jpg` wurde zu `Wohnzimmer.jpg`. |
| Zwei Bilder verschieben | In Ordnung. Beide liegen danach unter `p52/2026-11-11/eg`. |
| ZIP zum Teilen | Wird erzeugt (`p52.zip`, 17,6 MB). Verschicken ging im Kamera-WLAN mangels Internet nicht. |
| Gewählter Raum nach Schließen und Öffnen der App | In Ordnung (nach Korrektur, siehe unten). |
| Bildspeicher (7 automatische Geräte-Tests) | In Ordnung. |

## Bei der Abnahme gefundene und behobene Fehler

- **Nachgeholtes Bild ohne Rückmeldung.** Das Bild lag in der Übersicht, die App sagte es aber nicht. Jetzt erscheint eine Meldung.
- **Raumwahl ging beim Schließen der App verloren.** Das Bild hieß dann `Foto.jpg`. Der Raum wird jetzt gespeichert wie Baustelle und Stockwerk.
- **Teilen im Kamera-WLAN.** Ein Hinweis erklärt jetzt, dass dort kein Internet besteht.

## Nach der unabhängigen Durchsicht des Codes behoben

Diese Korrekturen sind durch automatische Tests am PC abgesichert, aber **noch nicht erneut an Kamera und Handy geprüft**:

- Fehlermeldung ließ sich nicht mehr schließen, wenn derselbe Fehler zweimal hintereinander auftrat.
- Eine veraltete Auswahl konnte Bilder einer anderen, nicht sichtbaren Baustelle löschen oder verschieben.
- Doppelter Druck auf den Auslöser oder spätes „Abbrechen" konnte eine laufende Aufnahme abreißen und die Anzeige hängen lassen.
- Voller Handy-Speicher führte zum Absturz statt zu einer Meldung.
- Nach jeder Aufnahme wird die Sitzung an der Kamera jetzt freigegeben, damit das nächste Handy sofort auslösen kann; bei einer abgelehnten Sitzung wird einmal neu versucht.
- Ein außerhalb der App gelöschtes Bild blockierte das Teilen der ganzen Baustelle.
- Bei der Rückkehr in die App erschien mehrere Sekunden lang fälschlich die Anleitung „Kamera verbinden".
- Ein Druck auf den Auslöser während des Nachholens wurde stillschweigend verworfen.
- Ein offenes Bild wurde zu schnell als „nicht mehr auffindbar" abgeschrieben, etwa wenn die Kamera kurz nach dem Einschalten noch keine Bilder meldete.

## Noch offen

- **Zwei Handys gleichzeitig an der Kamera.** Nicht geprüft. Gedacht ist die Benutzung nacheinander.
- **Street-View-Modus nach Neustart der Kamera.** Nicht dokumentiert, ob er erhalten bleibt.
- **Android 10 bis 16.** Alle Läufe fanden auf Android 17 statt.
- **Mehr als 50 Bilder auf der Karte.** Ob die Kamera die neuesten Bilder zuerst meldet, ist ungeprüft; davon hängt das Nachholen bei voller Karte ab.
