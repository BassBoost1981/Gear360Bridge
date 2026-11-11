package io.github.bassboost1981.gear360bridge.catalog

import io.github.bassboost1981.gear360bridge.filing.PhotoLabel
import kotlinx.serialization.Serializable

/** Ein auf dem Handy abgelegtes Foto. [folder] ohne Wurzelordner. */
@Serializable
data class PhotoRecord(
    val id: String,
    val label: PhotoLabel,
    val folder: List<String>,
    val fileName: String,
    val storeUri: String,
    val takenAtMillis: Long,
)

/**
 * Ein ausgeloestes Foto, das noch nicht sicher auf dem Handy liegt.
 * Die Kamera-Angaben werden gesetzt, sobald die Kamera die Datei gemeldet hat; zusammen
 * weisen sie das Bild als das eigene aus (Dateinamen wiederholen sich nach dem Formatieren).
 */
@Serializable
data class PendingCapture(
    val id: String,
    val label: PhotoLabel,
    val triggeredAtMillis: Long,
    val cameraUri: String? = null,
    val cameraSize: Long? = null,
    val cameraDate: String? = null,
)

/** Was beim Fotografieren eingestellt bleibt, bis man es aendert. */
@Serializable
data class Selection(
    val site: String = "",
    val floor: String = "",
    val flat: String = "",
    val room: String = "",
    val customRooms: List<String> = emptyList(),
)

@Serializable
internal data class CatalogData(
    val photos: List<PhotoRecord> = emptyList(),
    val pending: List<PendingCapture> = emptyList(),
)
