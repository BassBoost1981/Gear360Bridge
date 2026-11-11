# Gear360Bridge Stufe 1 – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine Android-App, mit der man die Samsung Gear 360 (SM-C200) aus der Ferne auslöst, das 360°-Foto aufs Handy holt, nach Baustelle/Datum/Stockwerk/Wohnung/Raum ablegt, nachträglich verschieben und als Baustelle weitergeben kann.

**Architecture:** Eine einzelne Android-App (ein Gradle-Modul `app`). Die Kamera-Anbindung (`osc`), die Ablage-Regeln (`filing`), der Bildspeicher (`storage`), das Verzeichnis der Bilder (`catalog`) und der Aufnahme-Ablauf (`capture`) sind getrennte Pakete mit schmalen Schnittstellen; alles außer `storage` und der Oberfläche ist ohne Handy testbar. Eine Aufnahme läuft in einem Vordergrund-Dienst, damit sie Display-Aus und Hintergrund übersteht.

**Tech Stack:** Kotlin 2.0, Gradle (Kotlin DSL) mit Android Gradle Plugin 8.x, JDK 17, Jetpack Compose (Material 3), Coroutines/Flow, OkHttp 4.12 (+ MockWebServer), kotlinx-serialization-json, AndroidX DataStore, AndroidX ExifInterface, Coil, JUnit 4, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-11-10-gear360-android-app-design.md`

## Global Constraints

- App-Name `Gear360Bridge`, Paket `io.github.bassboost1981.gear360bridge`, Repository `https://github.com/BassBoost1981/Gear360Bridge`.
- `minSdk 29` (Android 10), `targetSdk 35`, `compileSdk 35`. Plan-Entscheidung: Android 10 ist die niedrigste Version, auf der Bilder ohne Sonderweg in Unterordnern von „Bilder" abgelegt und verschoben werden können.
- Kamera: nur SM-C200, Adresse `http://192.168.107.1` (Port 80), Open Spherical Camera API v1, unverschlüsseltes HTTP nur für diese Adresse erlaubt.
- Alle sichtbaren Texte auf Deutsch, in `strings.xml`. Keine technischen Fehlermeldungen in der Oberfläche.
- Kein Internet, keine Anmeldung, keine Samsung-Dienste, keine Analyse- oder Werbe-Bibliotheken.
- Datum immer aus der Handy-Uhr, nie von der Kamera.
- Von der Kamera-Karte wird nichts gelöscht.
- Die App fasst auf der Kamera nur Bilder an, die sie selbst ausgelöst hat.
- Bilder liegen unter `Pictures/Gear360Bridge/…`.
- Ohne ausdrückliche Zustimmung des Auftraggebers: nichts installieren, nichts zu GitHub hochladen.
- `testfoto-SAM_0005.jpg` zeigt eine Person und darf nicht ins Repository (steht in `.gitignore`).
- Commits enden mit `Co-Authored-By: Claude Fable 5.2 <noreply@anthropic.com>`.

## Plan-Entscheidungen, die der Auftraggeber sehen sollte

- **Dateinamen:** Das erste Foto eines Raums heißt `Bad.jpg`, weitere `Bad_2.jpg`, `Bad_3.jpg`. Vorhandene Dateien werden nie nachträglich umnummeriert. Ohne Raum-Angabe heißt die Datei `Foto.jpg`, `Foto_2.jpg`.
- **Wohnungs-Ordner** heißen `Whg <Nummer>`.
- **Teilen** erzeugt eine ZIP-Datei, damit die Ordnerstruktur beim Empfänger ankommt.
- **Einstellungen ändern** wirkt nur auf neue Fotos; vorhandene werden nicht umsortiert.
- **Nach Deinstallation** bleiben die Bilder erhalten, die Übersicht in der App ist nach Neuinstallation leer.

## Review Focus

1. **Namen mit unzulässigen Zeichen oder leer** (`Bad/WC`, `EG.`, nur Leerzeichen): Es entsteht ein gültiger Ordner- bzw. Dateiname, kein Absturz, kein falscher Unterordner. → Test in Task 3.
2. **Verbindung reißt mitten im Herunterladen ab:** Es bleibt keine halbe Datei in der Galerie und kein Eintrag in der Übersicht; das Bild wird später nachgeholt. → Tests in Task 2 und Task 7.
3. **Karte wurde formatiert, Kamera vergibt denselben Dateinamen neu, oder ein anderes Handy hat fotografiert:** Die App holt kein fremdes Bild unter falschem Raum nach. → Test in Task 7.
4. **Verschieben in einen Ordner, in dem der Name schon vergeben ist, oder Abbruch mitten in einer Mehrfach-Verschiebung:** Kein Bild wird überschrieben, Übersicht und Dateien stimmen danach überein. → Tests in Task 6.
5. **Mobile Daten eingeschaltet, Kamera-WLAN ohne Internet:** Die App erreicht die Kamera trotzdem. Nur an echter Hardware prüfbar. → Prüfschritt in Task 8 und Task 11.

---

