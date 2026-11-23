package fr.rdvmedicaux

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

/** Écran d'alarme plein écran : s'affiche par-dessus l'écran de verrouillage et allume l'écran. */
class AlarmActivity : Activity() {

    private var med = "Rendez-vous médical"
    private var ts = 0L

    private val closer = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        readIntent(intent)
        setContentView(buildUi())

        val filter = IntentFilter(AlarmService.ACTION_CLOSE_UI)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(closer, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closer, filter)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        setContentView(buildUi())
    }

    private fun readIntent(i: Intent?) {
        med = i?.getStringExtra(Alarms.EXTRA_MED) ?: med
        ts = i?.getLongExtra(Alarms.EXTRA_TS, ts) ?: ts
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(closer)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, sp: Float, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(0xFFFFFFFF.toInt())
        t.gravity = Gravity.CENTER
        if (bold) t.setTypeface(null, Typeface.BOLD)
        t.setPadding(0, dp(8), 0, dp(8))
        return t
    }

    private fun bigButton(text: String, bg: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        b.setTextColor(0xFFFFFFFF.toInt())
        b.setBackgroundColor(bg)
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(84)
        )
        lp.setMargins(0, dp(14), 0, 0)
        b.layoutParams = lp
        return b
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(0xFF0B1F26.toInt())
        root.setPadding(dp(24), dp(24), dp(24), dp(24))

        val clock = TextClock(this)
        clock.format24Hour = "HH:mm"
        clock.format12Hour = "HH:mm"
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
        clock.setTextColor(0xFFFFFFFF.toInt())
        clock.gravity = Gravity.CENTER
        root.addView(clock)

        root.addView(label("⏰ RAPPEL DE RENDEZ-VOUS", 16f))
        root.addView(label(med, 34f, true))
        root.addView(label(Alarms.whenText(ts), 20f))

        root.addView(bigButton("ARRÊTER", 0xFFC62828.toInt()) {
            AlarmService.stop(this, med, ts)
            finish()
        })
        root.addView(bigButton("Répéter dans ${Alarms.SNOOZE_MIN} min", 0xFF37474F.toInt()) {
            AlarmService.snooze(this, med, ts)
            finish()
        })
        return root
    }
}
