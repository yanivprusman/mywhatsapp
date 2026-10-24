package com.automatelinux.mywhatsapp.data

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

private val months = listOf("January", "February", "March", "April", "May", "June", "July",
    "August", "September", "October", "November", "December")
private val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

fun localTime(ts: String): LocalDateTime =
    Instant.parse(ts).toLocalDateTime(TimeZone.currentSystemDefault())

private fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun two(n: Int) = n.toString().padStart(2, '0')

fun clock(ts: String): String = localTime(ts).let { "${two(it.hour)}:${two(it.minute)}" }

/** Chat-list stamp: time today, "Yesterday", weekday this week, else dd/mm/yyyy. */
fun listStamp(ts: String): String {
    val t = localTime(ts)
    val d = t.date
    val now = today()
    return when {
        d == now -> "${two(t.hour)}:${two(t.minute)}"
        d == now.minus(1, DateTimeUnit.DAY) -> "Yesterday"
        d > now.minus(7, DateTimeUnit.DAY) -> weekdays[d.dayOfWeek.ordinal]
        else -> "${two(d.dayOfMonth)}/${two(d.monthNumber)}/${d.year}"
    }
}

/**
 * Date chip inside a conversation. Always carries the calendar date — a bare
 * "Wednesday" leaves you counting back days — and the year only when it isn't this one.
 */
fun dayLabel(d: LocalDate): String {
    val now = today()
    val date = "${d.dayOfMonth} ${months[d.monthNumber - 1]}" + if (d.year != now.year) " ${d.year}" else ""
    return when {
        d == now -> "Today · $date"
        d == now.minus(1, DateTimeUnit.DAY) -> "Yesterday · $date"
        else -> "${weekdays[d.dayOfWeek.ordinal]}, $date"
    }
}

/** Compact date for tight spots: "Thu 22 Oct · 2d", "Today · 24 Oct". */
fun shortDay(d: LocalDate): String {
    val now = today()
    val n = now.toEpochDays() - d.toEpochDays()
    val date = "${d.dayOfMonth} ${months[d.monthNumber - 1].take(3)}" + if (d.year != now.year) " ${d.year % 100}" else ""
    return when (n) {
        0 -> "Today · $date"
        1 -> "Yesterday · $date"
        else -> "${weekdays[d.dayOfWeek.ordinal].take(3)} $date · ${n}d"
    }
}

/** How long ago, in whole days — what matters for a job post. */
fun daysAgo(d: LocalDate): String {
    val n = today().toEpochDays() - d.toEpochDays()
    return when {
        n <= 0 -> "today"
        n == 1 -> "1 day ago"
        n < 60 -> "$n days ago"
        else -> "${n / 30} months ago"
    }
}

/** Files the bridge sent carry a numeric upload prefix ("1792840097052791053-name.pdf"). */
fun displayFilename(name: String): String = name.replace(Regex("^\\d{12,}-"), "")

fun mediaLabel(m: Message): String = when (m.mediaType) {
    "image" -> "Photo"
    "video" -> "Video"
    "audio" -> "Voice message"
    "document" -> displayFilename(m.filename).ifBlank { "Document" }
    else -> ""
}
