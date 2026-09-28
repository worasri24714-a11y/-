package app.copyruler.ui.common

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Thai-style dates with Buddhist-era years, e.g. "28 ก.ย. 2569 14:05". */
object ThaiDateFormat {
    private val MONTHS = arrayOf(
        "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
        "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.",
    )

    private const val BUDDHIST_ERA_OFFSET = 543

    fun full(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        val year = time.year + BUDDHIST_ERA_OFFSET
        return "${time.dayOfMonth} ${MONTHS[time.monthValue - 1]} $year ${clock(millis, zone)}"
    }

    /** "วันนี้ 14:05", "เมื่อวาน 09:30" or the full date for anything older. */
    fun relative(
        millis: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (date) {
            today -> "วันนี้ ${clock(millis, zone)}"
            today.minusDays(1) -> "เมื่อวาน ${clock(millis, zone)}"
            else -> full(millis, zone)
        }
    }

    private fun clock(millis: Long, zone: ZoneId): String {
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        return "%02d:%02d".format(Locale.ROOT, time.hour, time.minute)
    }
}
