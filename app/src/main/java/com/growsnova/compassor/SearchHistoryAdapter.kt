package com.growsnova.compassor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchHistoryAdapter(
    private val searchHistories: List<SearchHistory>,
    private val onItemClick: (SearchHistory) -> Unit,
    private val onDeleteClick: (SearchHistory) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.SearchHistoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return SearchHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchHistoryViewHolder, position: Int) {
        holder.bind(searchHistories[position], onItemClick, onDeleteClick)
    }

    override fun getItemCount() = searchHistories.size

    class SearchHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text1: TextView = itemView.findViewById(android.R.id.text1)
        private val text2: TextView = itemView.findViewById(android.R.id.text2)
        
        fun bind(
            searchHistory: SearchHistory,
            onItemClick: (SearchHistory) -> Unit,
            onDeleteClick: (SearchHistory) -> Unit
        ) {
            text1.text = searchHistory.query
            text2.text = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", searchHistory.timestamp)
            itemView.setOnClickListener { onItemClick(searchHistory) }
        }
    }
}