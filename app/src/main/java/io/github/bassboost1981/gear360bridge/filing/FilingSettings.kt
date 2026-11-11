package io.github.bassboost1981.gear360bridge.filing

import kotlinx.serialization.Serializable

/** Welche Angaben verwendet werden und wie die Ordner aufgebaut sind. Gilt pro Handy. */
@Serializable
data class FilingSettings(
    val useDate: Boolean = true,
    val useFloor: Boolean = true,
    val useFlat: Boolean = false,
    val useRoom: Boolean = true,
    val dateAboveSite: Boolean = false,
    val delaySeconds: Int = 10,
)
