package org.fossify.phone.extensions

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import org.fossify.commons.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.CallConfirmationDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.canUseFullScreenIntent
import org.fossify.commons.extensions.initiateCall
import org.fossify.commons.extensions.isDefaultDialer
import org.fossify.commons.extensions.launchCallIntent
import org.fossify.commons.extensions.openFullScreenIntentSettings
import org.fossify.commons.extensions.openNotificationSettings
import org.fossify.commons.extensions.telecomManager
import org.fossify.commons.helpers.PERMISSION_READ_PHONE_STATE
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.BuildConfig
import org.fossify.phone.activities.DialerActivity
import org.fossify.phone.activities.SimpleActivity
import org.fossify.phone.dialogs.SelectSIMDialog

fun SimpleActivity.startCallIntent(
    recipient: String,
    forceSimSelector: Boolean = false
) {
    if (isDefaultDialer()) {
        getHandleToUse(
            intent = null,
            phoneNumber = recipient,
            forceSimSelector = forceSimSelector
        ) { handle ->
            launchCallIntent(recipient, handle)
        }
    } else {
        launchCallIntent(recipient, null)
    }
}

fun SimpleActivity.startCallWithConfirmationCheck(
    recipient: String,
    name: String,
    forceSimSelector: Boolean = false
) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(this, name) {
            startCallIntent(recipient, forceSimSelector)
        }
    } else {
        startCallIntent(recipient, forceSimSelector)
    }
}

fun SimpleActivity.startCallWithConfirmationCheck(contact: Contact) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(
            activity = this,
            callee = contact.getNameToDisplay()
        ) {
            initiateCall(contact) { launchCallIntent(it) }
        }
    } else {
        initiateCall(contact) { launchCallIntent(it) }
    }
}

fun BaseSimpleActivity.callContactWithSim(
    recipient: String,
    useMainSIM: Boolean
) {
    handlePermission(PERMISSION_READ_PHONE_STATE) {
        val wantedSimIndex = if (useMainSIM) 0 else 1
        val handle = getAvailableSIMCardLabels()
            .sortedBy { it.id }
            .getOrNull(wantedSimIndex)?.handle
        launchCallIntent(recipient, handle)
    }
}

fun BaseSimpleActivity.callContactWithSimWithConfirmationCheck(
    recipient: String,
    name: String,
    useMainSIM: Boolean
) {
    if (config.showCallConfirmation) {
        CallConfirmationDialog(this, name) {
            callContactWithSim(recipient, useMainSIM)
        }
    } else {
        callContactWithSim(recipient, useMainSIM)
    }
}

// used at devices with multiple SIM cards
@SuppressLint("MissingPermission")
fun SimpleActivity.getHandleToUse(
    intent: Intent?,
    phoneNumber: String,
    forceSimSelector: Boolean = false,
    callback: (handle: PhoneAccountHandle?) -> Unit
) {
    handlePermission(PERMISSION_READ_PHONE_STATE) {
        if (it) {
            val defaultHandle =
                telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            when {
                forceSimSelector -> showSelectSimDialog(phoneNumber, callback)
                intent?.hasExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE) == true -> {
                    callback(intent.getParcelableExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)!!)
                }

                config.getCustomSIM(phoneNumber) != null -> {
                    callback(config.getCustomSIM(phoneNumber))
                }

                defaultHandle != null -> callback(defaultHandle)
                else -> showSelectSimDialog(phoneNumber, callback)
            }
        }
    }
}

fun SimpleActivity.showSelectSimDialog(
    phoneNumber: String,
    callback: (handle: PhoneAccountHandle?) -> Unit
) = SelectSIMDialog(
    activity = this,
    phoneNumber = phoneNumber,
    onDismiss = {
        if (this is DialerActivity) {
            finish()
        }
    }
) { handle ->
    callback(handle)
}

fun SimpleActivity.handleFullScreenNotificationsPermission(callback: (granted: Boolean) -> Unit) {
    handleNotificationPermission { granted ->
        if (granted) {
            if (canUseFullScreenIntent()) {
                callback(true)
            } else {
                PermissionRequiredDialog(
                    activity = this,
                    textId = R.string.allow_full_screen_notifications_incoming_calls,
                    positiveActionCallback = {
                        @SuppressLint("NewApi")
                        openFullScreenIntentSettings(BuildConfig.APPLICATION_ID)
                    },
                    negativeActionCallback = {
                        callback(false)
                    }
                )
            }
        } else {
            PermissionRequiredDialog(
                activity = this,
                textId = R.string.allow_notifications_incoming_calls,
                positiveActionCallback = {
                    openNotificationSettings()
                },
                negativeActionCallback = {
                    callback(false)
                }
            )
        }
    }
}

private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco")

/**
 * MIUI / HyperOS block the incoming call screen from the background unless the app-specific
 * "Show on Lock screen" and "Display pop-up windows while running in the background" permissions
 * are enabled — the standard full-screen intent permission alone is not enough there, and the OS
 * regularly resets it. Explain this once and offer to open the right settings screen.
 */
fun SimpleActivity.showXiaomiCallPermissionsHintIfNeeded() {
    val isXiaomi = Build.MANUFACTURER.lowercase() in XIAOMI_BRANDS || Build.BRAND.lowercase() in XIAOMI_BRANDS
    if (!isXiaomi || config.xiaomiCallPermissionsHintShown) {
        return
    }

    config.xiaomiCallPermissionsHintShown = true
    ConfirmationDialog(this, getString(org.fossify.phone.R.string.xiaomi_call_permissions_hint)) {
        val miuiEditor = Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", packageName)
        try {
            startActivity(miuiEditor)
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                )
            } catch (_: Exception) {
            }
        }
    }
}
