package com.example.floatingspeedruntimer.ui
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.floatingspeedruntimer.R
import com.example.floatingspeedruntimer.data.DataManager
import com.example.floatingspeedruntimer.data.Game
import com.example.floatingspeedruntimer.databinding.ActivityListLayoutBinding
import com.example.floatingspeedruntimer.databinding.DialogAddEditBinding
class MainActivity : AppCompatActivity(), GameAdapter.OnItemClickListener {
    private lateinit var binding: ActivityListLayoutBinding
    private lateinit var dataManager: DataManager
    private lateinit var gameAdapter: GameAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        dataManager = DataManager.getInstance(this)
        gameAdapter = GameAdapter(dataManager.games, this)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = gameAdapter
        }
        binding.fab.setOnClickListener { showAddEditDialog(null) }
    }
    override fun onResume() {
        super.onResume()
        gameAdapter.updateData(dataManager.games)
        updateEmptyView()
    }
    override fun onGameClick(game: Game) {
        val intent = Intent(this, CategoryActivity::class.java).apply {
            putExtra("GAME_NAME", game.name)
        }
        startActivity(intent)
    }
    override fun onGameLongClick(game: Game) {
        val items = arrayOf("Edit", "Delete")
        AlertDialog.Builder(this)
            .setTitle(game.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showAddEditDialog(game)
                    1 -> showDeleteConfirmationDialog(game)
                }
            }
            .show()
    }
    private fun updateEmptyView() {
        if (dataManager.games.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyViewContainer.visibility = View.VISIBLE
            binding.emptyViewText.text = "No games added.
Click '+' to begin."
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyViewContainer.visibility = View.GONE
        }
    }
    private fun showAddEditDialog(game: Game?) {
        val dialogBinding = DialogAddEditBinding.inflate(layoutInflater)
        val isEditing = game != null
        if (isEditing) {
            dialogBinding.editText.setText(game!!.name)
        }
        AlertDialog.Builder(this)
            .setTitle(if (isEditing) "Edit Game" else "Add Game")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val name = dialogBinding.editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (isEditing) {
                        game!!.name = name
                    } else {
                        dataManager.games.add(Game(name))
                    }
                    dataManager.saveGames()
                    gameAdapter.updateData(dataManager.games)
                    updateEmptyView()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun showDeleteConfirmationDialog(game: Game) {
        AlertDialog.Builder(this)
            .setTitle("Delete Game")
            .setMessage("Are you sure you want to delete '${game.name}'? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                dataManager.games.remove(game)
                dataManager.saveGames()
                gameAdapter.updateData(dataManager.games)
                updateEmptyView()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
