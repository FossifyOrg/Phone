package org.fossify.phone.interfaces

interface RefreshItemsListener {
    fun refreshItems(callback: (() -> Unit)? = null)
}
