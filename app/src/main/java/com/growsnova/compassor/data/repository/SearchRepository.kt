package com.growsnova.compassor.data.repository

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.growsnova.compassor.SearchHistory
import com.growsnova.compassor.SearchHistoryDao
import com.growsnova.compassor.base.AppConstants
import com.growsnova.compassor.base.CompassorException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class SearchRepository @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao,
    @ApplicationContext private val context: Context
) {
    suspend fun getRecentSearches(): List<SearchHistory> = searchHistoryDao.getRecentSearches()

    suspend fun insertSearchHistory(query: String) {
        searchHistoryDao.insert(SearchHistory(query = query))
    }

    suspend fun deleteSearchHistory(id: Long) {
        searchHistoryDao.delete(id)
    }

    suspend fun clearAllHistory() {
        searchHistoryDao.clearAll()
    }

    @Suppress("DEPRECATION")
    suspend fun reverseGeocode(latLng: com.amap.api.maps.model.LatLng): Result<String> = suspendCancellableCoroutine { continuation ->
        val geocodeSearch = GeocodeSearch(context)
        val query = RegeocodeQuery(LatLonPoint(latLng.latitude, latLng.longitude), 200f, GeocodeSearch.AMAP)

        geocodeSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                if (rCode == 1000) {
                    val address = result?.regeocodeAddress?.formatAddress
                    continuation.resume(Result.success(address ?: "自定义位置"))
                } else {
                    continuation.resume(Result.success("自定义位置"))
                }
            }
            override fun onGeocodeSearched(result: com.amap.api.services.geocoder.GeocodeResult?, rCode: Int) {}
        })
        geocodeSearch.getFromLocationAsyn(query)
    }

    @Suppress("DEPRECATION")
    suspend fun searchPOI(keyword: String, center: LatLonPoint?): Result<PoiResult> = suspendCancellableCoroutine { continuation ->
        val query = PoiSearch.Query(keyword, "", "")
        query.pageSize = 10
        query.pageNum = 1

        val poiSearch = PoiSearch(context, query)
        center?.let {
            poiSearch.bound = PoiSearch.SearchBound(it, AppConstants.POI_SEARCH_RADIUS)
        }

        poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResult?, errorCode: Int) {
                if (errorCode == 1000) {
                    if (result != null) {
                        continuation.resume(Result.success(result))
                    } else {
                        continuation.resume(Result.failure(CompassorException.NetworkException("No search results")))
                    }
                } else {
                    continuation.resume(Result.failure(CompassorException.NetworkException("POI search failed: $errorCode")))
                }
            }

            override fun onPoiItemSearched(poiItem: com.amap.api.services.core.PoiItem?, errorCode: Int) {}
        })

        poiSearch.searchPOIAsyn()

        continuation.invokeOnCancellation {
            // AMap doesn't seem to have a simple cancel for POISearch
        }
    }
}
