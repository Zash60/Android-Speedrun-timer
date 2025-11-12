package com.example.floatingspeedruntimer.ui
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.floatingspeedruntimer.data.Category
import com.example.floatingspeedruntimer.databinding.ListItemCategoryBinding
import com.example.floatingspeedruntimer.util.TimeFormatter.formatTime
class CategoryAdapter(private var categories: MutableList<Category>, private val listener: CategoryClickListener) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {
    interface CategoryClickListener {
        fun onPlayClick(category: Category)
        fun onCategoryClick(category: Category)
    }
    fun updateData(newCategories: MutableList<Category>) {
        this.categories = newCategories
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ListItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) { holder.bind(categories[position]) }
    override fun getItemCount(): Int = categories.size
    inner class CategoryViewHolder(private val binding: ListItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: Category) {
            binding.textName.text = category.name
            binding.textRuns.text = category.runs.toString()
            binding.textPb.text = if (category.personalBest > 0) formatTime(category.personalBest, true) else "--:--.--"
            binding.playIcon.setOnClickListener { listener.onPlayClick(category) }
            binding.categoryDetailsLayout.setOnClickListener { listener.onCategoryClick(category) }
        }
    }
}
