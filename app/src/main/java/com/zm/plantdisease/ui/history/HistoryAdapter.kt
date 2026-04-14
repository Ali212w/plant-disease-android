package com.zm.plantdisease.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.zm.plantdisease.data.model.CLASS_EMOJIS
import com.zm.plantdisease.data.model.HistoryItem
import com.zm.plantdisease.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val onLongClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, HistoryAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: HistoryItem) {
            val emoji = CLASS_EMOJIS.getOrElse(item.predictedClass) { "🌿" }
            b.tvEmoji.text      = emoji
            b.tvNameAr.text     = item.predictedNameAr
            b.tvModelName.text  = item.modelName.split("—").firstOrNull()?.trim() ?: item.modelName
            b.tvConfidence.text = "%.1f%%".format(item.confidence)

            val fmt = SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault())
            b.tvDate.text = item.timestamp?.toDate()?.let { fmt.format(it) } ?: "—"

            if (item.imageUrl.isNotEmpty()) {
                b.imgThumb.load(item.imageUrl) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(12f))
                    placeholder(android.R.drawable.ic_menu_gallery)
                }
            }

            b.root.setOnLongClickListener { onLongClick(item); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(a: HistoryItem, b: HistoryItem) = a.id == b.id
            override fun areContentsTheSame(a: HistoryItem, b: HistoryItem) = a == b
        }
    }
}
