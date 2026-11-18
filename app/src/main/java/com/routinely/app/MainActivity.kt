package com.routinely.app

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Ink = Color(0xFF090909)
private val Panel = Color(0xFF171717)
private val Muted = Color(0xFF929292)
private val Paper = Color(0xFFF5F5F5)

data class RoutineTask(val title: String, val time: String, val done: Boolean = false)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoutinelyApp() }
    }
}

@Composable
fun RoutinelyApp() {
    var tasks by remember {
        mutableStateOf(
            listOf(
                RoutineTask("Wake up & make bed", "07:00 AM"),
                RoutineTask("Drink a glass of water", "07:05 AM"),
                RoutineTask("Morning workout", "07:30 AM"),
                RoutineTask("Read for 20 minutes", "08:00 AM")
            )
        )
    }
    var showAdd by remember { mutableStateOf(false) }
    var newTask by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val completed = tasks.count { it.done }
    val progress = if (tasks.isEmpty()) 0f else completed.toFloat() / tasks.size

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink,
            surface = Panel,
            primary = Paper,
            onPrimary = Ink,
            onBackground = Paper,
            onSurface = Paper,
            secondary = Muted
        )
    ) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Paper) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Today") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Insights, contentDescription = "Stats") },
                        label = { Text("Stats") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = "Alarms") },
                        label = { Text("Alarms") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = { showAdd = true },
                        containerColor = Paper,
                        contentColor = Ink
                    ) { Icon(Icons.Default.Add, contentDescription = "Add routine") }
                }
            }
        ) { padding ->
            when (selectedTab) {
                0 -> TodayScreen(
                    tasks = tasks,
                    progress = progress,
                    completed = completed,
                    modifier = Modifier.padding(padding),
                    onToggle = { index ->
                        tasks = tasks.mapIndexed { i, task ->
                            if (i == index) task.copy(done = !task.done) else task
                        }
                    }
                )
                1 -> SimpleInfoScreen("Your progress", "Your completed routines and streak insights will appear here.")
                2 -> SimpleInfoScreen("Smart alarms", "Alarm scheduling is coming in the next build.")
                else -> SimpleInfoScreen("Settings", "Personalise your Routinely experience in a future update.")
            }
        }

        if (showAdd) {
            AlertDialog(
                onDismissRequest = { showAdd = false },
                containerColor = Panel,
                title = { Text("New routine", color = Paper) },
                text = {
                    OutlinedTextField(
                        value = newTask,
                        onValueChange = { newTask = it },
                        label = { Text("Routine name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newTask.isNotBlank()) {
                            tasks = tasks + RoutineTask(newTask.trim(), "Anytime")
                            newTask = ""
                            showAdd = false
                        }
                    }) { Text("Add", color = Paper) }
                },
                dismissButton = {
                    TextButton(onClick = { showAdd = false }) { Text("Cancel", color = Muted) }
                }
            )
        }
    }
}

@Composable
private fun TodayScreen(
    tasks: List<RoutineTask>,
    progress: Float,
    completed: Int,
    modifier: Modifier = Modifier,
    onToggle: (Int) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text("ROUTINELY", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(12.dp))
            Text("Make today\ncount.", color = Paper, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Muted, fontSize = 14.sp)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Paper),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("DAILY PROGRESS", color = Color.DarkGray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("$completed of ${tasks.size} completed", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(62.dp).clip(CircleShape).background(Ink)) {
                            Text("${(progress * 100).toInt()}%", color = Paper, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
                        color = Ink,
                        trackColor = Color(0xFFD4D4D4)
                    )
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
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Panel).clickable { onToggle(index) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(27.dp).clip(CircleShape)
                        .background(if (task.done) Paper else Color.Transparent)
                        .then(if (!task.done) Modifier.background(Panel).padding(1.dp) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.done) Icon(Icons.Default.Check, contentDescription = "Completed", tint = Ink, modifier = Modifier.size(17.dp))
                    else Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.Transparent))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, color = if (task.done) Muted else Paper, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(task.time, color = Muted, fontSize = 12.sp)
                }
                if (task.done) Text("DONE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (tasks.isEmpty()) {
            item { Text("Nothing planned yet. Tap + to add your first routine.", color = Muted) }
        }
    }
}

@Composable
private fun SimpleInfoScreen(title: String, description: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("ROUTINELY", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(14.dp))
        Text(title, color = Paper, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(description, color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
    }
}