## Dateistruktur

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, .gitignore, README.md
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/xml/network_security_config.xml, file_paths.xml
app/src/main/res/values/strings.xml
app/src/main/res/drawable/guide_step1..4.xml        Zeichnungen der Anleitung
app/src/main/java/io/github/bassboost1981/gear360bridge/
  MainActivity.kt, AppContainer.kt
  osc/      OscClient.kt, OscModels.kt
  filing/   FilingSettings.kt, PhotoLabel.kt, PathBuilder.kt
  storage/  PhotoStore.kt, MediaStorePhotoStore.kt
  catalog/  CatalogModels.kt, Catalog.kt, SettingsRepository.kt, PhotoOrganizer.kt
  capture/  CaptureCoordinator.kt, CameraLink.kt, CaptureService.kt
  share/    ShareZipper.kt
  ui/       ConnectGuideScreen.kt, CaptureScreen.kt, CaptureViewModel.kt,
            OverviewScreen.kt, OverviewViewModel.kt, SettingsScreen.kt, Theme.kt
app/src/test/…          JVM-Tests, FakeOsc.kt, FakePhotoStore.kt, resources/osc/*.json
app/src/androidTest/…   MediaStorePhotoStoreTest.kt, assets/sample_360.jpg
```

Testbefehle: JVM-Tests `./gradlew :app:testDebugUnitTest --tests "<Klasse>"`, Geräte-Tests `./gradlew :app:connectedDebugAndroidTest` (Handy per USB, USB-Debugging an).

---

### Task 1: Werkzeuge, Repository, Projektgerüst

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `.gitignore`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/network_security_config.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/java/io/github/bassboost1981/gear360bridge/MainActivity.kt`, Gradle-Wrapper

**Interfaces:**
- Produces: ein baubares leeres Projekt; alle späteren Tasks hängen ihre Abhängigkeiten in `gradle/libs.versions.toml` ein.

- [ ] **Step 1: Zustimmung einholen.** Dem Auftraggeber genau sagen, was installiert wird (JDK 17 „Temurin", Android-Kommandozeilenwerkzeuge mit `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`, zusammen rund 2 GB) und auf sein Ja warten.
- [ ] **Step 2: Werkzeuge installieren** und `JAVA_HOME`/`ANDROID_HOME` setzen. Prüfen: `java -version` zeigt 17, `adb version` läuft.
- [ ] **Step 3: Git einrichten.** `git init`, dann `git ls-remote https://github.com/BassBoost1981/Gear360Bridge`. Ist das Repository nicht leer: anhalten und den Auftraggeber fragen. Sonst `git remote add origin …`. Kein Push.
- [ ] **Step 4: `.gitignore`** mit Android-Standardeinträgen plus `testfoto-SAM_0005.jpg`, `local.properties`.
- [ ] **Step 5: Projektgerüst anlegen.** Compose aktiviert, `MainActivity` zeigt den Text „Gear360Bridge". Manifest: `android:networkSecurityConfig="@xml/network_security_config"`, `android:label="Gear360Bridge"`. `network_security_config.xml` erlaubt Klartext ausschließlich für `192.168.107.1`.
- [ ] **Step 6: Bauen.** Run: `./gradlew :app:assembleDebug` — Expected: `BUILD SUCCESSFUL`, `app/build/outputs/apk/debug/app-debug.apk` existiert.
- [ ] **Step 7: Commit** (`chore: project scaffold`), einschließlich `docs/`.

---

### Task 2: Kamera-Anbindung `OscClient`

**Files:**
- Create: `osc/OscModels.kt`, `osc/OscClient.kt`
- Test: `app/src/test/…/osc/OscClientTest.kt`, `app/src/test/resources/osc/{info,state,startSession,takePicture,statusInProgress,statusDone,listImages,error}.json`

Die JSON-Dateien enthalten die Antworten wortgleich aus dem Abschnitt „Was an der echten Kamera geprüft wurde" des Entwurfs (inklusive `"heigh"`). `error.json`: `{"name":"camera.takePicture","state":"error","error":{"code":"invalidSessionId","message":"x"}}`.

**Interfaces:**
- Produces:

```kotlin
data class CameraInfo(val model: String, val serialNumber: String, val firmwareVersion: String)
data class CameraImage(val name: String, val uri: String, val size: Long, val dateTimeZone: String)
sealed interface CommandStatus {
    data object InProgress : CommandStatus
    data class Done(val fileUri: String) : CommandStatus
    data class Failed(val code: String) : CommandStatus
}
sealed class OscException(msg: String) : Exception(msg) {
    class Unreachable(cause: Throwable) : OscException("unreachable")
    class CameraFailure(val code: String) : OscException(code)
    class BadResponse(detail: String) : OscException(detail)
}
interface Osc {
    suspend fun info(): CameraInfo
    suspend fun batteryLevel(): Float                      // 0f..1f
    suspend fun startSession(): String
    suspend fun takePicture(sessionId: String): String     // Kommando-ID
    suspend fun commandStatus(commandId: String): CommandStatus
    suspend fun listImages(entryCount: Int = 50): List<CameraImage>
    suspend fun downloadImage(fileUri: String, target: java.io.File): Long   // Bytes
}
class OscClient(http: OkHttpClient, baseUrl: HttpUrl = "http://192.168.107.1/".toHttpUrl()) : Osc
```

- [ ] **Step 1: Tests schreiben** (MockWebServer, `baseUrl = server.url("/")`):

```kotlin
@Test fun info_parsesModelAndFirmware()            // model == "GEAR 360", firmwareVersion == "C200GLU0API1"; GET /osc/info
@Test fun batteryLevel_readsState()                // 0.7f; POST /osc/state
@Test fun startSession_returnsId()                 // "SID_0216"
@Test fun takePicture_sendsSessionAndReturnsId()   // Body enthält "camera.takePicture" und "SID_0216"; Ergebnis "1"; POST /osc/commands/execute
@Test fun commandStatus_inProgress_and_done()      // Done("/mnt/mmc/DCIM/100PHOTO/SAM_0005.JPG"); POST /osc/commands/status mit {"id":"1"}
@Test fun listImages_toleratesHeighTypo()          // 1 Eintrag, size == 8298241, dateTimeZone == "2016:01:01 00:14:50+0100"
@Test fun downloadImage_writesFile()               // 5000 Bytes Antwort → Datei 5000 Bytes, Rückgabe 5000; Body enthält "camera.getImage"
@Test fun downloadImage_truncated_throwsAndDeletesFile() // Content-Length 5000, Verbindung nach 2000 getrennt → Unreachable, target existiert nicht
@Test fun errorState_throwsCameraFailure()         // code == "invalidSessionId"
@Test fun http500_orGarbage_throwsBadResponse()
@Test fun serverDown_throwsUnreachable()
```

- [ ] **Step 2: Fehlschlag prüfen.** Run: `./gradlew :app:testDebugUnitTest --tests "*OscClientTest"` — Expected: Kompilierfehler „OscClient".
- [ ] **Step 3: `OscModels.kt` und `OscClient.kt` implementieren.** JSON mit `ignoreUnknownKeys = true`; Zeitlimits: Verbindung 5 s, Lesen 30 s; alle Aufrufe auf `Dispatchers.IO`.
- [ ] **Step 4: Tests grün.** Selber Befehl — Expected: 11 Tests PASS.
- [ ] **Step 5: Commit** (`feat: OSC camera client`).

---

### Task 3: Ablage-Regeln `filing`

**Files:**
- Create: `filing/FilingSettings.kt`, `filing/PhotoLabel.kt`, `filing/PathBuilder.kt`
- Test: `app/src/test/…/filing/PathBuilderTest.kt`

**Interfaces:**
- Produces:

```kotlin
@Serializable data class FilingSettings(
    val useDate: Boolean = true, val useFloor: Boolean = true,
    val useFlat: Boolean = false, val useRoom: Boolean = true,
    val dateAboveSite: Boolean = false, val delaySeconds: Int = 10)
@Serializable data class PhotoLabel(val site: String, val date: String /* yyyy-MM-dd */,
    val floor: String? = null, val flat: String? = null, val room: String? = null)
