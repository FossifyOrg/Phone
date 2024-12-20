package org.fossify.phone.helpers.countries

import android.net.Uri
import android.util.Log

// brazil number constants
private const val BR_LOCAL_AREA_LEN = 8
private const val BR_LAND_PREFIX_MAX = 5
private const val BR_CEL_PREFIX_CHAR = '9'

/**
 * Returns the start index of the local area number
 */
private fun getLocalAreaIndex(input: String): Int {
	var digitCount = 0
	for (i in input.length - 1 downTo 0) {
		if (input[i].isDigit()) {
		  digitCount++
		  if (digitCount == BR_LOCAL_AREA_LEN) {
		    return i
		  }
		}
	}
	return -1
}

private fun prevDigit(input: String, index: Int): Char {
	for (i in index - 1 downTo 0) {
		if (input[i].isDigit()) {
		  return input[i]
		}
	}
	return ' '
}

fun fixInvalidNumbersBrazil(tel: Uri): Uri {
	val decodedTel = tel.getSchemeSpecificPart()
	Log.d("CallNumberHelper", "Brazil phone: $decodedTel")
	val idx = getLocalAreaIndex(decodedTel)
	if (idx >= 0 && decodedTel.get(idx).digitToInt() > BR_LAND_PREFIX_MAX) {
		val prev = prevDigit(decodedTel, idx)
		if (prev != BR_CEL_PREFIX_CHAR) {
		  val fixedNum = decodedTel.substring(0, idx) + BR_CEL_PREFIX_CHAR + decodedTel.substring(idx)
		  Log.d("CallNumberHelper", "Brazil fixed phone: $fixedNum")
		  return Uri.parse("tel:$fixedNum")
		}
	}
	return tel
}
