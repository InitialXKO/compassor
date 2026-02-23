package com.growsnova.compassor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchHistoryAdapter(
    var searchHistories: List<SearchHistory>,
    private val onItemClick: (SearchHistory) -> Unit,
    private val onDeleteClick: (SearchHistory) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.SearchHistoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_history, parent, false)
        return SearchHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchHistoryViewHolder, position: Int) {
        holder.bind(searchHistories[position], onItemClick, onDeleteClick)
    }

    override fun getItemCount() = searchHistories.size

    class SearchHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val queryText: TextView = itemView.findViewById(R.id.queryText)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        
        init {
            itemView.applyTouchScale()
            deleteButton.applyTouchScale()
        }
        
        fun bind(
            searchHistory: SearchHistory,
            onItemClick: (SearchHistory) -> Unit,
            onDeleteClick: (SearchHistory) -> Unit
        ) {
            queryText.text = searchHistory.query
            timestampText.text = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", searchHistory.timestamp)
            itemView.setOnClickListener { onItemClick(searchHistory) }
            deleteButton.setOnClickListener { onDeleteClick(searchHistory) }
        }
    }
}