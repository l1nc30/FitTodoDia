package com.dlynce.fittododia.utils

import kotlin.math.max
import kotlin.math.min

fun estimateXpSafe(exercises: Int, plannedSets: Int, doneSets: Int): Int {
    val base = 90
    val ex = 10 * max(0, exercises)
    val planned = max(0, plannedSets)
    val doneCapped = min(max(0, doneSets), planned)
    val perSet = 2 * doneCapped
    val completionBonus = if (planned > 0 && doneCapped == planned) 25 else 0
    return base + ex + perSet + completionBonus
}

fun xpToLevel(totalXp: Int): Pair<Int, Float> {
    val xp = max(0, totalXp)
    val perLevel = 500
    val level = (xp / perLevel) + 1
    val inLevel = xp % perLevel
    val progress = inLevel.toFloat() / perLevel.toFloat()
    return level to progress
}

fun xpToNextLevel(totalXp: Int): Int {
    val xp = max(0, totalXp)
    val perLevel = 500
    val inLevel = xp % perLevel
    return perLevel - inLevel
}
