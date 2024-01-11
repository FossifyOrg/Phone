package org.fossify.phone.dialogs

import android.os.Bundle
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import org.fossify.commons.adapters.SimpleListItemAdapter
import org.fossify.commons.fragments.BaseBottomSheetDialogFragment
import org.fossify.commons.models.SimpleListItem
import org.fossify.phone.databinding.LayoutSimpleRecyclerViewBinding

// same as BottomSheetChooserDialog but with dynamic updates
class DynamicBottomSheetChooserDialog : BaseBottomSheetDialogFragment() {
    private lateinit var binding: LayoutSimpleRecyclerViewBinding

    var onItemClick: ((SimpleListItem) -> Unit)? = null

    override fun setupContentView(parent: ViewGroup) {
        binding = LayoutSimpleRecyclerViewBinding.inflate(layoutInflater, parent, false)
        parent.addView(binding.root)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        @Suppress("UNCHECKED_CAST")
        val listItems = arguments?.getParcelableArray(ITEMS) as Array<SimpleListItem>
        getRecyclerViewAdapter().submitList(listItems.toList())
    }

    private fun getRecyclerViewAdapter(): SimpleListItemAdapter {
        var adapter = binding.recyclerView.adapter as? SimpleListItemAdapter
        if (adapter == null) {
            adapter = SimpleListItemAdapter(requireActivity()) {
                onItemClick?.invoke(it)
                dismissAllowingStateLoss()
            }
            binding.recyclerView.adapter = adapter
        }
        return adapter
    }

    fun updateChooserItems(newItems: Array<SimpleListItem>) {
        if (isAdded) {
            getRecyclerViewAdapter().submitList(newItems.toList())
        }
    }

    companion object {
        private const val TAG = "BottomSheetChooserDialog"
        private const val ITEMS = "data"

        fun createChooser(
            fragmentManager: FragmentManager,
            title: Int?,
            items: Array<SimpleListItem>,
            callback: (SimpleListItem) -> Unit
        ): DynamicBottomSheetChooserDialog {
            val extras = Bundle().apply {
                if (title != null) {
                    putInt(BOTTOM_SHEET_TITLE, title)
                }
                putParcelableArray(ITEMS, items)
            }
            return DynamicBottomSheetChooserDialog().apply {
                arguments = extras
                onItemClick = callback
                show(fragmentManager, TAG)
            }
        }
    }
}
