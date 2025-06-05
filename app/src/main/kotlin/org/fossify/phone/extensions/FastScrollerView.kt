package org.fossify.phone.extensions

import androidx.recyclerview.widget.RecyclerView
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.reddit.indicatorfastscroll.FastScrollerView
import org.fossify.commons.models.contacts.Contact

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
