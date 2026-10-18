package com.example.thirtydaytracker

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("tracker_data", Context.MODE_PRIVATE) }
    private val activities = mutableListOf<String>()
    private val completed = mutableSetOf<String>()
    private var startDate: Long = 0L
    private lateinit var root: LinearLayout
    private lateinit var dateLabel: TextView
    private lateinit var clockLabel: TextView

private val clockHandler = Handler(Looper.getMainLooper())

private val clockUpdater = object : Runnable {
    override fun run() {
        if (::clockLabel.isInitialized) {
            clockLabel.text = SimpleDateFormat(
                "hh:mm:ss a",
                Locale.getDefault()
            ).format(Date())
        }

        clockHandler.postDelayed(this, 1000)
    }
}
    private lateinit var progressLabel: TextView
    private lateinit var list: LinearLayout
    private val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadData()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 50)
        }
        showHome()
    }

    private fun loadData() {
        startDate = prefs.getLong("startDate", 0L)
        if (startDate == 0L) {
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            startDate = c.timeInMillis
            prefs.edit().putLong("startDate", startDate).apply()
        }
        val rawActivities = prefs.getString("activities", null)
        if (rawActivities == null) activities.addAll(listOf("Exercise", "Study", "Read"))
        else {
            val arr = JSONArray(rawActivities)
            for (i in 0 until arr.length()) activities.add(arr.getString(i))
        }
        val rawCompleted = prefs.getString("completed", null)
        if (rawCompleted != null) {
            val arr = JSONArray(rawCompleted)
            for (i in 0 until arr.length()) completed.add(arr.getString(i))
        }
        saveData()
    }

    private fun saveData() {
        val a = JSONArray()
        activities.forEach { a.put(it) }
        val c = JSONArray()
        completed.forEach { c.put(it) }
        prefs.edit().putString("activities", a.toString())
            .putString("completed", c.toString()).putLong("startDate", startDate).apply()
    }

    private fun dayNumber(): Int {
        val today = Calendar.getInstance()
        val start = Calendar.getInstance().apply { timeInMillis = startDate }
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0)
        start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0)
        val days = ((today.timeInMillis - start.timeInMillis) / 86400000L).toInt() + 1
        return days.coerceIn(1, 30)
    }

    private fun keyForDay(day: Int, activity: String) = "$day::$activity"

    private fun showHome() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "30-Day Tracker"; textSize = 27f
            setTextColor(0xFF222222.toInt())
        })
        clockLabel = TextView(this).apply {
    textSize = 30f
    setTextColor(0xFF1565C0.toInt())
    setPadding(0, 12, 0, 12)
    gravity = Gravity.CENTER
}

