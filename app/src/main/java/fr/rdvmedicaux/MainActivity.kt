package fr.rdvmedicaux

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---- Palette : change ces couleurs pour changer l'allure de toute l'appli ----
private val C_BG = 0xFFEFF6F5.toInt()
private val C_PRIMARY = 0xFF0F766E.toInt()
private val C_PRIMARY_DARK = 0xFF0A4F4A.toInt()
private val C_URGENT = 0xFFE8590C.toInt()
private val C_INK = 0xFF12302E.toInt()
private val C_MUTE = 0xFF5C7573.toInt()
private val C_LINE = 0xFFD3E3E1.toInt()
private val C_CHIP = 0xFFDDF0ED.toInt()
private val C_DANGER = 0xFFB42318.toInt()
private val C_DANGER_BG = 0xFFFDECEA.toInt()
private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

class MainActivity : Activity() {

    private lateinit var headerSub: TextView
    private lateinit var phrase: EditText
    private lateinit var med: EditText
    private lateinit var btnDate: Button
    private lateinit var btnHeure: Button
    private lateinit var jours: EditText
    private lateinit var r1: EditText
    private lateinit var r2: EditText
    private lateinit var msg: TextView
    private lateinit var listBox: LinearLayout

    private var dateSet = false
    private var yy = 0
    private var mm = 0
    private var dd = 0
    private var timeSet = false
    private var hh = 8
    private var mn = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C_PRIMARY_DARK
        window.navigationBarColor = C_BG
        setContentView(buildUi())
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Alarms.rescheduleAll(this)
        refresh()
    }

    // ---------- Petits outils de style ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun params(
        w: Int = MATCH, h: Int = WRAP,
        l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0,
        weight: Float = 0f
    ): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.setMargins(dp(l), dp(t), dp(r), dp(b))
        return p
    }

    private fun rounded(color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(radiusDp).toFloat()
        g.setColor(color)
        if (stroke != null) g.setStroke(dp(1), stroke)
        return g
    }

    private fun label(text: String, sp: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun field(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(0xFF8AA0A0.toInt())
        e.setTextColor(C_INK)
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        e.background = rounded(0xFFF6FAF9.toInt(), 14, C_LINE)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        e.setSingleLine(true)
        return e
    }

    private fun pill(text: String, bg: Int, fg: Int, sp: Float = 16f, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        b.setTypeface(null, Typeface.BOLD)
        b.setTextColor(fg)
        b.background = rounded(bg, 16)
        b.stateListAnimator = null
        b.setPadding(dp(16), dp(14), dp(16), dp(14))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(Color.WHITE, 22)
        c.setPadding(dp(18), dp(18), dp(18), dp(18))
        c.elevation = dp(3).toFloat()
        return c
    }

    // ---------- Écran ----------

    private fun buildUi(): ScrollView {
        val sv = ScrollView(this)
        sv.setBackgroundColor(C_BG)
        sv.isFillViewport = true
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        sv.addView(root)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        val hg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(C_PRIMARY, C_PRIMARY_DARK))
        val rr = dp(28).toFloat()
        hg.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, rr, rr, rr, rr)
        header.background = hg
        header.setPadding(dp(22), dp(36), dp(22), dp(28))
        header.addView(label("Mes RDV médicaux", 28f, Color.WHITE, true))
        headerSub = label("", 16f, 0xFFCDEBE7.toInt(), false)
        header.addView(headerSub, params(t = 4))
        root.addView(header, params())

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(18), dp(16), dp(36))
        root.addView(body, params())

        val form = card()
        form.addView(label("Dis-moi ton rendez-vous", 18f, C_INK, true))
        form.addView(
            label("Exemple : cardiologue samedi à 8h. Tu peux aussi dicter avec le micro du clavier.", 14f, C_MUTE, false),
            params(t = 4, b = 10)
        )
        phrase = field("cardiologue samedi à 8h")
        form.addView(phrase, params())
        form.addView(pill("Remplir automatiquement", C_CHIP, C_PRIMARY) { fill() }, params(t = 10))

        form.addView(label("Médecin ou examen", 14f, C_MUTE, true), params(t = 18, b = 4))
        med = field("Ex : Cardiologue")
        form.addView(med, params())

        form.addView(label("Quand ?", 14f, C_MUTE, true), params(t = 14, b = 4))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        btnDate = pill("Date", C_CHIP, C_PRIMARY, 15f) { pickDate() }
        btnHeure = pill("Heure", C_CHIP, C_PRIMARY, 15f) { pickTime() }
        row.addView(btnDate, params(w = 0, weight = 1f, r = 6))
        row.addView(btnHeure, params(w = 0, weight = 1f, l = 6))
        form.addView(row, params())

        form.addView(label("Rappels : jours avant, heure 1, heure 2", 14f, C_MUTE, true), params(t = 14, b = 4))
        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        jours = field("1")
        jours.setText("1")
        jours.inputType = InputType.TYPE_CLASS_NUMBER
        r1 = field("10:00")
        r1.setText("10:00")
        r2 = field("20:00")
        r2.setText("20:00")
        row2.addView(jours, params(w = 0, weight = 0.7f, r = 6))
        row2.addView(r1, params(w = 0, weight = 1f, l = 3, r = 3))
        row2.addView(r2, params(w = 0, weight = 1f, l = 6))
        form.addView(row2, params())

        form.addView(pill("Ajouter le rendez-vous", C_PRIMARY, Color.WHITE, 18f) { add() }, params(t = 20))
        msg = label("", 15f, C_PRIMARY, true)
        form.addView(msg, params(t = 10))
        body.addView(form, params())

        body.addView(label("À venir", 22f, C_INK, true), params(t = 26, b = 8, l = 4))
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        body.addView(listBox, params())

        updateButtons()
        return sv
    }

    private fun fmtShort(): String {
        val c = Calendar.getInstance()
        c.clear()
        c.set(yy, mm, dd)
        return SimpleDateFormat("EEE d MMM yyyy", Locale.FRANCE).format(c.time)
    }

    private fun updateButtons() {
        btnDate.text = if (dateSet) fmtShort() else "Choisir la date"
        btnHeure.text = if (timeSet) String.format(Locale.FRANCE, "%02dh%02d", hh, mn) else "Choisir l’heure"
    }

    private fun fill() {
        val r = Parser.parse(phrase.text.toString())
        if (r.med.isNotEmpty()) med.setText(r.med.replaceFirstChar { it.uppercase() })
        val d = r.date
        if (d != null) {
            yy = d.get(Calendar.YEAR)
            mm = d.get(Calendar.MONTH)
            dd = d.get(Calendar.DAY_OF_MONTH)
            dateSet = true
        }
        val h = r.h
        if (h != null) {
            hh = h
            mn = r.m ?: 0
            timeSet = true
        }
        updateButtons()
        val manque = mutableListOf<String>()
        if (r.med.isEmpty()) manque.add("le médecin")
        if (d == null) manque.add("la date")
        if (h == null) manque.add("l’heure")
        msg.text = if (manque.isEmpty()) "Vérifie, puis appuie sur Ajouter."
        else "Je n’ai pas trouvé : " + manque.joinToString(", ") + ". Complète ci-dessus."
    }

    private fun pickDate() {
        val c = Calendar.getInstance()
        val y = if (dateSet) yy else c.get(Calendar.YEAR)
        val m = if (dateSet) mm else c.get(Calendar.MONTH)
        val d = if (dateSet) dd else c.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(
            this,
            DatePickerDialog.OnDateSetListener { _, y2, m2, d2 ->
                yy = y2
                mm = m2
                dd = d2
                dateSet = true
                updateButtons()
            },
            y, m, d
        ).show()
    }

    private fun pickTime() {
        TimePickerDialog(
            this,
            TimePickerDialog.OnTimeSetListener { _, h2, m2 ->
                hh = h2
                mn = m2
                timeSet = true
                updateButtons()
            },
            hh, mn, true
        ).show()
    }

    private fun add() {
        val name = med.text.toString().trim()
        if (name.isEmpty() || !dateSet || !timeSet) {
            msg.text = "Il faut un médecin, une date et une heure."
            return
        }
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(yy, mm, dd, hh, mn, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            msg.text = "Cette date est déjà passée."
            return
        }
        val days = jours.text.toString().trim().toIntOrNull() ?: 1
        val rems = listOf(r1.text.toString(), r2.text.toString()).mapNotNull { Alarms.normalize(it) }
        val id = (System.currentTimeMillis() / 1000 % 100000000).toInt()
        val rdv = Rdv(id, name, cal.timeInMillis, days, rems)
        val list = Store.load(this)
        list.add(rdv)
        Store.save(this, list)
        val n = Alarms.schedule(this, rdv)
        msg.text = if (n > 0) "Ajouté. $n rappel(s) programmé(s)."
        else "Ajouté, mais aucun rappel à venir : les heures choisies sont déjà passées."
        med.setText("")
        phrase.setText("")
        dateSet = false
        timeSet = false
        updateButtons()
        refresh()
    }

    private fun startOfDay(ts: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ts
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun dansTexte(ts: Long): String {
        val n = Math.round((startOfDay(ts) - startOfDay(System.currentTimeMillis())) / 86400000.0).toInt()
        return when {
            n <= 0 -> "Aujourd’hui"
            n == 1 -> "Demain"
            else -> "Dans $n jours"
        }
    }

    private fun confirmDelete(r: Rdv) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer ce rendez-vous ?")
            .setMessage(r.med)
            .setPositiveButton("Supprimer") { _, _ ->
                Alarms.cancel(this, r)
                val l = Store.load(this)
                l.removeAll { it.id == r.id }
                Store.save(this, l)
                refresh()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun refresh() {
        listBox.removeAllViews()
        val now = System.currentTimeMillis()
        val list = Store.load(this).filter { it.ts > now - 3600_000L }.sortedBy { it.ts }
        headerSub.text = when (list.size) {
            0 -> "Aucun rendez-vous à venir"
            1 -> "1 rendez-vous à venir"
            else -> "${list.size} rendez-vous à venir"
        }
        if (list.isEmpty()) {
            val e = label("📅\nAucun rendez-vous pour l’instant.\nAjoute le premier ci-dessus.", 16f, C_MUTE, false)
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(20), 0, dp(20))
            listBox.addView(e, params())
            return
        }
        val jourFmt = SimpleDateFormat("EEEE", Locale.FRANCE)
        val moisFmt = SimpleDateFormat("MMM", Locale.FRANCE)
        val numFmt = SimpleDateFormat("d", Locale.FRANCE)
        val heureFmt = SimpleDateFormat("HH'h'mm", Locale.FRANCE)
        for (r in list) {
            val d = Date(r.ts)
            val urgent = r.ts - now < 48 * 3600_000L

            val item = LinearLayout(this)
            item.orientation = LinearLayout.HORIZONTAL
            item.gravity = Gravity.CENTER_VERTICAL
            item.background = rounded(Color.WHITE, 20)
            item.elevation = dp(2).toFloat()
            item.setPadding(dp(14), dp(14), dp(14), dp(14))

            val badge = LinearLayout(this)
            badge.orientation = LinearLayout.VERTICAL
            badge.gravity = Gravity.CENTER
            badge.background = rounded(if (urgent) C_URGENT else C_PRIMARY, 16)
            badge.addView(label(numFmt.format(d), 24f, Color.WHITE, true))
            badge.addView(label(moisFmt.format(d).uppercase(Locale.FRANCE), 12f, Color.WHITE, true))
            item.addView(badge, params(w = dp(60), h = dp(64), r = 14))

            val info = LinearLayout(this)
            info.orientation = LinearLayout.VERTICAL
            info.addView(label(r.med, 19f, C_INK, true))
            info.addView(
                label(jourFmt.format(d).replaceFirstChar { it.uppercase() } + " à " + heureFmt.format(d), 16f, C_INK, false),
                params(t = 2)
            )
            info.addView(label(dansTexte(r.ts), 15f, if (urgent) C_URGENT else C_PRIMARY, true), params(t = 2))
            info.addView(label("🔔 " + Alarms.describe(r), 13f, C_MUTE, false), params(t = 4))
            item.addView(info, params(w = 0, weight = 1f))

            val del = label("✕", 18f, C_DANGER, true)
            del.gravity = Gravity.CENTER
            del.background = rounded(C_DANGER_BG, 14)
            del.contentDescription = "Supprimer"
            del.setOnClickListener { confirmDelete(r) }
            item.addView(del, params(w = dp(44), h = dp(44), l = 8))

            listBox.addView(item, params(b = 10))
        }
    }
}
