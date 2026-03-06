package com.dlynce.fittododia.utils

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate

fun baseKeyForDayOfWeek(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "segunda"
    DayOfWeek.TUESDAY -> "terca"
    DayOfWeek.WEDNESDAY -> "quarta"
    DayOfWeek.THURSDAY -> "quinta"
    DayOfWeek.FRIDAY -> "sexta"
    DayOfWeek.SATURDAY -> "sabado"
    DayOfWeek.SUNDAY -> "domingo"
}

fun labelForDayOfWeek(d: DayOfWeek): String = when (d) {
    DayOfWeek.MONDAY -> "Segunda"
    DayOfWeek.TUESDAY -> "Terça"
    DayOfWeek.WEDNESDAY -> "Quarta"
    DayOfWeek.THURSDAY -> "Quinta"
    DayOfWeek.FRIDAY -> "Sexta"
    DayOfWeek.SATURDAY -> "Sábado"
    DayOfWeek.SUNDAY -> "Domingo"
}

fun fallbackDayId(d: DayOfWeek): Int = when (d) {
    DayOfWeek.MONDAY -> 1
    DayOfWeek.TUESDAY -> 2
    DayOfWeek.WEDNESDAY -> 3
    DayOfWeek.THURSDAY -> 4
    DayOfWeek.FRIDAY -> 5
    DayOfWeek.SATURDAY -> 6
    DayOfWeek.SUNDAY -> 7
}

fun normalizeDayName(name: String): String {
    val lower = name.trim().lowercase()
    val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
    val noFeira = noAccents.replace("-feira", "").replace(" feira", "")
    return noFeira.replace("\\s+".toRegex(), " ").trim()
}

fun computeCurrentStreak(todayEpochDay: Long, daysDesc: List<Long>): Int {
    if (daysDesc.isEmpty()) return 0
    val set = daysDesc.toHashSet()
    var start = todayEpochDay
    if (!set.contains(start)) start = todayEpochDay - 1
    if (!set.contains(start)) return 0
    var streak = 0
    var cur = start
    while (set.contains(cur)) {
        streak++
        cur -= 1
    }
    return streak
}

fun formatEpochDay(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val dow = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Seg"
        DayOfWeek.TUESDAY -> "Ter"
        DayOfWeek.WEDNESDAY -> "Qua"
        DayOfWeek.THURSDAY -> "Qui"
        DayOfWeek.FRIDAY -> "Sex"
        DayOfWeek.SATURDAY -> "Sáb"
        DayOfWeek.SUNDAY -> "Dom"
    }
    return "$dow • ${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthValue.toString().padStart(2, '0')}"
}
