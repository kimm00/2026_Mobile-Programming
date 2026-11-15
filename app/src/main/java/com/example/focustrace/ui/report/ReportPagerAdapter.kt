package com.example.focustrace.ui.report

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Adapter for ViewPager2 managing Day, Week, and Month report fragments.
 */
class ReportPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ReportDayFragment()
            1 -> ReportWeekFragment()
            2 -> ReportMonthFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
