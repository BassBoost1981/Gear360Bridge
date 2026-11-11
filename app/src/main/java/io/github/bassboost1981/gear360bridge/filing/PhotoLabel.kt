package io.github.bassboost1981.gear360bridge.filing

import kotlinx.serialization.Serializable

/** Die Angaben zu einem Foto. [date] im Format yyyy-MM-dd, immer aus der Handy-Uhr. */
@Serializable
data class PhotoLabel(
    val site: String,
    val date: String,
    val floor: String? = null,
    val flat: String? = null,
    val room: String? = null,
)
