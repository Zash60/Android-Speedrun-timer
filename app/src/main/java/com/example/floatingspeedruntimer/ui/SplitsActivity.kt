package com.example.floatingspeedruntimer.ui
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.floatingspeedruntimer.R
import com.example.floatingspeedruntimer.data.Category
import com.example.floatingspeedruntimer.data.DataManager
import com.example.floatingspeedruntimer.data.Game
import com.example.floatingspeedruntimer.data.Split
import com.example.floatingspeedruntimer.databinding.ActivitySplitsBinding
import com.example.floatingspeedruntimer.databinding.DialogAddEditBinding
import com.example.floatingspeedruntimer.databinding.DialogEditSplitBinding
import com.example.floatingspeedruntimer.util.TimeFormatter
class SplitsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplitsBinding
    private lateinit var dataManager: DataManager
    private lateinit var splitAdapter: SplitAdapter
    private var game: Game? = null
    private var category: Category? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplitsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        dataManager = DataManager.getInstance(this)
        val gameName = intent.getStringExtra("GAME_NAME")
        val categoryName = intent.getStringExtra("CATEGORY_NAME")
        game = dataManager.findGameByName(gameName)
        category = dataManager.findCategoryByName(game, categoryName)
        if (category == null) {
            finish()
            return
        }
        setupToolbar()
        setupRecyclerView()
        setupSpinner()
        binding.fab.setOnClickListener { showAddEditDialog(null) }
    }
    override fun onResume() {
        super.onResume()
        category?.let {
            splitAdapter.updateData(it.splits, it.runHistory.lastOrNull())
            updateEmptyView()
        }
    }
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = category?.name
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }
    private fun setupRecyclerView() {
        splitAdapter = SplitAdapter(category!!.splits, category!!.runHistory.lastOrNull(), this::onSplitLongClick)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@SplitsActivity)
            adapter = splitAdapter
        }
    }
    private fun setupSpinner() {
        // ArrayAdapter requires a resource ID for the array. We need to create it.
        val comparisonEntries = arrayOf("Personal Best", "Best Segments", "Current Comparison")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, comparisonEntries)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.comparisonSpinner.adapter = spinnerAdapter
        binding.comparisonSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val values = arrayOf("personal_best", "best_segments", "current_comparison")
                splitAdapter.setComparisonMode(values[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    private fun onSplitLongClick(split: Split, position: Int) {
        showAdvancedEditDialog(split, position)
    }
    private fun showAddEditDialog(split: Split?) {
        val dialogBinding = DialogAddEditBinding.inflate(layoutInflater)
        val isEditing = split != null
        if (isEditing) {
            dialogBinding.editText.setText(split!!.name)
        }
        AlertDialog.Builder(this)
            .setTitle(if (isEditing) "Edit Split" else "Add Split")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val name = dialogBinding.editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (isEditing) {
                        split!!.name = name
                    } else {
                        category!!.splits.add(Split(name))
                    }
                    dataManager.saveGames()
                    splitAdapter.updateData(category!!.splits, category!!.runHistory.lastOrNull())
                    updateEmptyView()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun showAdvancedEditDialog(split: Split, position: Int) {
        val dialogBinding = DialogEditSplitBinding.inflate(layoutInflater)
        dialogBinding.editTextName.setText(split.name)
        populateTimeFields(dialogBinding, split)
        populatePositionSpinner(dialogBinding, position)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit split")
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                try {
                    val newName = dialogBinding.editTextName.text.toString()
                    val newPbTime = TimeFormatter.parseTime(dialogBinding.editTextPbHrs, dialogBinding.editTextPbMin, dialogBinding.editTextPbSec, dialogBinding.editTextPbMs)
                    val newBestTime = TimeFormatter.parseTime(dialogBinding.editTextBestHrs, dialogBinding.editTextBestMin, dialogBinding.editTextBestSec, dialogBinding.editTextBestMs)
                    val newPosition = dialogBinding.spinnerPosition.selectedItemPosition
                    split.name = newName
                    split.personalBestTime = newPbTime
                    split.bestSegmentTime = newBestTime
                    if (newPosition != position) {
                        category!!.splits.removeAt(position)
                        category!!.splits.add(newPosition, split)
                    }
                    dataManager.saveGames()
                    splitAdapter.notifyDataSetChanged()
                    calculateSumOfBest()
                    dialog.dismiss()
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Invalid time format", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }
    private fun populateTimeFields(binding: DialogEditSplitBinding, split: Split) {
        val pbParts = TimeFormatter.toHMS(split.personalBestTime)
        binding.editTextPbHrs.setText(pbParts.hours.toString())
        binding.editTextPbMin.setText(pbParts.minutes.toString())
        binding.editTextPbSec.setText(pbParts.seconds.toString())
        binding.editTextPbMs.setText(pbParts.milliseconds.toString())
        val bestParts = TimeFormatter.toHMS(split.bestSegmentTime)
        binding.editTextBestHrs.setText(bestParts.hours.toString())
        binding.editTextBestMin.setText(bestParts.minutes.toString())
        binding.editTextBestSec.setText(bestParts.seconds.toString())
        binding.editTextBestMs.setText(bestParts.milliseconds.toString())
    }
    private fun populatePositionSpinner(binding: DialogEditSplitBinding, currentPosition: Int) {
        val positions = (1..category!!.splits.size).map { it.toString() }.toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, positions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPosition.adapter = adapter
        binding.spinnerPosition.setSelection(currentPosition)
    }
    private fun updateEmptyView() {
        if (category!!.splits.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyViewText.visibility = View.VISIBLE
            binding.emptyViewText.text = "No splits added for this category."
            binding.footerSumOfBest.root.visibility = View.GONE
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyViewText.visibility = View.GONE
            binding.footerSumOfBest.root.visibility = View.VISIBLE
        }
        calculateSumOfBest()
    }
    private fun calculateSumOfBest() {
        val sum = category!!.splits.sumOf { it.bestSegmentTime }
        binding.footerSumOfBest.sumOfBestText.text = TimeFormatter.formatTime(sum, showMillis = true)
    }
}
