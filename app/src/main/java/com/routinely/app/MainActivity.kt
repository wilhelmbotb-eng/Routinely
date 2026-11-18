package com.routinely.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Ink = Color(0xFF080808)
private val Panel = Color(0xFF151515)
private val Raised = Color(0xFF202020)
private val Muted = Color(0xFF999999)
private val Paper = Color(0xFFF6F5F1)
private val Line = Color(0xFF303030)

data class RoutineTask(val title: String, val time: String, val done: Boolean = false)

private object RoutineStorage {
    private const val PREFS = "routinely_v2"
    private const val TASKS = "tasks"
    private const val COMPLETION_DAYS = "completion_days"

    fun loadTasks(context: Context): List<RoutineTask> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val raw = prefs.getString(TASKS, null)
            ?: return listOf(
                RoutineTask("Wake up & make bed", "07:00"),
                RoutineTask("Drink a glass of water", "07:05"),
                RoutineTask("Morning workout", "07:30"),
                RoutineTask("Read for 20 minutes", "08:00")
            )
        val savedDate = prefs.getString("last_active_date", today)
        if (savedDate != today) {
            prefs.edit().putString("last_active_date", today).apply()
        }
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val wasDone = item.optBoolean("done")
                RoutineTask(item.getString("title"), item.optString("time", "Anytime"), wasDone && savedDate == today)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun saveTasks(context: Context, tasks: List<RoutineTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(JSONObject().put("title", task.title).put("time", task.time).put("done", task.done))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(TASKS, array.toString())
            .putString("last_active_date", LocalDate.now().toString())
            .apply()
    }

    fun loadCompletionDays(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(COMPLETION_DAYS, emptySet())?.toSet() ?: emptySet()

    fun saveCompletionDay(context: Context, date: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val days = prefs.getStringSet(COMPLETION_DAYS, emptySet())?.toMutableSet() ?: mutableSetOf()
        days.add(date)
        prefs.edit().putStringSet(COMPLETION_DAYS, days).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoutinelyApp(this) }
    }
}

@Composable
fun RoutinelyApp(context: Context) {
    var tasks by remember { mutableStateOf(RoutineStorage.loadTasks(context)) }
    val completionDays = remember { RoutineStorage.loadCompletionDays(context) }
    var showAdd by remember { mutableStateOf(false) }
    var newTask by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val completed = tasks.count { it.done }
    val progress = if (tasks.isEmpty()) 0f else completed.toFloat() / tasks.size
    val today = LocalDate.now().toString()
    val currentStreak = calculateStreak(completionDays + if (tasks.isNotEmpty() && tasks.all { it.done }) setOf(today) else emptySet())

    LaunchedEffect(tasks) { RoutineStorage.saveTasks(context, tasks) }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Paper, onPrimary = Ink,
        onBackground = Paper, onSurface = Paper, secondary = Muted
    )) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Paper) {
                    val tabs = listOf("Today", "Stats", "AI", "Alarms", "Settings")
                    val icons = listOf(Icons.Default.Home, Icons.Default.Insights, Icons.Default.AutoAwesome, Icons.Default.NotificationsActive, Icons.Default.Settings)
                    tabs.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icons[index], contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == 0) FloatingActionButton(
                    onClick = { showAdd = true }, containerColor = Paper, contentColor = Ink
                ) { Icon(Icons.Default.Add, contentDescription = "Add routine") }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> TodayScreen(tasks, progress, completed, currentStreak, Modifier.padding(padding)) { index ->
                    val changed = tasks.mapIndexed { i, task -> if (i == index) task.copy(done = !task.done) else task }
                    tasks = changed
                    if (changed.isNotEmpty() && changed.all { it.done }) RoutineStorage.saveCompletionDay(context, today)
                }
                1 -> StatsScreen(tasks, currentStreak, completionDays.size, Modifier.padding(padding))
                2 -> AssistantScreen(Modifier.padding(padding))
                3 -> InfoScreen("Smart alarms", "Alarm scheduling, recurring profiles and bedtime automation are in the next implementation phase.", Modifier.padding(padding))
                else -> InfoScreen("Your space", "Customise your Routinely experience. More appearance, sound and backup controls are coming.", Modifier.padding(padding))
            }
        }

        if (showAdd) AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = Panel,
            title = { Text("New routine", color = Paper) },
            text = {
                OutlinedTextField(value = newTask, onValueChange = { newTask = it },
                    label = { Text("Routine name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTask.isNotBlank()) {
                        tasks = tasks + RoutineTask(newTask.trim(), "Anytime")
                        newTask = ""
                        showAdd = false
                    }
                }) { Text("Add routine", color = Paper) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel", color = Muted) } }
        )
    }
}

