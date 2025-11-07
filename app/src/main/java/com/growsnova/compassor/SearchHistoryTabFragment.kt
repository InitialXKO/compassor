package com.growsnova.compassor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class SearchHistoryTabFragment : Fragment() {

    private val viewModel: CreateRouteViewModel by activityViewModels()
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyLabel: TextView
    private lateinit var clearHistoryButton: Button
    private lateinit var historyAdapter: SearchHistoryAdapter
    private var searchHistories: MutableList<SearchHistory> = mutableListOf()
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search_history_tab, container, false)
        historyRecyclerView = view.findViewById(R.id.historyRecyclerView)
        historyLabel = view.findViewById(R.id.historyLabel)
        clearHistoryButton = view.findViewById(R.id.clearHistoryButton)
        
        historyRecyclerView.layoutManager = LinearLayoutManager(context)
        historyAdapter = SearchHistoryAdapter(
            searchHistories,
            onItemClick = { history ->
                // Switch to search tab and set the query
                val parentFragment = parentFragment
                if (parentFragment is SearchFragment) {
                    parentFragment.switchToSearchTab(history.query)
                }
            },
            onDeleteClick = { history ->
                deleteSearchHistory(history)
            }
        )
        historyRecyclerView.adapter = historyAdapter

        clearHistoryButton.setOnClickListener {
            clearAllHistory()
        }

        loadSearchHistory()
        return view
    }

    private fun loadSearchHistory() {
        lifecycleScope.launch {
            val histories = db.searchHistoryDao().getRecentSearches()
            searchHistories.clear()
            searchHistories.addAll(histories)
            activity?.runOnUiThread {
                historyAdapter.notifyDataSetChanged()
                updateHistoryVisibility()
            }
        }
    }

    private fun deleteSearchHistory(history: SearchHistory) {
        lifecycleScope.launch {
            db.searchHistoryDao().delete(history.id)
            loadSearchHistory()
        }
    }

    private fun clearAllHistory() {
        lifecycleScope.launch {
            db.searchHistoryDao().clearAll()
            loadSearchHistory()
        }
    }

    private fun updateHistoryVisibility() {
        if (searchHistories.isEmpty()) {
            historyRecyclerView.visibility = View.GONE
            historyLabel.visibility = View.GONE
            clearHistoryButton.visibility = View.GONE
            view?.findViewById<View>(R.id.emptyState)?.visibility = View.VISIBLE
        } else {
            historyRecyclerView.visibility = View.VISIBLE
            historyLabel.visibility = View.VISIBLE
            clearHistoryButton.visibility = View.VISIBLE
            view?.findViewById<View>(R.id.emptyState)?.visibility = View.GONE
        }
    }

    fun refreshHistory() {
        loadSearchHistory()
    }
}