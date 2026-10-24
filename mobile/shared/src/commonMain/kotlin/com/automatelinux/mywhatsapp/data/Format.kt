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

/** Date chip inside a conversation. */
fun dayLabel(d: LocalDate): String {
    val now = today()
    return when {
        d == now -> "Today"
        d == now.minus(1, DateTimeUnit.DAY) -> "Yesterday"
        d > now.minus(7, DateTimeUnit.DAY) -> weekdays[d.dayOfWeek.ordinal]
        else -> "${d.dayOfMonth} ${months[d.monthNumber - 1]} ${d.year}"
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
