package com.growsnova.compassor

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.AMapException
import com.amap.api.maps.offlinemap.OfflineMapCity
import com.amap.api.maps.offlinemap.OfflineMapManager
import com.amap.api.maps.offlinemap.OfflineMapStatus
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout

class OfflineMapActivity : AppCompatActivity(), OfflineMapManager.OfflineMapDownloadListener {

    private lateinit var offlineMapManager: OfflineMapManager
    private lateinit var adapter: OfflineMapAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView

    private var currentTabPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_map)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tabLayout = findViewById(R.id.tabLayout)
        recyclerView = findViewById(R.id.recyclerView)

        tabLayout.addTab(tabLayout.newTab().setText(R.string.offline_map_downloaded))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.offline_map_all))

        adapter = OfflineMapAdapter(
            context = this,
            onDownloadClicked = { item -> downloadOfflineMap(item) },
            onPauseClicked = { item -> pauseOfflineMap(item) },
            onDeleteClicked = { item -> confirmDeleteOfflineMap(item) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTabPosition = tab?.position ?: 0
                refreshData()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        try {
            offlineMapManager = OfflineMapManager(this, this)
        } catch (e: Exception) {
            Log.e("OfflineMapActivity", "Error initializing OfflineMapManager", e)
        }

        refreshData()
    }

    private fun refreshData() {
        if (!::offlineMapManager.isInitialized) return

        val itemList = mutableListOf<OfflineMapItem>()

        if (currentTabPosition == 0) {
            // Downloaded tab (Downloading or Completed or Paused)
            val downloadedCities = offlineMapManager.downloadOfflineMapCityList ?: emptyList()
            val downloadingCities = offlineMapManager.downloadingCityList ?: emptyList()

            val combined = (downloadedCities + downloadingCities).distinctBy { it.city }
            combined.forEach { city ->
                itemList.add(
                    OfflineMapItem(
                        cityName = city.city,
                        cityCode = city.code,
                        size = city.size,
                        state = city.state,
                        completeCode = city.getcompleteCode()
                    )
                )
            }
        } else {
            // All cities / provinces tab
            val provinces = offlineMapManager.offlineMapProvinceList ?: emptyList()
            provinces.forEach { province ->
                val cityList = province.cityList ?: emptyList()
                cityList.forEach { city ->
                    itemList.add(
                        OfflineMapItem(
                            cityName = city.city,
                            cityCode = city.code,
                            size = city.size,
                            state = city.state,
                            completeCode = city.getcompleteCode(),
                            provinceName = province.provinceName
                        )
                    )
                }
            }
        }

        adapter.submitList(itemList)
    }

    private fun downloadOfflineMap(item: OfflineMapItem) {
        try {
            offlineMapManager.downloadByCityName(item.cityName)
            DialogUtils.showToast(this, getString(R.string.searching))
            refreshData()
        } catch (e: AMapException) {
            Log.e("OfflineMapActivity", "Error downloading city ${item.cityName}", e)
            DialogUtils.showErrorToast(this, e.errorMessage ?: e.message ?: "Download error")
        }
    }

    private fun pauseOfflineMap(item: OfflineMapItem) {
        try {
            offlineMapManager.pauseByName(item.cityName)
            refreshData()
        } catch (e: Exception) {
            Log.e("OfflineMapActivity", "Error pausing city ${item.cityName}", e)
        }
    }

    private fun confirmDeleteOfflineMap(item: OfflineMapItem) {
        DialogUtils.showConfirmationDialog(
            this,
            getString(R.string.delete),
            getString(R.string.confirm_delete_offline_map, item.cityName),
            onPositive = {
                try {
                    offlineMapManager.remove(item.cityName)
                    DialogUtils.showSuccessToast(this, getString(R.string.offline_map_deleted, item.cityName))
                    refreshData()
                } catch (e: Exception) {
                    Log.e("OfflineMapActivity", "Error deleting city ${item.cityName}", e)
                }
            }
        )
    }

    override fun onDownload(status: Int, completeCode: Int, name: String?) {
        runOnUiThread {
            if (status == OfflineMapStatus.EXCEPTION_NETWORK_LOADING ||
                status == OfflineMapStatus.EXCEPTION_AMAP ||
                status == OfflineMapStatus.EXCEPTION_SDCARD ||
                status == OfflineMapStatus.START_DOWNLOAD_FAILD ||
                status == OfflineMapStatus.ERROR
            ) {
                val reason = OfflineMapAdapter.getErrorReasonText(this, status)
                val cityName = name ?: getString(R.string.offline_maps)
                DialogUtils.showErrorToast(this, getString(R.string.offline_map_error_format, cityName, reason))
            }
            refreshData()
        }
    }

    override fun onCheckUpdate(hasNew: Boolean, name: String?) {
        runOnUiThread {
            refreshData()
        }
    }

    override fun onRemove(success: Boolean, name: String?, describe: String?) {
        runOnUiThread {
            refreshData()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::offlineMapManager.isInitialized) {
            offlineMapManager.destroy()
        }
    }
}
