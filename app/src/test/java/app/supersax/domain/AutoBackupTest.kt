package app.supersax.domain

import app.supersax.work.AutoBackup
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoBackupTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test fun dueAfterTheChosenPeriodWithTwoHoursOfSlack() {
        val now = 100 * day
        assertTrue(AutoBackup.due(0, 1, now), "never backed up")
        assertTrue(AutoBackup.due(now - 22 * hour, 1, now), "daily: 22 h is enough")
        assertFalse(AutoBackup.due(now - 20 * hour, 1, now))
        assertFalse(AutoBackup.due(now - 3 * day, 7, now))
        assertTrue(AutoBackup.due(now - 7 * day, 7, now))
        assertFalse(AutoBackup.due(0, 0, now), "off")
    }
}
