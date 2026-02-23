package com.growsnova.compassor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
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

@Suppress("DEPRECATION")
class SearchTabFragment : Fragment(), PoiSearch.OnPoiSearchListener {

    private var currentLatLng: LatLng? = null
    private val viewModel: CreateRouteViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var poiSearch: PoiSearch
    private var poiItems: MutableList<PoiItem> = mutableListOf()
    private lateinit var adapter: PoiListAdapter
    private var pendingQuery: String? = null
    private var searchJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentLatLng = it.getParcelableCompat<LatLng>(ARG_LATLNG)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search_tab, container, false)
        recyclerView = view.findViewById(R.id.searchResultsRecyclerView)
        searchEditText = view.findViewById(R.id.editText)
        searchButton = view.findViewById(R.id.searchButton)
        progressBar = view.findViewById(R.id.progressBar)

        searchButton.applyTouchScale()
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = PoiListAdapter(poiItems) { poiItem ->
            val waypoint = Waypoint(
                id = System.currentTimeMillis(),
                name = poiItem.title,
                latitude = poiItem.latLonPoint.latitude,
                longitude = poiItem.latLonPoint.longitude
            )
            viewModel.addWaypoint(waypoint)
            DialogUtils.showSuccessToast(requireContext(), "${poiItem.title} added to route")
        }
        recyclerView.adapter = adapter

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    searchJob = lifecycleScope.launch {
                        kotlinx.coroutines.delay(500) // Debounce 500ms
                        performSearch(hideKeyboard = false)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                performSearch(hideKeyboard = true)
                true
            } else {
                false
            }
        }

        searchButton.setOnClickListener {
            performSearch(hideKeyboard = true)
        }

        pendingQuery?.let {
            searchEditText.setText(it)
            performSearch(hideKeyboard = true)
            pendingQuery = null
        }

        return view
    }

    private fun performSearch(hideKeyboard: Boolean = true) {
        val keyword = searchEditText.text.toString().trim()
        if (keyword.isNotEmpty()) {
            if (!NetworkUtils.isNetworkAvailable(requireContext())) {
                DialogUtils.showErrorToast(requireContext(), getString(R.string.network_unavailable))
                return
            }
            searchPois(keyword)
            saveSearchHistory(keyword)
            if (hideKeyboard) {
                hideKeyboard()
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
    }

    private fun searchPois(keyword: String) {
        progressBar.visibility = View.VISIBLE
        val query = PoiSearch.Query(keyword, "", "")
        query.pageSize = 20
        poiSearch = PoiSearch(context, query)
        poiSearch.setOnPoiSearchListener(this)
        currentLatLng?.let {
             poiSearch.bound = PoiSearch.SearchBound(LatLonPoint(it.latitude, it.longitude), 50000)
        }
        poiSearch.searchPOIAsyn()
        
        view?.findViewById<TextView>(R.id.resultsLabel)?.visibility = View.VISIBLE
        view?.findViewById<RecyclerView>(R.id.searchResultsRecyclerView)?.visibility = View.VISIBLE
        view?.findViewById<View>(R.id.emptyState)?.visibility = View.GONE
    }

    override fun onPoiSearched(result: PoiResult?, rCode: Int) {
        progressBar.visibility = View.GONE
        if (rCode == 1000) {
            result?.pois?.let {
                poiItems.clear()
                poiItems.addAll(it)
                adapter.notifyDataSetChanged()
                
                if (poiItems.isEmpty()) {
                    view?.findViewById<TextView>(R.id.resultsLabel)?.visibility = View.GONE
                    view?.findViewById<RecyclerView>(R.id.searchResultsRecyclerView)?.visibility = View.GONE
                    view?.findViewById<View>(R.id.emptyState)?.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onPoiItemSearched(p0: PoiItem?, p1: Int) {}

    private fun saveSearchHistory(query: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            db.searchHistoryDao().insert(SearchHistory(query = query))
        }
    }
    
    fun setSearchQuery(query: String) {
        if (this::searchEditText.isInitialized) {
            searchEditText.setText(query)
            performSearch()
        } else {
            pendingQuery = query
        }
    }

    companion object {
        private const val ARG_LATLNG = "latlng"

        fun newInstance(latlng: LatLng?): SearchTabFragment {
            return SearchTabFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_LATLNG, latlng)
                }
            }
        }
    }
}