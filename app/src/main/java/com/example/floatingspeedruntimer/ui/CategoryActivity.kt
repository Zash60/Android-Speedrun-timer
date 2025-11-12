package com.example.floatingspeedruntimer.ui
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
import com.example.floatingspeedruntimer.databinding.DialogAddEditBinding
import com.example.floatingspeedruntimer.service.TimerService
class CategoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityListLayoutBinding
    private lateinit var dataManager: DataManager
    private lateinit var categoryAdapter: CategoryAdapter
    private var game: Game? = null
    private var pendingCategoryForTimer: Category? = null
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            checkOverlayPermissionAndStartTimer()
        } else {
            Toast.makeText(this, "Notification permission is needed for the 'Close' button.", Toast.LENGTH_LONG).show()
            checkOverlayPermissionAndStartTimer()
        }
    }
    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            pendingCategoryForTimer?.let { startTimerService(it) }
        } else {
            Toast.makeText(this, "Overlay permission is required to show the timer.", Toast.LENGTH_LONG).show()
        }
        pendingCategoryForTimer = null
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
            override fun onCategoryClick(category: Category) { onCategoryLongClick(category) }
        })
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@CategoryActivity)
            adapter = categoryAdapter
        }
    }
    private fun onCategoryLongClick(category: Category) {
        val items = arrayOf("Edit Splits", "View History", "Edit Name", "Delete")
        AlertDialog.Builder(this)
            .setTitle(category.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, SplitsActivity::class.java)
                        intent.putExtra("GAME_NAME", game!!.name)
                        intent.putExtra("CATEGORY_NAME", category.name)
                        startActivity(intent)
                    }
                    1 -> {
                        val intent = Intent(this, RunHistoryActivity::class.java)
                        intent.putExtra("GAME_NAME", game!!.name)
                        intent.putExtra("CATEGORY_NAME", category.name)
                        startActivity(intent)
                    }
                    2 -> showAddEditDialog(category)
                    3 -> showDeleteConfirmationDialog(category)
                }
            }.show()
    }
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    checkOverlayPermissionAndStartTimer()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
        val intent = Intent(this, TimerService::class.java).apply {
            putExtra("GAME_NAME", game!!.name)
            putExtra("CATEGORY_NAME", category.name)
        }
        startService(intent)
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