root.addView(clockLabel)
        dateLabel = TextView(this).apply { textSize = 14f; setTextColor(0xFF666666.toInt()) }
        root.addView(dateLabel)
        root.addView(button("Choose start date") { chooseStartDate() })
        progressLabel = TextView(this).apply {
            textSize = 18f; setPadding(0, 18, 0, 12); setTextColor(0xFF222222.toInt())
        }
        root.addView(progressLabel)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        root.addView(button("+ Add activity") { addActivity() })
        root.addView(button("Set a reminder") { setReminder() })
        root.addView(button("View 30-day calendar") { showCalendar() })
        root.addView(TextView(this).apply {
            text = "Your checkmarks are saved on this phone. Missed days stay in the history."
            textSize = 12f; setTextColor(0xFF777777.toInt()); setPadding(0, 20, 0, 0)
        })
        refresh()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
    }

    private fun refresh() {
        dateLabel.text = "Today: ${dateFormat.format(Date())}"
        val day = dayNumber()
        val done = activities.count { completed.contains(keyForDay(day, it)) }
        progressLabel.text = "Day $day of 30  •  $done/${activities.size} completed"
        list.removeAllViews()
        if (activities.isEmpty()) {
            list.addView(TextView(this).apply { text = "Add an activity to get started."; textSize = 16f })
        }
        activities.forEach { activity ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            val cb = CheckBox(this).apply {
                text = activity; textSize = 17f
                isChecked = completed.contains(keyForDay(day, activity))
                setOnCheckedChangeListener { _, checked ->
                    val key = keyForDay(day, activity)
                    if (checked) completed.add(key) else completed.remove(key)
                    saveData(); refresh()
                }
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            row.addView(cb)
            row.addView(button("Rename") { renameActivity(activity) })
            row.addView(button("Remove") {
                activities.remove(activity); saveData(); refresh()
            })
            list.addView(row)
        }
    }

    private fun addActivity() {
        val input = EditText(this).apply { hint = "Activity name" }
        AlertDialogCompat.show(this, "Add activity", input, "Add") {
            val name = input.text.toString().trim()
            if (name.isNotEmpty() && !activities.contains(name)) {
                activities.add(name); saveData(); refresh()
            }
        }
    }

    private fun renameActivity(old: String) {
        val input = EditText(this).apply { setText(old) }
        AlertDialogCompat.show(this, "Rename activity", input, "Save") {
            val name = input.text.toString().trim()
            if (name.isNotEmpty() && name != old) {
                val index = activities.indexOf(old)
                if (index >= 0) activities[index] = name
                val updated = completed.map { key ->
                    if (key.endsWith("::$old")) key.substringBefore("::") + "::$name" else key
                }
                completed.clear(); completed.addAll(updated)
                saveData(); refresh()
            }
        }
    }

    private fun chooseStartDate() {
        val c = Calendar.getInstance().apply { timeInMillis = startDate }
        DatePickerDialog(this, { _, year, month, day ->
            val chosen = Calendar.getInstance()
            chosen.set(year, month, day, 0, 0, 0); chosen.set(Calendar.MILLISECOND, 0)
            startDate = chosen.timeInMillis; saveData(); refresh()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun setReminder() {
        TimePickerDialog(this, { _, hour, minute ->
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
            val intent = Intent(this, ReminderReceiver::class.java).apply {
                putExtra("message", "Time to check today's activities.")
                putExtra("notificationId", hour * 60 + minute)
            }
            val id = hour * 60 + minute
            val pi = PendingIntent.getBroadcast(this, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.setRepeating(AlarmManager.RTC_WAKEUP, c.timeInMillis,
                AlarmManager.INTERVAL_DAY, pi)
            Toast.makeText(this, String.format(Locale.getDefault(),
                "Daily reminder set for %02d:%02d", hour, minute), Toast.LENGTH_LONG).show()
        }, 20, 0, false).show()
    }

    private fun showCalendar() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12)
        }
        val start = Calendar.getInstance().apply { timeInMillis = startDate }
        for (day in 1..30) {
            val c = Calendar.getInstance().apply { timeInMillis = startDate; add(Calendar.DAY_OF_YEAR, day - 1) }
            val dayActivities = activities.count { completed.contains(keyForDay(day, it)) }
            val status = when {
                dayActivities == activities.size && activities.isNotEmpty() -> "Complete"
                dayActivities > 0 -> "Partially complete"
                c.timeInMillis < System.currentTimeMillis() -> "Missed / no check-ins"
                else -> "Upcoming"
            }
            layout.addView(TextView(this).apply {
                text = "Day $day — ${dateFormat.format(c.time)} — $status"
                textSize = 14f; setPadding(0, 7, 0, 7); setTextColor(0xFF222222.toInt())
            })
        }
        val scroll = ScrollView(this).apply { addView(layout) }
        android.app.AlertDialog.Builder(this).setTitle("30-day history")
            .setView(scroll).setPositiveButton("Close", null).show()
    }
}

override fun onResume() {
    super.onResume()
    if (::clockLabel.isInitialized) {
        clockHandler.removeCallbacks(clockUpdater)
        clockUpdater.run()
    }
}

override fun onPause() {
    clockHandler.removeCallbacks(clockUpdater)
    super.onPause()
}
private object AlertDialogCompat {
    fun show(context: Context, title: String, view: View, positive: String, action: () -> Unit) {
        android.app.AlertDialog.Builder(context).setTitle(title).setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(positive) { _, _ -> action() }.show()
    }
}
