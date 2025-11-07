package com.growsnova.compassor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import kotlinx.coroutines.launch

class SearchFragment : Fragment(), PoiSearch.OnPoiSearchListener {

    private var currentLatLng: LatLng? = null
    private val viewModel: CreateRouteViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var clearHistoryButton: Button
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyLabel: TextView
    private lateinit var poiSearch: PoiSearch
    private var poiItems: MutableList<PoiItem> = mutableListOf()
    private lateinit var adapter: PoiListAdapter
    private lateinit var historyAdapter: SearchHistoryAdapter
    private var searchHistories: MutableList<SearchHistory> = mutableListOf()
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentLatLng = it.getParcelable(ARG_LATLNG)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        recyclerView = view.findViewById(R.id.searchResultsRecyclerView)
        searchEditText = view.findViewById(R.id.editText)
        searchButton = view.findViewById(R.id.searchButton)
        clearHistoryButton = view.findViewById(R.id.clearHistoryButton)
        historyRecyclerView = view.findViewById(R.id.historyRecyclerView)
        historyLabel = view.findViewById(R.id.historyLabel)
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = PoiListAdapter(poiItems) { poiItem ->
            val waypoint = Waypoint(
                id = System.currentTimeMillis(),
                name = poiItem.title,
                latitude = poiItem.latLonPoint.latitude,
                longitude = poiItem.latLonPoint.longitude
            )
            viewModel.addWaypoint(waypoint)
            Toast.makeText(context, "${poiItem.title} added to route", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = adapter

        historyRecyclerView.layoutManager = LinearLayoutManager(context)
        historyAdapter = SearchHistoryAdapter(
            searchHistories,
            onItemClick = { history ->
                searchEditText.setText(history.query)
                searchPois(history.query)
            },
            onDeleteClick = { history ->
                deleteSearchHistory(history)
            }
        )
        historyRecyclerView.adapter = historyAdapter

        searchButton.setOnClickListener {
            val keyword = searchEditText.text.toString().trim()
            if (keyword.isNotEmpty()) {
                searchPois(keyword)
                saveSearchHistory(keyword)
            }
        }

        clearHistoryButton.setOnClickListener {
            clearAllHistory()
        }

        loadSearchHistory()
        return view
    }

    private fun searchPois(keyword: String) {
        val query = PoiSearch.Query(keyword, "", "")
        query.pageSize = 20
        poiSearch = PoiSearch(context, query)
        poiSearch.setOnPoiSearchListener(this)
        currentLatLng?.let {
             poiSearch.bound = PoiSearch.SearchBound(LatLonPoint(it.latitude, it.longitude), 50000)
        }
        poiSearch.searchPOIAsyn()
        
        historyRecyclerView.visibility = View.GONE
        historyLabel.visibility = View.GONE
        clearHistoryButton.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    override fun onPoiSearched(result: PoiResult?, rCode: Int) {
        if (rCode == 1000) {
            result?.pois?.let {
                poiItems.clear()
                poiItems.addAll(it)
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onPoiItemSearched(p0: PoiItem?, p1: Int) {}

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

    private fun saveSearchHistory(query: String) {
        lifecycleScope.launch {
            db.searchHistoryDao().insert(SearchHistory(query = query))
            loadSearchHistory()
        }
    }

    private fun deleteSearchHistory(history: SearchHistory) {
        lifecycleScope.launch {
            db.searchHistoryDao().delete(history.id)
            searchHistories.remove(history)
            activity?.runOnUiThread {
                historyAdapter.notifyDataSetChanged()
                updateHistoryVisibility()
            }
        }
    }

    private fun clearAllHistory() {
        lifecycleScope.launch {
            db.searchHistoryDao().clearAll()
            searchHistories.clear()
            activity?.runOnUiThread {
                historyAdapter.notifyDataSetChanged()
                updateHistoryVisibility()
                Toast.makeText(context, R.string.clear_history, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateHistoryVisibility() {
        if (searchHistories.isEmpty()) {
            historyRecyclerView.visibility = View.GONE
            historyLabel.visibility = View.GONE
            clearHistoryButton.visibility = View.GONE
        } else {
            historyRecyclerView.visibility = View.VISIBLE
            historyLabel.visibility = View.VISIBLE
            clearHistoryButton.visibility = View.VISIBLE
        }
    }

    companion object {
        private const val ARG_LATLNG = "latlng"

        @JvmStatic
        fun newInstance(latLng: LatLng?) =
            SearchFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_LATLNG, latLng)
                }
            }
    }
}

class SearchHistoryAdapter(
    private val histories: List<SearchHistory>,
    private val onItemClick: (SearchHistory) -> Unit,
    private val onDeleteClick: (SearchHistory) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val queryText: TextView = view.findViewById(R.id.historyQueryText)
        val deleteButton: ImageView = view.findViewById(R.id.deleteHistoryButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val history = histories[position]
        holder.queryText.text = history.query
        holder.itemView.setOnClickListener { onItemClick(history) }
        holder.deleteButton.setOnClickListener { onDeleteClick(history) }
    }

    override fun getItemCount() = histories.size
}
