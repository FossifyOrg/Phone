package org.fossify.dialer.interfaces

interface RefreshItemsListener {
    fun refreshItems(callback: (() -> Unit)? = null)
}
