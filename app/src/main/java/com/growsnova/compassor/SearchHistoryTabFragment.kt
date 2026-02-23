package com.growsnova.compassor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.growsnova.compassor.data.repository.SearchRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SearchHistoryTabFragment : Fragment() {

    @Inject lateinit var searchRepository: SearchRepository

    private val viewModel: CreateRouteViewModel by activityViewModels()
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyLabel: TextView
    private lateinit var clearHistoryButton: Button
    private lateinit var historyAdapter: SearchHistoryAdapter

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
            emptyList(),
            onItemClick = { history ->
                val parentFragment = parentFragment
                if (parentFragment is SearchFragment) {
                    parentFragment.switchToSearchTab(history.query)
                }
            },
            onDeleteClick = { history ->
                lifecycleScope.launch { searchRepository.deleteSearchHistory(history.id) }
            }
        )
        historyRecyclerView.adapter = historyAdapter

        clearHistoryButton.setOnClickListener {
            lifecycleScope.launch { searchRepository.clearSearchHistory() }
        }

        setupObservers()
        return view
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchRepository.getSearchHistoryFlow().collectLatest { history ->
                    historyAdapter.updateData(history)
                    updateHistoryVisibility(history.isEmpty())
                }
            }
        }
    }

    private fun updateHistoryVisibility(isEmpty: Boolean) {
        if (isEmpty) {
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
}
