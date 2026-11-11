package io.github.bassboost1981.gear360bridge.filing

/** Regeln, nach denen aus den Angaben zu einem Foto Ordner und Dateiname entstehen. */
object PathBuilder {
    const val ROOT = "Gear360Bridge"
    private const val MAX_LENGTH = 60
    private const val FALLBACK = "Unbenannt"
    private val illegal = Regex("""[\\/:*?"<>|]""")
    private val whitespace = Regex("""\s+""")

    /** Macht aus einer freien Eingabe einen gueltigen Ordner- oder Dateinamen. */
    fun sanitize(raw: String): String {
        val cleaned = raw.replace(whitespace, " ").replace(illegal, "-")
            .trim(' ', '.')
            .take(MAX_LENGTH)
            .trim(' ', '.')
        return cleaned.ifEmpty { FALLBACK }
    }

    /** Ordner unterhalb von [ROOT], von aussen nach innen. */
    fun folder(label: PhotoLabel, s: FilingSettings): List<String> {
        val site = sanitize(label.site)
        val date = label.date.takeIf { s.useDate }
        val head = if (s.dateAboveSite) listOfNotNull(date, site) else listOfNotNull(site, date)
        val floor = label.floor.takeIf { s.useFloor }.present()?.let(::sanitize)
        val flat = label.flat.takeIf { s.useFlat }.present()?.let { "Whg " + sanitize(it) }
        return head + listOfNotNull(floor, flat)
    }

    /** Dateiname ohne Endung: der Raum, sonst "Foto". */
    fun baseName(label: PhotoLabel, s: FilingSettings): String =
        label.room.takeIf { s.useRoom }.present()?.let(::sanitize) ?: "Foto"

    /** Erster freier Name: Bad.jpg, Bad_2.jpg, Bad_3.jpg. Gross- und Kleinschreibung zaehlt nicht. */
    fun fileName(baseName: String, taken: Set<String>): String {
        val lower = taken.map { it.lowercase() }.toSet()
        var candidate = "$baseName.jpg"
        var n = 2
        while (candidate.lowercase() in lower) {
            candidate = "${baseName}_$n.jpg"
            n++
        }
        return candidate
    }

    private fun String?.present(): String? = this?.takeIf { it.isNotBlank() }
}
