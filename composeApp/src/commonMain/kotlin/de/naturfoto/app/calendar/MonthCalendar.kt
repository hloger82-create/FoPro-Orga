package de.naturfoto.app.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDate
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun MonthCalendar(
    month: LocalDate,
    eventDays: Set<Int>,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstOfMonth = month.withDayOfMonth(1)
    val daysInMonth = month.lengthOfMonth()
    val firstWeekday = firstOfMonth.dayOfWeek.ordinal // Mo=0

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            }
        }
        var day = 1
        repeat(6) { week ->
            if (day > daysInMonth) return@Column
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(7) { dow ->
                    val cellDay = if (week == 0 && dow < firstWeekday) 0 else if (day <= daysInMonth) day++ else 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    cellDay == selectedDay -> MaterialTheme.colorScheme.primary
                                    cellDay in eventDays -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .clickable(enabled = cellDay != 0) { onSelectDay(cellDay) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cellDay != 0) {
                            Text(
                                cellDay.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (cellDay == selectedDay) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

fun LocalDate.withDayOfMonth(day: Int): LocalDate =
    kotlinx.datetime.LocalDate(year, month, day.coerceIn(1, lengthOfMonth()))

fun LocalDate.lengthOfMonth(): Int = when (monthNumber) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 30
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
