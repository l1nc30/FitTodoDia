# ── Stack traces legíveis no Crashlytics/logcat ──────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Room ─────────────────────────────────────────────────────────────────
# Entidades: mantém campos mapeados para colunas
-keep class com.dlynce.fittododia.data.db.entities.** { *; }
# DAOs gerados pelo KSP
-keep interface com.dlynce.fittododia.data.db.dao.** { *; }
# Classe de resultado de query personalizada
-keep class com.dlynce.fittododia.data.db.dao.WorkoutExerciseRow { *; }

# ── Kotlin Coroutines ─────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Kotlin Serialization (caso adicione no futuro) ────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ── DataStore Preferences ─────────────────────────────────────────────────
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ── WorkManager ───────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
# Worker de notificações do app
-keep class com.dlynce.fittododia.notifications.TrainingReminderWorker { *; }

# ── Coil (carregamento de imagens/GIFs) ───────────────────────────────────
-dontwarn coil.**

# ── Compose ───────────────────────────────────────────────────────────────
# O compilador Compose já lida com a maioria; apenas garantir lambdas
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
