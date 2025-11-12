package com.example.floatingspeedruntimer.ui
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.example.floatingspeedruntimer.databinding.ActivitySettingsBinding
import com.example.floatingspeedruntimer.service.TimerService
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            saveSettings()
            onBackPressedDispatcher.onBackPressed()
        }
        loadSettings()
    }
    private fun loadSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding.switchShowMilliseconds.isChecked = prefs.getBoolean("show_milliseconds", true)
        binding.switchShowDelta.isChecked = prefs.getBoolean("show_delta", true)
        binding.switchShowCurrentSplit.isChecked = prefs.getBoolean("show_current_split", true)
        binding.editTextCountdown.setText(prefs.getString("timer_countdown", "0"))
    }
    private fun saveSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit {
            putBoolean("show_milliseconds", binding.switchShowMilliseconds.isChecked)
            putBoolean("show_delta", binding.switchShowDelta.isChecked)
            putBoolean("show_current_split", binding.switchShowCurrentSplit.isChecked)
            putString("timer_countdown", binding.editTextCountdown.text.toString().ifEmpty { "0" })
        }
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
