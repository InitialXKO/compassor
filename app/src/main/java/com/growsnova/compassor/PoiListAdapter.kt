package com.growsnova.compassor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.services.core.PoiItem

class PoiListAdapter(
    private var poiItems: List<PoiItem>,
    private val onPoiClicked: (PoiItem) -> Unit
) : RecyclerView.Adapter<PoiListAdapter.PoiViewHolder>() {

    fun updateData(newItems: List<PoiItem>) {
        poiItems = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poi, parent, false)
        return PoiViewHolder(view)
    }

    override fun onBindViewHolder(holder: PoiViewHolder, position: Int) {
        holder.bind(poiItems[position], onPoiClicked)
    }

    override fun getItemCount() = poiItems.size

    class PoiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.poiTitle)
        private val snippetView: TextView = itemView.findViewById(R.id.poiSnippet)

        init {
            itemView.applyTouchScale()
        }

        fun bind(poiItem: PoiItem, onPoiClicked: (PoiItem) -> Unit) {
            titleView.text = poiItem.title
            snippetView.text = poiItem.snippet ?: poiItem.adName
            itemView.setOnClickListener { onPoiClicked(poiItem) }
        }
    }
}
