package com.growsnova.compassor.manager

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class SearchManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    interface SearchListener {
        fun onPoiSearched(result: PoiResult?, errorCode: Int)
    }

    interface GeocodeListener {
        fun onRegeocodeSearched(result: RegeocodeResult?, errorCode: Int)
    }

    fun search(keyword: String, city: String = "", nearbyPoint: LatLonPoint? = null, listener: SearchListener) {
        val query = PoiSearch.Query(keyword, "", city)
        query.pageSize = 20
        query.setCityLimit(false)

        val poiSearch = PoiSearch(context, query)
        nearbyPoint?.let {
            poiSearch.bound = PoiSearch.SearchBound(it, 10000)
        }
        poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResult?, errorCode: Int) {
                listener.onPoiSearched(result, errorCode)
            }
            override fun onPoiItemSearched(p0: com.amap.api.services.core.PoiItem?, p1: Int) {}
        })
        poiSearch.searchPOIAsyn()
    }

    fun reverseGeocode(latLonPoint: LatLonPoint, listener: GeocodeListener) {
        val geocodeSearch = GeocodeSearch(context)
        geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult?, errorCode: Int) {
                listener.onRegeocodeSearched(result, errorCode)
            }
            override fun onGeocodeSearched(p0: GeocodeResult?, p1: Int) {}
        })
        val query = RegeocodeQuery(latLonPoint, 200f, GeocodeSearch.AMAP)
        geocodeSearch.getFromLocationAsyn(query)
    }
}
