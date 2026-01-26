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

                database.streakDao().upsert(
                    StreakEntity(
                        id = 1,
                        currentStreak = 0,
                        lastWorkoutEpochDay = null
                    )
                )

                database.settingsDao().upsert(
                    SettingsEntity(
                        id = 1,
                        adsRemoved = false,
                        reminderEnabled = false
                    )
                )

                database.exerciseDao().upsertAll(
                    listOf(
                        ExerciseEntity(name = "Flexão de braço", muscleGroup = "Peito", gifAssetPath = "gifs/pushup.gif"),
                        ExerciseEntity(name = "Agachamento livre", muscleGroup = "Pernas", gifAssetPath = "gifs/squat.gif"),
                        ExerciseEntity(name = "Prancha", muscleGroup = "Core", gifAssetPath = "gifs/plank.gif"),
                        ExerciseEntity(name = "Remada unilateral", muscleGroup = "Costas", gifAssetPath = "gifs/one_arm_row.gif"),
                        ExerciseEntity(name = "Desenvolvimento com halteres", muscleGroup = "Ombros", gifAssetPath = "gifs/dumbbell_press.gif"),
                        ExerciseEntity(name = "Rosca direta", muscleGroup = "Bíceps", gifAssetPath = "gifs/biceps_curl.gif"),
                        ExerciseEntity(name = "Tríceps testa", muscleGroup = "Tríceps", gifAssetPath = "gifs/skullcrusher.gif")
                    )
                )
            }
        }
    }
}
