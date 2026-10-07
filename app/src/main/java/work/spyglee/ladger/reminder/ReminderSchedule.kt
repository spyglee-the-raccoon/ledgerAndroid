package work.spyglee.ladger.reminder

import java.time.LocalTime
import java.time.ZonedDateTime

/** When the daily "log today's expenses" reminder fires: every day at 20:30, as on iOS. */
object ReminderSchedule {
    val TIME: LocalTime = LocalTime.of(20, 30)

    /**
     * The first 20:30 after [now], in [now]'s time zone. On a day when 20:30 falls in a DST gap,
     * the reminder moves forward by the length of the gap.
     */
    fun nextTrigger(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(TIME).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(TIME).atZone(now.zone)
    }
}
