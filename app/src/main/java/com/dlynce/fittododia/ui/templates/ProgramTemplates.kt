package com.dlynce.fittododia.ui.templates

/* ============================================================
 * TIPOS BÁSICOS
 * ============================================================ */

enum class GoalType {
    HIPERTROFIA,
    EMAGRECIMENTO,
    INICIANTE,
    FORCA
}

data class ProgramTemplate(
    val id: String,
    val title: String,
    val goal: GoalType,
    val daysPerWeek: Int,     // 2,3,4,5
    val split: String,        // "Upper/Lower", "PPL", "ABCD", ...
    val level: String,        // "Iniciante", "Intermediário"
    val durationWeeks: Int,   // sugestão: 8
    val description: String,
    val workouts: List<WorkoutTemplate>
)

data class WorkoutTemplate(
    val name: String,
    val exercises: List<WorkoutExerciseTemplate>
)

data class WorkoutExerciseTemplate(
    val exerciseId: Long,
    val sets: Int,
    val reps: String,
    val restSeconds: Int?
)

/* ============================================================
 * IDs REAIS DOS EXERCÍCIOS (BATEM COM O SEED DO BANCO)
 * ============================================================ */

private object ExId {

    // ===== PEITO =====
    const val SUPINO_RETO_BARRA = 1L
    const val SUPINO_RETO_HALTER = 2L
    const val SUPINO_INCLINADO_BARRA = 3L
    const val SUPINO_INCLINADO_HALTER = 4L
    const val SUPINO_MAQUINA = 14L

    // ===== COSTAS =====
    const val PUXADA_FRENTE_BARRA = 15L
    const val REMADA_BAIXA_CABO = 20L
    const val REMADA_CURVADA_BARRA = 21L
    const val REMADA_UNILATERAL_HALTER = 22L
    const val REMADA_CAVALINHO = 23L
    const val REMADA_MAQUINA = 24L
    const val PULLOVER_CABO = 25L
    const val HIPEREXTENSAO_LOMBAR = 26L

    // ===== OMBROS =====
    const val DESENVOLVIMENTO_MILITAR_BARRA = 28L
    const val DESENVOLVIMENTO_HALTER = 29L
    const val DESENVOLVIMENTO_MAQUINA = 30L
    const val ELEVACAO_LATERAL = 32L
    const val FACE_PULL = 36L

    // ===== PERNAS =====
    const val AGACHAMENTO_LIVRE = 54L
    const val LEG_PRESS_45 = 57L
    const val CADEIRA_EXTENSORA = 59L
    const val CADEIRA_FLEXORA = 60L
    const val STIFF = 65L
    const val TERRA_ROMENO = 66L
    const val HIP_THRUST = 68L
}

/* ============================================================
 * REPOSITÓRIO DE TEMPLATES (CATÁLOGO)
 * ============================================================ */

object ProgramTemplatesRepo {

