package com.growsnova.compassor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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

class SearchFragment : Fragment(), PoiSearch.OnPoiSearchListener {

    private var currentLatLng: LatLng? = null
    private val viewModel: CreateRouteViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var poiSearch: PoiSearch
    private var poiItems: MutableList<PoiItem> = mutableListOf()
    private lateinit var adapter: PoiListAdapter

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
        searchEditText = view.findViewById(R.id.searchEditText)
        searchButton = view.findViewById(R.id.searchButton)
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

        searchButton.setOnClickListener {
            val keyword = searchEditText.text.toString().trim()
            if (keyword.isNotEmpty()) {
                searchPois(keyword)
            }
        }
        return view
    }

    private fun searchPois(keyword: String) {
        val query = PoiSearch.Query(keyword, "", "")
        query.pageSize = 20
        poiSearch = PoiSearch(context, query)
        poiSearch.setOnPoiSearchListener(this)
        currentLatLng?.let {
             poiSearch.bound = PoiSearch.SearchBound(LatLonPoint(it.latitude, it.longitude), 50000) // 50km
        }
        poiSearch.searchPOIAsyn()
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
