package com.growsnova.compassor

import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.PoiItem

class PoiListAdapter(
    private var poiItems: List<PoiItem>,
    private var userLocation: LatLng? = null,
    private var hasMoreRadius: Boolean = false,
    private var onLoadMoreClicked: (() -> Unit)? = null,
    private val onPoiClicked: (PoiItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_POI = 0
        private const val TYPE_LOAD_MORE = 1
    }

    fun updateData(newItems: List<PoiItem>, location: LatLng? = userLocation, hasMore: Boolean = hasMoreRadius) {
        poiItems = newItems
        userLocation = location
        hasMoreRadius = hasMore
        notifyDataSetChanged()
    }

    fun setOnLoadMoreClickListener(listener: () -> Unit) {
        onLoadMoreClicked = listener
    }

    override fun getItemViewType(position: Int): Int {
        return if (hasMoreRadius && position == poiItems.size) {
            TYPE_LOAD_MORE
        } else {
            TYPE_POI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_LOAD_MORE) {
            val view = inflater.inflate(R.layout.item_poi_load_more, parent, false)
            LoadMoreViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_poi, parent, false)
            PoiViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is PoiViewHolder) {
            holder.bind(poiItems[position], userLocation, onPoiClicked)
        } else if (holder is LoadMoreViewHolder) {
            holder.bind(onLoadMoreClicked)
        }
    }

    override fun getItemCount(): Int {
        return if (hasMoreRadius) poiItems.size + 1 else poiItems.size
    }

    class PoiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(R.id.poiTitle)
        private val snippetView: TextView = itemView.findViewById(R.id.poiSnippet)
        private val distanceView: TextView = itemView.findViewById(R.id.poiDistance)
        private val floorView: TextView = itemView.findViewById(R.id.poiFloor)
        private val addPoiButton: View? = itemView.findViewById(R.id.addPoiButton)

        init {
            itemView.applyTouchScale()
            addPoiButton?.applyTouchScale()
        }

        fun bind(poiItem: PoiItem, userLocation: LatLng?, onPoiClicked: (PoiItem) -> Unit) {
            titleView.text = poiItem.title
            snippetView.text = poiItem.snippet ?: poiItem.adName

            val floorText = FloorUtils.formatFloor(
                FloorUtils.parseFloor(poiItem.indoorData?.floor),
                itemView.context
            )
            if (floorText != null) {
                floorView.text = floorText
                floorView.visibility = View.VISIBLE
            } else {
                floorView.visibility = View.GONE
            }

            val poiLat = poiItem.latLonPoint?.latitude
            val poiLng = poiItem.latLonPoint?.longitude

            if (userLocation != null && poiLat != null && poiLng != null) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    userLocation.latitude, userLocation.longitude,
                    poiLat, poiLng,
                    results
                )
                val distMeters = results[0]
                distanceView.text = if (distMeters >= 1000) {
                    "%.1fkm".format(distMeters / 1000f)
                } else {
                    "${distMeters.toInt()}m"
                }
                distanceView.visibility = View.VISIBLE
            } else if (poiItem.distance > 0) {
                distanceView.text = if (poiItem.distance >= 1000) {
                    "%.1fkm".format(poiItem.distance / 1000f)
                } else {
                    "${poiItem.distance}m"
                }
                distanceView.visibility = View.VISIBLE
            } else {
                distanceView.visibility = View.GONE
            }

            itemView.setOnClickListener { onPoiClicked(poiItem) }
            addPoiButton?.setOnClickListener { onPoiClicked(poiItem) }
        }
    }

    class LoadMoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.applyTouchScale()
        }

        fun bind(onLoadMoreClicked: (() -> Unit)?) {
            itemView.setOnClickListener {
                onLoadMoreClicked?.invoke()
            }
        }
    }
}
