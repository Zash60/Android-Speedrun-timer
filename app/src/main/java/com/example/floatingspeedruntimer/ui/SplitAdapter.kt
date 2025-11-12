package com.example.floatingspeedruntimer.ui
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.floatingspeedruntimer.data.Run
import com.example.floatingspeedruntimer.data.Split
import com.example.floatingspeedruntimer.databinding.ListItemSplitBinding
import com.example.floatingspeedruntimer.util.TimeFormatter.formatTime
class SplitAdapter(private var splits: MutableList<Split>, private var lastRun: Run?, private val listener: (Split, Int) -> Unit) : RecyclerView.Adapter<SplitAdapter.SplitViewHolder>() {
    private var comparisonMode = "personal_best"
    fun updateData(newSplits: MutableList<Split>, newLastRun: Run?) {
        this.splits = newSplits
        this.lastRun = newLastRun
        notifyDataSetChanged()
    }
    fun setComparisonMode(mode: String) {
        this.comparisonMode = mode
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SplitViewHolder {
        val binding = ListItemSplitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SplitViewHolder(binding)
    }
    override fun onBindViewHolder(holder: SplitViewHolder, position: Int) {
        holder.bind(splits[position], position)
    }
    override fun getItemCount(): Int = splits.size
    inner class SplitViewHolder(private val binding: ListItemSplitBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(split: Split, position: Int) {
            binding.textName.text = split.name
            var segmentTime = 0L
            var splitTime = 0L
            when(comparisonMode) {
                "personal_best" -> {
                    splitTime = split.personalBestTime
                    val previousSplitTime = if (position > 0) splits[position - 1].personalBestTime else 0
                    if (splitTime > 0) segmentTime = splitTime - previousSplitTime
                }
                "best_segments" -> {
                    segmentTime = split.bestSegmentTime
                    splitTime = (0..position).sumOf { splits[it].bestSegmentTime }
                }
                "current_comparison" -> {
                    lastRun?.segmentTimes?.let {
                        if (position < it.size) {
                            segmentTime = it[position]
                            splitTime = (0..position).sumOf { idx -> it[idx] }
                        }
                    }
                }
            }
            binding.textSegmentTime.text = formatTime(segmentTime, true)
            binding.textSplitTime.text = formatTime(splitTime, true)
            itemView.setOnLongClickListener {
                listener(split, adapterPosition)
                true
            }
        }
    }
}
