package work.spyglee.ladger.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import work.spyglee.ladger.R
import work.spyglee.ladger.data.BudgetAlerts
import work.spyglee.ladger.data.Category
import work.spyglee.ladger.domain.BudgetUsage
import work.spyglee.ladger.ui.formatMoney

/**
 * "Budget limit reached" notifications. The channel is high importance so the alert also pops up while
 * the app is open, like the iOS banner: the limit is usually reached right after adding an expense.
 */
class BudgetNotifier(context: Context) : BudgetAlerts {
    private val context = context.applicationContext

    /** One notification per budget, replacing its previous one. */
    override fun showReached(usage: BudgetUsage, category: Category?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.budgeting), NotificationManager.IMPORTANCE_HIGH)
        )
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val limit = formatMoney(usage.budget.monthlyLimit)
        val body = if (category != null) {
            context.getString(R.string.budget_reached_category_body, category.name, limit)
        } else {
            context.getString(R.string.budget_reached_general_body, limit)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.budget_reached_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_TAG, usage.budget.id.toInt(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "budgets"
        const val NOTIFICATION_TAG = "budget"
    }
}
