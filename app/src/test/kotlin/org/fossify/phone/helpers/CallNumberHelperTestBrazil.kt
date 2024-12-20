import android.content.Context
import android.net.Uri
import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.fossify.phone.helpers.fixInvalidNumbers
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowTelephonyManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallNumberHelperTestBrazil {

	private lateinit var context: Context
	private lateinit var telephonyManager: TelephonyManager
	private lateinit var shadowTelephonyManager: ShadowTelephonyManager

	@Before
	fun setUp() {
		context = RuntimeEnvironment.getApplication()
		telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
		shadowTelephonyManager = Shadows.shadowOf(telephonyManager)
		shadowTelephonyManager.setSimCountryIso("BR")
	}

	// Country and Area code

	@Test
	fun testInvalidWithCountryAndAreaCode() {
		val inputNumber = Uri.parse("tel:+551190000000")
		val expectedNumber = Uri.parse("tel:+5511990000000")
		val outputNumber = fixInvalidNumbers(inputNumber, context )
		assertEquals(expectedNumber, outputNumber)
	}

	@Test
	fun testInvalidWithCountryAndAreaCodeAndNonDigits() {
		val inputNumber = Uri.parse("tel:+55 (11) 9000-0000")
		val expectedNumber = Uri.parse("tel:+55 (11) 99000-0000")
		val outputNumber = fixInvalidNumbers(inputNumber, context )
		assertEquals(expectedNumber, outputNumber)
	}

	@Test
	fun testValidWithCountryAndAreaCode() {
		val inputNumber = Uri.parse("tel:+5511990000000")
		val expectedNumber = Uri.parse("tel:+5511990000000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

	@Test
	fun testLandWithCountryAndAreaCode() {
		val inputNumber = Uri.parse("tel:+551130000000")
		val expectedNumber = Uri.parse("tel:+551130000000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

	@Test
	fun testUnsupportedWithCountryAndAreaCode() {
		val inputNumber = Uri.parse("tel:+190000000")
		val expectedNumber = Uri.parse("tel:+190000000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

	// Carrier and Area code

	@Test
	fun testInvalidWithCarrierAndAreaCode() {
		val inputNumber = Uri.parse("tel:0211190000000")
		val expectedNumber = Uri.parse("tel:02111990000000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

	@Test
	fun testInvalidWithCarrierAndAreaCodeAndNonDigits() {
		val inputNumber = Uri.parse("tel:021 (11) 9000-0000")
		val expectedNumber = Uri.parse("tel:021 (11) 99000-0000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

	@Test
	fun testValidWithCarrierAndAreaCode() {
		val inputNumber = Uri.parse("tel:02111990000000")
		val expectedNumber = Uri.parse("tel:02111990000000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

	@Test
	fun testLandWithCarrierAndAreaCode() {
		val inputNumber = Uri.parse("tel:0211130000000")
		val expectedNumber = Uri.parse("tel:0211130000000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

	// Local Area number

	@Test
	fun testInvalidLocalAreaNumber() {
		val inputNumber = Uri.parse("tel:90000000")
		val expectedNumber = Uri.parse("tel:990000000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

	@Test
	fun testInvalidLocalAreaNumberAndNonDigits() {
		val inputNumber = Uri.parse("tel:9000-0000")
		val expectedNumber = Uri.parse("tel:99000-0000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

	@Test
	fun testValidLocalAreaNumber() {
		val inputNumber = Uri.parse("tel:990000000")
		val expectedNumber = Uri.parse("tel:990000000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

	@Test
	fun testlLandLocalAreaNumber() {
		val inputNumber = Uri.parse("tel:30000000")
		val expectedNumber = Uri.parse("tel:30000000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

	@Test
	fun testUnsupportedLocalAreaNumber() {
		shadowTelephonyManager.setSimCountryIso("US")
		val inputNumber = Uri.parse("tel:90000000")
		val expectedNumber = Uri.parse("tel:90000000")
		val outputNumber = fixInvalidNumbers(inputNumber, context)
		assertEquals(expectedNumber, outputNumber)
	}

}
