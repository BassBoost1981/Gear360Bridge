package io.github.bassboost1981.gear360bridge.filing

import org.junit.Assert.assertEquals
import org.junit.Test

class PathBuilderTest {
    private val label = PhotoLabel("Musterstraße 12", "2026-11-10", "2. OG", "5", "Bad")
    private val all = FilingSettings(useFlat = true)

    @Test fun folder_dateBelowSite() =
        assertEquals(listOf("Musterstraße 12", "2026-11-10", "2. OG", "Whg 5"), PathBuilder.folder(label, all))

    @Test fun folder_dateAboveSite() =
        assertEquals(
            listOf("2026-11-10", "Musterstraße 12", "2. OG", "Whg 5"),
            PathBuilder.folder(label, all.copy(dateAboveSite = true)),
        )

    @Test fun folder_uncheckedPartsAreOmitted() =
        assertEquals(
            listOf("Musterstraße 12"),
            PathBuilder.folder(label, FilingSettings(useDate = false, useFloor = false, useFlat = false)),
        )

    @Test fun folder_blankFloorIsOmitted() =
        assertEquals(
            listOf("Musterstraße 12", "2026-11-10", "Whg 5"),
            PathBuilder.folder(label.copy(floor = "  "), all),
        )

    @Test fun folder_nullFlatIsOmitted() =
        assertEquals(
            listOf("Musterstraße 12", "2026-11-10", "2. OG"),
            PathBuilder.folder(label.copy(flat = null), all),
        )

    @Test fun folder_partsAreSanitized() =
        assertEquals(
            listOf("Haus A-B", "2026-11-10", "EG"),
            PathBuilder.folder(PhotoLabel("Haus A/B.", "2026-11-10", "EG.", null, "Bad"), FilingSettings()),
        )

    @Test fun baseName_roomOrFoto() {
        assertEquals("Bad", PathBuilder.baseName(label, all))
        assertEquals("Foto", PathBuilder.baseName(label, all.copy(useRoom = false)))
        assertEquals("Foto", PathBuilder.baseName(label.copy(room = null), all))
        assertEquals("Foto", PathBuilder.baseName(label.copy(room = " "), all))
        assertEquals("Bad-WC", PathBuilder.baseName(label.copy(room = "Bad/WC"), all))
    }

    @Test fun fileName_numbering() {
        assertEquals("Bad.jpg", PathBuilder.fileName("Bad", emptySet()))
        assertEquals("Bad_2.jpg", PathBuilder.fileName("Bad", setOf("Bad.jpg")))
        assertEquals("Bad_3.jpg", PathBuilder.fileName("Bad", setOf("bad.JPG", "Bad_2.jpg")))
        assertEquals("Bad.jpg", PathBuilder.fileName("Bad", setOf("Bad_2.jpg", "Küche.jpg")))
    }

    @Test fun sanitize_cases() {
        assertEquals("Bad-WC", PathBuilder.sanitize("Bad/WC"))
        assertEquals("EG", PathBuilder.sanitize(" EG. "))
        assertEquals("Unbenannt", PathBuilder.sanitize("   "))
        assertEquals("a-b-c-d-e-f-g-h-i", PathBuilder.sanitize("a\\b:c*d?e\"f<g>h|i"))
        assertEquals(60, PathBuilder.sanitize("x".repeat(200)).length)
        assertEquals("Unbenannt", PathBuilder.sanitize(".."))
        assertEquals("2. OG", PathBuilder.sanitize("2. OG"))
        assertEquals("a b", PathBuilder.sanitize("a\n\tb"))
    }
}
