package com.dlynce.fittododia.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dlynce.fittododia.MainActivity
import com.dlynce.fittododia.R
import kotlin.random.Random

class TrainingReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val CHANNEL_ID = "training_reminders"
        private const val CHANNEL_NAME = "Lembretes de treino"
        private const val NOTIF_ID = 1001
    }

    override suspend fun doWork(): Result {
        createChannelIfNeeded()

        val (title, text) = pickMotivationalMessage()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // se quiser, troque por um ícone seu
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(NOTIF_ID, notification)

        // Reagenda para amanhã (mantém 1 notificação/dia)
        ReminderScheduler.scheduleDaily(applicationContext, hour = 18, minute = 0)

        return Result.success()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificações motivacionais para te ajudar a manter o hábito de treinar."
        }

        nm.createNotificationChannel(channel)
    }

    private fun pickMotivationalMessage(): Pair<String, String> {
        // Forte, positivo, sem incentivar “quantidade”.
        val list = listOf(
            "Hoje você não precisa motivação. Precisa começar." to
                    "Abra o FitTodoDia, faça o aquecimento e deixe o corpo te levar. Consistência vence.",
            "Disciplina é liberdade." to
                    "A melhor versão de você começa com um passo. Vá e comece com calma e técnica.",
            "Você é o tipo de pessoa que treina." to
                    "Não negocie com a preguiça: apareça. Depois você decide o resto.",
            "Seu futuro agradece." to
                    "Treino feito (no seu ritmo) é vitória. Segurança e qualidade primeiro.",
            "Só hoje." to
                    "Faça o básico bem-feito. Um treino conta. Um passo por dia muda tudo."
        )
        return list[Random.nextInt(list.size)]
    }
}
