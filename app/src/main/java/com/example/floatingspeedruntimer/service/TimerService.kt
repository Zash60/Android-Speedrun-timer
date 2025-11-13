package com.example.floatingspeedruntimer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.example.floatingspeedruntimer.R
import com.example.floatingspeedruntimer.data.Category
import com.example.floatingspeedruntimer.data.DataManager
import com.example.floatingspeedruntimer.data.Run
import com.example.floatingspeedruntimer.data.Split
import com.example.floatingspeedruntimer.databinding.TimerLayoutBinding
import com.example.floatingspeedruntimer.ui.DialogActivity
import com.example.floatingspeedruntimer.util.TimeFormatter
import java.io.Serializable
import kotlin.math.abs

class TimerService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var binding: TimerLayoutBinding
    private val timerHandler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var timeInMilliseconds = 0L

    private lateinit var dataManager: DataManager
    private var category: Category? = null
    private var splits: List<Split> = emptyList()
    private var currentSplitIndex = -1
    private val currentRunSegmentTimes = mutableListOf<Long>()

    private enum class TimerState { STOPPED, COUNTDOWN, RUNNING, FINISHED }
    private var state = TimerState.STOPPED

    private var isGoldSplit = false
    private var showDelta = true
    private var compareAgainst = "personal_best"
    private var countdownMillis = 0L
    
    // Cores
    private var colorNeutral = Color.WHITE
    private var colorAhead = Color.GREEN
    private var colorBehind = Color.RED
    private var colorPb = Color.CYAN
    private var colorBestSegment = Color.YELLOW

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CLOSE_SERVICE -> { stopSelf(); return START_NOT_STICKY }
            ACTION_RESET_TIMER -> { resetTimer(); return START_NOT_STICKY }
            ACTION_SETTINGS_UPDATED -> {
                if (::binding.isInitialized) { loadSettings() }
                return START_STICKY
            }
        }
        
        if (intent?.hasExtra("GAME_NAME") == true) {
            dataManager = DataManager.getInstance(this)
            val gameName = intent.getStringExtra("GAME_NAME")
            val categoryName = intent.getStringExtra("CATEGORY_NAME")
            val game = dataManager.findGameByName(gameName)
            category = dataManager.findCategoryByName(game, categoryName)
            splits = category?.splits ?: emptyList()

            startForeground(NOTIFICATION_ID, createNotification(gameName, categoryName))
            if (!::binding.isInitialized) createFloatingView()
            loadSettings()
            resetTimer()
        }
        return START_NOT_STICKY
    }

    private fun loadSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        showDelta = prefs.getBoolean("show_delta", true)
        val countdownSeconds = prefs.getString("timer_countdown", "0")?.toFloatOrNull() ?: 0f
        countdownMillis = (countdownSeconds * 1000).toLong()

        val timerSizeValue = prefs.getString("timer_size", "large")
        val timerFontSize = when (timerSizeValue) { "small" -> 26f; "medium" -> 32f; else -> 40f }
        
        val splitNameSizeValue = prefs.getString("split_name_size", "medium")
        val splitNameFontSize = when (splitNameSizeValue) { "small" -> 12f; "large" -> 16f; else -> 14f }

        colorNeutral = prefs.getInt("color_neutral", Color.WHITE)
        colorAhead = prefs.getInt("color_ahead", Color.GREEN)
        colorBehind = prefs.getInt("color_behind", Color.RED)
        colorPb = prefs.getInt("color_pb", Color.CYAN)
        colorBestSegment = prefs.getInt("color_best_segment", Color.YELLOW)

        binding.timerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, timerFontSize)
        binding.splitNameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, splitNameFontSize)
        
        binding.deltaText.visibility = if (showDelta) View.VISIBLE else View.GONE
        binding.splitNameText.visibility = if (prefs.getBoolean("show_current_split", true)) View.VISIBLE else View.GONE
        
        binding.timerRoot.background = null

        if (state == TimerState.STOPPED) {
             if (countdownMillis > 0) {
                binding.timerText.text = "-" + TimeFormatter.formatCountdownTime(countdownMillis, prefs.getBoolean("show_milliseconds", true))
            } else {
                binding.timerText.text = TimeFormatter.formatTime(
                    millis = 0, 
                    showMillis = prefs.getBoolean("show_milliseconds", true), 
                    alwaysShowMinutes = prefs.getBoolean("always_show_minutes", true)
                )
            }
        }
        updateRunningTimerColor()
    }

    private fun createFloatingView() {
        binding = TimerLayoutBinding.inflate(LayoutInflater.from(this))
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50; y = 150
        }
        windowManager.addView(binding.root, params)
        setupTouchListener(params)
    }

    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        binding.root.setOnClickListener { handleTap() }
        binding.root.setOnLongClickListener { handleLongPress(); true }
        binding.root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            private var isDragging = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        isDragging = false
                        return false 
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX; val dy = event.rawY - initialTouchY
                        if (!isDragging && (abs(dx) > ViewConfiguration.get(v.context).scaledTouchSlop || abs(dy) > ViewConfiguration.get(v.context).scaledTouchSlop)) {
                            isDragging = true
                        }
                        if (isDragging) {
                            params.x = initialX + dx.toInt(); params.y = initialY + dy.toInt()
                            windowManager.updateViewLayout(binding.root, params)
                        }
                        return isDragging
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasDragging = isDragging; isDragging = false; return wasDragging
                    }
                }
                return false
            }
        })
    }

    private fun handleTap() {
        when (state) {
            TimerState.STOPPED -> start()
            TimerState.RUNNING -> split()
            else -> {}
        }
    }

    private fun handleLongPress() {
        when (state) {
            TimerState.RUNNING, TimerState.COUNTDOWN -> {
                // Bug SOB Corrigido: Resetar no meio da run não salva best segments.
                // Apenas reseta o timer. A lógica de salvar só acontece no final.
                resetTimer()
                Toast.makeText(this, "Run discarded", Toast.LENGTH_SHORT).show()
            }
            TimerState.FINISHED -> showPbDialog()
            else -> {}
        }
    }

    private fun showPbDialog() {
        val cat = category ?: run { stopSelf(); return }
        val isNewPb = cat.personalBest == 0L || timeInMilliseconds < cat.personalBest

        val dialogIntent = Intent(this, DialogActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("IS_NEW_PB", isNewPb)
            putExtra("NEW_TIME", timeInMilliseconds)
            putExtra("OLD_TIME", cat.personalBest)
            putExtra("GAME_NAME", dataManager.games.find { it.categories.contains(cat) }?.name)
            putExtra("CATEGORY_NAME", cat.name)
            putExtra("SEGMENT_TIMES", currentRunSegmentTimes as Serializable)
        }
        startActivity(dialogIntent)
    }

    private fun start() {
        if (countdownMillis > 0) startCountdown() else startTimer()
    }

    private fun startCountdown() {
        state = TimerState.COUNTDOWN
        startTime = SystemClock.uptimeMillis() + countdownMillis
        timerHandler.post(updateCountdownThread)
    }

    private fun startTimer() {
        timerHandler.removeCallbacks(updateCountdownThread)
        startTime = SystemClock.uptimeMillis()
        timerHandler.post(updateTimerThread)
        state = TimerState.RUNNING
        currentSplitIndex = 0
        updateSplitName()
        binding.deltaText.text = ""
        category?.let { it.runs++; dataManager.saveGames() }
        updateRunningTimerColor()
    }

    private fun split() {
        if (currentSplitIndex < 0 || splits.isEmpty()) { stopTimer(); return }
        isGoldSplit = false
        val lastTotalTime = currentRunSegmentTimes.sum()
        val currentSegmentTime = timeInMilliseconds - lastTotalTime
        currentRunSegmentTimes.add(currentSegmentTime)

        val currentSplit = splits[currentSplitIndex]
        
        // Bug SOB Corrigido:
        // A lógica de atualizar o 'bestSegmentTime' foi movida para a DialogActivity,
        // e só é chamada quando o usuário clica em "Salvar".
        // Aqui, apenas verificamos se é um novo "gold" para fins de cor.
        if (currentSplit.bestSegmentTime == 0L || currentSegmentTime < currentSplit.bestSegmentTime) {
            isGoldSplit = true
        }

        val comparisonTime = when (compareAgainst) {
            "personal_best" -> currentSplit.personalBestTime
            "best_segments" -> (0..currentSplitIndex).sumOf { splits[it].bestSegmentTime }
            else -> 0L
        }
        if (comparisonTime > 0) { showDelta(timeInMilliseconds - comparisonTime) }

        currentSplitIndex++
        if (currentSplitIndex >= splits.size) { stopTimer() } else { updateSplitName() }
        updateRunningTimerColor()
    }

    private fun stopTimer() {
        timerHandler.removeCallbacks(updateTimerThread)
        state = TimerState.FINISHED
        isGoldSplit = false
        if(binding.splitNameText.visibility == View.VISIBLE) binding.splitNameText.text = "Finished!"
        
        category?.let {
            if (it.personalBest > 0) { showDelta(timeInMilliseconds - it.personalBest) }
            else { binding.deltaText.text = "" }
        }
        updateRunningTimerColor()
    }

    private fun resetTimer() {
        timerHandler.removeCallbacks(updateTimerThread)
        timerHandler.removeCallbacks(updateCountdownThread)
        startTime = 0L; timeInMilliseconds = 0L
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (countdownMillis > 0) {
            binding.timerText.text = "-" + TimeFormatter.formatCountdownTime(countdownMillis, prefs.getBoolean("show_milliseconds", true))
        } else {
            binding.timerText.text = TimeFormatter.formatTime(
                millis = 0,
                showMillis = prefs.getBoolean("show_milliseconds", true),
                alwaysShowMinutes = prefs.getBoolean("always_show_minutes", true)
            )
        }
        
        binding.deltaText.text = ""
        currentSplitIndex = -1; updateSplitName()
        currentRunSegmentTimes.clear()
        state = TimerState.STOPPED
        isGoldSplit = false
        updateRunningTimerColor()
    }

    private val updateTimerThread = object : Runnable {
        override fun run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime
            val prefs = PreferenceManager.getDefaultSharedPreferences(this@TimerService)
            binding.timerText.text = TimeFormatter.formatTime(
                millis = timeInMilliseconds,
                showMillis = prefs.getBoolean("show_milliseconds", true),
                alwaysShowMinutes = prefs.getBoolean("always_show_minutes", true)
            )
            updateRunningTimerColor()
            timerHandler.postDelayed(this, 30)
        }
    }

    private val updateCountdownThread = object : Runnable {
        override fun run() {
            val millisRemaining = startTime - SystemClock.uptimeMillis()
            if (millisRemaining > 0) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this@TimerService)
                val formattedTime = TimeFormatter.formatCountdownTime(millisRemaining, prefs.getBoolean("show_milliseconds", true))
                binding.timerText.text = "-$formattedTime"
                timerHandler.postDelayed(this, 30)
            } else {
                startTimer()
            }
        }
    }

    private fun updateRunningTimerColor() {
        if (isGoldSplit) {
            binding.timerText.setTextColor(colorBestSegment)
            return
        }
        
        if (state == TimerState.FINISHED) {
             category?.let {
                if (it.personalBest == 0L || timeInMilliseconds < it.personalBest) {
                    binding.timerText.setTextColor(colorPb)
                } else {
                     binding.timerText.setTextColor(colorNeutral)
                }
            }
            return
        }

        if (state != TimerState.RUNNING || splits.isEmpty() || currentSplitIndex < 0 || currentSplitIndex >= splits.size) {
            binding.timerText.setTextColor(colorNeutral)
            return
        }

        val comparisonTime = when (compareAgainst) {
            "personal_best" -> splits[currentSplitIndex].personalBestTime
            "best_segments" -> (0..currentSplitIndex).sumOf { splits[it].bestSegmentTime }
            else -> 0L
        }
        
        if (comparisonTime > 0) {
            binding.timerText.setTextColor(if (timeInMilliseconds > comparisonTime) colorBehind else colorAhead)
        } else {
            binding.timerText.setTextColor(colorNeutral)
        }
    }

    private fun updateSplitName() {
        val show = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("show_current_split", true)
        if (!show || state == TimerState.STOPPED) {
            binding.splitNameText.text = ""; return
        }
        binding.splitNameText.text = splits.getOrNull(currentSplitIndex)?.name ?: ""
    }

    private fun showDelta(delta: Long) {
        if (!showDelta) {
            binding.deltaText.text = ""
            return
        }
        val sign = if (delta >= 0) "+" else "-"
        binding.deltaText.text = sign + TimeFormatter.formatTime(
            millis = abs(delta),
            showMillis = true,
            alwaysShowMinutes = false
        )
        binding.deltaText.setTextColor(if (delta >= 0) colorBehind else colorAhead)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Timer Service"
            val descriptionText = "Notification for the running speedrun timer"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(null, null)
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(gameName: String?, categoryName: String?) =
        NotificationCompat.Builder(this, CHANNEL_ID).apply {
            val pbTime = category?.personalBest?.takeIf { it > 0 }?.let { "PB: ${TimeFormatter.formatTime(it, true)}" } ?: "No PB set"
            setContentTitle("$gameName - $categoryName")
            setContentText(pbTime)
            setSmallIcon(R.mipmap.ic_launcher_round)
            priority = NotificationCompat.PRIORITY_DEFAULT
            setOngoing(true)
            addAction(R.drawable.ic_close, "Close timer", getCloseServicePendingIntent())
        }.build()
        
    private fun getCloseServicePendingIntent() : PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val stopIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_CLOSE_SERVICE
        }
        return PendingIntent.getService(this, 1, stopIntent, flags)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacksAndMessages(null)
        if (::binding.isInitialized && binding.root.isAttachedToWindow) {
            windowManager.removeView(binding.root)
        }
    }
    
    companion object {
        const val CHANNEL_ID = "TimerServiceChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_CLOSE_SERVICE = "ACTION_CLOSE_SERVICE"
        const val ACTION_RESET_TIMER = "ACTION_RESET_TIMER"
        const val ACTION_SETTINGS_UPDATED = "ACTION_SETTINGS_UPDATED"
    }
}
