package fr.rdvmedicaux

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast

/**
 * Fait sonner l'alarme (son d'alarme en boucle + vibrations) tant que l'utilisateur
 * n'a pas appuyé sur Arrêter, jusqu'à 5 minutes. Tourne en service au premier plan,
 * donc fonctionne appli fermée et téléphone verrouillé.
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_START = "fr.rdvmedicaux.START"
        const val ACTION_STOP = "fr.rdvmedicaux.STOP"
        const val ACTION_SNOOZE = "fr.rdvmedicaux.SNOOZE"
        const val ACTION_CLOSE_UI = "fr.rdvmedicaux.CLOSE_ALARM_UI"
        private const val MAX_RING_MS = 5 * 60_000L

        fun intent(c: Context, action: String, med: String, ts: Long): Intent {
            val i = Intent(c, AlarmService::class.java)
            i.action = action
            i.putExtra(Alarms.EXTRA_MED, med)
            i.putExtra(Alarms.EXTRA_TS, ts)
            return i
        }

        fun start(c: Context, med: String, ts: Long) {
            c.startForegroundService(intent(c, ACTION_START, med, ts))
        }

        fun stop(c: Context, med: String, ts: Long) {
            c.startService(intent(c, ACTION_STOP, med, ts))
        }

        fun snooze(c: Context, med: String, ts: Long) {
            c.startService(intent(c, ACTION_SNOOZE, med, ts))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
    private var med = "Rendez-vous médical"
    private var ts = 0L

    private val onTimeout = Runnable { timeout() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        med = intent?.getStringExtra(Alarms.EXTRA_MED) ?: med
        ts = intent?.getLongExtra(Alarms.EXTRA_TS, ts) ?: ts
        when (intent?.action) {
            ACTION_STOP -> {
                finishRinging()
            }
            ACTION_SNOOZE -> {
                val ok = Alarms.scheduleSnooze(this, med, ts)
                Toast.makeText(
                    this,
                    if (ok) "Je te rappelle dans ${Alarms.SNOOZE_MIN} min." else "Le rendez-vous est trop proche pour répéter.",
                    Toast.LENGTH_SHORT
                ).show()
                finishRinging()
            }
            else -> startRinging()
        }
        return START_NOT_STICKY
    }

    private fun startRinging() {
        Notif.ensureChannels(this)
        val n = Notif.build(this, Notif.CHANNEL_RING, med, ts, false)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notif.ID_RING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(Notif.ID_RING, n)
        }

        if (wake == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rdvmedicaux:alarme")
            wake?.acquire(MAX_RING_MS + 15_000L)
        }
        startSound()
        startVibration()
        handler.removeCallbacks(onTimeout)
        handler.postDelayed(onTimeout, MAX_RING_MS)
    }

    private fun alarmAttrs(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private fun startSound() {
        stopSound()
        val attrs = alarmAttrs()
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .build()
        am.requestAudioFocus(req)
        focus = req

        val uris = listOfNotNull(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (u in uris) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attrs)
                mp.setDataSource(this, u)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (e: Exception) {
                mp.release()
            }
        }
    }

    private fun stopSound() {
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        player?.release()
        player = null
        val f = focus
        if (f != null) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.abandonAudioFocusRequest(f)
            focus = null
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 400, 600, 1200), 0)
        v.vibrate(effect, alarmAttrs())
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    /** L'utilisateur n'a rien fait pendant 5 min : on coupe le son et on laisse une notification. */
    private fun timeout() {
        Notif.ensureChannels(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(
            this, 20, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = android.app.Notification.Builder(this, Notif.CHANNEL_MISSED)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Rappel non arrêté : $med")
            .setContentText(Alarms.whenText(ts))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(Notif.ID_MISSED_BASE + (Math.abs(med.hashCode()) % 1000), n)
        finishRinging()
    }

    private fun finishRinging() {
        handler.removeCallbacks(onTimeout)
        stopSound()
        stopVibration()
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        sendBroadcast(Intent(ACTION_CLOSE_UI).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(onTimeout)
        stopSound()
        stopVibration()
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        super.onDestroy()
    }
}
