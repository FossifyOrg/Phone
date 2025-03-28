package org.fossify.phone.extensions

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.reddit.indicatorfastscroll.FastScrollerView
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.R
import org.fossify.phone.models.DialpadItem

fun FastScrollerView.setupWithContacts(
    recyclerView: RecyclerView,
    contacts: List<Contact>,
) = setupWithRecyclerView(recyclerView, { position ->
    val initialLetter = try {
        contacts[position].getFirstLetter()
    } catch (e: IndexOutOfBoundsException) {
        ""
    }

    FastScrollItemIndicator.Text(initialLetter)
})

fun FastScrollerView.setupWithDialpadItems(
    recyclerView: RecyclerView,
    dialpadItems: List<DialpadItem>,
) = setupWithRecyclerView(recyclerView, { position ->
    Log.e(dialpadItems.size.toString(), position.toString())
    if (position < dialpadItems.size) {
        val dialpadItem = dialpadItems[position]

        when (dialpadItem.itemType) {
            DialpadItem.DialpadItemType.HEADER -> null
            DialpadItem.DialpadItemType.CONTACT -> try {
                FastScrollItemIndicator.Text(dialpadItem.contact!!.getFirstLetter())
            } catch (e: IndexOutOfBoundsException) {
                FastScrollItemIndicator.Text("")
            }

            DialpadItem.DialpadItemType.RECENTCALL -> FastScrollItemIndicator.Icon(R.drawable.ic_clock_vector)
        }
    }
    null
})
