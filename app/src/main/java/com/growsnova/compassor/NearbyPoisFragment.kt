package com.growsnova.compassor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class NearbyPoisFragment : Fragment(), PoiSearch.OnPoiSearchListener {

    private var currentLatLng: LatLng? = null
    private val viewModel: CreateRouteViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
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
        val view = inflater.inflate(R.layout.fragment_nearby_pois, container, false)
        recyclerView = view.findViewById(R.id.nearbyPoisRecyclerView)
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
        searchNearbyPois()
        return view
    }

    private fun searchNearbyPois() {
        currentLatLng?.let {
            val query = PoiSearch.Query("", "120000", "") // Automotive services
            query.pageSize = 20
            poiSearch = PoiSearch(context, query)
            poiSearch.setOnPoiSearchListener(this)
            poiSearch.bound = PoiSearch.SearchBound(LatLonPoint(it.latitude, it.longitude), 5000) // 5km
            poiSearch.searchPOIAsyn()
        }
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
            NearbyPoisFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_LATLNG, latLng)
                }
            }
    }
}

class PoiListAdapter(
    private val poiItems: List<PoiItem>,
    private val onPoiClicked: (PoiItem) -> Unit
) : RecyclerView.Adapter<PoiListAdapter.PoiViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return PoiViewHolder(view)
    }

    override fun onBindViewHolder(holder: PoiViewHolder, position: Int) {
        holder.bind(poiItems[position], onPoiClicked)
    }

    override fun getItemCount() = poiItems.size

    class PoiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(poiItem: PoiItem, onPoiClicked: (PoiItem) -> Unit) {
            (itemView as TextView).text = poiItem.title
            itemView.setOnClickListener { onPoiClicked(poiItem) }
        }
    }
}
