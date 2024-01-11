package org.fossify.phone.adapters

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import org.fossify.commons.helpers.TAB_CALL_HISTORY
import org.fossify.commons.helpers.TAB_CONTACTS
import org.fossify.commons.helpers.TAB_FAVORITES
import org.fossify.phone.R
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.extensions.config
import org.fossify.phone.fragments.MyViewPagerFragment
import org.fossify.phone.helpers.tabsList

class ViewPagerAdapter(val activity: SimpleActivity) : PagerAdapter() {

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = getFragment(position)
        val view = activity.layoutInflater.inflate(layout, container, false)
        container.addView(view)

        (view as MyViewPagerFragment<*>).apply {
            setupFragment(activity)
        }

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        container.removeView(item as View)
    }

    override fun getCount() = tabsList.filter { it and activity.config.showTabs != 0 }.size

    override fun isViewFromObject(view: View, item: Any) = view == item

    private fun getFragment(position: Int): Int {
        val showTabs = activity.config.showTabs
        val fragments = arrayListOf<Int>()
        if (showTabs and TAB_CONTACTS > 0) {
            fragments.add(R.layout.fragment_contacts)
        }

        if (showTabs and TAB_FAVORITES > 0) {
            fragments.add(R.layout.fragment_favorites)
        }

        if (showTabs and TAB_CALL_HISTORY > 0) {
            fragments.add(R.layout.fragment_recents)
        }

        return if (position < fragments.size) fragments[position] else fragments.last()
    }
}
