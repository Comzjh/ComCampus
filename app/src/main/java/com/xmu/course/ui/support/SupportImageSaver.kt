package com.xmu.course.ui.support

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.DrawableRes

/** 将内置赞助图片保存到系统相册，不上传图片或课程数据。 */
object SupportImageSaver {

    fun saveToGallery(context: Context, @DrawableRes resourceId: Int, fileName: String): Boolean {
        val resolver = context.contentResolver
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/XMU Course")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            runCatching {
                resolver.openOutputStream(uri)?.use { output ->
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, output))
                } ?: error("无法打开相册文件")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            }.getOrElse {
                resolver.delete(uri, null, null)
                false
            }
        } else {
            runCatching {
                MediaStore.Images.Media.insertImage(resolver, bitmap, fileName, "XMU Course 支持开发") != null
            }.getOrDefault(false)
        }
    }
}
