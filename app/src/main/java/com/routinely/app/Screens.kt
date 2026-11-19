package com.routinely.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val Danger = Color(0xFFFF8A80)

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Headline(label: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Paper, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoCard(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(body, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)) { Text(action) }
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Ink, checkedTrackColor = Paper,
    uncheckedThumbColor = Muted, uncheckedTrackColor = Raised, uncheckedBorderColor = Muted
)

// ---------------------------------------------------------------- Alarms

@Composable
internal fun AlarmsScreen(
    tasks: List<RoutineTask>, settings: AppSettings, permTick: Int,
    onToggleRemind: (Int) -> Unit, onAllowNotifications: () -> Unit, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notifOk = remember(permTick) { ReminderScheduler.hasNotificationPermission(context) }
    val exactOk = remember(permTick) { ReminderScheduler.canScheduleExact(context) }
    fun open(intent: Intent) {
        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { }
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp, 28.dp, 22.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Headline("SMART ALARMS", "Never miss a routine.") }
        if (!settings.remindersEnabled) item {
            InfoCard("Reminders are switched off", "Turn them back on in Settings > Notifications.")
        }
        if (!notifOk) item {
            InfoCard("Notifications are off", "Allow notifications so reminders can appear.", "Allow", onAllowNotifications)
        }
        if (!exactOk) item {
            InfoCard(
                "Exact timing is off",
                "Without this, Android may delay reminders by several minutes.",
                "Allow"
            ) {
                open(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }
        }
        item {
            InfoCard(
                "Late reminders?",
                "Some phones pause background apps. Set Routinely to Unrestricted in battery settings if reminders arrive late.",
                "Battery settings"
            ) { open(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        item { SectionLabel("ROUTINE REMINDERS  -  REPEAT DAILY") }
        itemsIndexed(tasks, key = { _, t -> t.id }) { index, task ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).padding(16.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(task.time, color = Muted, fontSize = 12.sp)
                }
                Switch(checked = task.remind, onCheckedChange = { onToggleRemind(index) }, colors = switchColors())
            }
        }
        if (tasks.isEmpty()) item { Text("Add a routine on the Today tab first.", color = Muted) }
    }
}

// ----------------------------------------------------------------- Stats

@Composable
internal fun StatsScreen(
    tasks: List<RoutineTask>, days: Set<String>, history: Map<String, Pair<Int, Int>>,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val streak = calculateStreak(days)
    val longest = longestStreak(days)
    val monthDays = days.count { s ->
        val d = runCatching { LocalDate.parse(s) }.getOrNull()
        d != null && d.year == today.year && d.month == today.month
    }
    val last30 = (0 until 30).count { today.minusDays(it.toLong()).toString() in days }
    val doneToday = tasks.count { it.done }
    // Today comes from live state; earlier days from recorded history.
    fun dayFraction(offset: Int): Float {
        if (offset == 0) return if (tasks.isEmpty()) 0f else doneToday.toFloat() / tasks.size
        val h = history[today.minusDays(offset.toLong()).toString()] ?: return 0f
        return if (h.second == 0) 0f else (h.first.toFloat() / h.second).coerceIn(0f, 1f)
    }
    fun dayDone(offset: Int): Int =
        if (offset == 0) doneToday else history[today.minusDays(offset.toLong()).toString()]?.first ?: 0
    val weekDone = (0 until 7).sumOf { dayDone(it) }
    val totalDone = history.filterKeys { it != today.toString() }.values.sumOf { it.first } + doneToday

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp, 28.dp, 22.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Headline("YOUR PROGRESS", "Consistency looks good on you.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("CURRENT STREAK", "$streak days", "✦", Modifier.weight(1f))
            MetricCard("LONGEST STREAK", "$longest days", "↗", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("PERFECT DAYS", "${days.size}", "✓", Modifier.weight(1f))
            MetricCard("THIS MONTH", "$monthDays days", "◷", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("THIS WEEK", "$weekDone done", "↗", Modifier.weight(1f))
            MetricCard("30-DAY RATE", "${last30 * 100 / 30}%", "◐", Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel("LAST 7 DAYS")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (offset in 6 downTo 0) {
                    val fraction = dayFraction(offset)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(10.dp)).background(Raised),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            if (fraction > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).background(Paper))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            today.minusDays(offset.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            color = if (offset == 0) Paper else Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Text("Bars show the share of routines completed each day, recorded from this version onward.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        SectionLabel("ACHIEVEMENTS")
        AchievementCard("First steps", "Complete every routine in a day", days.isNotEmpty())
        AchievementCard("Three-day rhythm", "Reach a 3-day streak", longest >= 3)
        AchievementCard("One-week focus", "Reach a 7-day streak", longest >= 7)
        AchievementCard("Fortnight", "Reach a 14-day streak", longest >= 14)
        AchievementCard("Month master", "Reach a 30-day streak", longest >= 30)
        AchievementCard("Century", "Complete 100 routines in total", totalDone >= 100)
    }
}

@Composable
private fun AchievementCard(title: String, description: String, unlocked: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(if (unlocked) Paper else Raised), contentAlignment = Alignment.Center) {
            Text(if (unlocked) "★" else "·", color = if (unlocked) Ink else Muted, fontSize = 22.sp)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Paper, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 11.sp)
        }
        if (unlocked) Text("UNLOCKED", color = Paper, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// -------------------------------------------------------------- Settings

@Composable
internal fun SettingsScreen(
    settings: AppSettings, version: String, onChange: (AppSettings) -> Unit,
    onTestNotification: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val urlBad = settings.backendUrl.isNotBlank() && !settings.backendUrl.startsWith("https://")
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp, 28.dp, 22.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item { Headline("SETTINGS", "Make it yours.") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("PERSONALISATION")
                OutlinedTextField(
                    value = settings.name, onValueChange = { onChange(settings.copy(name = it.take(30))) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Your name") },
                    supportingText = { Text("Used in your dashboard greeting.") }
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("NOTIFICATIONS")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).padding(16.dp, 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Routine reminders", color = Paper, modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.remindersEnabled,
                        onCheckedChange = { onChange(settings.copy(remindersEnabled = it)) },
                        colors = switchColors()
                    )
                }
                Text("Snooze length", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { m ->
                        val selected = settings.snoozeMinutes == m
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Paper else Raised)
                                .clickable { onChange(settings.copy(snoozeMinutes = m)) }.padding(14.dp, 10.dp)
                        ) { Text("$m min", color = if (selected) Ink else Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                OutlinedButton(onClick = onTestNotification) { Text("Send a test notification") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("AI ASSISTANT")
                OutlinedTextField(
                    value = settings.backendUrl, onValueChange = { onChange(settings.copy(backendUrl = it.trim())) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, isError = urlBad,
                    label = { Text("Backend URL (https)") },
                    supportingText = { Text(if (urlBad) "Must start with https://" else "Optional. Never paste an API key here.") }
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("DATA")
                Button(onClick = onExport, colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)) {
                    Text("Export backup")
                }
                OutlinedButton(onClick = onImport) { Text("Restore from backup") }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete all data", color = Danger) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("ABOUT ROUTINELY")
                Text("Version $version", color = Paper, fontSize = 14.sp)
                Text(
                    "Privacy: your routines, history and settings stay on this device. Nothing is sent anywhere unless you add an AI backend URL; then only the messages you type in the AI tab are sent to it.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Panel,
            title = { Text("Delete all data?", color = Paper) },
            text = { Text("This removes every routine, your history and your settings from this device. Export a backup first if you might want them back.", color = Muted) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteAll() }) { Text("Delete everything", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep my data", color = Paper) } }
        )
    }
}

// -------------------------------------------------------------------- AI

data class ChatMessage(
    val fromUser: Boolean, val text: String,
    val suggestions: List<RoutineTask> = emptyList(), val added: Boolean = false
)

internal object AiClient {
    /** POSTs {"messages":[{"role","content"}]} and expects {"reply":"..."}. Holds no secrets. */
    fun chat(url: String, history: List<ChatMessage>): Result<String> = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        val messages = JSONArray()
        history.takeLast(12).dropWhile { !it.fromUser }.forEach {
            messages.put(JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text))
        }
        conn.outputStream.use { it.write(JSONObject().put("messages", messages).toString().toByteArray()) }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("server returned HTTP $code")
        JSONObject(body).getString("reply")
    }
}

/** Ready-made routine templates. This is NOT AI; it works offline from keyword matching. */
internal object OfflinePlanner {
    data class Plan(val text: String, val routines: List<RoutineTask>)

    private fun plan(text: String, vararg items: Pair<String, String>) =
        Plan(text, items.map { RoutineTask(it.first, it.second) })

    fun reply(prompt: String): Plan {
        val p = prompt.lowercase()
        return when {
            "morning" in p -> plan(
                "A balanced morning routine. Add it to Today and adjust the times to suit you.",
                "Wake up & make bed" to "07:00", "Drink a glass of water" to "07:05",
                "Stretch or light movement" to "07:15", "Healthy breakfast" to "07:45", "Plan your top 3 priorities" to "08:15"
            )
            "study" in p || "exam" in p || "learn" in p -> plan(
                "A simple study block pattern: focused sessions with breaks.",
                "Study session 1 (45 min)" to "16:00", "Short break" to "16:45",
                "Study session 2 (45 min)" to "17:00", "Review and summarise" to "18:00"
            )
            "evening" in p || "night" in p || "sleep" in p || "wind" in p -> plan(
                "A wind-down routine to help you sleep well.",
                "Tidy up and prepare for tomorrow" to "20:30", "Screens off" to "21:30",
                "Read or journal" to "21:45", "Lights out" to "22:30"
            )
            "workout" in p || "fitness" in p || "gym" in p || "exercise" in p || "health" in p -> plan(
                "A simple healthy-habits set.",
                "Drink water" to "08:00", "Workout (30 min)" to "17:30", "Walk for 10 minutes" to "13:00", "Prepare a healthy dinner" to "18:30"
            )
            "busy" in p || "organi" in p || "plan" in p || "productiv" in p || "focus" in p -> plan(
                "A structure for a busy day: priorities first, protected focus time, a real break.",
                "Choose your top 3 tasks" to "08:30", "Deep focus block (90 min)" to "09:00",
                "Lunch break away from screens" to "12:30", "Admin and messages" to "15:00", "End-of-day review" to "17:30"
            )
            else -> Plan(
                "I'm the offline planner: I can suggest ready-made routines for mornings, studying, workouts, evenings or a busy day. For open-ended AI answers, add your AI backend URL in Settings.",
                emptyList()
            )
        }
    }
}

@Composable
internal fun AssistantScreen(settings: AppSettings, onAddRoutines: (List<RoutineTask>) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var prompt by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var busy by remember { mutableStateOf(false) }
    val connected = settings.backendUrl.startsWith("https://")

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || busy) return
        prompt = ""
        messages = messages + ChatMessage(true, clean)
        if (connected) {
            busy = true
            val history = messages
            val url = settings.backendUrl
            scope.launch {
                val result = withContext(Dispatchers.IO) { AiClient.chat(url, history) }
                val reply = result.getOrElse { "I couldn't reach your AI backend (${it.message ?: "unknown error"}). Check the URL in Settings." }
                messages = messages + ChatMessage(false, reply)
                busy = false
            }
        } else {
            val plan = OfflinePlanner.reply(clean)
            messages = messages + ChatMessage(false, plan.text, plan.routines)
        }
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
    }

    Column(
        modifier.fillMaxSize().imePadding().padding(22.dp, 28.dp, 22.dp, 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ROUTINELY AI", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text("A clearer day starts here.", color = Paper, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
        Text(
            if (connected) "Connected to your AI backend."
            else "Offline planner: ready-made routine templates that work without internet. Add a backend URL in Settings for full AI answers.",
            color = Muted, fontSize = 12.sp, lineHeight = 18.sp
        )
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("TRY ASKING")
                    listOf(
                        "Build me a balanced morning routine", "Plan my study time for this week",
                        "Help me organise a busy day", "A wind-down routine for better sleep"
                    ).forEach { suggestion ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Raised)
                                .clickable { send(suggestion) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(suggestion, color = Paper, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text("↗", color = Muted)
                        }
                    }
                }
            }
            items(messages) { m ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (m.fromUser) Paper else Panel).padding(14.dp)
                    ) { Text(m.text, color = if (m.fromUser) Ink else Paper, fontSize = 14.sp, lineHeight = 20.sp) }
                    if (m.suggestions.isNotEmpty()) {
                        Column(
                            Modifier.widthIn(max = 310.dp).clip(RoundedCornerShape(16.dp)).background(Raised).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            m.suggestions.forEach { Text("${it.time}   ${it.title}", color = Paper, fontSize = 13.sp) }
                            Spacer(Modifier.height(2.dp))
                            Button(
                                onClick = {
                                    onAddRoutines(m.suggestions)
                                    messages = messages.map { if (it === m) it.copy(added = true) else it }
                                },
                                enabled = !m.added,
                                colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink)
                            ) { Text(if (m.added) "Added to Today" else "Add ${m.suggestions.size} routines to Today") }
                        }
                    }
                }
            }
            if (busy) item { Text("Thinking…", color = Muted, fontSize = 13.sp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Tell Routinely what you need...") },
                shape = RoundedCornerShape(16.dp), maxLines = 4
            )
            IconButton(onClick = { send(prompt) }, enabled = prompt.isNotBlank() && !busy) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (prompt.isNotBlank() && !busy) Paper else Muted)
            }
        }
    }
}
