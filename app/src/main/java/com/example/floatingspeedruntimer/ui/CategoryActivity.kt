package com.example.floatingspeedruntimer.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View // <-- IMPORTAÇÃO CORRIGIDA AQUI
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.floatingspeedruntimer.data.Category
import com.example.floatingspeedruntimer.data.DataManager
import com.example.floatingspeedruntimer.data.Game
import com.example.floatingspeedruntimer.databinding.ActivityListLayoutBinding
import com.example.floatingspeedruntimer.databinding.BottomSheetCategoryMenuBinding
import com.example.floatingspeedruntimer.databinding.DialogAddEditBinding
import com.example.floatingspeedruntimer.service.AutosplitterService
import com.example.floatingspeedruntimer.service.TimerService
import com.google.android.material.bottomsheet.BottomSheetDialog

class CategoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListLayoutBinding
    private lateinit var dataManager: DataManager
    private lateinit var categoryAdapter: CategoryAdapter
    private var game: Game? = null
    private var pendingCategoryForTimer: Category? = null

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        checkOverlayPermissionAndStartTimer()
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            pendingCategoryForTimer?.let { startTimerService(it) }
        } else {
            Toast.makeText(this, "Overlay permission is required to show the timer.", Toast.LENGTH_LONG).show()
        }
        pendingCategoryForTimer = null
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, AutosplitterService::class.java).apply {
                action = AutosplitterService.ACTION_START
                putExtra(AutosplitterService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(AutosplitterService.EXTRA_RESULT_DATA, result.data!!)
                putExtra(AutosplitterService.EXTRA_GAME_NAME, game?.name)
                putExtra(AutosplitterService.EXTRA_CATEGORY_NAME, pendingCategoryForTimer?.name)
            }
            startService(intent)
        } else {
            Toast.makeText(this, "Screen capture permission is required for autosplitter to work.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        dataManager = DataManager.getInstance(this)
        val gameName = intent.getStringExtra("GAME_NAME")
        game = dataManager.findGameByName(gameName)

        if (game == null) {
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()

        binding.fab.setOnClickListener { showAddEditDialog(null) }
    }

    override fun onResume() {
        super.onResume()
        game?.let {
            categoryAdapter.updateData(it.categories)
            updateEmptyView()
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = game?.name
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter(game!!.categories, object : CategoryAdapter.CategoryClickListener {
            override fun onPlayClick(category: Category) {
                pendingCategoryForTimer = category
                checkNotificationPermission()
            }
            override fun onCategoryClick(category: Category) {
                showCategoryMenu(category)
            }
        })
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@CategoryActivity)
            adapter = categoryAdapter
        }
    }
    
    private fun showCategoryMenu(category: Category) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetCategoryMenuBinding.inflate(LayoutInflater.from(this))
        dialog.setContentView(sheetBinding.root)

        sheetBinding.bottomSheetTitle.text = category.name

        sheetBinding.optionEditSplits.setOnClickListener {
            val intent = Intent(this, SplitsActivity::class.java).apply {
                putExtra("GAME_NAME", game!!.name)
                putExtra("CATEGORY_NAME", category.name)
            }
            startActivity(intent)
            dialog.dismiss()
        }
        sheetBinding.optionViewHistory.setOnClickListener {
            val intent = Intent(this, RunHistoryActivity::class.java).apply {
                putExtra("GAME_NAME", game!!.name)
                putExtra("CATEGORY_NAME", category.name)
            }
            startActivity(intent)
            dialog.dismiss()
        }
        sheetBinding.optionEditName.setOnClickListener {
            showAddEditDialog(category)
            dialog.dismiss()
        }
        sheetBinding.optionDelete.setOnClickListener {
            showDeleteConfirmationDialog(category)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    checkOverlayPermissionAndStartTimer()
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            checkOverlayPermissionAndStartTimer()
        }
    }

    private fun checkOverlayPermissionAndStartTimer() {
        if (pendingCategoryForTimer == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
             AlertDialog.Builder(this)
                .setTitle("Permission Needed")
                .setMessage("This app needs permission to display the timer over other apps.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    overlayPermissionLauncher.launch(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            startTimerService(pendingCategoryForTimer!!)
        }
    }
    
    private fun startTimerService(category: Category) {
        // 1. Inicia o TimerService normal
        val timerIntent = Intent(this, TimerService::class.java).apply {
            putExtra("GAME_NAME", game!!.name)
            putExtra("CATEGORY_NAME", category.name)
        }
        startService(timerIntent)
        
        // 2. Verifica se a categoria tem algum split com imagem configurada
        if (category.splits.any { !it.autoSplitImagePath.isNullOrEmpty() }) {
            // 3. Se tiver, pede permissão para capturar a tela e inicia o AutosplitterService
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }

        // 4. Fecha a UI principal
        finishAffinity()
    }

    private fun showAddEditDialog(category: Category?) {
        val dialogBinding = DialogAddEditBinding.inflate(layoutInflater)
        val isEditing = category != null
        if (isEditing) {
            dialogBinding.editText.setText(category!!.name)
        }

        AlertDialog.Builder(this)
            .setTitle(if (isEditing) "Edit Category" else "Add Category")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val name = dialogBinding.editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (isEditing) {
                        category!!.name = name
                    } else {
                        game!!.categories.add(Category(name))
                    }
                    dataManager.saveGames()
                    categoryAdapter.updateData(game!!.categories)
                    updateEmptyView()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(category: Category) {
        AlertDialog.Builder(this)
            .setTitle("Delete Category")
            .setMessage("Are you sure you want to delete '${category.name}'? All splits and run history will be lost.")
            .setPositiveButton("Delete") { _, _ ->
                game!!.categories.remove(category)
                dataManager.saveGames()
                categoryAdapter.updateData(game!!.categories)
                updateEmptyView()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateEmptyView() {
        if (game!!.categories.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyViewContainer.visibility = View.VISIBLE
            binding.emptyViewText.text = "No categories added for this game."
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyViewContainer.visibility = View.GONE
        }
    }
}
