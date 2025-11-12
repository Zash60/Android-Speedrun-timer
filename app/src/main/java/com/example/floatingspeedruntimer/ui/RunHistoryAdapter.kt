package com.example.floatingspeedruntimer.ui
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.floatingspeedruntimer.data.Run
import com.example.floatingspeedruntimer.databinding.ListItemRunBinding
import com.example.floatingspeedruntimer.util.TimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class RunHistoryAdapter(private val runs: List<Run>) : RecyclerView.Adapter<RunHistoryAdapter.RunViewHolder>() {
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
        val binding = ListItemRunBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RunViewHolder(binding)
    }
    override fun onBindViewHolder(holder: RunViewHolder, position: Int) { holder.bind(runs[position]) }
    override fun getItemCount(): Int = runs.size
    inner class RunViewHolder(private val binding: ListItemRunBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(run: Run) {
            binding.textFinalTime.text = TimeFormatter.formatTime(run.finalTime, true)
            binding.textDate.text = dateFormat.format(Date(run.timestamp))
        }
    }
}
