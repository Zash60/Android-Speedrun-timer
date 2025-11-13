package com.example.floatingspeedruntimer.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.floatingspeedruntimer.data.DataManager
import com.example.floatingspeedruntimer.data.Run
import com.example.floatingspeedruntimer.service.TimerService
import com.example.floatingspeedruntimer.util.TimeFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.Serializable

class DialogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isNewPb = intent.getBooleanExtra("IS_NEW_PB", false)
        val newTime = intent.getLongExtra("NEW_TIME", 0)
        val oldTime = intent.getLongExtra("OLD_TIME", 0)
        val gameName = intent.getStringExtra("GAME_NAME")
        val categoryName = intent.getStringExtra("CATEGORY_NAME")
        val segmentTimes = getSerializable(intent, "SEGMENT_TIMES", ArrayList::class.java) as? ArrayList<Long> ?: arrayListOf()

        if (isNewPb) {
            showNewPbDialog(newTime, oldTime, gameName, categoryName, segmentTimes)
        } else {
            // Se não for PB, apenas salva a run e reseta
            saveRunToHistory(newTime, gameName, categoryName, segmentTimes)
            resetTimerAndFinish()
        }
    }
    
    private fun showNewPbDialog(newTime: Long, oldTime: Long, gameName: String?, categoryName: String?, segmentTimes: List<Long>) {
        val message = if (oldTime > 0) {
            val improvement = oldTime - newTime
            "Previous PB: ${TimeFormatter.formatTime(oldTime, true)}\n" +
            "Improvement: -${TimeFormatter.formatTime(improvement, true)}"
        } else {
            "New Time: ${TimeFormatter.formatTime(newTime, true)}"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("New Personal Best!")
            .setMessage(message)
            .setPositiveButton("Save & Reset") { _, _ ->
                saveData(gameName, categoryName, newTime, segmentTimes)
                resetTimerAndFinish()
            }
            .setNegativeButton("Discard") { _, _ ->
                resetTimerAndFinish()
            }
            .setOnCancelListener {
                resetTimerAndFinish()
            }
            .show()
    }
    
    private fun saveRunToHistory(time: Long, gameName: String?, categoryName: String?, segmentTimes: List<Long>) {
        val dataManager = DataManager.getInstance(applicationContext)
        val game = dataManager.findGameByName(gameName)
        val category = dataManager.findCategoryByName(game, categoryName)
        category?.runHistory?.add(Run(time, System.currentTimeMillis(), segmentTimes))
        dataManager.saveGames()
        // Toast.makeText(applicationContext, "Run saved to history", Toast.LENGTH_SHORT).show()
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
