package com.example.floatingspeedruntimer.ui.autosplit

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.floatingspeedruntimer.R
import com.example.floatingspeedruntimer.data.Split
import com.example.floatingspeedruntimer.databinding.ListItemSplitImageBinding
import java.io.File

class SplitImageAdapter(
    private var splits: List<Split>,
    private val onCaptureClick: (Split) -> Unit
) : RecyclerView.Adapter<SplitImageAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ListItemSplitImageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemSplitImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val split = splits[position]
        holder.binding.textSplitName.text = split.name
        
        // Reseta para o placeholder
        holder.binding.imagePreview.setImageResource(R.drawable.ic_image_placeholder)
        
        split.autoSplitImagePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                holder.binding.imagePreview.setImageURI(Uri.fromFile(file))
            }
        }
        
        holder.binding.buttonCaptureImage.setOnClickListener {
            onCaptureClick(split)
        }
    }

    override fun getItemCount() = splits.size

    fun updateData(newSplits: List<Split>) {
        splits = newSplits
        notifyDataSetChanged()
    }
}
