package com.routinely.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.MoreVert
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal val Ink = Color(0xFF080808)
internal val Panel = Color(0xFF151515)
internal val Raised = Color(0xFF202020)
internal val Muted = Color(0xFF999999)
internal val Paper = Color(0xFFF6F5F1)
internal val Line = Color(0xFF303030)

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoutinelyApp(this, resumeTick.intValue) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

@Composable
fun RoutinelyApp(context: Context, resumeTick: Int) {
    var tasks by remember { mutableStateOf(RoutineStorage.loadTasks(context)) }
    var completionDays by remember { mutableStateOf(RoutineStorage.loadCompletionDays(context)) }
    var history by remember { mutableStateOf(RoutineStorage.loadHistory(context)) }
    var settings by remember { mutableStateOf(RoutineStorage.loadSettings(context)) }
    var loadedDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var permTick by remember { mutableIntStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var editorTitle by remember { mutableStateOf("") }
    var editorTime by remember { mutableStateOf("09:00") }
    var editorError by remember { mutableStateOf<String?>(null) }
    var pendingDeleteIndex by remember { mutableIntStateOf(-1) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val completed = tasks.count { it.done }
    val progress = if (tasks.isEmpty()) 0f else completed.toFloat() / tasks.size
    val today = LocalDate.now().toString()
    val allDone = tasks.isNotEmpty() && tasks.all { it.done }
    val effectiveDays = if (allDone) completionDays + today else completionDays
    val currentStreak = calculateStreak(effectiveDays)
    val hour = LocalTime.now().hour
    val greeting = (if (hour < 12) "Good morning" else if (hour < 18) "Good afternoon" else "Good evening") +
        (if (settings.name.isNotBlank()) ", ${settings.name.trim()}" else "")
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "2.0.0"
    }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    fun reloadAll() {
        tasks = RoutineStorage.loadTasks(context)
        completionDays = RoutineStorage.loadCompletionDays(context)
        history = RoutineStorage.loadHistory(context)
        settings = RoutineStorage.loadSettings(context)
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(RoutineStorage.exportJson(context).toByteArray()) } != null
            }.getOrDefault(false)
            toast(if (ok) "Backup saved" else "Could not save the backup")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text != null && RoutineStorage.importJson(context, text)) {
                reloadAll()
                toast("Backup restored")
            } else toast("That isn't a valid Routinely backup")
        }
    }

    // Back from the background (possibly past midnight, or after changing a permission).
    LaunchedEffect(resumeTick) {
        val now = LocalDate.now().toString()
        if (now != loadedDate) {
            loadedDate = now
            tasks = RoutineStorage.loadTasks(context)
            completionDays = RoutineStorage.loadCompletionDays(context)
        }
        permTick++
    }

    // Persist everything and keep real alarms in step with the routine list.
    LaunchedEffect(tasks, settings.remindersEnabled) {
        RoutineStorage.saveTasks(context, tasks)
        val day = LocalDate.now().toString()
        if (tasks.isNotEmpty() && tasks.all { it.done }) RoutineStorage.saveCompletionDay(context, day)
        else RoutineStorage.removeCompletionDay(context, day)
        RoutineStorage.saveDayProgress(context, day, tasks.count { it.done }, tasks.size)
        completionDays = RoutineStorage.loadCompletionDays(context)
        history = RoutineStorage.loadHistory(context)
        ReminderScheduler.sync(context, tasks, settings.remindersEnabled)
    }

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
                    onClick = {
                        editingIndex = -1
                        editorTitle = ""
                        editorTime = "09:00"
                        editorError = null
                        showEditor = true
                    }, containerColor = Paper, contentColor = Ink
                ) { Icon(Icons.Default.Add, contentDescription = "Add routine") }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> TodayScreen(
                    tasks, progress, completed, currentStreak, Modifier.padding(padding), greeting,
                    onToggle = { index ->
                        tasks = tasks.mapIndexed { i, task -> if (i == index) task.copy(done = !task.done) else task }
                    },
                    onEdit = { index ->
                        editingIndex = index
                        editorTitle = tasks[index].title
                        editorTime = tasks[index].time
                        editorError = null
                        showEditor = true
                    },
                    onDelete = { index -> pendingDeleteIndex = index }
                )
                1 -> StatsScreen(tasks, effectiveDays, history, Modifier.padding(padding))
                2 -> AssistantScreen(settings, onAddRoutines = { added -> tasks = tasks + added }, modifier = Modifier.padding(padding))
                3 -> AlarmsScreen(
                    tasks, settings, permTick,
                    onToggleRemind = { index ->
                        val turningOn = !tasks[index].remind
                        tasks = tasks.mapIndexed { i, task -> if (i == index) task.copy(remind = !task.remind) else task }
                        if (turningOn && !ReminderScheduler.hasNotificationPermission(context)) requestNotifications()
                    },
                    onAllowNotifications = { requestNotifications() },
                    modifier = Modifier.padding(padding)
                )
                else -> SettingsScreen(
                    settings, version,
                    onChange = { settings = it; RoutineStorage.saveSettings(context, it) },
                    onTestNotification = {
                        if (ReminderScheduler.hasNotificationPermission(context)) {
                            ReminderScheduler.showNotification(context, "test", "Test reminder")
                        } else requestNotifications()
                    },
                    onExport = { exportLauncher.launch("routinely-backup.json") },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onDeleteAll = {
                        ReminderScheduler.sync(context, tasks, false)
                        RoutineStorage.clearAll(context)
                        reloadAll()
                        toast("All data deleted")
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }

        if (pendingDeleteIndex >= 0) {
            AlertDialog(
                onDismissRequest = { pendingDeleteIndex = -1 },
                containerColor = Panel,
                title = { Text("Delete routine?", color = Paper) },
                text = { Text("This will remove \"${tasks.getOrNull(pendingDeleteIndex)?.title ?: "this routine"}\" from your list.", color = Muted) },
                confirmButton = {
                    TextButton(onClick = {
                        tasks.getOrNull(pendingDeleteIndex)?.let { ReminderScheduler.cancel(context, it.id) }
                        tasks = tasks.filterIndexed { i, _ -> i != pendingDeleteIndex }
                        pendingDeleteIndex = -1
                    }) { Text("Delete", color = Color(0xFFFF8A80)) }
                },
                dismissButton = { TextButton(onClick = { pendingDeleteIndex = -1 }) { Text("Keep it", color = Paper) } }
            )
        }

        if (showEditor) RoutineEditorDialog(
            title = if (editingIndex >= 0) "Edit routine" else "Create routine",
            routineTitle = editorTitle,
            time = editorTime,
            error = editorError,
            onTitleChange = { editorTitle = it; editorError = null },
            onTimeChange = { editorTime = it; editorError = null },
            onDismiss = { showEditor = false },
            onSave = {
                when {
                    editorTitle.isBlank() -> editorError = "Give your routine a name."
                    !editorTime.matches(Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")) -> editorError = "Use 24-hour time, like 07:30."
                    editingIndex >= 0 -> {
                        tasks = tasks.mapIndexed { i, task ->
                            if (i == editingIndex) task.copy(title = editorTitle.trim(), time = editorTime) else task
                        }
                        showEditor = false
                    }
                    else -> {
                        tasks = tasks + RoutineTask(editorTitle.trim(), editorTime)
                        showEditor = false
                    }
                }
            }
        )
    }
}

