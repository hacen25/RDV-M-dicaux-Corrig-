package fr.rdvmedicaux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object Notif {
    /** Canal silencieux : le son et les vibrations sont gérés par AlarmService. */
    const val CHANNEL_RING = "rdv_alarme"

    /** Canal de secours avec son d'alarme, si le service ne peut pas démarrer. */
    const val CHANNEL_FALLBACK = "rdv_alarme_secours"
    const val CHANNEL_MISSED = "rdv_rappels_manques"

    const val ID_RING = 4242
    const val ID_MISSED_BASE = 5000

    fun ensureChannels(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val ring = NotificationChannel(CHANNEL_RING, "Alarme de rendez-vous", NotificationManager.IMPORTANCE_HIGH)
        ring.setSound(null, null)
        ring.enableVibration(false)
        ring.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ring)

        val fb = NotificationChannel(
            CHANNEL_FALLBACK, "Alarme de rendez-vous (secours)", NotificationManager.IMPORTANCE_HIGH
        )
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fb.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attrs)
        fb.enableVibration(true)
        fb.vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
        fb.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(fb)

        val missed = NotificationChannel(
            CHANNEL_MISSED, "Rappels non arrêtés", NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(missed)
    }

    fun ringIntent(c: Context, med: String, ts: Long): Intent {
        val i = Intent(c, AlarmActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        i.putExtra(Alarms.EXTRA_MED, med)
        i.putExtra(Alarms.EXTRA_TS, ts)
        return i
    }

    @Suppress("DEPRECATION")
    fun build(c: Context, channel: String, med: String, ts: Long, fallback: Boolean): Notification {
        val text = Alarms.whenText(ts)
        val full = PendingIntent.getActivity(
            c, 10, ringIntent(c, med, ts), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(c, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ $med")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)

        if (fallback) {
            b.setAutoCancel(true)
        } else {
            val stop = PendingIntent.getService(
                c, 11, AlarmService.intent(c, AlarmService.ACTION_STOP, med, ts),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val snooze = PendingIntent.getService(
                c, 12, AlarmService.intent(c, AlarmService.ACTION_SNOOZE, med, ts),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            b.setOngoing(true)
            b.addAction(0, "Arrêter", stop)
            b.addAction(0, "Répéter ${Alarms.SNOOZE_MIN} min", snooze)
            if (Build.VERSION.SDK_INT >= 31) {
                b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
        }
        val n = b.build()
        if (fallback) n.flags = n.flags or Notification.FLAG_INSISTENT
        return n
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val med = intent.getStringExtra(Alarms.EXTRA_MED) ?: "Rendez-vous médical"
        val ts = intent.getLongExtra(Alarms.EXTRA_TS, 0L)
        try {
            // Le service joue la sonnerie en boucle et affiche l'écran d'alarme, même verrouillé.
            AlarmService.start(context, med, ts)
        } catch (e: Exception) {
            // Secours : notification plein écran avec le son d'alarme du canal.
            Notif.ensureChannels(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(Notif.ID_RING, Notif.build(context, Notif.CHANNEL_FALLBACK, med, ts, true))
        }
    }
}

/** Reprogramme les alarmes après un redémarrage, une mise à jour de l'appli ou un changement d'heure. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.rescheduleAll(context)
    }
}
