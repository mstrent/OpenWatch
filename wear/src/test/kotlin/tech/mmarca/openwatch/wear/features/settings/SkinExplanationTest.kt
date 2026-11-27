package tech.mmarca.openwatch.wear.features.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.mmarca.openwatch.wear.R

class SkinExplanationTest {

    @Test
    fun `a raw-sensor watch says all day, the Samsung platform at night`() {
        assertEquals(R.string.settings_skin_explained_all_day, skinExplanation(samsung = false))
        assertEquals(R.string.settings_skin_explained, skinExplanation(samsung = true))
    }
}