@Composable
private fun TodayScreen(
    tasks: List<RoutineTask>, progress: Float, completed: Int, streak: Int,
    modifier: Modifier = Modifier, greeting: String = "", onToggle: (Int) -> Unit,
    onEdit: (Int) -> Unit, onDelete: (Int) -> Unit
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp, 28.dp, 22.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text(greeting.uppercase().ifBlank { "ROUTINELY  /  YOUR DAY" }, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
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
            RoutineTaskCard(task, onToggle = { onToggle(index) }, onEdit = { onEdit(index) }, onDelete = { onDelete(index) })
        }
        if (tasks.isEmpty()) item { Text("Nothing planned yet. Tap + to add your first routine.", color = Muted) }
    }
}

@Composable
private fun RoutineTaskCard(task: RoutineTask, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Panel).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable { onToggle() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Routine options", tint = Muted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Edit routine") }, onClick = { menuOpen = false; onEdit() })
                DropdownMenuItem(text = { Text("Delete routine") }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

@Composable
private fun RoutineEditorDialog(
    title: String, routineTitle: String, time: String, error: String?,
    onTitleChange: (String) -> Unit, onTimeChange: (String) -> Unit,
    onDismiss: () -> Unit, onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(title, color = Paper) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = routineTitle, onValueChange = onTitleChange,
                    label = { Text("Routine name") }, singleLine = true)
                OutlinedTextField(value = time, onValueChange = onTimeChange,
                    label = { Text("Time (24-hour format)") }, placeholder = { Text("07:30") }, singleLine = true)
                Text("Choose a time between 00:00 and 23:59.", color = Muted, fontSize = 11.sp)
                if (error != null) Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save", color = Paper) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } }
    )
}

@Composable
internal fun MetricCard(label: String, value: String, symbol: String, modifier: Modifier = Modifier) {
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