object PathBuilder {
    const val ROOT = "Gear360Bridge"
    fun sanitize(raw: String): String
    fun folder(label: PhotoLabel, s: FilingSettings): List<String>   // ohne ROOT
    fun baseName(label: PhotoLabel, s: FilingSettings): String       // Raum oder "Foto"
    fun fileName(baseName: String, taken: Set<String>): String
}
```

- [ ] **Step 1: Tests schreiben**

```kotlin
val label = PhotoLabel("Musterstraße 12", "2026-11-10", "2. OG", "5", "Bad")
val all = FilingSettings(useFlat = true)
@Test fun folder_dateBelowSite() = assertEquals(listOf("Musterstraße 12","2026-11-10","2. OG","Whg 5"), PathBuilder.folder(label, all))
@Test fun folder_dateAboveSite() = assertEquals(listOf("2026-11-10","Musterstraße 12","2. OG","Whg 5"), PathBuilder.folder(label, all.copy(dateAboveSite = true)))
@Test fun folder_uncheckedPartsAreOmitted() = assertEquals(listOf("Musterstraße 12"), PathBuilder.folder(label, FilingSettings(useDate=false,useFloor=false,useFlat=false)))
@Test fun folder_blankFloorIsOmitted()          // floor = "  " → kein leeres Segment
@Test fun baseName_roomOrFoto()                 // "Bad"; useRoom=false → "Foto"; room=null → "Foto"
@Test fun fileName_numbering() {
    assertEquals("Bad.jpg", PathBuilder.fileName("Bad", emptySet()))
    assertEquals("Bad_2.jpg", PathBuilder.fileName("Bad", setOf("Bad.jpg")))
    assertEquals("Bad_3.jpg", PathBuilder.fileName("Bad", setOf("bad.JPG", "Bad_2.jpg")))   // Groß/Klein egal
}
@Test fun sanitize_cases() {
    assertEquals("Bad-WC", PathBuilder.sanitize("Bad/WC"))
    assertEquals("EG", PathBuilder.sanitize(" EG. "))
    assertEquals("Unbenannt", PathBuilder.sanitize("   "))
    assertEquals("a-b-c-d-e-f-g-h-i", PathBuilder.sanitize("a\\b:c*d?e\"f<g>h|i"))
    assertEquals(60, PathBuilder.sanitize("x".repeat(200)).length)
    assertEquals("Unbenannt", PathBuilder.sanitize(".."))
}
```

- [ ] **Step 2: Fehlschlag prüfen.** Run: `./gradlew :app:testDebugUnitTest --tests "*PathBuilderTest"` — Expected: Kompilierfehler.
- [ ] **Step 3: Implementieren.** `folder` und `baseName` wenden `sanitize` auf jeden Teil an.
- [ ] **Step 4: Tests grün.**
- [ ] **Step 5: Commit** (`feat: filing rules`).

---

### Task 4: Bildspeicher `storage`

**Files:**
- Create: `storage/PhotoStore.kt`, `storage/MediaStorePhotoStore.kt`, `app/src/test/…/FakePhotoStore.kt`
- Test: `app/src/androidTest/…/MediaStorePhotoStoreTest.kt`, `app/src/androidTest/assets/sample_360.jpg`

`sample_360.jpg`: ein neues, mit der Kamera aufgenommenes 360°-Foto eines leeren Raums ohne Personen (über Task-2-Client oder `curl` geholt). Vor dem Commit dem Auftraggeber zeigen.

**Interfaces:**
- Produces:

```kotlin
interface PhotoStore {
    suspend fun namesIn(folder: List<String>): Set<String>
    /** Kopiert source nach Pictures/Gear360Bridge/<folder>/<fileName>, setzt das Aufnahmedatum. Gibt die Speicher-URI als String zurück. */
    suspend fun save(folder: List<String>, fileName: String, takenAtMillis: Long, source: java.io.File): String
    suspend fun move(uri: String, newFolder: List<String>, newFileName: String)   // IllegalStateException, wenn Ziel belegt
    suspend fun delete(uri: String)
    fun open(uri: String): java.io.InputStream
}
class MediaStorePhotoStore(context: Context) : PhotoStore
class FakePhotoStore : PhotoStore   // im Speicher; var failMoveAfter: Int? zum Erzwingen eines Fehlers
```

- [ ] **Step 1: Geräte-Tests schreiben**

```kotlin
@Test fun save_putsFileInExpectedFolder()   // RELATIVE_PATH == "Pictures/Gear360Bridge/Musterstraße 12/2026-11-10/2. OG/Whg 5/", DISPLAY_NAME == "Bad.jpg"
@Test fun save_keeps360Marker_andSetsDate() // Bytes enthalten "GPano:ProjectionType"; EXIF DateTimeOriginal == takenAtMillis (lokale Zeit)
@Test fun save_failure_leavesNoEntry()      // source existiert nicht → Exception, namesIn(folder) ist leer
@Test fun move_changesFolderAndName()       // danach namesIn(alt) leer, namesIn(neu) == {"Küche.jpg"}
@Test fun move_ontoExistingName_throws_andKeepsBoth()
@Test fun delete_removesFile()
```

- [ ] **Step 2: Fehlschlag prüfen.** Run: `./gradlew :app:connectedDebugAndroidTest` — Expected: Kompilierfehler.
- [ ] **Step 3: Implementieren.** `save` schreibt mit `IS_PENDING = 1`, setzt EXIF-Datum über AndroidX ExifInterface auf der Quelldatei vor dem Kopieren, gibt mit `IS_PENDING = 0` frei; bei Fehler Zeile löschen. `move` über `RELATIVE_PATH`/`DISPLAY_NAME`-Update. `FakePhotoStore` bildet dasselbe Verhalten im Speicher nach.
- [ ] **Step 4: Tests grün** (Handy angeschlossen).
- [ ] **Step 5: Commit** (`feat: photo storage`).

---

### Task 5: Verzeichnis der Bilder und Einstellungen

**Files:**
- Create: `catalog/CatalogModels.kt`, `catalog/Catalog.kt`, `catalog/SettingsRepository.kt`
- Test: `app/src/test/…/catalog/CatalogTest.kt`, `SettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: `FilingSettings`, `PhotoLabel` (Task 3)
- Produces:

