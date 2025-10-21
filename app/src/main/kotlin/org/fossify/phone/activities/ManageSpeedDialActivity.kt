package org.fossify.phone.activities

import android.os.Bundle
import com.google.gson.Gson
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getPhoneNumberTypeText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ContactsHelper
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.NavigationIcon
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
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(manageSpeedDialScrollview))
            setupMaterialScrollListener(binding.manageSpeedDialScrollview, binding.manageSpeedDialAppbar)

        }

        speedDialValues = config.getSpeedDialValues()
        updateAdapter()

        ContactsHelper(this).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
            allContacts.addAll(contacts)

            val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
            val privateContacts = MyContactsContentProvider.getContacts(this, privateCursor)
            allContacts.addAll(privateContacts)
            allContacts.sort()
        }

        updateTextColors(binding.manageSpeedDialScrollview)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.manageSpeedDialAppbar, NavigationIcon.Arrow)
    }

    override fun onStop() {
        super.onStop()
        config.speedDial = Gson().toJson(speedDialValues)
    }

    private fun updateAdapter() {
        SpeedDialAdapter(this, speedDialValues, this, binding.speedDialList) { any ->
            val clickedContact = any as SpeedDial
            if (allContacts.isEmpty()) {
                return@SpeedDialAdapter
            }

            SelectContactDialog(this, allContacts) { selectedContact ->
                if (selectedContact.phoneNumbers.size > 1) {
                    val radioItems = selectedContact.phoneNumbers.mapIndexed { index, item ->
                        RadioItem(index, "${item.value} (${getPhoneNumberTypeText(item.type, item.label)})", item)
                    }
                    val userPhoneNumbersList = selectedContact.phoneNumbers.map { it.value }
                    val checkedItemId = userPhoneNumbersList.indexOf(clickedContact.number)
                    RadioGroupDialog(this, ArrayList(radioItems), checkedItemId = checkedItemId) { selectedValue ->
                        val selectedNumber = selectedValue as PhoneNumber
                        speedDialValues.first { it.id == clickedContact.id }.apply {
                            displayName = selectedContact.getNameToDisplay()
                            number = selectedNumber.value
                            type = selectedNumber.type
                            label = selectedNumber.label
                        }
                        updateAdapter()
                    }
                } else {
                    speedDialValues.first { it.id == clickedContact.id }.apply {
                        displayName = selectedContact.getNameToDisplay()
                        number = selectedContact.phoneNumbers.first().value
                    }
                    updateAdapter()
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
                type = null
                label = null
            }
        }
        updateAdapter()
    }
}
