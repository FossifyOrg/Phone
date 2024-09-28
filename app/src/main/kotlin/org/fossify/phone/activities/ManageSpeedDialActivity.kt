package org.fossify.phone.activities

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.google.gson.Gson
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.shortcutManager
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.*
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.RadioItem
import org.fossify.commons.models.contacts.Contact
import org.fossify.phone.adapters.SpeedDialAdapter
import org.fossify.phone.databinding.ActivityManageSpeedDialBinding
import org.fossify.phone.dialogs.SelectContactDialog
import org.fossify.phone.extensions.config
import org.fossify.phone.interfaces.RemoveSpeedDialListener
import org.fossify.phone.models.SpeedDial

class ManageSpeedDialActivity : SimpleActivity(), RemoveSpeedDialListener {
    private val binding by viewBinding(ActivityManageSpeedDialBinding::inflate)

    private var allContacts = mutableListOf<Contact>()
    private var speedDialValues = mutableListOf<SpeedDial>()

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            updateMaterialActivityViews(manageSpeedDialCoordinator, manageSpeedDialHolder, useTransparentNavigation = true, useTopSearchMenu = false)
            setupMaterialScrollListener(manageSpeedDialScrollview, manageSpeedDialToolbar)

        }

        speedDialValues = config.getSpeedDialValues()
        updateAdapter()

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts.addAll(contacts)

            val privateCursor = getMyContactsCursor(false, true)
            val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        updateTextColors(binding.manageSpeedDialScrollview)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.manageSpeedDialToolbar, NavigationIcon.Arrow)
    }

    override fun onStop() {
        super.onStop()
        config.speedDial = Gson().toJson(speedDialValues)
    }

    private fun updateAdapter() {
        SpeedDialAdapter(this, speedDialValues, this, binding.speedDialList) {
            val clickedContact = it as SpeedDial
            if (allContacts.isEmpty()) {
                return@SpeedDialAdapter
            }

            SelectContactDialog(this, allContacts) { selectedContact ->
                if (selectedContact.phoneNumbers.size > 1) {
                    val radioItems = selectedContact.phoneNumbers.mapIndexed { index, item ->
                        RadioItem(index, item.normalizedNumber, item)
                    }
                    val userPhoneNumbersList = selectedContact.phoneNumbers.map { it.value }
                    val checkedItemId = userPhoneNumbersList.indexOf(clickedContact.number)
                    RadioGroupDialog(this, ArrayList(radioItems), checkedItemId = checkedItemId) { selectedValue ->
                        val selectedNumber = selectedValue as PhoneNumber
                        speedDialValues.first { it.id == clickedContact.id }.apply {
                            displayName = selectedContact.getNameToDisplay()
                            number = selectedNumber.normalizedNumber
                            photoUri = selectedContact.photoUri
                        }
                        updateAdapter()
                    }
                } else {
                    speedDialValues.first { it.id == clickedContact.id }.apply {
                        displayName = selectedContact.getNameToDisplay()
                        number = selectedContact.phoneNumbers.first().normalizedNumber
                        photoUri = selectedContact.photoUri
                    }
                    updateAdapter()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    speedDialValues.filter { it.number.isNotBlank() }.take(3).forEach {
                        this.handlePermission(PERMISSION_CALL_PHONE) { hasPermission ->
                            val action = if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                            val intent = Intent(action).apply {
                                data = Uri.fromParts("tel", it.number, null)
                            }
                            SimpleContactsHelper(this).getShortcutImage(it.photoUri, it.displayName) { image ->
                                this.runOnUiThread {
                                    val shortcut = ShortcutInfo.Builder(this, "sd${it.id}")
                                        .setShortLabel(it.displayName)
                                        .setIcon(Icon.createWithAdaptiveBitmap(image))
                                        .setIntent(intent)
                                        .build()
                                    this.shortcutManager.pushDynamicShortcut(shortcut)
                                }
                            }
                        }
                    }
                }
            }
        }.apply {
            binding.speedDialList.adapter = this
        }
    }

    override fun removeSpeedDial(ids: ArrayList<Int>) {
        ids.forEach { dialId ->
            speedDialValues.first { it.id == dialId }.apply {
                displayName = ""
                number = ""
                photoUri = ""
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.shortcutManager.removeDynamicShortcuts(ids.map { "sd$it" })
        }
        updateAdapter()
    }
}
