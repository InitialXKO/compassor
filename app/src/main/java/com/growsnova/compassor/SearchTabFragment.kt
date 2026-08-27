package com.growsnova.compassor
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

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
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
@AndroidEntryPoint
class SearchTabFragment : Fragment(), PoiSearch.OnPoiSearchListener, Inputtips.InputtipsListener {

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

    private val radiusTiers = listOf(5000, 15000, 50000, -1)
    private var currentRadiusIndex = 0
    private var currentKeyword = ""
    private var hasMoreRadius = true
    private var currentSearchRequestId = 0L

    @Inject
    lateinit var searchRepository: com.growsnova.compassor.data.repository.SearchRepository

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
        adapter = PoiListAdapter(
            poiItems = poiItems,
            userLocation = currentLatLng,
            hasMoreRadius = false,
            onLoadMoreClicked = { expandSearchRadius() }
        ) { poiItem ->
            val waypoint = Waypoint(
                id = 0L,
                name = poiItem.title,
                latitude = poiItem.latLonPoint.latitude,
                longitude = poiItem.latLonPoint.longitude,
                floor = FloorUtils.extractFloorFromPoi(poiItem)
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
                if (query.isNotEmpty()) {
                    searchJob = lifecycleScope.launch {
                        kotlinx.coroutines.delay(300)
                        val ctx = context ?: return@launch
                        val inputQuery = InputtipsQuery(query, "")
                        inputQuery.setCityLimit(false)
                        val inputtips = Inputtips(ctx, inputQuery)
                        inputtips.setInputtipsListener(this@SearchTabFragment)
                        inputtips.requestInputtipsAsyn()
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
            currentSearchRequestId++
            currentKeyword = keyword
            currentRadiusIndex = 0
            poiItems.clear()
            hasMoreRadius = true
            adapter.updateData(poiItems, currentLatLng, hasMore = false)
            searchPoisTier()
            saveSearchHistory(keyword)
            if (hideKeyboard) {
                hideKeyboard()
            }
        }
    }

    private fun expandSearchRadius() {
        if (currentRadiusIndex < radiusTiers.size - 1) {
            currentRadiusIndex++
            searchPoisTier()
        } else {
            hasMoreRadius = false
            adapter.updateData(poiItems, currentLatLng, hasMore = false)
        }
    }

    private fun searchPoisTier() {
        if (currentKeyword.isEmpty()) return
        progressBar.visibility = View.VISIBLE
        val requestIdAtStart = currentSearchRequestId
        val query = PoiSearch.Query(currentKeyword, "", "")
        query.pageSize = 20
        poiSearch = PoiSearch(context, query)
        poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResult?, rCode: Int) {
                if (requestIdAtStart == currentSearchRequestId) {
                    this@SearchTabFragment.onPoiSearched(result, rCode)
                }
            }

            override fun onPoiItemSearched(p0: PoiItem?, p1: Int) {}
        })

        val radius = radiusTiers.getOrNull(currentRadiusIndex) ?: -1
        if (currentLatLng != null && radius > 0) {
            poiSearch.bound = PoiSearch.SearchBound(LatLonPoint(currentLatLng!!.latitude, currentLatLng!!.longitude), radius, true)
        }
        poiSearch.searchPOIAsyn()

        view?.findViewById<TextView>(R.id.resultsLabel)?.visibility = View.VISIBLE
        view?.findViewById<RecyclerView>(R.id.searchResultsRecyclerView)?.visibility = View.VISIBLE
        view?.findViewById<View>(R.id.emptyState)?.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
    }

    override fun onPoiSearched(result: PoiResult?, rCode: Int) {
        progressBar.visibility = View.GONE
        if (rCode == 1000) {
            result?.pois?.let { newPois ->
                val existingIds = poiItems.map { it.poiId }.toSet()
                val filteredNew = newPois.filter { it.poiId !in existingIds }
                poiItems.addAll(filteredNew)

                hasMoreRadius = currentRadiusIndex < radiusTiers.size - 1
                adapter.updateData(poiItems, currentLatLng, hasMore = hasMoreRadius)

                if (poiItems.isEmpty()) {
                    if (currentRadiusIndex < radiusTiers.size - 1) {
                        expandSearchRadius()
                    } else {
                        view?.findViewById<TextView>(R.id.resultsLabel)?.visibility = View.GONE
                        view?.findViewById<RecyclerView>(R.id.searchResultsRecyclerView)?.visibility = View.GONE
                        view?.findViewById<View>(R.id.emptyState)?.visibility = View.VISIBLE
                    }
                }
            }
        } else {
            DialogUtils.showErrorToast(requireContext(), getString(R.string.no_result))
            hasMoreRadius = false
            adapter.updateData(poiItems, currentLatLng, hasMore = false)
            if (poiItems.isEmpty()) {
                view?.findViewById<TextView>(R.id.resultsLabel)?.visibility = View.GONE
                view?.findViewById<RecyclerView>(R.id.searchResultsRecyclerView)?.visibility = View.GONE
                view?.findViewById<View>(R.id.emptyState)?.visibility = View.VISIBLE
            }
        }
    }

    override fun onPoiItemSearched(p0: PoiItem?, p1: Int) {}

    override fun onGetInputtips(tipList: MutableList<Tip>?, rCode: Int) {
        if (rCode == 1000 && tipList != null) {
            val suggestions = tipList.filter { it.point != null && !it.name.isNullOrEmpty() }.map { tip ->
                val point = tip.point
                PoiItem(tip.poiID ?: "", LatLonPoint(point.latitude, point.longitude), tip.name, tip.address)
            }
            if (suggestions.isNotEmpty() && searchEditText.text.isNotEmpty()) {
                poiItems.clear()
                poiItems.addAll(suggestions)
                adapter.updateData(poiItems, currentLatLng, hasMore = false)
                view?.findViewById<TextView>(R.id.resultsLabel)?.visibility = View.VISIBLE
                view?.findViewById<RecyclerView>(R.id.searchResultsRecyclerView)?.visibility = View.VISIBLE
                view?.findViewById<View>(R.id.emptyState)?.visibility = View.GONE
            }
        }
    }

    private fun saveSearchHistory(query: String) {
        lifecycleScope.launch {
            searchRepository.insertSearchHistory(query)
            (parentFragment as? SearchFragment)?.let { parent ->
                parent.childFragmentManager.fragments
                    .filterIsInstance<SearchHistoryTabFragment>()
                    .firstOrNull()
                    ?.refreshHistory()
            }
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