package work.spyglee.ladger.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.Instant
import work.spyglee.ladger.R
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.data.PlannedTransaction
import work.spyglee.ladger.data.PlannedTransactionAlarm
import work.spyglee.ladger.data.TransactionKind
import work.spyglee.ladger.ui.formatMoney

/**
 * Records planned transactions when they fall due, also while the app is closed: an inexact alarm
 * goes off at the earliest due occurrence, and [PlannedTransactionReceiver] records it and shows
 * "Planned transaction performed". iOS schedules only the notifications and records the transactions
 * the next time the app opens.
 */
class PlannedTransactionNotifier(context: Context) : PlannedTransactionAlarm {
    private val context = context.applicationContext
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant) {
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), WINDOW.toMillis(), alarmIntent())
    }

    override fun cancel() {
        alarmManager.cancel(alarmIntent())
    }

    /** One notification per plan, replacing its previous one. */
    fun showRecorded(planned: PlannedTransaction, category: Category?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.planned_transactions), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.planned_transaction_performed_title))
            .setContentText(context.getString(R.string.planned_transaction_performed_body, title(planned, category), formatMoney(planned.amount)))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_TAG, planned.id.toInt(), notification)
    }

    /** The note, else "Transfer", the category name or "Uncategorized", as the planned transactions list. */
    private fun title(planned: PlannedTransaction, category: Category?): String = when {
        planned.note.isNotEmpty() -> planned.note
        planned.kind == TransactionKind.TRANSFER -> context.getString(R.string.transfer)
        category != null -> category.name
        else -> context.getString(R.string.uncategorized)
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, PlannedTransactionReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL_ID = "planned_transactions"
        const val NOTIFICATION_TAG = "planned_transaction"

        /** How late the system may record a due transaction, so it can batch the alarm with others. */
        val WINDOW: Duration = Duration.ofMinutes(10)
    }
}
