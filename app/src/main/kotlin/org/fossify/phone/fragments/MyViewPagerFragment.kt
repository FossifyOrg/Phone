package org.fossify.phone.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.helpers.SORT_BY_FIRST_NAME
import org.fossify.commons.helpers.SORT_BY_SURNAME
import org.fossify.commons.views.MyRecyclerView
import org.fossify.phone.activities.MainActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.adapters.ContactsAdapter
import org.fossify.phone.adapters.RecentCallsAdapter
import org.fossify.phone.databinding.FragmentLettersLayoutBinding
import org.fossify.phone.databinding.FragmentRecentsBinding
import org.fossify.phone.extensions.config
import org.fossify.phone.helpers.Config

abstract class MyViewPagerFragment<BINDING : MyViewPagerFragment.InnerBinding>(context: Context, attributeSet: AttributeSet) :
    RelativeLayout(context, attributeSet) {
    protected var activity: SimpleActivity? = null
    protected lateinit var innerBinding: BINDING
    private lateinit var config: Config

    fun setupFragment(activity: SimpleActivity) {
        config = activity.config
        if (this.activity == null) {
            this.activity = activity

            setupFragment()
            setupColors(activity.getProperTextColor(), activity.getProperPrimaryColor(), activity.getProperPrimaryColor())
        }
    }

    fun startNameWithSurnameChanged(startNameWithSurname: Boolean) {
        if (this !is RecentsFragment) {
            (innerBinding.fragmentList?.adapter as? ContactsAdapter)?.apply {
                config.sorting = if (startNameWithSurname) SORT_BY_SURNAME else SORT_BY_FIRST_NAME
                (this@MyViewPagerFragment.activity!! as MainActivity).refreshFragments()
            }
        }
    }

    fun showContactThumbnailsChanged(showContactThumbnails: Boolean) {
        config.showContactThumbnails = showContactThumbnails

        if (this is RecentsFragment) {
            (innerBinding.recentsList.adapter as? RecentCallsAdapter)?.apply {
                (this@MyViewPagerFragment.activity!! as MainActivity).refreshFragments()
            }
        } else {
            (innerBinding.fragmentList?.adapter as? ContactsAdapter)?.apply {
                (this@MyViewPagerFragment.activity!! as MainActivity).refreshFragments()
            }
        }
    }

    fun finishActMode() {
        (innerBinding.fragmentList?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
        (innerBinding.recentsList?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    fun fontSizeChanged() {
        if (this is RecentsFragment) {
            (innerBinding.recentsList.adapter as? RecentCallsAdapter)?.apply {
                fontSize = activity.getTextSize()
                notifyDataSetChanged()
            }
        } else {
            (innerBinding.fragmentList?.adapter as? ContactsAdapter)?.apply {
                fontSize = activity.getTextSize()
                notifyDataSetChanged()
            }
        }
    }

    abstract fun setupFragment()

    abstract fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int)

    abstract fun onSearchClosed()

    abstract fun onSearchQueryChanged(text: String)

    interface InnerBinding {
        val fragmentList: MyRecyclerView?
        val recentsList: MyRecyclerView?
    }

    class LettersInnerBinding(val binding: FragmentLettersLayoutBinding) : InnerBinding {
        override val fragmentList: MyRecyclerView = binding.fragmentList
        override val recentsList = null
    }

    class RecentsInnerBinding(val binding: FragmentRecentsBinding) : InnerBinding {
        override val fragmentList = null
        override val recentsList = binding.recentsList
    }
}
