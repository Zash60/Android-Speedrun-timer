package com.example.floatingspeedruntimer.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.floatingspeedruntimer.data.Category
import com.example.floatingspeedruntimer.data.DataManager
import com.example.floatingspeedruntimer.data.RectData
import com.example.floatingspeedruntimer.data.Split
import com.example.floatingspeedruntimer.databinding.ActivityAutosplitConfigBinding
import com.example.floatingspeedruntimer.service.CaptureOverlayService
import com.example.floatingspeedruntimer.ui.autosplit.SplitImageAdapter
import com.google.android.material.slider.Slider
import kotlin.math.roundToInt

class AutoSplitConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAutosplitConfigBinding
    private lateinit var dataManager: DataManager
    private var category: Category? = null
    private lateinit var splitImageAdapter: SplitImageAdapter

    private var currentMode = ""
    private var splitToCapture: Split? = null
    
    private val mediaProjectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, CaptureOverlayService::class.java).apply {
                action = CaptureOverlayService.ACTION_SHOW
                putExtra(CaptureOverlayService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureOverlayService.EXTRA_RESULT_DATA, result.data!!)
                putExtra(CaptureOverlayService.EXTRA_INITIAL_RECT, category?.autoSplitterCaptureRegion)
                putExtra(CaptureOverlayService.EXTRA_MODE, currentMode)
                putExtra(CaptureOverlayService.EXTRA_CATEGORY_NAME, category?.name)
                if (currentMode == CaptureOverlayService.MODE_CAPTURE_SPLIT) {
                    putExtra(CaptureOverlayService.EXTRA_SPLIT_ID, splitToCapture?.name)
                }
            }
            startService(intent)
        }
    }
    
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                CaptureOverlayService.ACTION_REGION_SAVED -> {
                    val region = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getSerializableExtra("region", RectData::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getSerializableExtra("region") as? RectData
                    }
                    category?.autoSplitterCaptureRegion = region
                    dataManager.saveGames()
                    Toast.makeText(this@AutoSplitConfigActivity, "Region saved", Toast.LENGTH_SHORT).show()
                }
                CaptureOverlayService.ACTION_IMAGE_CAPTURED -> {
                    val path = intent.getStringExtra("path")
                    val splitId = intent.getStringExtra("splitId")
                    val split = category?.splits?.find { it.name == splitId }
                    split?.autoSplitImagePath = path
                    dataManager.saveGames()
                    splitImageAdapter.notifyDataSetChanged()
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutosplitConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        dataManager = DataManager.getInstance(this)
        val gameName = intent.getStringExtra("GAME_NAME")
        val categoryName = intent.getStringExtra("CATEGORY_NAME")
        val game = dataManager.findGameByName(gameName)
        category = dataManager.findCategoryByName(game, categoryName)

        if (category == null) {
            finish()
            return
        }

        setupViews()
        loadCategorySettings()

        val filter = IntentFilter().apply {
            addAction(CaptureOverlayService.ACTION_REGION_SAVED)
            addAction(CaptureOverlayService.ACTION_IMAGE_CAPTURED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, filter)
    }
    
    private fun setupViews() {
        binding.switchAutosplitEnabled.setOnCheckedChangeListener { _, isChecked ->
            category?.autoSplitterEnabled = isChecked
        }

        binding.sliderThreshold.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            val percentage = (value * 100).roundToInt()
            binding.textThresholdValue.text = "$percentage%"
            category?.autoSplitterThreshold = value.toDouble()
        })

        binding.buttonSetCaptureRegion.setOnClickListener {
            currentMode = CaptureOverlayService.MODE_SET_REGION
            requestMediaProjection()
        }
        
        splitImageAdapter = SplitImageAdapter(category?.splits ?: emptyList()) { split ->
            currentMode = CaptureOverlayService.MODE_CAPTURE_SPLIT
            splitToCapture = split
            Toast.makeText(this, "Opening overlay to capture image for ${split.name}", Toast.LENGTH_SHORT).show()
            requestMediaProjection()
        }
        binding.recyclerViewSplitImages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSplitImages.adapter = splitImageAdapter
    }

    private fun loadCategorySettings() {
        category?.let {
            binding.switchAutosplitEnabled.isChecked = it.autoSplitterEnabled
            binding.sliderThreshold.value = it.autoSplitterThreshold.toFloat()
            val percentage = (it.autoSplitterThreshold * 100).roundToInt()
            binding.textThresholdValue.text = "$percentage%"
            splitImageAdapter.updateData(it.splits)
        }
    }
    
    private fun requestMediaProjection() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        dataManager.saveGames()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }
}
