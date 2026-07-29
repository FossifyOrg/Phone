package org.fossify.phone.helpers

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.canUseFullScreenIntent
import org.fossify.commons.extensions.isDefaultDialer
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.commons.helpers.isUpsideDownCakePlus
import org.fossify.phone.R

/**
 * Everything the dialer needs the user to hand over, in the order it is presented on the setup
 * screen. [runtimePermissions] is empty for the entries that can only be granted by walking the
 * user into a system settings screen or a role request.
 */
enum class AppPermission(
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    @DrawableRes val iconRes: Int,
    val isRequired: Boolean,
    val runtimePermissions: List<String> = emptyList(),
) {
    DEFAULT_DIALER(
        titleRes = R.string.permission_default_dialer,
        summaryRes = R.string.permission_default_dialer_summary,
        iconRes = R.drawable.ic_simple_phone_vector,
        isRequired = true,
    ),

    CONTACTS(
        titleRes = R.string.permission_contacts,
        summaryRes = R.string.permission_contacts_summary,
        iconRes = R.drawable.ic_person_vector,
        isRequired = true,
        runtimePermissions = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
        ),
    ),

    PHONE(
        titleRes = R.string.permission_phone,
        summaryRes = R.string.permission_phone_summary,
        iconRes = R.drawable.ic_phone_vector,
        isRequired = true,
        runtimePermissions = listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
        ),
    ),

    CALL_LOG(
        titleRes = R.string.permission_call_log,
        summaryRes = R.string.permission_call_log_summary,
        iconRes = R.drawable.ic_clock_vector,
        isRequired = true,
        runtimePermissions = listOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
        ),
    ),

    NOTIFICATIONS(
        titleRes = R.string.permission_notifications,
        summaryRes = R.string.permission_notifications_summary,
        iconRes = R.drawable.ic_bell_vector,
        isRequired = true,
        runtimePermissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
    ),

    FULL_SCREEN_INTENT(
        titleRes = R.string.permission_full_screen_intent,
        summaryRes = R.string.permission_full_screen_intent_summary,
        iconRes = R.drawable.ic_lock_vector,
        isRequired = true,
    ),

    DRAW_OVER_OTHER_APPS(
        titleRes = R.string.permission_draw_over_other_apps,
        summaryRes = R.string.permission_draw_over_other_apps_summary,
        iconRes = R.drawable.ic_info_outline_vector,
        isRequired = false,
    ),

    CALLER_ID(
        titleRes = R.string.permission_caller_id,
        summaryRes = R.string.permission_caller_id_summary,
        iconRes = R.drawable.ic_block_vector,
        isRequired = false,
    );

    /** true when granting means leaving the app for a system screen, so we cannot show a dialog */
    val opensSystemScreen: Boolean get() = runtimePermissions.isEmpty()
}

/**
 * Hides the entries that do not exist on the running OS version, so the setup screen never shows a
 * row the user has no way to act on.
 */
fun appPermissions(): List<AppPermission> = AppPermission.entries.filter {
    when (it) {
        AppPermission.NOTIFICATIONS -> isTiramisuPlus()
        AppPermission.FULL_SCREEN_INTENT -> isUpsideDownCakePlus()
        AppPermission.CALLER_ID -> isQPlus()
        else -> true
    }
}

fun Context.isPermissionGranted(permission: AppPermission): Boolean {
    return when (permission) {
        AppPermission.DEFAULT_DIALER -> isDefaultDialer()
        AppPermission.FULL_SCREEN_INTENT -> canUseFullScreenIntent()
        AppPermission.DRAW_OVER_OTHER_APPS -> Settings.canDrawOverlays(this)
        AppPermission.CALLER_ID -> isCallScreeningRoleHeld()
        else -> permission.runtimePermissions.all { hasRuntimePermission(it) }
    }
}

fun Context.hasRuntimePermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

fun Context.isCallScreeningRoleHeld(): Boolean {
    if (!isQPlus()) {
        return false
    }

    return try {
        val roleManager = getSystemService(RoleManager::class.java)
        roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true &&
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    } catch (_: Exception) {
        false
    }
}

/** Required entries that are still missing, i.e. the ones worth nagging the user about. */
fun Context.getMissingRequiredPermissions(): List<AppPermission> {
    return appPermissions().filter { it.isRequired && !isPermissionGranted(it) }
}

/**
 * Installers whose apps the platform exempts from Restricted Settings. The real allowlist is a
 * per-device XML under /system/etc/sysconfig that we cannot read, so this covers Play plus the
 * major OEM stores. Anything else - F-Droid, Obtainium, a file manager, adb - is treated as
 * sideloaded, which is the safe direction to be wrong in: an unnecessary hint costs the user a few
 * seconds, a missing one leaves them with a dialer that cannot answer calls.
 */
private val EXEMPT_INSTALLERS = setOf(
    "com.android.vending",             // Google Play
    "com.sec.android.app.samsungapps", // Galaxy Store
    "com.heytap.market",               // OPPO/OnePlus/realme App Market
    "com.oppo.market",
    "com.xiaomi.market",               // Xiaomi GetApps
    "com.huawei.appmarket",            // Huawei AppGallery
    "com.vivo.appstore",
)

/**
 * The Restricted Settings entries, as mandated by the Android 15 CDD (9.8/H-0-1): the Dialer role
 * and "Display over other apps" are both on that list, and both are things this app cannot work
 * without. Call screening is not listed but OEMs may extend the set.
 */
private val RESTRICTED_SETTING_PERMISSIONS = setOf(
    AppPermission.DEFAULT_DIALER,
    AppPermission.DRAW_OVER_OTHER_APPS,
    AppPermission.CALLER_ID,
)

/**
 * True when the user is likely to find the default-dialer and overlay toggles greyed out, with a
 * warning that the app is unsafe, and has to unblock them from App info first. Restricted Settings
 * arrived in Android 13 and was extended to cover roles in Android 15; there is no public API to
 * query the state (EnhancedConfirmationManager is a system API), so this is inferred from the
 * install source.
 */
fun Context.mayNeedRestrictedSettingsUnlock(): Boolean {
    if (!isTiramisuPlus()) {
        return false
    }

    if (RESTRICTED_SETTING_PERMISSIONS.none { it in appPermissions() && !isPermissionGranted(it) }) {
        return false
    }

    return getInstallerPackageName() !in EXEMPT_INSTALLERS
}

private fun Context.getInstallerPackageName(): String? {
    return try {
        if (isRPlus()) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
    } catch (_: Exception) {
        null
    }
}
