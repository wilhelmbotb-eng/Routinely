package com.routinely.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class RoutineTask(
    val title: String,
    val time: String,
    val done: Boolean = false,
    val id: String = UUID.randomUUID().toString(),
    val remind: Boolean = false
)

data class AppSettings(
    val name: String = "",
    val remindersEnabled: Boolean = true,
    val snoozeMinutes: Int = 10,
    val backendUrl: String = ""
)

internal object RoutineStorage {
    private const val PREFS = "routinely_v2"
    private const val SETTINGS_PREFS = "routinely_settings"
    private const val TASKS = "tasks"
    private const val COMPLETION_DAYS = "completion_days"
    private const val HISTORY = "history"
    private const val LEGACY_ACTIVE_DATE = "last_active_date"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun settingsPrefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    private fun defaultTasks() = listOf(
        RoutineTask("Wake up & make bed", "07:00"),
        RoutineTask("Drink a glass of water", "07:05"),
        RoutineTask("Morning workout", "07:30"),
        RoutineTask("Read for 20 minutes", "08:00")
    )

    // ---- Routines -------------------------------------------------------

    /** Pure read: a routine counts as done only if it was ticked today. */
    fun loadTasks(context: Context): List<RoutineTask> {
        val p = prefs(context)
        val raw = p.getString(TASKS, null) ?: return defaultTasks()
        return try {
            parseTasks(JSONArray(raw), LocalDate.now().toString(), p.getString(LEGACY_ACTIVE_DATE, null))
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTasks(context: Context, tasks: List<RoutineTask>) {
        prefs(context).edit().putString(TASKS, tasksToJson(tasks, LocalDate.now().toString()).toString()).apply()
    }

    private fun parseTasks(array: JSONArray, today: String, legacyDate: String?): List<RoutineTask> =
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val doneToday = item.optString("doneOn") == today ||
                (!item.has("doneOn") && item.optBoolean("done") && legacyDate == today)
            RoutineTask(
                title = item.getString("title"),
                time = item.optString("time", "Anytime"),
                done = doneToday,
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                remind = item.optBoolean("remind", false)
            )
        }

    private fun tasksToJson(tasks: List<RoutineTask>, today: String): JSONArray {
        val array = JSONArray()
        tasks.forEach { t ->
            val o = JSONObject().put("id", t.id).put("title", t.title).put("time", t.time).put("remind", t.remind)
            if (t.done) o.put("doneOn", today)
            array.put(o)
        }
        return array
    }

    // ---- Completion days and history -----------------------------------

    fun loadCompletionDays(context: Context): Set<String> =
        prefs(context).getStringSet(COMPLETION_DAYS, emptySet())?.toSet() ?: emptySet()

    fun saveCompletionDay(context: Context, date: String) {
        val days = prefs(context).getStringSet(COMPLETION_DAYS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (days.add(date)) prefs(context).edit().putStringSet(COMPLETION_DAYS, days).apply()
    }

    fun removeCompletionDay(context: Context, date: String) {
        val days = prefs(context).getStringSet(COMPLETION_DAYS, emptySet())?.toMutableSet() ?: return
        if (days.remove(date)) prefs(context).edit().putStringSet(COMPLETION_DAYS, days).apply()
    }

    /** date -> (routines done, routines total) */
    fun loadHistory(context: Context): Map<String, Pair<Int, Int>> {
        val raw = prefs(context).getString(HISTORY, null) ?: return emptyMap()
        return try { parseHistory(JSONObject(raw)) } catch (_: Exception) { emptyMap() }
    }

    fun saveDayProgress(context: Context, date: String, done: Int, total: Int) {
        val map = loadHistory(context).toMutableMap()
        map[date] = done to total
        saveHistory(context, map)
    }

    private fun parseHistory(obj: JSONObject): Map<String, Pair<Int, Int>> =
        obj.keys().asSequence().mapNotNull { key ->
            val parts = obj.optString(key).split("/")
            val d = parts.getOrNull(0)?.toIntOrNull()
            val t = parts.getOrNull(1)?.toIntOrNull()
            if (d != null && t != null) key to (d to t) else null
        }.toMap()

    private fun saveHistory(context: Context, map: Map<String, Pair<Int, Int>>) {
        val cutoff = LocalDate.now().minusDays(120).toString()
        val obj = JSONObject()
        map.filterKeys { it >= cutoff }.forEach { (k, v) -> obj.put(k, "${v.first}/${v.second}") }
        prefs(context).edit().putString(HISTORY, obj.toString()).apply()
    }

    // ---- Settings -------------------------------------------------------

    fun loadSettings(context: Context): AppSettings {
        val p = settingsPrefs(context)
        return AppSettings(
            name = p.getString("name", "") ?: "",
            remindersEnabled = p.getBoolean("reminders", true),
            snoozeMinutes = p.getInt("snooze", 10),
            backendUrl = p.getString("backend", "") ?: ""
        )
    }

    fun saveSettings(context: Context, s: AppSettings) {
        settingsPrefs(context).edit()
            .putString("name", s.name).putBoolean("reminders", s.remindersEnabled)
            .putInt("snooze", s.snoozeMinutes).putString("backend", s.backendUrl).apply()
    }

    // ---- Backup / restore / reset --------------------------------------

    fun exportJson(context: Context): String {
        val history = JSONObject()
        loadHistory(context).forEach { (k, v) -> history.put(k, "${v.first}/${v.second}") }
        val s = loadSettings(context)
        return JSONObject()
            .put("app", "routinely")
            .put("version", 1)
            .put("tasks", tasksToJson(loadTasks(context), LocalDate.now().toString()))
            .put("completionDays", JSONArray(loadCompletionDays(context).sorted()))
            .put("history", history)
            .put("settings", JSONObject().put("name", s.name).put("remindersEnabled", s.remindersEnabled)
                .put("snoozeMinutes", s.snoozeMinutes).put("backendUrl", s.backendUrl))
            .toString(2)
    }

    fun importJson(context: Context, text: String): Boolean {
        return try {
            val root = JSONObject(text)
            if (root.optString("app") != "routinely") return false
            val tasks = parseTasks(root.getJSONArray("tasks"), LocalDate.now().toString(), null)
            saveTasks(context, tasks)
            val daysArray = root.optJSONArray("completionDays")
            val days: Set<String> =
                if (daysArray == null) emptySet() else (0 until daysArray.length()).map { daysArray.getString(it) }.toSet()
            prefs(context).edit().putStringSet(COMPLETION_DAYS, days).apply()
            root.optJSONObject("history")?.let { saveHistory(context, parseHistory(it)) }
            root.optJSONObject("settings")?.let { o ->
                saveSettings(context, AppSettings(
                    name = o.optString("name"),
                    remindersEnabled = o.optBoolean("remindersEnabled", true),
                    snoozeMinutes = o.optInt("snoozeMinutes", 10),
                    backendUrl = o.optString("backendUrl")
                ))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
        settingsPrefs(context).edit().clear().apply()
    }
}

fun calculateStreak(days: Set<String>): Int {
    var date = LocalDate.now()
    if (date.toString() !in days) date = date.minusDays(1)
    var streak = 0
    while (date.toString() in days) {
        streak++
        date = date.minusDays(1)
    }
    return streak
}

fun longestStreak(days: Set<String>): Int {
    val sorted = days.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in sorted) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        if (run > best) best = run
        prev = d
    }
    return best
}
