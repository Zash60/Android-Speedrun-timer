package com.example.floatingspeedruntimer.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.floatingspeedruntimer.R
import com.example.floatingspeedruntimer.api.SpeedrunApiClient
import com.example.floatingspeedruntimer.data.Category
import com.example.floatingspeedruntimer.data.DataManager
import com.example.floatingspeedruntimer.data.Game
import com.example.floatingspeedruntimer.databinding.ActivityListLayoutBinding
import com.example.floatingspeedruntimer.databinding.DialogAddEditBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

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

        // Botão FAB agora abre a seleção de método (Manual ou API)
        binding.fab.setOnClickListener { showAddMethodSelection() }
    }

    override fun onResume() {
        super.onResume()
        gameAdapter.updateData(dataManager.games)
        updateEmptyView()
    }

    // --- MÉTODOS DE NAVEGAÇÃO E ADAPTER ---

    override fun onGameClick(game: Game) {
        val intent = Intent(this, CategoryActivity::class.java).apply {
            putExtra("GAME_NAME", game.name)
        }
        startActivity(intent)
    }

    override fun onGameLongClick(game: Game) {
        val items = arrayOf("Edit Name", "Delete")
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
            binding.emptyViewText.text = "No games added.\nClick '+' to begin."
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyViewContainer.visibility = View.GONE
        }
    }

    // --- SELEÇÃO DE MÉTODO DE ADIÇÃO ---

    private fun showAddMethodSelection() {
        val options = arrayOf("Search Speedrun.com (Recommended)", "Manual Entry")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Game")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSearchDialog()
                    1 -> showAddEditDialog(null)
                }
            }
            .show()
    }

    // --- LÓGICA DA API DO SPEEDRUN.COM ---

    private fun showSearchDialog() {
        val context = this
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle("Search Speedrun.com")

        // Criando o layout do diálogo programaticamente
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val input = EditText(context).apply { 
            hint = "Game Name (e.g. Celeste)" 
            maxLines = 1
        }
        
        val searchButton = MaterialButton(context).apply {
            text = "Search"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
        }

        val progressBar = ProgressBar(context).apply { 
            visibility = View.GONE 
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.CENTER }
        }

        val recyclerSearch = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                800 // Altura fixa para a lista não ocupar a tela toda
            ).apply { topMargin = 20 }
        }

        dialogLayout.addView(input)
        dialogLayout.addView(searchButton)
        dialogLayout.addView(progressBar)
        dialogLayout.addView(recyclerSearch)
        
        builder.setView(dialogLayout)
        builder.setNegativeButton("Cancel", null)

        val dialog = builder.create()

        // Configura o Adapter da busca
        val searchAdapter = GameSearchAdapter(emptyList()) { selectedGame ->
            // Ao clicar num jogo da lista
            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                try {
                    // Busca as categorias desse jogo
                    val categoriesResponse = SpeedrunApiClient.service.getCategories(selectedGame.id)
                    // Filtra apenas categorias principais ("per-game")
                    val validCategories = categoriesResponse.data.filter { it.type == "per-game" }
                    
                    dialog.dismiss() // Fecha busca
                    showCategorySelectionDialog(selectedGame.names.international, validCategories)
                    
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load categories.", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                } finally {
                    progressBar.visibility = View.GONE
                }
            }
        }
        recyclerSearch.adapter = searchAdapter

        // Lógica do botão Buscar
        searchButton.setOnClickListener {
            val query = input.text.toString().trim()
            if (query.isNotEmpty()) {
                progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    try {
                        val response = SpeedrunApiClient.service.searchGames(query)
                        searchAdapter.updateList(response.data)
                        if (response.data.isEmpty()) {
                            Toast.makeText(context, "No games found.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Connection error. Check internet.", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    } finally {
                        progressBar.visibility = View.GONE
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showCategorySelectionDialog(gameName: String, apiCategories: List<com.example.floatingspeedruntimer.api.CategoryData>) {
        if (apiCategories.isEmpty()) {
            // Se não tiver categorias, cria só o jogo
            createGameWithCategories(gameName, emptyList())
            return
        }

        val categoryNames = apiCategories.map { it.name }.toTypedArray()
        val checkedItems = BooleanArray(categoryNames.size)

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Categories")
            .setMultiChoiceItems(categoryNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Add Game") { _, _ ->
                val selectedCategories = mutableListOf<String>()
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) selectedCategories.add(categoryNames[i])
                }
                createGameWithCategories(gameName, selectedCategories)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createGameWithCategories(gameName: String, categories: List<String>) {
        // Verifica se o jogo já existe para não duplicar
        val existingGame = dataManager.findGameByName(gameName)
        
        if (existingGame != null) {
            Toast.makeText(this, "Game updated with new categories", Toast.LENGTH_SHORT).show()
            categories.forEach { catName ->
                if (existingGame.categories.none { it.name == catName }) {
                    existingGame.categories.add(Category(catName))
                }
            }
        } else {
            val newGame = Game(gameName)
            if (categories.isEmpty()) {
                // Adiciona uma categoria padrão se nenhuma for selecionada
                newGame.categories.add(Category("Any%"))
            } else {
                categories.forEach { newGame.categories.add(Category(it)) }
            }
            dataManager.games.add(newGame)
        }

        dataManager.saveGames()
        gameAdapter.updateData(dataManager.games)
        updateEmptyView()
    }

    // --- LÓGICA MANUAL (ANTIGA) ---

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

    // --- MENU DE OPÇÕES ---

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
