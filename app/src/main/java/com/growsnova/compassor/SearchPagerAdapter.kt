package com.growsnova.compassor

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.amap.api.maps.model.LatLng

class SearchPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    private val latLng: LatLng? = fragment.arguments?.getParcelableCompat<LatLng>("latlng")

    private val fragments: List<Fragment> = listOf(
        SearchTabFragment.newInstance(latLng),
        SearchHistoryTabFragment()
    )

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

    fun getFragment(position: Int): Fragment = fragments[position]
}
