package com.example.floatingspeedruntimer.ui

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.example.floatingspeedruntimer.databinding.ActivitySettingsBinding
import com.example.floatingspeedruntimer.service.TimerService
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerClickListener
import com.flask.colorpicker.builder.ColorPickerDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences
    private val timerSizeMap = mapOf("Small" to "small", "Medium" to "medium", "Large" to "large")
    private val splitNameSizeMap = mapOf("Small (12sp)" to "small", "Medium (14sp)" to "medium", "Large (16sp)" to "large")

    // Lançador para o seletor de arquivos de fonte
    private val fontPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                // Pega a permissão persistente para que o app possa ler o arquivo no futuro
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                saveFontPreference(uri)
                updateFontSummary()
            }
        }
    }

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
        setupFontPickers()
        loadSettings()
    }

    private fun setupSpinners() {
        binding.spinnerTimerSize.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timerSizeMap.keys.toTypedArray())
        binding.spinnerSplitNameSize.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, splitNameSizeMap.keys.toTypedArray())
    }

    private fun setupColorPickers() {
        binding.colorPickerNeutral.setOnClickListener { openColorPicker("color_neutral", "Neutral Color") }
        binding.colorPickerAhead.setOnClickListener { openColorPicker("color_ahead", "Ahead Color") }
        binding.colorPickerBehind.setOnClickListener { openColorPicker("color_behind", "Behind Color") }
        binding.colorPickerPb.setOnClickListener { openColorPicker("color_pb", "New PB Color") }
        binding.colorPickerBestSegment.setOnClickListener { openColorPicker("color_best_segment", "Best Segment Color") }
    }

    private fun setupFontPickers() {
        binding.buttonChooseFont.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // Permite qualquer tipo de arquivo, mas filtramos por mime type
                val mimeTypes = arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/font-sfnt")
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            }
            fontPickerLauncher.launch(intent)
        }

        binding.buttonResetFont.setOnClickListener {
            // Libera a permissão do URI antigo, se existir
            val oldUriString = prefs.getString("custom_font_uri", null)
            if (oldUriString != null) {
                try {
                    val oldUri = Uri.parse(oldUriString)
                    contentResolver.releasePersistableUriPermission(oldUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // Ignora o erro se a permissão já foi revogada
                }
            }
            prefs.edit { remove("custom_font_uri") }
            updateFontSummary()
        }
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
            .showAlphaSlider(true)
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
        
        updateFontSummary()
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

    private fun saveFontPreference(uri: Uri) {
        prefs.edit { putString("custom_font_uri", uri.toString()) }
    }

    private fun updateFontSummary() {
        val uriString = prefs.getString("custom_font_uri", null)
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            binding.textFontName.text = getFileName(uri) ?: "Custom font selected"
        } else {
            binding.textFontName.text = "Default monospace font"
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor.use { c ->
                if (c != null && c.moveToFirst()) {
                    val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = c.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
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
