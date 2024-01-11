package org.fossify.phone.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import org.fossify.commons.helpers.isQPlus
import org.fossify.phone.R
import org.fossify.phone.models.CallContact

class CallContactAvatarHelper(private val context: Context) {
    @SuppressLint("NewApi")
    fun getCallContactAvatar(callContact: CallContact?): Bitmap? {
        var bitmap: Bitmap? = null
        if (callContact?.photoUri?.isNotEmpty() == true) {
            val photoUri = Uri.parse(callContact.photoUri)
            try {
                val contentResolver = context.contentResolver
                bitmap = if (isQPlus()) {
                    val tmbSize = context.resources.getDimension(R.dimen.list_avatar_size).toInt()
                    contentResolver.loadThumbnail(photoUri, Size(tmbSize, tmbSize), null)
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
                }
                bitmap = getCircularBitmap(bitmap!!)
            } catch (ignored: Exception) {
                return null
            }
        }
        return bitmap
    }

    fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.width, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val radius = bitmap.width / 2.toFloat()

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }
}