```kotlin
@Serializable data class PhotoRecord(val id: String, val label: PhotoLabel, val folder: List<String>,
    val fileName: String, val storeUri: String, val takenAtMillis: Long)
@Serializable data class PendingCapture(val id: String, val label: PhotoLabel, val triggeredAtMillis: Long,
    val cameraUri: String? = null, val cameraSize: Long? = null, val cameraDate: String? = null)
class Catalog(file: java.io.File) {
    val photos: StateFlow<List<PhotoRecord>>
    val pending: StateFlow<List<PendingCapture>>
    suspend fun putPhoto(r: PhotoRecord)          // neu oder ersetzen (gleiche id)
    suspend fun removePhoto(id: String)
    suspend fun putPending(p: PendingCapture)
    suspend fun removePending(id: String)
}
@Serializable data class Selection(val site: String = "", val floor: String = "", val flat: String = "",
    val customRooms: List<String> = emptyList())
class SettingsRepository(dataStore: DataStore<Preferences>) {
    val filing: Flow<FilingSettings>
    val selection: Flow<Selection>
    suspend fun updateFiling(f: (FilingSettings) -> FilingSettings)
    suspend fun updateSelection(f: (Selection) -> Selection)
}
```

- [ ] **Step 1: Tests schreiben**

```kotlin
@Test fun catalog_persistsAcrossReopen()          // putPhoto + putPending, neue Catalog(file) liefert beides
@Test fun catalog_putPhoto_sameId_replaces()
@Test fun catalog_corruptFile_startsEmpty_andKeepsBackup()  // Datei "{{{" → photos leer, "<name>.bak" existiert
@Test fun catalog_missingFile_startsEmpty()
@Test fun settings_defaults()                     // filing.first() == FilingSettings(); selection.first() == Selection()
@Test fun settings_updatePersists()               // delaySeconds = 20, dateAboveSite = true nach Neu-Öffnen
```

