package com.growsnova.compassor

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class SearchPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    
    override fun getItemCount(): Int = 2
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SearchTabFragment()
            1 -> SearchHistoryTabFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}