package org.fossify.phone.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isVisible
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.openFullScreenIntentSettings
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.isQPlus
import org.fossify.phone.BuildConfig
import org.fossify.phone.R
import org.fossify.phone.databinding.ActivityPermissionsBinding
import org.fossify.phone.databinding.ItemPermissionBinding
import org.fossify.phone.helpers.AppPermission
import org.fossify.phone.helpers.appPermissions
import org.fossify.phone.helpers.isPermissionGranted
import org.fossify.phone.helpers.mayNeedRestrictedSettingsUnlock

/**
 * Shows every permission and special access the dialer can use, each with its current state and a
 * button starting the matching grant flow. States are re-read in onResume because most of the
 * special accesses are granted in a system screen that gives us no usable result.
 */
class PermissionsActivity : SimpleActivity() {
    companion object {
        private const val RESTRICTED_SETTINGS_HELP_URL =
            "https://support.google.com/android/answer/12623953"
    }

    private val binding by viewBinding(ActivityPermissionsBinding::inflate)
    private var permissionRows: Map<AppPermission, ItemPermissionBinding> = emptyMap()

    private val requestRuntimePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissionStates()
        }

    private val requestRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissionStates()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.permissionsNestedScrollview))
        setupMaterialScrollListener(binding.permissionsNestedScrollview, binding.permissionsAppbar)

        binding.permissionsDone.setOnClickListener { finish() }
        binding.permissionsRestrictedHeader.setOnClickListener { toggleRestrictedDetails() }
        binding.permissionsRestrictedAppInfo.setOnClickListener { openAppDetailsSettings() }
        binding.permissionsRestrictedLearnMore.setOnClickListener {
            launchViewIntent(RESTRICTED_SETTINGS_HELP_URL)
        }

        buildPermissionRows()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.permissionsAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.permissionsHolder)
        binding.permissionsRequiredLabel.setTextColor(getProperPrimaryColor())
        binding.permissionsOptionalLabel.setTextColor(getProperPrimaryColor())
        refreshPermissionStates()
    }

    /**
     * Rows are built once and only their state is refreshed later, so a row never moves or
     * disappears under the user's finger while they work through the list.
     */
    private fun buildPermissionRows() {
        permissionRows = appPermissions().associateWith { permission ->
            val parent = if (permission.isRequired) {
                binding.permissionsRequiredHolder
            } else {
                binding.permissionsOptionalHolder
            }

            ItemPermissionBinding.inflate(layoutInflater, parent, true).apply {
                permissionIcon.setImageResource(permission.iconRes)
                permissionIcon.applyColorFilter(getProperTextColor())
                permissionTitle.setText(permission.titleRes)
                permissionSummary.setText(permission.summaryRes)
                root.setOnClickListener { requestPermission(permission) }
                permissionAction.setOnClickListener { requestPermission(permission) }
            }
        }

        binding.permissionsRequiredLabel
            .beVisibleIf(binding.permissionsRequiredHolder.childCount > 0)
        binding.permissionsOptionalLabel
            .beVisibleIf(binding.permissionsOptionalHolder.childCount > 0)
    }

    private fun refreshPermissionStates() {
        val grantedColor = getColor(R.color.md_green_400)
        val missingColor = getColor(R.color.md_red_400)

        permissionRows.forEach { (permission, row) ->
            val granted = isPermissionGranted(permission)
            val stateColor = if (granted) grantedColor else missingColor

            row.permissionStatusIcon.setImageResource(
                if (granted) R.drawable.ic_check_circle_vector else R.drawable.ic_cross_vector
            )
            row.permissionStatusIcon.applyColorFilter(stateColor)
            row.permissionStatusLabel.setText(
                if (granted) R.string.permission_granted else R.string.permission_not_granted
            )
            row.permissionStatusLabel.setTextColor(stateColor)

            row.permissionAction.beVisibleIf(!granted)
            row.permissionAction.setText(
                if (permission.opensSystemScreen) {
                    R.string.permission_open_settings
                } else {
                    org.fossify.commons.R.string.grant_permission
                }
            )
            row.root.isClickable = !granted
        }

        // only worth the screen space while a restricted entry is actually still blocked
        binding.permissionsRestrictedNotice.beVisibleIf(mayNeedRestrictedSettingsUnlock())
        binding.permissionsRestrictedIcon.applyColorFilter(getColor(R.color.md_amber_700))
        binding.permissionsRestrictedTitle.setTextColor(getColor(R.color.md_amber_700))
        binding.permissionsRestrictedChevron.applyColorFilter(getProperTextColor())
    }

    private fun toggleRestrictedDetails() {
        val expanded = !binding.permissionsRestrictedDetails.isVisible()
        binding.permissionsRestrictedDetails.beVisibleIf(expanded)
        binding.permissionsRestrictedTeaser.beVisibleIf(!expanded)
        binding.permissionsRestrictedChevron.setImageResource(
            if (expanded) R.drawable.ic_chevron_up_vector else R.drawable.ic_chevron_down_vector
        )
        binding.permissionsRestrictedChevron.applyColorFilter(getProperTextColor())
    }

    private fun requestPermission(permission: AppPermission) {
        if (isPermissionGranted(permission)) {
            return
        }

        when (permission) {
            AppPermission.DEFAULT_DIALER -> launchSetDefaultDialerIntent()
            AppPermission.FULL_SCREEN_INTENT -> openFullScreenIntentSettingsSafely()
            AppPermission.DRAW_OVER_OTHER_APPS -> openOverlaySettings()
            AppPermission.CALLER_ID -> requestCallScreeningRole()
            else -> requestRuntimePermissions.launch(permission.runtimePermissions.toTypedArray())
        }
    }

    @SuppressLint("NewApi")
    private fun openFullScreenIntentSettingsSafely() {
        try {
            openFullScreenIntentSettings(BuildConfig.APPLICATION_ID)
        } catch (_: Exception) {
            openAppDetailsSettings()
        }
    }

    private fun openOverlaySettings() {
        val packageUri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
        } catch (_: Exception) {
            try {
                // some ROMs reject the per-package variant and only accept the global screen
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (_: Exception) {
                openAppDetailsSettings()
            }
        }
    }

    @SuppressLint("NewApi")
    private fun requestCallScreeningRole() {
        if (!isQPlus()) {
            return
        }

        try {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) != true) {
                openAppDetailsSettings()
                return
            }

            requestRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        } catch (_: Exception) {
            openAppDetailsSettings()
        }
    }

    private fun openAppDetailsSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                )
            )
        } catch (_: Exception) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
        }
    }
}
