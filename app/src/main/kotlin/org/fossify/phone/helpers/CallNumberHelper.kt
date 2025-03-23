package org.fossify.phone.helpers

import android.net.Uri
import android.util.Log
import android.content.Context
import android.telephony.TelephonyManager 
import org.fossify.phone.helpers.countries.fixInvalidNumbersBrazil

// Need to adapt to support 3 digit countries, or just use libphonenumber at that point
private val COUNTRY_CODE_PATTERN = Regex("""^\+(\d{1,2})""") 
private val SUPPORTED_COUNTRY_CODES = listOf("55")
private val SUPPORTED_COUNTRY_ISOS = listOf("BR")
private val SUPPORTED_COUNTRY_FUNS = listOf(::fixInvalidNumbersBrazil)

/**
 * Fix invalid numbers based on their country code
 */
@Suppress("TooGenericExceptionCaught")
fun fixInvalidNumbers(tel: Uri?, context: Context): Uri? {
	if (tel != null) {
		  try {
		      return getCountryFun(tel, context)(tel)
		  } catch (e: Exception) {
		      Log.e("CallNumberHelper", "Error fixing invalid number: $e")
		  }
	}
	return tel
}

private fun identity(uri: Uri): Uri {
	return uri
}

/**
 * Get the transformation function from the country code of the dialed phone
 * number, or infer from SIM if not an international call. If the country
 * cannot be determined or is not supported, return the identity function.
 */
private fun getCountryFun(tel: Uri, context: Context): (Uri) -> Uri {
	var idx: Int

	// Try to match by country code
	val decodedTel = tel.getSchemeSpecificPart()
	val countryCodeMatch = COUNTRY_CODE_PATTERN.find(decodedTel)
	if (countryCodeMatch != null) {
		  val countryCode = countryCodeMatch.groupValues.get(1)
		  Log.d("CallNumberHelper", "Code: $countryCode")
		  idx = SUPPORTED_COUNTRY_CODES.indexOf(countryCode)
	} else {
		  // No country code in the number, so it must be a local call,
		  // so we can use the SIM country code
		  val tm = context.getSystemService(TelephonyManager::class.java)
		  val countryIso = tm.getSimCountryIso().uppercase()
		  Log.d("CallNumberHelper", "Iso: $countryIso")
		  idx = SUPPORTED_COUNTRY_ISOS.indexOf(countryIso)
	}

	if (idx >= 0) {
		  return SUPPORTED_COUNTRY_FUNS[idx]
	}

	return ::identity
}
