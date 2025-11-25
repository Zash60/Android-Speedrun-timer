package com.example.floatingspeedruntimer.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.floatingspeedruntimer.api.GameData
import com.example.floatingspeedruntimer.databinding.ListItemGameSearchBinding

class GameSearchAdapter(
    private var games: List<GameData>,
    private val onGameClick: (GameData) -> Unit
) : RecyclerView.Adapter<GameSearchAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ListItemGameSearchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemGameSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val game = games[position]
        holder.binding.textGameName.text = game.names.international
        
        // Carrega a capa usando Glide
        game.assets.cover_medium?.uri?.let { url ->
            Glide.with(holder.itemView.context)
                .load(url)
                .into(holder.binding.imgGameCover)
        }
        
        holder.itemView.setOnClickListener { onGameClick(game) }
    }

    override fun getItemCount() = games.size

    fun updateList(newGames: List<GameData>) {
        games = newGames
        notifyDataSetChanged()
    }
}
