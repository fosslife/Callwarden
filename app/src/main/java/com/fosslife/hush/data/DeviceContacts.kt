package com.fosslife.hush.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import com.fosslife.hush.core.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactItem(
    val id: Long,
    val name: String,
    val numbers: List<String>,
    val sendToVoicemail: Boolean,
)

/**
 * Android never passes calls from saved contacts to a call-screening app, so a saved
 * contact is blocked with the system's own "send to voicemail" flag instead.
 */
object DeviceContacts {

    suspend fun load(context: Context): List<ContactItem> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val numbers = HashMap<Long, MutableList<String>>()
        resolver.query(Phone.CONTENT_URI, arrayOf(Phone.CONTACT_ID, Phone.NUMBER), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(1) ?: continue
                numbers.getOrPut(c.getLong(0)) { mutableListOf() } += number
            }
        }

        val contacts = mutableListOf<ContactItem>()
        resolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY, Contacts.SEND_TO_VOICEMAIL),
            "${Contacts.HAS_PHONE_NUMBER} = 1",
            null,
            "${Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                contacts += ContactItem(
                    id = id,
                    name = c.getString(1) ?: "(no name)",
                    numbers = numbers[id].orEmpty().distinctBy { PhoneNumbers.normalize(it) },
                    sendToVoicemail = c.getInt(2) == 1,
                )
            }
        }
        contacts
    }

    suspend fun setSendToVoicemail(context: Context, contactId: Long, value: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply { put(Contacts.SEND_TO_VOICEMAIL, if (value) 1 else 0) }
            val uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
            context.contentResolver.update(uri, values, null, null) > 0
        }
}
