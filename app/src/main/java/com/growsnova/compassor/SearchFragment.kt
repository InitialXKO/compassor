package com.growsnova.compassor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class SearchFragment : Fragment() {

    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var adapter: SearchPagerAdapter
    private var currentLatLng: com.amap.api.maps.model.LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            currentLatLng = it.getParcelableCompat<com.amap.api.maps.model.LatLng>(ARG_LATLNG)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        viewPager = view.findViewById(R.id.viewPager)
        tabLayout = view.findViewById(R.id.tabLayout)
        
        adapter = SearchPagerAdapter(this, currentLatLng)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.search_location)
                1 -> getString(R.string.search_history)
                else -> ""
            }
        }.attach()

        return view
    }

    fun switchToSearchTab(query: String) {
        viewPager.currentItem = 0
        viewPager.post {
            childFragmentManager.fragments.filterIsInstance<SearchTabFragment>().firstOrNull()?.setSearchQuery(query)
        }
    }

    companion object {
        private const val ARG_LATLNG = "latlng"

        fun newInstance(latlng: com.amap.api.maps.model.LatLng?): SearchFragment {
            return SearchFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_LATLNG, latlng)
                }
            }
        }
    }
}