- [ ] **Step 2: Fehlschlag prüfen.** Run: `./gradlew :app:testDebugUnitTest --tests "*CatalogTest" --tests "*SettingsRepositoryTest"`.
- [ ] **Step 3: Implementieren.** `Catalog` schreibt nach jeder Änderung atomar (Temp-Datei, dann umbenennen), Zugriffe über einen `Mutex`.
- [ ] **Step 4: Tests grün.**
- [ ] **Step 5: Commit** (`feat: catalog and settings`).

---

### Task 6: Einsortieren, Verschieben, Umbenennen, Löschen

**Files:**
- Create: `catalog/PhotoOrganizer.kt`
- Test: `app/src/test/…/catalog/PhotoOrganizerTest.kt`

**Interfaces:**
- Consumes: `PathBuilder` (Task 3), `PhotoStore`/`FakePhotoStore` (Task 4), `Catalog` (Task 5)
- Produces:

```kotlin
data class LabelChange(val site: String? = null, val floor: String? = null,
    val flat: String? = null, val room: String? = null)      // null = unverändert
class PhotoOrganizer(store: PhotoStore, catalog: Catalog, settings: suspend () -> FilingSettings) {
    suspend fun file(label: PhotoLabel, takenAtMillis: Long, source: java.io.File): PhotoRecord
    suspend fun move(ids: List<String>, change: LabelChange): Int     // Anzahl verschobener Bilder
    suspend fun rename(id: String, newRoom: String)
    suspend fun delete(id: String)
}
```

- [ ] **Step 1: Tests schreiben**

```kotlin
@Test fun file_twoPhotosSameRoom_getBadAndBad2()
@Test fun file_usesFolderFromSettings()               // folder == ["Musterstraße 12","2026-11-10","2. OG","Whg 5"]
@Test fun move_threePhotos_toOtherFloor()             // alle drei: folder enthält "2. OG", label.floor == "2. OG", Store-Pfad stimmt
@Test fun move_nameTakenInTarget_getsNextFreeNumber() // Ziel hat "Bad.jpg" → verschobenes wird "Bad_2.jpg", nichts überschrieben
@Test fun move_failureMidBatch_keepsCatalogAndStoreInSync() // failMoveAfter = 1: 1 verschoben, 2 unverändert, für jedes Bild record.folder == Ort im Store; Exception wird weitergereicht
@Test fun rename_changesRoomAndFileName()             // "Bad.jpg" → room "Küche", fileName "Küche.jpg"
@Test fun delete_removesFromStoreAndCatalog()
```

- [ ] **Step 2: Fehlschlag prüfen.** Run: `./gradlew :app:testDebugUnitTest --tests "*PhotoOrganizerTest"`.
- [ ] **Step 3: Implementieren.** Reihenfolge je Bild: erst `store.move`, dann `catalog.putPhoto`; ein Bild nach dem anderen.
- [ ] **Step 4: Tests grün.**
- [ ] **Step 5: Commit** (`feat: organize photos`).

---

### Task 7: Aufnahme-Ablauf `CaptureCoordinator`

**Files:**
- Create: `capture/CaptureCoordinator.kt`, `app/src/test/…/FakeOsc.kt`
- Test: `app/src/test/…/capture/CaptureCoordinatorTest.kt`

**Interfaces:**
- Consumes: `Osc` (Task 2), `PhotoOrganizer` (Task 6), `Catalog` (Task 5)
- Produces:

```kotlin
enum class CaptureFailure { CameraUnreachable, CameraRefused, DownloadInterrupted, Timeout }
sealed interface CaptureState {
    data object Idle : CaptureState
    data class Countdown(val secondsLeft: Int) : CaptureState
    data object CameraWorking : CaptureState
    data object Downloading : CaptureState
    data class Saved(val photo: PhotoRecord) : CaptureState
    data class Failed(val reason: CaptureFailure) : CaptureState
}
data class RecoveryResult(val recovered: Int, val lost: Int)
class CaptureCoordinator(osc: Osc, organizer: PhotoOrganizer, catalog: Catalog,
    tempDir: java.io.File, now: () -> Long) {
    val state: StateFlow<CaptureState>
    suspend fun capture(label: PhotoLabel, delaySeconds: Int)
    fun cancelCountdown()
    suspend fun recoverPending(): RecoveryResult
}
```

