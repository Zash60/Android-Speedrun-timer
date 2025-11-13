package com.example.floatingspeedruntimer.ui

import android.content.Intent
import android.os.Build // <-- IMPORTAÇÃO CORRIGIDA AQUI
import android.os.Bundle
import android.widget.Toast
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
            showFinishedRunDialog(newTime, gameName, categoryName, segmentTimes)
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
                savePbData(gameName, categoryName, newTime, segmentTimes)
                resetTimerAndFinish()
            }
            .setNegativeButton("Discard") { _, _ ->
                // Ao descartar um PB, a run ainda é salva no histórico, mas os recordes não são atualizados
                saveRunToHistory(newTime, gameName, categoryName, segmentTimes)
                resetTimerAndFinish()
            }
            .setOnCancelListener {
                resetTimerAndFinish()
            }
            .show()
    }

    private fun showFinishedRunDialog(newTime: Long, gameName: String?, categoryName: String?, segmentTimes: List<Long>) {
         MaterialAlertDialogBuilder(this)
            .setTitle("Run Finished")
            .setMessage("Time: ${TimeFormatter.formatTime(newTime, true)}")
            .setPositiveButton("Save to History & Reset") { _, _ ->
                saveRunToHistory(newTime, gameName, categoryName, segmentTimes)
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
        Toast.makeText(applicationContext, "Run saved to history", Toast.LENGTH_SHORT).show()
    }

    private fun <T : Serializable?> getSerializable(intent: Intent, key: String, clazz: Class<T>): T? {
        // A verificação de versão do SDK agora funciona porque 'Build' foi importado
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        finishAndRemoveTask() // Garante que a activity transparente seja completamente removida
    }

    private fun savePbData(gameName: String?, categoryName: String?, newTime: Long, segmentTimes: List<Long>) {
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
                        // Se o PB está sendo salvo, então os splits do PB também são salvos.
                        it.splits[i].personalBestTime = cumulativeTime
                        
                        // Bug SOB Corrigido: Atualiza o melhor segmento apenas quando a run é salva.
                        if (it.splits[i].bestSegmentTime == 0L || segmentTimes[i] < it.splits[i].bestSegmentTime) {
                            it.splits[i].bestSegmentTime = segmentTimes[i]
                        }
                    }
                }
            }
            dataManager.saveGames()
            Toast.makeText(applicationContext, "New PB saved!", Toast.LENGTH_SHORT).show()
        }
    }
}
