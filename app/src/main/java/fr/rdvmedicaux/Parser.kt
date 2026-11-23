package fr.rdvmedicaux

import java.util.Calendar
import java.util.Locale

object Parser {
    private val JOURS = listOf("dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi")
    private val MOIS = listOf(
        "janvier", "février", "mars", "avril", "mai", "juin",
        "juillet", "août", "septembre", "octobre", "novembre", "décembre"
    )
    private val STOP = setOf(
        "rdv", "rendez-vous", "rendez", "vous", "chez", "avec", "le", "la", "les", "un", "une",
        "du", "de", "des", "au", "à", "a", "prochain", "prochaine", "ce", "cette", "matin",
        "soir", "pour", "mon", "ma", "mes", "midi", "dans", "en", "et", "h"
    )

    class Result(val med: String, val date: Calendar?, val h: Int?, val m: Int?)

    private fun cal(y: Int, mo: Int, d: Int): Calendar {
        val c = Calendar.getInstance()
        c.clear()
        c.set(y, mo, d)
        return c
    }

    /** Vrai si le jour existe dans ce mois (mo : 0 = janvier). */
    private fun validDay(y: Int, mo: Int, d: Int): Boolean {
        if (mo !in 0..11 || d < 1) return false
        return d <= cal(y, mo, 1).getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun parse(txt: String): Result {
        var rest = " " + txt.lowercase(Locale.FRANCE).replace('’', '\'') + " "
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)
        val year = today.get(Calendar.YEAR)
        val month = today.get(Calendar.MONTH)

        var h: Int? = null
        var m: Int? = null
        var date: Calendar? = null
        val monthAlt = MOIS.joinToString("|")

        // 1. Moment de la journée : "du soir", "de l'après-midi", "ce soir"
        var pm = false
        val apresMidi = Regex("apr[eè]s[- ]midi")
        if (apresMidi.containsMatchIn(rest)) {
            pm = true
            rest = rest.replace(apresMidi, " ")
        }
        var ceSoir = false
        if (rest.contains("ce soir")) {
            ceSoir = true
            pm = true
            rest = rest.replace("ce soir", " ")
        }
        val soir = Regex("\\bsoir\\b")
        if (soir.containsMatchIn(rest)) {
            pm = true
            rest = rest.replace(soir, " ")
        }
        rest = rest.replace(Regex("\\bmatin\\b"), " ")

        // 2. Heure : 8h, 8h30, 8:30, 8 heures, 8 heures 30.
        //    Un "h" collé à une lettre (ex. "12 hématologue") n'est pas une heure,
        //    et "8h 15 mars" ne prend pas 15 pour des minutes.
        val mt = Regex(
            "(\\d{1,2})\\s*(?:heures?|h(?!\\p{L})|:)" +
                "(?:\\s*(\\d{2})(?![\\d/]|\\s*(?:/|$monthAlt)))?"
        ).find(rest)
        if (mt != null) {
            val hh = mt.groupValues[1].toInt()
            val mm = if (mt.groupValues[2].isEmpty()) 0 else mt.groupValues[2].toInt()
            if (hh in 0..23 && mm in 0..59) {
                h = hh
                m = mm
                rest = rest.replaceRange(mt.range, " ")
            }
        }
        val midi = Regex("\\bmidi\\b")
        if (h == null && midi.containsMatchIn(rest)) {
            h = 12
            m = 0
            rest = rest.replace(midi, " ")
        }
        val hv = h
        if (hv != null && pm && hv in 1..11) h = hv + 12

        // 3. Date : 15/03, 15/03/2027, 15 mars, 15 mars 2027
        val md = Regex("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?").find(rest)
        if (md != null) {
            val d = md.groupValues[1].toInt()
            val mo = md.groupValues[2].toInt() - 1
            val explicitYear = md.groupValues[3].isNotEmpty()
            var y = year
            if (explicitYear) {
                y = md.groupValues[3].toInt()
                if (y < 100) y += 2000
            }
            if (validDay(y, mo, d)) {
                var c = cal(y, mo, d)
                // Sans année et déjà passé : on prend l'année suivante
                if (!explicitYear && c.before(today) && validDay(y + 1, mo, d)) c = cal(y + 1, mo, d)
                date = c
                rest = rest.replaceRange(md.range, " ")
            }
        }

        if (date == null) {
            val mo = Regex("(\\d{1,2})\\s*(?:er)?\\s*($monthAlt)(?:\\s+(\\d{4}))?").find(rest)
            if (mo != null) {
                val explicitYear = mo.groupValues[3].isNotEmpty()
                val y = if (explicitYear) mo.groupValues[3].toInt() else year
                val idx = MOIS.indexOf(mo.groupValues[2])
                val day = mo.groupValues[1].toInt()
                if (validDay(y, idx, day)) {
                    var c = cal(y, idx, day)
                    if (!explicitYear && c.before(today) && validDay(y + 1, idx, day)) c = cal(y + 1, idx, day)
                    date = c
                    rest = rest.replaceRange(mo.range, " ")
                }
            }
        }

        // 4. "le 15" : le prochain 15 (ce mois-ci ou les mois suivants)
        if (date == null) {
            val ld = Regex("\\ble\\s+(\\d{1,2})(?:er)?\\b").find(rest)
            if (ld != null) {
                val d = ld.groupValues[1].toInt()
                var y = year
                var mo = month
                for (i in 0..12) {
                    if (validDay(y, mo, d)) {
                        val c = cal(y, mo, d)
                        if (!c.before(today)) {
                            date = c
                            rest = rest.replaceRange(ld.range, " ")
                            break
                        }
                    }
                    mo++
                    if (mo > 11) {
                        mo = 0
                        y++
                    }
                }
            }
        }

        // 5. Dates relatives : dans 3 jours, dans 2 semaines, aujourd'hui, demain, après-demain, lundi...
        if (date == null) {
            val dans = Regex("dans\\s+(\\d{1,2}|une|un|deux|trois)\\s+(jours?|semaines?)").find(rest)
            if (dans != null) {
                val n = when (dans.groupValues[1]) {
                    "un", "une" -> 1
                    "deux" -> 2
                    "trois" -> 3
                    else -> dans.groupValues[1].toInt()
                }
                val c = today.clone() as Calendar
                c.add(Calendar.DAY_OF_YEAR, if (dans.groupValues[2].startsWith("semaine")) n * 7 else n)
                date = c
                rest = rest.replaceRange(dans.range, " ")
            } else if (ceSoir || rest.contains("aujourd'hui") || rest.contains("aujourdhui")) {
                date = today.clone() as Calendar
                rest = rest.replace("aujourd'hui", " ").replace("aujourdhui", " ")
            } else if (rest.contains("après-demain") || rest.contains("apres-demain")) {
                val c = today.clone() as Calendar
                c.add(Calendar.DAY_OF_YEAR, 2)
                date = c
                rest = rest.replace("après-demain", " ").replace("apres-demain", " ")
            } else if (rest.contains("demain")) {
                val c = today.clone() as Calendar
                c.add(Calendar.DAY_OF_YEAR, 1)
                date = c
                rest = rest.replace("demain", " ")
            } else {
                for ((i, j) in JOURS.withIndex()) {
                    if (rest.contains(j)) {
                        var diff = (i - (today.get(Calendar.DAY_OF_WEEK) - 1) + 7) % 7
                        if (diff == 0) diff = 7
                        val c = today.clone() as Calendar
                        c.add(Calendar.DAY_OF_YEAR, diff)
                        date = c
                        rest = rest.replace(j, " ")
                        break
                    }
                }
            }
        }

        val med = rest.split(Regex("[\\s,;.!?]+"))
            .map { it.trim().removePrefix("l'").removePrefix("d'") }
            .filter { it.isNotEmpty() && it !in STOP }
            .joinToString(" ")

        return Result(med, date, h, m)
    }
}
