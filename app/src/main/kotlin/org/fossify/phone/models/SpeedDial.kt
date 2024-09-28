package org.fossify.phone.models

data class SpeedDial(val id: Int, var number: String, var displayName: String, var photoUri: String) {
    fun isValid() = number.trim().isNotEmpty()
}
