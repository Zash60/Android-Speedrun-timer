package com.example.floatingspeedruntimer.ui
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.floatingspeedruntimer.data.DataManager
import com.example.floatingspeedruntimer.data.Run
import com.example.floatingspeedruntimer.databinding.ActivityDialogBinding
import com.example.floatingspeedruntimer.service.TimerService
import com.example.floatingspeedruntimer.util.TimeFormatter.formatTime
import java.io.Serializable
class DialogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDialogBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        this.setFinishOnTouchOutside(false)
        val gameName = intent.getStringExtra("GAME_NAME")
        val categoryName = intent.getStringExtra("CATEGORY_NAME")
        val newTime = intent.getLongExtra("NEW_TIME", 0)
        val oldTime = intent.getLongExtra("OLD_TIME", 0)
        val segmentTimes = getSerializable(intent, "SEGMENT_TIMES", ArrayList::class.java) as? ArrayList<Long> ?: arrayListOf()
        if (oldTime > 0) {
            binding.textPreviousPb.text = "Previous PB: ${formatTime(oldTime, true)}"
            val improvement = oldTime - newTime
            binding.textImprovement.text = "Improvement: -${formatTime(improvement, true)}"
        } else {
            binding.textPreviousPb.text = "Previous PB: None"
            binding.textImprovement.text = "New Time: ${formatTime(newTime, true)}"
        }
        binding.buttonCancel.setOnClickListener { resetTimerAndFinish() }
        binding.buttonReset.setOnClickListener {
            saveData(gameName, categoryName, newTime, segmentTimes)
            resetTimerAndFinish()
        }
    }
    private fun <T : Serializable?> getSerializable(intent: Intent, key: String, clazz: Class<T>): T? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(key) as? T
        }
    }
    private fun resetTimerAndFinish() {
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_RESET_TIMER
        }
        startService(serviceIntent)
        finish()
    }
    private fun saveData(gameName: String?, categoryName: String?, newTime: Long, segmentTimes: List<Long>) {
        val dataManager = DataManager.getInstance(applicationContext)
        val game = dataManager.findGameByName(gameName)
        val category = dataManager.findCategoryByName(game, categoryName)
        category?.let {
            it.personalBest = newTime
            it.runHistory.add(Run(newTime, System.currentTimeMillis(), segmentTimes))
            if (segmentTimes.isNotEmpty()) {
                var cumulativeTime = 0L
                for (i in segmentTimes.indices) {
                    if (i < it.splits.size) {
                        cumulativeTime += segmentTimes[i]
                        it.splits[i].personalBestTime = cumulativeTime
                    }
                }
            }
            dataManager.saveGames()
        }
    }
}