Festgelegtes Verhalten:
- Countdown im Sekundentakt; `cancelCountdown` wirkt nur während `Countdown`.
- Vor `takePicture` wird eine `PendingCapture` gespeichert. Status alle 1 s abfragen, höchstens 60 s, dann `Timeout`.
- `CameraFailure("invalidSessionId")` → einmal neue Sitzung, einmal wiederholen.
- Nach `Done`: `listImages`, Größe und Kamera-Datum des eigenen Bilds in der `PendingCapture` festhalten, dann in `tempDir` herunterladen, Dateigröße mit `size` vergleichen, dann `organizer.file`, dann `PendingCapture` entfernen, Temp-Datei löschen.
- Scheitert das Herunterladen: `Failed(DownloadInterrupted)`, `PendingCapture` bleibt.
- `recoverPending`: nur Einträge mit `cameraUri`; das Bild gilt als das eigene, wenn `listImages` einen Eintrag mit gleicher `uri` **und** gleicher `size` **und** gleichem `dateTimeZone` enthält. Sonst „verloren": Eintrag entfernen und zählen. Einträge ohne `cameraUri` zählen als verloren.

- [ ] **Step 1: Tests schreiben** (`runTest`, virtuelle Zeit, `FakeOsc` mit einstellbaren Antworten und Aufrufzähler)

```kotlin
@Test fun countdown_emits10to1_thenTakesPicture()
@Test fun cancelDuringCountdown_takesNoPicture()        // takePictureCalls == 0, state == Idle, pending leer
@Test fun happyPath_savesPhoto_andClearsPending()       // state is Saved; catalog.photos.size == 1; pending leer; tempDir leer
@Test fun downloadFails_keepsPending_noPhoto()          // Failed(DownloadInterrupted); pending[0].cameraUri gesetzt; photos leer; Store leer
@Test fun sizeMismatchAfterDownload_isDownloadInterrupted()
@Test fun cameraReportsFailed_isCameraRefused_noPending()
@Test fun invalidSession_retriesOnceWithNewSession()    // startSessionCalls == 2, Ergebnis Saved
@Test fun statusNeverDone_timesOutAfter60s()
@Test fun unreachable_isCameraUnreachable()
@Test fun recover_downloadsOwnPendingImage()            // RecoveryResult(1, 0), Foto mit ursprünglichem Label
@Test fun recover_sameUriDifferentSize_isLost_notDownloaded()   // RecoveryResult(0, 1), downloadCalls == 0
@Test fun recover_ignoresImagesOfOtherPhones()          // Liste enthält fremde Einträge → downloadCalls == 1 (nur das eigene)
```

- [ ] **Step 2: Fehlschlag prüfen.** Run: `./gradlew :app:testDebugUnitTest --tests "*CaptureCoordinatorTest"`.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Tests grün** — Expected: 12 Tests PASS.
- [ ] **Step 5: Commit** (`feat: capture flow with recovery`).

---

### Task 8: Verbindung zur Kamera und Vordergrund-Dienst

**Files:**
- Create: `capture/CameraLink.kt`, `capture/CaptureService.kt`, `AppContainer.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `MainActivity.kt`
- Test: `app/src/test/…/capture/CameraLinkTest.kt`

**Interfaces:**
- Consumes: `OscClient` (Task 2), `CaptureCoordinator` (Task 7)
- Produces:

```kotlin
data class LinkState(val connected: Boolean, val batteryPercent: Int?)
class CameraLink(context: Context) {
    val state: StateFlow<LinkState>
    val osc: Osc                       // an das WLAN gebunden
    fun start(); fun stop()
    companion object { fun isGear360(info: CameraInfo): Boolean; fun toPercent(level: Float): Int }
}
class AppContainer(context: Context) { val link; val catalog; val settings; val organizer; val coordinator; val store }
// CaptureService: startet per Intent-Extra (PhotoLabel als JSON, delaySeconds), ruft coordinator.capture
```

Festgelegtes Verhalten:
- `CameraLink` fordert per `ConnectivityManager.requestNetwork` ein WLAN **ohne** Internet-Anforderung an und baut den `OkHttpClient` mit der `socketFactory` dieses Netzes.
- Alle 5 s `info()` und `batteryLevel()`; verbunden nur, wenn `isGear360(info)`. Beim Übergang auf „verbunden" einmal `coordinator.recoverPending()`.
- Berechtigungen: `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`.
- `CaptureService` läuft als Vordergrund-Dienst (Typ `connectedDevice`) mit Benachrichtigung „Foto wird aufgenommen", hält während der Aufnahme einen Teil-Wakelock, piept im Countdown jede Sekunde und am Ende einmal länger.

- [ ] **Step 1: Tests schreiben**

```kotlin
@Test fun isGear360_trueForTestedCamera()   // CameraInfo("GEAR 360", "TESTSERIAL01", "C200GLU0API1")
@Test fun isGear360_falseForOtherModel()    // model "RICOH THETA"
@Test fun toPercent_rounds()                // 0.7f → 70, 1.0f → 100, 0.049f → 5
```

- [ ] **Step 2: Fehlschlag prüfen**, **Step 3: Implementieren**, **Step 4: Tests grün.**
- [ ] **Step 5: Prüfung am Handy.** Mobile Daten **an**, Handy im Kamera-WLAN, App starten. Expected: Logcat zeigt `info` mit Modell „GEAR 360" und einen Akkuwert. Dasselbe mit mobilen Daten aus.
- [ ] **Step 6: Commit** (`feat: camera link and capture service`).

---

### Task 9: Oberfläche – Verbinden und Fotografieren

**Files:**
- Create: `ui/Theme.kt`, `ui/ConnectGuideScreen.kt`, `ui/CaptureScreen.kt`, `ui/CaptureViewModel.kt`, `res/drawable/guide_step1.xml` … `guide_step4.xml`
- Modify: `res/values/strings.xml`, `MainActivity.kt`
- Test: `app/src/test/…/ui/CaptureViewModelTest.kt`

**Interfaces:**
- Consumes: `AppContainer` (Task 8), `CaptureState`, `LinkState`, `SettingsRepository`
- Produces:

```kotlin
data class CaptureUi(val connected: Boolean, val batteryPercent: Int?, val site: String, val floor: String,
    val flat: String, val room: String, val rooms: List<String>, val filing: FilingSettings,
    val phase: CaptureState, val canShoot: Boolean, val messageRes: Int?)
