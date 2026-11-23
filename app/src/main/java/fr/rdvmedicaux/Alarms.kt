package fr.rdvmedicaux

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Alarms {

    const val EXTRA_MED = "med"
    const val EXTRA_TS = "ts"
    const val SNOOZE_MIN = 10

    private const val CODE_SNOOZE = 9_000_001
    private const val CODE_TEST = 9_000_002

    /** "8h30", "8:30", "08:30", "8h" ou "8" -> "08:30" / "08:00". Null si invalide. */
    fun normalize(s: String): String? {
        val m = Regex("^\\s*(\\d{1,2})\\s*(?:[h:]\\s*(\\d{0,2}))?\\s*$").find(s.lowercase()) ?: return null
        val h = m.groupValues[1].toInt()
        val mi = if (m.groupValues[2].isEmpty()) 0 else m.groupValues[2].toInt()
        if (h > 23 || mi > 59) return null
        return String.format(Locale.US, "%02d:%02d", h, mi)
    }

    private fun requestCode(r: Rdv, i: Int): Int = (r.id % 20_000_000) * 100 + i

    /** Moment exact où le rappel [rem] doit sonner pour le rendez-vous [r]. */
    fun triggerTime(r: Rdv, rem: Reminder): Long {
        val hm = rem.hm ?: return r.ts - rem.minBefore * 60_000L
        val p = hm.split(":")
        val c = Calendar.getInstance()
        c.timeInMillis = r.ts
        c.add(Calendar.DAY_OF_YEAR, -rem.days)
        c.set(Calendar.HOUR_OF_DAY, p[0].toInt())
        c.set(Calendar.MINUTE, p[1].toInt())
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun pad(n: Int): String = String.format(Locale.US, "%02d", n)

    /** Texte lisible d'un rappel : "la veille à 20:00", "2 h avant"... */
    fun label(rem: Reminder): String {
        val hm = rem.hm
        if (hm != null) {
            val quand = when (rem.days) {
                0 -> "le jour même"
                1 -> "la veille"
                else -> "${rem.days} jours avant"
            }
            return "$quand à $hm"
        }
        val m = rem.minBefore
        return when {
            m < 60 -> "$m min avant"
            m < 1440 -> if (m % 60 == 0) "${m / 60} h avant" else "${m / 60} h ${pad(m % 60)} avant"
            m % 1440 == 0 -> "${m / 1440} j avant"
            else -> "$m min avant"
        }
    }

    /** "dans 25 min", "dans 2 h 05", "dans 3 jours"... */
    fun countdown(ts: Long, now: Long): String {
        val min = (ts - now) / 60_000L
        return when {
            ts - now < 0 -> "le rendez-vous est passé"
            min < 1 -> "maintenant"
            min < 60 -> "dans $min min"
            min < 1440 -> "dans ${min / 60} h" + (if (min % 60 > 0) " ${pad((min % 60).toInt())}" else "")
            else -> "dans ${min / 1440} jour" + (if (min / 1440 > 1) "s" else "")
        }
    }

    /** "samedi 28 novembre à 08h00 — dans 2 h". */
    fun whenText(ts: Long): String {
        if (ts <= 0L) return ""
        val fmt = SimpleDateFormat("EEEE d MMMM 'à' HH'h'mm", Locale.FRANCE)
        return fmt.format(Date(ts)) + " — " + countdown(ts, System.currentTimeMillis())
    }

    private fun setAlarm(c: Context, at: Long, code: Int, med: String, rdvTs: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(c, AlarmReceiver::class.java)
        intent.putExtra(EXTRA_MED, med)
        intent.putExtra(EXTRA_TS, rdvTs)
        val pi = PendingIntent.getBroadcast(
            c, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val show = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock : vraie alarme de réveil, déclenchée à l'heure exacte même en veille (Doze),
        // sans permission spéciale, et visible dans la barre d'état.
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
    }

    /** Programme tous les rappels à venir de ce rendez-vous. Renvoie leur nombre. */
    fun schedule(c: Context, r: Rdv): Int {
        val now = System.currentTimeMillis()
        var count = 0
        for ((i, rem) in r.reminders.withIndex()) {
            val t = triggerTime(r, rem)
            if (t <= now || t >= r.ts) continue
            setAlarm(c, t, requestCode(r, i), r.med, r.ts)
            count++
        }
        return count
    }

    fun cancel(c: Context, r: Rdv) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in r.reminders.indices) {
            val pi = PendingIntent.getBroadcast(
                c, requestCode(r, i), Intent(c, AlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
    }

    fun rescheduleAll(c: Context) {
        for (r in Store.load(c)) schedule(c, r)
    }

    /** "Répéter" : re-sonne dans [SNOOZE_MIN] min, mais jamais après l'heure du rendez-vous. */
    fun scheduleSnooze(c: Context, med: String, rdvTs: Long): Boolean {
        val now = System.currentTimeMillis()
        var at = now + SNOOZE_MIN * 60_000L
        if (rdvTs > 0L) at = minOf(at, rdvTs - 60_000L)
        if (at <= now + 30_000L) return false
        setAlarm(c, at, CODE_SNOOZE, med, rdvTs)
        return true
    }

    /** Alarme de test dans 10 secondes. */
    fun scheduleTest(c: Context) {
        val now = System.currentTimeMillis()
        setAlarm(c, now + 10_000L, CODE_TEST, "Test d’alarme", now + 2 * 3600_000L)
    }
}
