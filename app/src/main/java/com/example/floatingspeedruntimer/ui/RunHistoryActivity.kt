package com.example.floatingspeedruntimer.ui
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.floatingspeedruntimer.data.Category
import com.example.floatingspeedruntimer.data.DataManager
import com.example.floatingspeedruntimer.data.Game
import com.example.floatingspeedruntimer.databinding.ActivityListLayoutBinding
class RunHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityListLayoutBinding
    private lateinit var dataManager: DataManager
    private var game: Game? = null
    private var category: Category? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.fab.visibility = View.GONE
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
        updateEmptyView()
    }
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "${category?.name} History"
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }
    private fun setupRecyclerView() {
        val reversedHistory = category!!.runHistory.reversed()
        val runHistoryAdapter = RunHistoryAdapter(reversedHistory)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@RunHistoryActivity)
            adapter = runHistoryAdapter
        }
    }
    private fun updateEmptyView() {
        if (category!!.runHistory.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyViewContainer.visibility = View.VISIBLE
            binding.emptyViewText.text = "No completed runs found."
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyViewContainer.visibility = View.GONE
        }
    }
}
