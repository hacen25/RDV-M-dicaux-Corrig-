package fr.rdvmedicaux

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un rappel avant un rendez-vous. Deux formes :
 *  - heure fixe : [hm] = "20:00" et [days] jours avant (0 = le jour même, 1 = la veille) ;
 *  - délai : [hm] = null et [minBefore] minutes avant le rendez-vous.
 */
data class Reminder(val days: Int, val hm: String?, val minBefore: Int)

data class Rdv(
    val id: Int,
    val med: String,
    val ts: Long,
    val reminders: List<Reminder>
)

object Store {
    private const val PREFS = "rdv"
    private const val KEY = "list"

    fun load(c: Context): MutableList<Rdv> {
        val out = mutableListOf<Rdv>()
        try {
            val s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
            val a = JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val reminders = mutableListOf<Reminder>()
                if (o.has("rm")) {
                    val arr = o.getJSONArray("rm")
                    for (k in 0 until arr.length()) {
                        val x = arr.getJSONObject(k)
                        if (x.has("t")) reminders.add(Reminder(x.getInt("d"), x.getString("t"), 0))
                        else reminders.add(Reminder(0, null, x.getInt("m")))
                    }
                } else {
                    // Ancien format : "db" jours avant + liste d'heures "rems"
                    val days = o.optInt("db", 1)
                    val r = o.optJSONArray("rems")
                    if (r != null) for (k in 0 until r.length()) reminders.add(Reminder(days, r.getString(k), 0))
                }
                out.add(Rdv(o.getInt("id"), o.getString("med"), o.getLong("ts"), reminders))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun save(c: Context, list: List<Rdv>) {
        val a = JSONArray()
        for (r in list) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("med", r.med)
            o.put("ts", r.ts)
            val rm = JSONArray()
            for (x in r.reminders) {
                val j = JSONObject()
                if (x.hm != null) {
                    j.put("d", x.days)
                    j.put("t", x.hm)
                } else {
                    j.put("m", x.minBefore)
                }
                rm.put(j)
            }
            o.put("rm", rm)
            a.put(o)
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }

    fun nextId(list: List<Rdv>): Int = (list.maxOfOrNull { it.id } ?: 0) + 1

    /** Supprime les rendez-vous passés depuis plus de 24 h. Renvoie ceux qui ont été retirés. */
    fun purge(c: Context): List<Rdv> {
        val limite = System.currentTimeMillis() - 24 * 3600_000L
        val all = load(c)
        val old = all.filter { it.ts < limite }
        if (old.isNotEmpty()) save(c, all.filter { it.ts >= limite })
        return old
    }
}