private fun calculateStreak(days: Set<String>): Int {
    var date = LocalDate.now()
    if (date.toString() !in days) date = date.minusDays(1)
    var streak = 0
    while (date.toString() in days) {
        streak++
        date = date.minusDays(1)
    }
    return streak
}

@Composable
private fun TodayScreen(
    tasks: List<RoutineTask>, progress: Float, completed: Int, streak: Int,
    modifier: Modifier = Modifier, onToggle: (Int) -> Unit
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp, 28.dp, 22.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("ROUTINELY  /  YOUR DAY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
            Spacer(Modifier.height(14.dp))
            Text("Make today\ncount.", color = Paper, fontSize = 39.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Muted, fontSize = 14.sp)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("STREAK", "$streak days", "↗", Modifier.weight(1f))
                MetricCard("COMPLETED", "$completed / ${tasks.size}", "✓", Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("DAILY PROGRESS", color = Color.DarkGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(7.dp))
                            Text("$completed of ${tasks.size} completed", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${(progress * 100).toInt()}%", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = Ink, trackColor = Color(0xFFD8D7D2))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today's routine", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${tasks.size} TASKS", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }
        itemsIndexed(tasks) { index, task ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel)
                .clickable { onToggle(index) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(27.dp).clip(CircleShape).background(if (task.done) Paper else Raised),
                    contentAlignment = Alignment.Center) {
                    if (task.done) Icon(Icons.Default.Check, null, tint = Ink, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, color = if (task.done) Muted else Paper, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(task.time, color = Muted, fontSize = 12.sp)
                }
                if (task.done) Text("DONE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (tasks.isEmpty()) item { Text("Nothing planned yet. Tap + to add your first routine.", color = Muted) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, symbol: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Panel).padding(15.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(symbol, color = Paper, fontSize = 16.sp)
        }
    }
}

@Composable
private fun StatsScreen(tasks: List<RoutineTask>, streak: Int, completedDays: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("YOUR PROGRESS", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text("Consistency looks good on you.", color = Paper, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("CURRENT STREAK", "$streak days", "✦", Modifier.weight(1f))
            MetricCard("ACTIVE ROUTINES", "${tasks.size}", "↗", Modifier.weight(1f))
        }
        Text("ACHIEVEMENTS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        AchievementCard("First steps", "Complete your first full day", completedDays >= 1)
        AchievementCard("Three-day rhythm", "Build a 3-day completion streak", streak >= 3)
        AchievementCard("One-week focus", "Build a 7-day completion streak", streak >= 7)
        Text("Completed days: $completedDays", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun AchievementCard(title: String, description: String, unlocked: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun AssistantScreen(modifier: Modifier = Modifier) {
    var prompt by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("ROUTINELY AI", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text("A clearer day starts here.", color = Paper, fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(17.dp),
            verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Paper)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your personal planning assistant", color = Paper, fontWeight = FontWeight.SemiBold)
                Text("The AI chat interface is being prepared. Secure AI service connection and account settings are the next backend milestone. Your routines will remain available offline.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Text("WHAT YOU'LL BE ABLE TO ASK", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
        listOf("Build me a balanced morning routine", "Plan my study time for this week", "Help me organise a busy day").forEach { suggestion ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Raised).clickable { prompt = suggestion }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(suggestion, color = Paper, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("↗", color = Muted)
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Tell Routinely what you need...") }, shape = RoundedCornerShape(16.dp))
        Text("AI replies will activate after the secure service is connected.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun InfoScreen(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("ROUTINELY", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp)
        Spacer(Modifier.height(14.dp))
        Text(title, color = Paper, fontSize = 31.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(description, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
    }
}
