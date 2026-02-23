package com.growsnova.compassor

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.amap.api.maps.model.LatLng

class CreateRoutePagerAdapter(
    fragmentActivity: FragmentActivity,
    private val currentLatLng: LatLng?
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SavedWaypointsFragment.newInstance()
            1 -> NearbyPoisFragment.newInstance(currentLatLng)
            else -> SearchFragment.newInstance(currentLatLng)
        }
    }
}
