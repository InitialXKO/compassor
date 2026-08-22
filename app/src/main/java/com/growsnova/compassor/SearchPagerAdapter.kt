package com.growsnova.compassor

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

import com.amap.api.maps.model.LatLng

class SearchPagerAdapter(
    fragment: Fragment,
    private val currentLatLng: LatLng?
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SearchTabFragment.newInstance(currentLatLng)
            else -> SearchHistoryTabFragment()
        }
    }
}
