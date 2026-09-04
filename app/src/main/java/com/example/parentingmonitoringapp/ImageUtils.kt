package com.example.parentingmonitoringapp

import android.graphics.BitmapFactory
import android.graphics.Outline
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import java.net.URL

/**
 * Clips this ImageView into a perfect circle using its current bounds.
 * Used for every profile-picture avatar across the Admin, Parent and
 * Student dashboards plus the Profile screen itself.
 */
fun ImageView.clipToCircle() {
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
}

/**
 * Downloads and displays a remote profile photo (an https download URL,
 * typically from Firebase Storage) into this ImageView on a background
 * thread. No-op and leaves the current placeholder if [url] is null/blank.
 */
fun ImageView.loadAvatar(url: String?) {
    if (url.isNullOrBlank()) return
    val target = this
    Thread {
        try {
            val bytes = URL(url).openStream().use { it.readBytes() }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            Handler(Looper.getMainLooper()).post {
                target.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            Log.e("ImageUtils", "Failed to load avatar: ${e.message}")
        }
    }.start()
}