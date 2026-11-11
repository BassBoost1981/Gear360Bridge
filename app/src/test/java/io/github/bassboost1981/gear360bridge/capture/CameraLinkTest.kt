package io.github.bassboost1981.gear360bridge.capture

import io.github.bassboost1981.gear360bridge.osc.CameraInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraLinkTest {
    @Test fun isGear360_trueForTestedCamera() =
        assertTrue(CameraLink.isGear360(CameraInfo("GEAR 360", "TESTSERIAL01", "C200GLU0API1")))

    @Test fun isGear360_falseForOtherModel() =
        assertFalse(CameraLink.isGear360(CameraInfo("RICOH THETA", "1", "1")))

    @Test fun toPercent_rounds() {
        assertEquals(70, CameraLink.toPercent(0.7f))
        assertEquals(100, CameraLink.toPercent(1.0f))
        assertEquals(5, CameraLink.toPercent(0.049f))
        assertEquals(100, CameraLink.toPercent(1.4f))
        assertEquals(0, CameraLink.toPercent(-0.2f))
    }
}
