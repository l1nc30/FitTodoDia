package com.dlynce.fittododia.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dlynce.fittododia.data.db.dao.*
import com.dlynce.fittododia.data.db.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        WeekDayEntity::class,
        WorkoutEntity::class,
        ExerciseEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutLogEntity::class,
        StreakEntity::class,
        SettingsEntity::class,

        WorkoutSessionEntity::class,
        WorkoutSessionExerciseEntity::class,

        DailyMissionEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun weekDayDao(): WeekDayDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutExerciseDao(): WorkoutExerciseDao

    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun dailyMissionDao(): DailyMissionDao

    abstract fun streakDao(): StreakDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS workout_session (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workoutId INTEGER NOT NULL,
                        weekDayId INTEGER NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        startedAtEpochMs INTEGER NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        workoutNameSnapshot TEXT NOT NULL,
                        totalExercises INTEGER NOT NULL,
                        totalSetsPlanned INTEGER NOT NULL,
                        totalSetsDone INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_session_dateEpochDay ON workout_session(dateEpochDay)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_session_workoutId ON workout_session(workoutId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_session_weekDayId ON workout_session(weekDayId)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS workout_session_exercise (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        exerciseNameSnapshot TEXT NOT NULL,
                        muscleGroupSnapshot TEXT NOT NULL,
                        setsPlanned INTEGER NOT NULL,
                        setsDone INTEGER NOT NULL,
                        repsSnapshot TEXT NOT NULL,
                        restSecondsSnapshot INTEGER,
                        FOREIGN KEY(sessionId) REFERENCES workout_session(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_session_exercise_sessionId ON workout_session_exercise(sessionId)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_mission (
                        dateEpochDay INTEGER PRIMARY KEY NOT NULL,
                        missionKey TEXT NOT NULL,
                        completed INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fittododia.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(SeedCallback(context.applicationContext))
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }

    private class SeedCallback(
        private val appContext: Context
    ) : Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)

            CoroutineScope(Dispatchers.IO).launch {
                val database = getInstance(appContext)

                // 7 dias
                database.weekDayDao().upsertAll(
                    listOf(
                        WeekDayEntity(1, "Segunda"),
                        WeekDayEntity(2, "Terça"),
                        WeekDayEntity(3, "Quarta"),
                        WeekDayEntity(4, "Quinta"),
                        WeekDayEntity(5, "Sexta"),
                        WeekDayEntity(6, "Sábado"),
                        WeekDayEntity(7, "Domingo")
                    )
                )

                // Streak padrão
                database.streakDao().upsert(
                    StreakEntity(
                        id = 1,
                        currentStreak = 0,
                        lastWorkoutEpochDay = null
                    )
                )

                // Settings padrão
                database.settingsDao().upsert(
                    SettingsEntity(
                        id = 1,
                        adsRemoved = false,
                        reminderEnabled = false
                    )
                )

                // Exercícios seed (PADRONIZADOS)
                database.exerciseDao().upsertAll(
                    listOf(
                        // ===== PEITO =====
                        ExerciseEntity(name = "Supino reto (barra)", muscleGroup = "Peito", pngAssetPath = "images/supinoRetoBarra.png"),
                        ExerciseEntity(name = "Supino reto (halteres)", muscleGroup = "Peito", pngAssetPath = "images/supinoRetoHalteres.png"),
                        ExerciseEntity(name = "Supino inclinado (barra)", muscleGroup = "Peito", pngAssetPath = "images/supinoInclinadoBarra.png"),
                        ExerciseEntity(name = "Supino inclinado (halteres)", muscleGroup = "Peito", pngAssetPath = "images/supinoInclinadoHalteres.png"),
                        ExerciseEntity(name = "Supino declinado", muscleGroup = "Peito", pngAssetPath = "images/supinoDeclinado.png"),
                        ExerciseEntity(name = "Crucifixo reto", muscleGroup = "Peito", pngAssetPath = "images/crucifixoReto.png"),
                        ExerciseEntity(name = "Crucifixo inclinado", muscleGroup = "Peito", pngAssetPath = "images/crucifixoInclinado.png"),
                        ExerciseEntity(name = "Peck deck (voador)", muscleGroup = "Peito", pngAssetPath = "images/peckDeckVoador.png"),
                        ExerciseEntity(name = "Crossover (polia alta)", muscleGroup = "Peito", pngAssetPath = "images/crossoverPoliaAlta.png"),
                        ExerciseEntity(name = "Crossover (polia baixa)", muscleGroup = "Peito", pngAssetPath = "images/crossoverPoliaBaixa.png"),
                        ExerciseEntity(name = "Flexão de braço", muscleGroup = "Peito", pngAssetPath = "images/flexaoDeBraco.png"),
                        ExerciseEntity(name = "Flexão diamante", muscleGroup = "Peito", pngAssetPath = "images/flexaoDiamante.png"),
                        ExerciseEntity(name = "Pullover (halter)", muscleGroup = "Peito", pngAssetPath = "images/pulloverHalter.png"),
                        ExerciseEntity(name = "Supino máquina", muscleGroup = "Peito", pngAssetPath = "images/supinoMaquina.png"),

                        // ===== COSTAS =====
                        ExerciseEntity(name = "Puxada na frente (barra)", muscleGroup = "Costas", pngAssetPath = "images/costasPuxadaFrente.png"),
                        ExerciseEntity(name = "Puxada aberta", muscleGroup = "Costas", pngAssetPath = "images/costasPuxadaAberta.png"),
                        ExerciseEntity(name = "Puxada neutra", muscleGroup = "Costas", pngAssetPath = "images/costasPuxadaNeutra.png"),
                        ExerciseEntity(name = "Puxada supinada", muscleGroup = "Costas", pngAssetPath = "images/costasPuxadaSupinada.png"),
                        ExerciseEntity(name = "Barra fixa (assistida)", muscleGroup = "Costas", pngAssetPath = "images/costasBarraFixa.png"),
                        ExerciseEntity(name = "Remada baixa (cabo)", muscleGroup = "Costas", pngAssetPath = "images/costasRemadaBaixa.png"),
                        ExerciseEntity(name = "Remada curvada (barra)", muscleGroup = "Costas", pngAssetPath = "images/costasRemadaCurvada.png"),
                        ExerciseEntity(name = "Remada unilateral (halter)", muscleGroup = "Costas", pngAssetPath = "images/costasRemadaUniHalter.png"),
                        ExerciseEntity(name = "Remada cavalinho (T-bar)", muscleGroup = "Costas", pngAssetPath = "images/costasRemadaCavalinho.png"),
                        ExerciseEntity(name = "Remada máquina", muscleGroup = "Costas", pngAssetPath = "images/costasRemadaMaq.png"),
                        ExerciseEntity(name = "Pullover (cabo)", muscleGroup = "Costas", pngAssetPath = "images/costasPulloverCabo.png"),
                        ExerciseEntity(name = "Hiperextensão lombar", muscleGroup = "Costas", pngAssetPath = "images/costasLombar.png"),
                        ExerciseEntity(name = "Puxada máquina (neutra)", muscleGroup = "Costas", pngAssetPath = "images/costasPuxadaMaq.png"),

                        // ===== OMBROS =====
                        ExerciseEntity(name = "Desenvolvimento militar (barra)", muscleGroup = "Ombros", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Desenvolvimento (halteres)", muscleGroup = "Ombros", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Desenvolvimento máquina", muscleGroup = "Ombros", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Arnold press", muscleGroup = "Ombros", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Elevação lateral", muscleGroup = "Ombros", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Elevação lateral no cabo", muscleGroup = "Ombros", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Elevação frontal", muscleGroup = "Ombros", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Crucifixo inverso (peck deck)", muscleGroup = "Ombros", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Face pull (cabo)", muscleGroup = "Ombros", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Encolhimento (halteres)", muscleGroup = "Ombros", pngAssetPath = "images/.png"),

                        // ===== BÍCEPS =====
                        ExerciseEntity(name = "Rosca direta (barra)", muscleGroup = "Bíceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Rosca direta (barra W)", muscleGroup = "Bíceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Rosca alternada (halteres)", muscleGroup = "Bíceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Rosca martelo", muscleGroup = "Bíceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Rosca concentrada", muscleGroup = "Bíceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Rosca Scott", muscleGroup = "Bíceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Rosca no cabo (barra)", muscleGroup = "Bíceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Rosca no cabo (corda)", muscleGroup = "Bíceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Rosca 21", muscleGroup = "Bíceps", pngAssetPath = "images/.png"),

                        // ===== TRÍCEPS =====
                        ExerciseEntity(name = "Tríceps pulley (barra)", muscleGroup = "Tríceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Tríceps corda", muscleGroup = "Tríceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Tríceps francês (halter)", muscleGroup = "Tríceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Tríceps testa (barra W)", muscleGroup = "Tríceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Tríceps coice (kickback)", muscleGroup = "Tríceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Mergulho no banco", muscleGroup = "Tríceps", pngAssetPath = "images/.png"),
                        ExerciseEntity(name = "Paralelas (assistida)", muscleGroup = "Tríceps", pngAssetPath = "images/.png"),

                        // ===== PERNAS (inclui glúteos/posterior/panturrilha/adutores) =====
                        ExerciseEntity(name = "Agachamento livre", muscleGroup = "Pernas", pngAssetPath = "images/pernaAgachamentoLivre.png"),
                        ExerciseEntity(name = "Agachamento no Smith", muscleGroup = "Pernas", pngAssetPath = "images/pernasAgachamentoSmith.png"),
                        ExerciseEntity(name = "Agachamento sumô", muscleGroup = "Pernas", pngAssetPath = "images/"),
                        ExerciseEntity(name = "Leg press 45°", muscleGroup = "Pernas", pngAssetPath = "images/pernasLegpress45.png"),
                        ExerciseEntity(name = "Leg press horizontal", muscleGroup = "Pernas", pngAssetPath = "images/pernasLegpressHorizontal.png"),
                        ExerciseEntity(name = "Cadeira extensora", muscleGroup = "Pernas", pngAssetPath = "images/pernasCadeiraExtensora.png"),
                        ExerciseEntity(name = "Cadeira flexora", muscleGroup = "Pernas", pngAssetPath = "images/pernasCadeiraFlex.png"),
                        ExerciseEntity(name = "Mesa flexora", muscleGroup = "Pernas", pngAssetPath = "images/pernasMesaFlexora.png"),
                        ExerciseEntity(name = "Hack squat", muscleGroup = "Pernas", pngAssetPath = "images/pernasHackSquat.png"),
                        ExerciseEntity(name = "Afundo (halteres)", muscleGroup = "Pernas", pngAssetPath = "images/pernasAfundoHalteres.png"),
                        ExerciseEntity(name = "Passada (walking lunge)", muscleGroup = "Pernas", pngAssetPath = "images/pernasPassada.png"),
                        ExerciseEntity(name = "Stiff", muscleGroup = "Pernas", pngAssetPath = "images/pernasStiff.png"),
                        ExerciseEntity(name = "Terra romeno", muscleGroup = "Pernas", pngAssetPath = "images/pernasTerraRomeno.png"),
                        ExerciseEntity(name = "Terra (tradicional)", muscleGroup = "Pernas", pngAssetPath = "images/pernasTerraTrad.png"),
                        ExerciseEntity(name = "Elevação pélvica (hip thrust)", muscleGroup = "Pernas", pngAssetPath = "images/pernasElevacaoP.png"),
                        ExerciseEntity(name = "Glúteo no cabo (kickback)", muscleGroup = "Pernas", pngAssetPath = "images/pernasGruteoCabo.png"),
                        ExerciseEntity(name = "Cadeira abdutora", muscleGroup = "Pernas", pngAssetPath = "images/pernasCadeiraAb.png"),
                        ExerciseEntity(name = "Cadeira adutora", muscleGroup = "Pernas", pngAssetPath = "images/pernasCadeiraAd.png"),
                        ExerciseEntity(name = "Panturrilha em pé", muscleGroup = "Pernas", pngAssetPath = "images/pernasPanturrilhaPe.png"),
                        ExerciseEntity(name = "Panturrilha sentada", muscleGroup = "Pernas", pngAssetPath = "images/pernasPanturrilhaSentado.png"),

                        // ===== CORE/ABDÔMEN (junto) =====
                        ExerciseEntity(name = "Prancha", muscleGroup = "Core/Abdômen", pngAssetPath = ""),
                        ExerciseEntity(name = "Prancha lateral", muscleGroup = "Core/Abdômen", pngAssetPath = ""),
                        ExerciseEntity(name = "Abdominal crunch", muscleGroup = "Core/Abdômen", pngAssetPath = ""),
                        ExerciseEntity(name = "Infra (elevação de pernas)", muscleGroup = "Core/Abdômen", pngAssetPath = ""),
                        ExerciseEntity(name = "Abdominal na polia (cable crunch)", muscleGroup = "Core/Abdômen", pngAssetPath = ""),
                        ExerciseEntity(name = "Russian twist", muscleGroup = "Core/Abdômen", pngAssetPath = ""),
                        ExerciseEntity(name = "Dead bug", muscleGroup = "Core/Abdômen", pngAssetPath = ""),
                        ExerciseEntity(name = "Mountain climber", muscleGroup = "Core/Abdômen", pngAssetPath = ""),

                        // ===== CARDIO =====
                        ExerciseEntity(name = "Esteira (caminhada/corrida)", muscleGroup = "Cardio", pngAssetPath = "images/cardioEsteira.png"),
                        ExerciseEntity(name = "Bike ergométrica", muscleGroup = "Cardio", pngAssetPath = "images/cardioBike.png"),
                        ExerciseEntity(name = "Elíptico", muscleGroup = "Cardio", pngAssetPath = "images/cardioElip.png"),
                        ExerciseEntity(name = "Remo ergômetro", muscleGroup = "Cardio", pngAssetPath = "images/cardioRemoErgo.png")
                    )
                )

            }
        }
    }
}
