# Gear360Bridge

Android-App für die Samsung Gear 360 (Modell 2016, SM-C200), deren Begleit-Apps Samsung eingestellt hat. Gedacht für Baustellen-Dokumentation: Kamera in den Raum stellen, hinausgehen, per Handy auslösen. Das 360°-Foto landet danach einsortiert auf dem Handy.

## Was die App kann

- Kamera über ihr eigenes WLAN fernauslösen, mit einstellbarer Wartezeit zum Rausgehen
- Akkustand der Kamera anzeigen
- Foto automatisch aufs Handy holen und unter `Bilder/Gear360Bridge/<Baustelle>/<Datum>/<Stockwerk>/<Wohnung>/<Raum>.jpg` ablegen
- Abgebrochene Übertragungen nachholen, sobald die Kamera wieder erreichbar ist
- Bilder nachträglich verschieben, umbenennen, löschen
- Eine Baustelle als ZIP-Datei mit erhaltener Ordnerstruktur teilen

Nicht enthalten: Video, Live-Vorschau, 360°-Betrachter in der App (geplant als Stufe 2), iOS.

## Kamera vorbereiten

1. Kamera einschalten.
2. Menü-Taste gedrückt halten, mit der Menü-Taste bis „Google Street View" blättern, mit der Auslöser-Taste bestätigen. Im Display erscheint ein „G".
3. Das WLAN-Passwort steht im Display der Kamera.
4. Am Handy mit dem WLAN „Gear 360 …" verbinden. Die Meldung „kein Internet" ist normal.

Solange das Handy am Kamera-WLAN hängt, hat es kein Internet. Zum Verschicken von Bildern zuerst das Kamera-WLAN trennen.

## Installieren

Die App wird als APK-Datei installiert, nicht über den Play Store. Sie braucht Android 10 oder neuer.

## Bauen

Voraussetzungen: JDK 17 und das Android-SDK (Plattform 35, Build-Tools 35.0.0). Der Pfad zum SDK steht in `local.properties` (`sdk.dir=...`).

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Die Geräte-Tests (`./gradlew :app:connectedDebugAndroidTest`) brauchen ein per USB angeschlossenes Handy mit USB-Debugging.

## Weitere Unterlagen

- [Entwurf](docs/superpowers/specs/2026-11-10-gear360-android-app-design.md): Zweck, Ziele und das an der echten Kamera gemessene Verhalten der Schnittstelle
- [Umsetzungsplan Stufe 1](docs/superpowers/plans/2026-11-10-gear360bridge-stufe1.md)
- [Abnahme Stufe 1](docs/abnahme-stufe1.md): was an Kamera und Handy geprüft wurde und was noch offen ist
