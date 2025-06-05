package org.fossify.phone.models

sealed class CallLogItem {
    data class Date(
        val timestamp: Long,
        val dayCode: String,
    ) : CallLogItem()

    fun getItemId(): Int {
        return when (this) {
            is Date -> dayCode.hashCode()
            is RecentCall -> id
        }
    }
}
