package com.example.floatingspeedruntimer.ui

import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.example.floatingspeedruntimer.databinding.ActivitySettingsBinding
import com.example.floatingspeedruntimer.service.TimerService
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.OnColorSelectedListener
import com.flask.colorpicker.builder.ColorPickerClickListener
import com.flask.colorpicker.builder.ColorPickerDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences
    private val timerSizeMap = mapOf("Small" to "small", "Medium" to "medium", "Large" to "large")
    private val splitNameSizeMap = mapOf("Small (12sp)" to "small", "Medium (14sp)" to "medium", "Large (16sp)" to "large")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setupSpinners()
        setupColorPickers()
        loadSettings()
    }

    private fun setupSpinners() {
        binding.spinnerTimerSize.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timerSizeMap.keys.toTypedArray())
        binding.spinnerSplitNameSize.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, splitNameSizeMap.keys.toTypedArray())
    }

    private fun setupColorPickers() {
        binding.colorPickerNeutral.setOnClickListener { openColorPicker("color_neutral", "Neutral Text Color") }
        binding.colorPickerAhead.setOnClickListener { openColorPicker("color_ahead", "Ahead Color") }
        binding.colorPickerBehind.setOnClickListener { openColorPicker("color_behind", "Behind Color") }
        binding.colorPickerPb.setOnClickListener { openColorPicker("color_pb", "New PB Color") }
        binding.colorPickerBestSegment.setOnClickListener { openColorPicker("color_best_segment", "Best Segment Color") }
    }

    private fun openColorPicker(key: String, title: String) {
        val defaultColor = getDefaultColor(key)
        val currentColor = prefs.getInt(key, defaultColor)

        ColorPickerDialogBuilder
            .with(this)
            .setTitle(title)
            .initialColor(currentColor)
            .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
            .density(12)
            .showAlphaSlider(true) // Permite ajustar a transparência
            .setPositiveButton("Select", ColorPickerClickListener { dialog, selectedColor, allColors ->
                prefs.edit { putInt(key, selectedColor) }
                updateColorPreview(key)
            })
            .setNegativeButton("Cancel", null)
            .build()
            .show()
    }

    private fun loadSettings() {
        binding.switchShowMilliseconds.isChecked = prefs.getBoolean("show_milliseconds", true)
        binding.switchAlwaysShowMinutes.isChecked = prefs.getBoolean("always_show_minutes", true)
        binding.switchShowDelta.isChecked = prefs.getBoolean("show_delta", true)
        binding.switchShowCurrentSplit.isChecked = prefs.getBoolean("show_current_split", true)
        binding.editTextCountdown.setText(prefs.getString("timer_countdown", "0"))

        val timerSizeValue = prefs.getString("timer_size", "large")
        binding.spinnerTimerSize.setSelection(timerSizeMap.values.indexOf(timerSizeValue).coerceAtLeast(0))

        val splitNameSizeValue = prefs.getString("split_name_size", "medium")
        binding.spinnerSplitNameSize.setSelection(splitNameSizeMap.values.indexOf(splitNameSizeValue).coerceAtLeast(0))

        updateColorPreview("color_neutral")
        updateColorPreview("color_ahead")
        updateColorPreview("color_behind")
        updateColorPreview("color_pb")
        updateColorPreview("color_best_segment")
    }

    private fun updateColorPreview(key: String) {
        val colorView: View? = when(key) {
            "color_neutral" -> binding.colorPreviewNeutral
            "color_ahead" -> binding.colorPreviewAhead
            "color_behind" -> binding.colorPreviewBehind
            "color_pb" -> binding.colorPreviewPb
            "color_best_segment" -> binding.colorPreviewBestSegment
            else -> null
        }
        val color = prefs.getInt(key, getDefaultColor(key))
        colorView?.background = ColorDrawable(color)
    }

    private fun getDefaultColor(key: String): Int = when(key) {
        "color_neutral" -> Color.WHITE
        "color_ahead" -> Color.GREEN
        "color_behind" -> Color.RED
        "color_pb" -> Color.CYAN
        "color_best_segment" -> Color.YELLOW
        else -> Color.BLACK
    }

    private fun saveSettings() {
        prefs.edit {
            putBoolean("show_milliseconds", binding.switchShowMilliseconds.isChecked)
            putBoolean("always_show_minutes", binding.switchAlwaysShowMinutes.isChecked)
            putBoolean("show_delta", binding.switchShowDelta.isChecked)
            putBoolean("show_current_split", binding.switchShowCurrentSplit.isChecked)
            putString("timer_countdown", binding.editTextCountdown.text.toString().ifEmpty { "0" })

            val selectedTimerSize = timerSizeMap.values.toList()[binding.spinnerTimerSize.selectedItemPosition]
            putString("timer_size", selectedTimerSize)

            val selectedSplitNameSize = splitNameSizeMap.values.toList()[binding.spinnerSplitNameSize.selectedItemPosition]
            putString("split_name_size", selectedSplitNameSize)
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
