package com.example.floatingspeedruntimer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.example.floatingspeedruntimer.databinding.ActivitySettingsBinding
import com.example.floatingspeedruntimer.service.TimerService

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val timerSizeMap = mapOf("Small" to "small", "Medium" to "medium", "Large" to "large")
    private val splitNameSizeMap = mapOf("Small (12sp)" to "small", "Medium (14sp)" to "medium", "Large (16sp)" to "large")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed() // onPause cuidará de salvar
        }

        setupSpinners()
        loadSettings()
    }

    private fun setupSpinners() {
        binding.spinnerTimerSize.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timerSizeMap.keys.toTypedArray())
        binding.spinnerSplitNameSize.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, splitNameSizeMap.keys.toTypedArray())
    }

    private fun loadSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.switchShowMilliseconds.isChecked = prefs.getBoolean("show_milliseconds", true)
        binding.switchAlwaysShowMinutes.isChecked = prefs.getBoolean("always_show_minutes", true)
        binding.switchShowDelta.isChecked = prefs.getBoolean("show_delta", true)
        binding.switchShowCurrentSplit.isChecked = prefs.getBoolean("show_current_split", true)
        binding.editTextCountdown.setText(prefs.getString("timer_countdown", "0"))

        // Carregar valores dos spinners
        val timerSizeValue = prefs.getString("timer_size", "large")
        val timerSizePosition = timerSizeMap.values.indexOf(timerSizeValue)
        binding.spinnerTimerSize.setSelection(if (timerSizePosition != -1) timerSizePosition else 2)

        val splitNameSizeValue = prefs.getString("split_name_size", "medium")
        val splitNameSizePosition = splitNameSizeMap.values.indexOf(splitNameSizeValue)
        binding.spinnerSplitNameSize.setSelection(if (splitNameSizePosition != -1) splitNameSizePosition else 1)
    }

    private fun saveSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit {
            putBoolean("show_milliseconds", binding.switchShowMilliseconds.isChecked)
            putBoolean("always_show_minutes", binding.switchAlwaysShowMinutes.isChecked)
            putBoolean("show_delta", binding.switchShowDelta.isChecked)
            putBoolean("show_current_split", binding.switchShowCurrentSplit.isChecked)
            putString("timer_countdown", binding.editTextCountdown.text.toString().ifEmpty { "0" })

            // Salvar valores dos spinners
            val selectedTimerSize = timerSizeMap.values.toList()[binding.spinnerTimerSize.selectedItemPosition]
            putString("timer_size", selectedTimerSize)

            val selectedSplitNameSize = splitNameSizeMap.values.toList()[binding.spinnerSplitNameSize.selectedItemPosition]
            putString("split_name_size", selectedSplitNameSize)
        }
        
        // Notifica o serviço que as configurações mudaram
        val intent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_SETTINGS_UPDATED
        }
        startService(intent)
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }
}
