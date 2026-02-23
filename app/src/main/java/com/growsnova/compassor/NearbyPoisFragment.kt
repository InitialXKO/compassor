package com.growsnova.compassor
import dagger.hilt.android.AndroidEntryPoint

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.google.android.material.chip.ChipGroup

@Suppress("DEPRECATION")
@AndroidEntryPoint
class NearbyPoisFragment : Fragment(), PoiSearch.OnPoiSearchListener {

    private var currentLatLng: LatLng? = null
    private val viewModel: CreateRouteViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var categoryChipGroup: ChipGroup
    private lateinit var poiSearch: PoiSearch
    private var poiItems: MutableList<PoiItem> = mutableListOf()
    private var filteredPois: MutableList<PoiItem> = mutableListOf()
    private lateinit var adapter: PoiListAdapter
    private var currentCategory = "all"

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
        val view = inflater.inflate(R.layout.fragment_nearby_pois, container, false)
        recyclerView = view.findViewById(R.id.nearbyPoisRecyclerView)
        categoryChipGroup = view.findViewById(R.id.categoryFilterChipGroup)
        val refreshButton = view.findViewById<View>(R.id.refreshButton)
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = PoiListAdapter(filteredPois) { poiItem ->
            val waypoint = Waypoint(
                id = 0L,
                name = poiItem.title,
                latitude = poiItem.latLonPoint.latitude,
                longitude = poiItem.latLonPoint.longitude
            )
            viewModel.addWaypoint(waypoint)
            DialogUtils.showSuccessToast(requireContext(), "${poiItem.title} added to route")
        }
        recyclerView.adapter = adapter

        refreshButton?.setOnClickListener {
            searchNearbyPois()
        }

        setupCategoryFilter()
        searchNearbyPois()
        return view
    }

    private fun setupCategoryFilter() {
        categoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: View.NO_ID
            currentCategory = when (checkedId) {
                R.id.chipAll -> "all"
                R.id.chipRestaurant -> "餐饮服务"
                R.id.chipGasStation -> "汽车服务"
                R.id.chipHotel -> "住宿服务"
                else -> currentCategory
            }
            filterPois()
        }
    }

    private fun filterPois() {
        filteredPois.clear()
        if (currentCategory == "all") {
            filteredPois.addAll(poiItems)
        } else {
            filteredPois.addAll(poiItems.filter { poiItem ->
                poiItem.typeDes?.contains(currentCategory) == true
            })
        }
        adapter.notifyDataSetChanged()
        updatePoiCount()
    }

    private fun updatePoiCount() {
        view?.findViewById<TextView>(R.id.poiCount)?.text = "${filteredPois.size} 个结果"
    }

    private fun searchNearbyPois() {
        currentLatLng?.let {
            showLoading(true)
            val query = PoiSearch.Query("", "", "") // Search all categories
            query.pageSize = 50
            poiSearch = PoiSearch(context, query)
            poiSearch.setOnPoiSearchListener(this)
            poiSearch.bound = PoiSearch.SearchBound(LatLonPoint(it.latitude, it.longitude), 5000) // 5km
            poiSearch.searchPOIAsyn()
        }
    }

    private fun showLoading(show: Boolean) {
        view?.findViewById<View>(R.id.loadingState)?.visibility = if (show) View.VISIBLE else View.GONE
        view?.findViewById<RecyclerView>(R.id.nearbyPoisRecyclerView)?.visibility = if (show) View.GONE else View.VISIBLE
        view?.findViewById<View>(R.id.emptyState)?.visibility = View.GONE
    }

    private fun showEmpty(show: Boolean) {
        view?.findViewById<View>(R.id.emptyState)?.visibility = if (show) View.VISIBLE else View.GONE
        view?.findViewById<RecyclerView>(R.id.nearbyPoisRecyclerView)?.visibility = if (show) View.GONE else View.VISIBLE
        view?.findViewById<View>(R.id.loadingState)?.visibility = View.GONE
    }

    override fun onPoiSearched(result: PoiResult?, rCode: Int) {
        showLoading(false)
        if (rCode == 1000) {
            result?.pois?.let {
                poiItems.clear()
                poiItems.addAll(it)
                filterPois()
                
                if (filteredPois.isEmpty()) {
                    showEmpty(true)
                } else {
                    showEmpty(false)
                }
            } ?: run {
                showEmpty(true)
            }
        } else {
            showEmpty(true)
        }
    }

    override fun onPoiItemSearched(p0: PoiItem?, p1: Int) {}

    companion object {
        private const val ARG_LATLNG = "latlng"

        @JvmStatic
        fun newInstance(latLng: LatLng?) =
            NearbyPoisFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_LATLNG, latLng)
                }
            }
    }
}

