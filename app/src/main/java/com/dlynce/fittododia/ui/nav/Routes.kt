package com.dlynce.fittododia.ui.nav

sealed class Route(val path: String) {
    data object Onboarding : Route("onboarding")
    data object Home : Route("home")
    data object Agenda : Route("agenda")
    data object Treino : Route("treino")
    data object Progresso : Route("progresso")
    data object Settings : Route("settings")

    data object EditWorkout : Route("editWorkout/{dayId}") {
        fun create(dayId: Int) = "editWorkout/$dayId"
    }

    data object Library : Route("library/{dayId}") {
        fun create(dayId: Int) = "library/$dayId"
    }

    data object AddExercise : Route("addExercise/{dayId}/{exerciseId}") {
        fun create(dayId: Int, exerciseId: Long) = "addExercise/$dayId/$exerciseId"
    }

    data object Programs : Route("programs")

    data object ProgramDetail : Route("programDetail/{programId}") {
        fun create(programId: String) = "programDetail/$programId"
    }
}