    val all: List<ProgramTemplate> = listOf(

        /* ====================================================
         * INICIANTE 3x — FULL BODY
         * ==================================================== */
        ProgramTemplate(
            id = "iniciante_3x_full",
            title = "Iniciante 3x – Full Body",
            goal = GoalType.INICIANTE,
            daysPerWeek = 3,
            split = "Full Body",
            level = "Iniciante",
            durationWeeks = 8,
            description = "Treinos completos para quem está começando ou retornando. Foco em técnica e consistência.",
            workouts = listOf(
                WorkoutTemplate(
                    name = "Full Body A",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.LEG_PRESS_45, 3, "10–12", 120),
                        WorkoutExerciseTemplate(ExId.SUPINO_RETO_HALTER, 3, "10–12", 120),
                        WorkoutExerciseTemplate(ExId.REMADA_MAQUINA, 3, "10–12", 120),
                        WorkoutExerciseTemplate(ExId.ELEVACAO_LATERAL, 2, "12–15", 60),
                        WorkoutExerciseTemplate(ExId.FACE_PULL, 2, "12–15", 60)
                    )
                ),
                WorkoutTemplate(
                    name = "Full Body B",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.AGACHAMENTO_LIVRE, 3, "8–10", 150),
                        WorkoutExerciseTemplate(ExId.SUPINO_MAQUINA, 3, "10–12", 120),
                        WorkoutExerciseTemplate(ExId.PUXADA_FRENTE_BARRA, 3, "10–12", 120),
                        WorkoutExerciseTemplate(ExId.TERRA_ROMENO, 2, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.FACE_PULL, 2, "12–15", 60)
                    )
                )
            )
        ),

        /* ====================================================
         * AB — UPPER/LOWER 2x/SEMANA
         * ==================================================== */
        ProgramTemplate(
            id = "ab_upper_lower_2x",
            title = "AB – Upper/Lower (2x/semana)",
            goal = GoalType.HIPERTROFIA,
            daysPerWeek = 2,
            split = "Upper/Lower",
            level = "Iniciante",
            durationWeeks = 8,
            description = "Para treinar 2 dias: um dia de superiores (Upper) e um de inferiores (Lower). Simples e eficiente.",
            workouts = listOf(
                WorkoutTemplate(
                    name = "A — Upper",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.SUPINO_RETO_BARRA, 3, "6–10", 120),
                        WorkoutExerciseTemplate(ExId.REMADA_BAIXA_CABO, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.PUXADA_FRENTE_BARRA, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.DESENVOLVIMENTO_HALTER, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.ELEVACAO_LATERAL, 2, "12–20", 60),
                        WorkoutExerciseTemplate(ExId.FACE_PULL, 2, "12–20", 60)
                    )
                ),
                WorkoutTemplate(
                    name = "B — Lower",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.AGACHAMENTO_LIVRE, 3, "6–10", 150),
                        WorkoutExerciseTemplate(ExId.LEG_PRESS_45, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.TERRA_ROMENO, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.CADEIRA_EXTENSORA, 2, "10–15", 75),
                        WorkoutExerciseTemplate(ExId.CADEIRA_FLEXORA, 2, "10–15", 75)
                    )
                )
            )
        ),

        /* ====================================================
         * HIPERTROFIA 3x — HALF BODY (A / B / C)
         * ==================================================== */
        ProgramTemplate(
            id = "hipertrofia_3x_half",
            title = "Hipertrofia 3x – Half Body (A/B/C)",
            goal = GoalType.HIPERTROFIA,
            daysPerWeek = 3,
            split = "Half Body",
            level = "Iniciante",
            durationWeeks = 8,
            description = "Plano base para ganho de massa: alterna superiores e inferiores com volume moderado.",
            workouts = listOf(
                WorkoutTemplate(
                    name = "Upper A",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.SUPINO_RETO_BARRA, 3, "6–10", 120),
                        WorkoutExerciseTemplate(ExId.REMADA_BAIXA_CABO, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.DESENVOLVIMENTO_MILITAR_BARRA, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.PUXADA_FRENTE_BARRA, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.ELEVACAO_LATERAL, 2, "12–20", 60),
                        WorkoutExerciseTemplate(ExId.FACE_PULL, 2, "12–20", 60)
                    )
                ),
                WorkoutTemplate(
                    name = "Lower B",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.LEG_PRESS_45, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.TERRA_ROMENO, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.CADEIRA_EXTENSORA, 3, "10–15", 75),
                        WorkoutExerciseTemplate(ExId.CADEIRA_FLEXORA, 3, "10–15", 75)
                    )
                ),
                WorkoutTemplate(
                    name = "Upper / Posterior C",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.SUPINO_INCLINADO_BARRA, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.REMADA_UNILATERAL_HALTER, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.HIP_THRUST, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.REMADA_CURVADA_BARRA, 2, "8–12", 120)
                    )
                )
            )
        ),

        /* ====================================================
         * ABC — PUSH/PULL/LEGS 3x/SEMANA
         * ==================================================== */
        ProgramTemplate(
            id = "abc_ppl_3x",
            title = "ABC – Push/Pull/Legs (3x/semana)",
            goal = GoalType.HIPERTROFIA,
            daysPerWeek = 3,
            split = "PPL",
            level = "Intermediário",
            durationWeeks = 8,
            description = "Divisão clássica: Push (peito/ombro), Pull (costas) e Legs (pernas). Boa distribuição de volume semanal.",
            workouts = listOf(
                WorkoutTemplate(
                    name = "A — Push",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.SUPINO_RETO_BARRA, 3, "6–10", 120),
                        WorkoutExerciseTemplate(ExId.SUPINO_INCLINADO_HALTER, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.DESENVOLVIMENTO_MILITAR_BARRA, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.ELEVACAO_LATERAL, 3, "12–20", 60),
                        WorkoutExerciseTemplate(ExId.FACE_PULL, 2, "12–20", 60)
                    )
                ),
                WorkoutTemplate(
                    name = "B — Pull",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.PUXADA_FRENTE_BARRA, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.REMADA_CURVADA_BARRA, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.REMADA_UNILATERAL_HALTER, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.PULLOVER_CABO, 2, "12–15", 75),
                        WorkoutExerciseTemplate(ExId.HIPEREXTENSAO_LOMBAR, 2, "12–15", 60)
                    )
                ),
                WorkoutTemplate(
                    name = "C — Legs",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.AGACHAMENTO_LIVRE, 3, "6–10", 150),
                        WorkoutExerciseTemplate(ExId.LEG_PRESS_45, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.TERRA_ROMENO, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.CADEIRA_EXTENSORA, 3, "10–15", 75),
                        WorkoutExerciseTemplate(ExId.CADEIRA_FLEXORA, 3, "10–15", 75)
                    )
                )
            )
        ),

        /* ====================================================
         * ABCD — UPPER/LOWER 2x (4x/SEMANA)
         * ==================================================== */
        ProgramTemplate(
            id = "abcd_upper_lower_4x",
            title = "ABCD – Upper/Lower (4x/semana)",
            goal = GoalType.HIPERTROFIA,
            daysPerWeek = 4,
            split = "Upper/Lower (2x)",
            level = "Intermediário",
            durationWeeks = 8,
            description = "4 dias com frequência 2x por grupo: Upper A, Lower B, Upper C, Lower D. Ótimo para progredir mais rápido.",
            workouts = listOf(
                WorkoutTemplate(
                    name = "A — Upper (força base)",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.SUPINO_RETO_BARRA, 4, "5–8", 150),
                        WorkoutExerciseTemplate(ExId.REMADA_CURVADA_BARRA, 4, "6–10", 150),
                        WorkoutExerciseTemplate(ExId.DESENVOLVIMENTO_MILITAR_BARRA, 3, "6–10", 120),
                        WorkoutExerciseTemplate(ExId.PUXADA_FRENTE_BARRA, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.ELEVACAO_LATERAL, 2, "12–20", 60)
                    )
                ),
                WorkoutTemplate(
                    name = "B — Lower (força base)",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.AGACHAMENTO_LIVRE, 4, "5–8", 180),
                        WorkoutExerciseTemplate(ExId.TERRA_ROMENO, 3, "6–10", 150),
                        WorkoutExerciseTemplate(ExId.LEG_PRESS_45, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.CADEIRA_EXTENSORA, 2, "10–15", 75),
                        WorkoutExerciseTemplate(ExId.CADEIRA_FLEXORA, 2, "10–15", 75)
                    )
                ),
                WorkoutTemplate(
                    name = "C — Upper (volume)",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.SUPINO_INCLINADO_BARRA, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.REMADA_BAIXA_CABO, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.DESENVOLVIMENTO_HALTER, 3, "10–12", 120),
                        WorkoutExerciseTemplate(ExId.REMADA_UNILATERAL_HALTER, 2, "10–12", 120),
                        WorkoutExerciseTemplate(ExId.FACE_PULL, 2, "12–20", 60)
                    )
                ),
                WorkoutTemplate(
                    name = "D — Lower (volume)",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.LEG_PRESS_45, 4, "10–12", 150),
                        WorkoutExerciseTemplate(ExId.HIP_THRUST, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.STIFF, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.CADEIRA_EXTENSORA, 3, "12–15", 75),
                        WorkoutExerciseTemplate(ExId.CADEIRA_FLEXORA, 3, "12–15", 75)
                    )
                )
            )
        ),

        /* ====================================================
         * ABCDE — 5x/SEMANA (DIVISÃO POR GRUPOS)
         * ==================================================== */
        ProgramTemplate(
            id = "abcde_5x",
            title = "ABCDE – 5x/semana (divisão por grupos)",
            goal = GoalType.HIPERTROFIA,
            daysPerWeek = 5,
            split = "ABCDE",
            level = "Intermediário",
            durationWeeks = 8,
            description = "5 dias com maior volume semanal. Bom para quem treina quase todos os dias e quer trabalhar detalhes.",
            workouts = listOf(
                WorkoutTemplate(
                    name = "A — Peito",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.SUPINO_RETO_BARRA, 4, "6–10", 150),
                        WorkoutExerciseTemplate(ExId.SUPINO_INCLINADO_HALTER, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.SUPINO_MAQUINA, 3, "10–12", 120)
                    )
                ),
                WorkoutTemplate(
                    name = "B — Costas",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.PUXADA_FRENTE_BARRA, 4, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.REMADA_CURVADA_BARRA, 4, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.REMADA_BAIXA_CABO, 3, "10–12", 120),
                        WorkoutExerciseTemplate(ExId.PULLOVER_CABO, 2, "12–15", 75)
                    )
                ),
                WorkoutTemplate(
                    name = "C — Pernas",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.AGACHAMENTO_LIVRE, 4, "6–10", 180),
                        WorkoutExerciseTemplate(ExId.LEG_PRESS_45, 4, "10–12", 150),
                        WorkoutExerciseTemplate(ExId.TERRA_ROMENO, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.CADEIRA_EXTENSORA, 3, "12–15", 75),
                        WorkoutExerciseTemplate(ExId.CADEIRA_FLEXORA, 3, "12–15", 75)
                    )
                ),
                WorkoutTemplate(
                    name = "D — Ombros",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.DESENVOLVIMENTO_MILITAR_BARRA, 4, "6–10", 150),
                        WorkoutExerciseTemplate(ExId.DESENVOLVIMENTO_HALTER, 3, "8–12", 120),
                        WorkoutExerciseTemplate(ExId.ELEVACAO_LATERAL, 4, "12–20", 60),
                        WorkoutExerciseTemplate(ExId.FACE_PULL, 3, "12–20", 60)
                    )
                ),
                WorkoutTemplate(
                    name = "E — Posterior/Glúteos (reforço)",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.HIP_THRUST, 4, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.STIFF, 3, "8–12", 150),
                        WorkoutExerciseTemplate(ExId.HIPEREXTENSAO_LOMBAR, 3, "12–15", 60)
                    )
                )
            )
        ),

        /* ====================================================
         * EMAGRECIMENTO 3x — FULL BODY
         * ==================================================== */
        ProgramTemplate(
            id = "emagrecimento_3x_full",
            title = "Emagrecimento 3x – Full Body",
            goal = GoalType.EMAGRECIMENTO,
            daysPerWeek = 3,
            split = "Full Body",
            level = "Iniciante",
            durationWeeks = 6,
            description = "Foco em gasto calórico mantendo força. Combine com 15–25 min de cardio após o treino.",
            workouts = listOf(
                WorkoutTemplate(
                    name = "Full Body Metabólico",
                    exercises = listOf(
                        WorkoutExerciseTemplate(ExId.AGACHAMENTO_LIVRE, 3, "10–12", 120),
                        WorkoutExerciseTemplate(ExId.SUPINO_RETO_BARRA, 3, "10–12", 120),
                        WorkoutExerciseTemplate(ExId.REMADA_CURVADA_BARRA, 3, "10–12", 120),
                        WorkoutExerciseTemplate(ExId.DESENVOLVIMENTO_HALTER, 2, "12–15", 90),
                        WorkoutExerciseTemplate(ExId.HIPEREXTENSAO_LOMBAR, 2, "12–15", 60)
                    )
                )
            )
        )
    )

    fun goals(): List<GoalType> =
        all.map { it.goal }.distinct()

    fun programsByGoal(goal: GoalType): List<ProgramTemplate> =
        all.filter { it.goal == goal }

    fun byId(id: String): ProgramTemplate =
        all.first { it.id == id }
}