class CaptureViewModel(...) {
    val ui: StateFlow<CaptureUi>
    fun setSite(v: String); fun setFloor(v: String); fun setFlat(v: String); fun setRoom(v: String)
    fun shoot(); fun cancel(); fun discardLast(); fun dismissMessage()
    companion object { fun messageFor(f: CaptureFailure): Int; val DEFAULT_ROOMS: List<String> }
}
```

Festgelegte Inhalte:
- `DEFAULT_ROOMS`: Bad, WC, Küche, Flur, Wohnzimmer, Schlafzimmer, Kinderzimmer, Abstellraum, Balkon, Keller, Technikraum, Treppenhaus. Eigene Namen werden in `Selection.customRooms` gemerkt.
- Anleitung, vier Schritte mit je einer Zeichnung: (1) „Kamera einschalten." (2) „Menü-Taste gedrückt halten, dann mit der Menü-Taste bis ‚Google Street View' blättern und mit der Auslöser-Taste bestätigen. Im Display erscheint ein G." (3) „Im Display der Kamera steht das WLAN-Passwort." (4) „Am Handy mit dem WLAN ‚Gear 360 …' verbinden. Die Meldung ‚kein Internet' ist normal." Knopf „WLAN-Einstellungen öffnen" (`Settings.ACTION_WIFI_SETTINGS`). Die Anleitung erscheint, solange `connected == false`.
- Fehlertexte:
  - `CameraUnreachable`: „Kamera nicht erreichbar. Ist das Handy noch mit dem Kamera-WLAN verbunden und zeigt die Kamera ein G im Display?"
  - `CameraRefused`: „Die Kamera hat das Foto nicht aufgenommen. Bitte Speicherkarte und Akku der Kamera prüfen und noch einmal auslösen."
  - `DownloadInterrupted`: „Das Foto ist aufgenommen, konnte aber nicht übertragen werden. Es liegt noch auf der Kamera und wird automatisch geholt, sobald die Verbindung wieder steht."
  - `Timeout`: „Die Kamera antwortet nicht mehr. Bitte Kamera aus- und wieder einschalten und den Street-View-Modus neu wählen."
- Aufnahme-Bildschirm: oben „Verbunden · Akku 70 %", darunter Baustelle/Stockwerk/Wohnung (nur die angehakten), Raum-Auswahl, darunter in Klartext der Zielordner samt Dateiname, Auslöser als Kreis mit mindestens 96 dp. Im Countdown große Zahl und „Abbrechen". Nach dem Speichern Vorschaubild mit „Verwerfen" und „Weiter".
- `canShoot` ist nur wahr, wenn verbunden, Baustelle nicht leer und keine Aufnahme läuft. `shoot()` startet `CaptureService`. `discardLast()` ruft `organizer.delete`.

- [ ] **Step 1: Tests schreiben**

```kotlin
@Test fun canShoot_falseWhenNotConnected()
@Test fun canShoot_falseWhenSiteBlank()
@Test fun canShoot_falseWhileCapturing()
@Test fun failure_mapsToMessage()            // jede CaptureFailure → eigener String, vier verschiedene
@Test fun setFloor_isRememberedInSelection() // nach setFloor("2. OG") liefert settings.selection.floor "2. OG"
@Test fun customRoom_isAddedToRooms()
@Test fun uncheckedFlat_isNotInTargetPreview()
```

- [ ] **Step 2: Fehlschlag prüfen**, **Step 3: Implementieren**, **Step 4: Tests grün.** Run: `./gradlew :app:testDebugUnitTest --tests "*CaptureViewModelTest"`.
- [ ] **Step 5: Am Handy prüfen.** Ohne Kamera: Anleitung erscheint. Mit Kamera: ein Foto mit 5 s Wartezeit, Datei liegt im angezeigten Ordner.
- [ ] **Step 6: Commit** (`feat: connect guide and capture screen`).

---

### Task 10: Oberfläche – Übersicht, Verschieben, Teilen, Einstellungen

**Files:**
- Create: `share/ShareZipper.kt`, `ui/OverviewScreen.kt`, `ui/OverviewViewModel.kt`, `ui/SettingsScreen.kt`, `res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml` (FileProvider), `MainActivity.kt` (Navigation: Aufnahme, Übersicht, Einstellungen), `strings.xml`
- Test: `app/src/test/…/share/ShareZipperTest.kt`, `app/src/test/…/ui/OverviewViewModelTest.kt`

**Interfaces:**
- Consumes: `Catalog`, `PhotoOrganizer`, `LabelChange`, `PhotoStore.open`, `SettingsRepository`
- Produces:

```kotlin
object ShareZipper {
    fun entryName(p: PhotoRecord): String                                 // folder + fileName mit "/" verbunden
    fun zip(photos: List<PhotoRecord>, open: (String) -> InputStream, out: OutputStream)
}
class OverviewViewModel(...) {
    val sites: StateFlow<List<String>>
    fun photosOf(site: String): StateFlow<List<PhotoRecord>>              // sortiert nach folder, fileName
    val selected: StateFlow<Set<String>>
    fun toggle(id: String); fun clearSelection()
    fun moveSelected(change: LabelChange); fun rename(id: String, room: String); fun deleteSelected()
    suspend fun buildShareZip(photos: List<PhotoRecord>): java.io.File    // in cacheDir/share/<Baustelle>.zip
}
```

Festgelegtes Verhalten:
- Übersicht: Liste der Baustellen, darin die Bilder gruppiert nach Ordner, mit Vorschaubild (Coil). Lange drücken startet die Mehrfachauswahl. Aktionen: „Verschieben" (Dialog mit Baustelle, Stockwerk, Wohnung, Raum; leere Felder bleiben unverändert), „Umbenennen" (nur bei genau einem Bild), „Löschen" mit Rückfrage „<n> Bilder endgültig löschen?", „Teilen".
- „Teilen" auf einer Baustelle, einem Ordner oder der Auswahl: ZIP bauen, per `FileProvider` und `ACTION_SEND` (`application/zip`) weitergeben.
- Einstellungen: vier Häkchen (Datum, Stockwerk, Wohnungsnummer, Raum), Auswahl „Datum über der Baustelle / unter der Baustelle", Wartezeit 0/5/10/20 Sekunden, Hinweistext „Änderungen gelten für neue Fotos."
- Scheitert `moveSelected` mittendrin: Meldung „Nicht alle Bilder konnten verschoben werden. Bitte Übersicht prüfen und noch einmal versuchen."

- [ ] **Step 1: Tests schreiben**

```kotlin
@Test fun entryName_keepsFolderStructure()   // "Musterstraße 12/2026-11-10/2. OG/Whg 5/Bad.jpg"
@Test fun zip_containsAllPhotosWithContent() // 2 Fotos → 2 Einträge, Namen wie oben, Bytes gleich, Umlaute lesbar (UTF-8)
@Test fun zip_emptyList_producesValidEmptyZip()
@Test fun sites_areDistinctAndSorted()
@Test fun moveSelected_callsOrganizer_andClearsSelection()
@Test fun deleteSelected_removesPhotos()
```

- [ ] **Step 2: Fehlschlag prüfen**, **Step 3: Implementieren**, **Step 4: Tests grün.** Run: `./gradlew :app:testDebugUnitTest --tests "*ShareZipperTest" --tests "*OverviewViewModelTest"`.
- [ ] **Step 5: Am Handy prüfen.** Zwei Bilder in ein anderes Stockwerk verschieben (Galerie zeigt neuen Ordner), Baustelle teilen (ZIP am Empfänger entpacken, Ordner stimmen).
- [ ] **Step 6: Commit** (`feat: overview, move, share, settings`).

---

### Task 11: Abnahme an Kamera und Handy

**Files:**
- Create: `README.md`, `docs/abnahme-stufe1.md`

- [ ] **Step 1: Gesamtlauf.** Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug` — Expected: alle Tests PASS, `BUILD SUCCESSFUL`.
- [ ] **Step 2: APK aufs Handy** des Auftraggebers (`adb install -r app/build/outputs/apk/debug/app-debug.apk`).
- [ ] **Step 3: Abnahme-Szenario des Entwurfs:** drei Räume einer Wohnung aus dem Flur heraus fotografieren, nur anhand der Anleitung in der App. Expected: drei richtig benannte 360°-Bilder im richtigen Ordner, heutiges Datum, in der Galerie als 360°-Bild erkannt, als Baustelle teilbar.
- [ ] **Step 4: Störfälle,** jeweils Ergebnis in `docs/abnahme-stufe1.md` festhalten:
  - Display während Countdown und Aufnahme ausschalten → Bild kommt trotzdem an.
  - Mobile Daten an → Verbindung steht.
  - Kamera direkt nach dem Auslösen ausschalten → verständliche Meldung; nach Wiederverbinden wird das Bild nachgeholt oder als nicht auffindbar gemeldet.
  - Kamera aus- und wieder einschalten → ist der Street-View-Modus noch aktiv? (Antwort dokumentieren, Anleitungstext bei Bedarf anpassen.)
  - Zweites Handy gleichzeitig verbinden → Verhalten dokumentieren; kein Bild darf falsch zugeordnet werden.
  - Raumname `Bad/WC`, Baustelle mit Umlauten und Punkt am Ende.
- [ ] **Step 5: `README.md`** auf Deutsch: was die App tut, Kamera in den Street-View-Modus bringen, APK installieren.
- [ ] **Step 6: Commit** (`docs: acceptance results and readme`). Push zu GitHub nur auf ausdrückliche Anweisung des Auftraggebers.
