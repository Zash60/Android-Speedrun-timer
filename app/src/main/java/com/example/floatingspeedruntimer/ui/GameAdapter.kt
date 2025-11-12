package com.example.floatingspeedruntimer.ui
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.floatingspeedruntimer.data.Game
import com.example.floatingspeedruntimer.databinding.ListItemBinding
class GameAdapter(private var games: MutableList<Game>, private val listener: OnItemClickListener) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {
    interface OnItemClickListener {
        fun onGameClick(game: Game)
        fun onGameLongClick(game: Game)
    }
    fun updateData(newGames: MutableList<Game>) {
        this.games = newGames
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val binding = ListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GameViewHolder(binding)
    }
    override fun onBindViewHolder(holder: GameViewHolder, position: Int) { holder.bind(games[position]) }
    override fun getItemCount(): Int = games.size
    inner class GameViewHolder(private val binding: ListItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(game: Game) {
            binding.textName.text = game.name
            binding.root.setOnClickListener { listener.onGameClick(game) }
            binding.root.setOnLongClickListener {
                listener.onGameLongClick(game)
                true
            }
        }
    }
}
