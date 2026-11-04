package com.example.kinnect


import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors


object StoryRepo {
    const val DAY_MS = 24L * 60 * 60 * 1000
    private const val MAX_VIDEO_BYTES = 5 * 1024 * 1024   // RTDB node limit is 10MB, base64 adds ~33%
    private val db get() = FirebaseDatabase.getInstance().reference
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun post(
        ctx: Context, uri: Uri, isVideo: Boolean, label: String,
        closeFriendsOnly: Boolean, done: (ok: Boolean, error: String?) -> Unit
    ) {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return done(false, "Not signed in")
        val app = ctx.applicationContext
        io.execute {
            try {
                val bytes = if (isVideo) {
                    app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                        .also { if (it.size > MAX_VIDEO_BYTES) throw Exception("Video too large (max 5 MB)") }
                } else compressImage(app, uri)
                val media = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val withAudience: ((List<String>) -> Unit) -> Unit = { cb ->
                    if (closeFriendsOnly) {
                        db.child("closeFriends").child(me).get()
                            .addOnSuccessListener { snap -> cb(snap.children.mapNotNull { it.key }) }
                            .addOnFailureListener { done(false, it.message) }
                    } else FriendsRepo.load { cb(it) }
                }
                withAudience { uids ->
                    val visibleTo = HashMap<String, Boolean>()
                    uids.forEach { visibleTo[it] = true }

                    val ref = db.child("stories").child(me).push()
                    val id = ref.key!!
                    val now = System.currentTimeMillis()
                    val meta = mapOf(
                        "createdAt" to now, "expiresAt" to now + DAY_MS,
                        "type" to if (isVideo) "video" else "image",
                        "label" to label, "visibleTo" to visibleTo
                    )
                    db.child("storyMedia").child(id).setValue(media).addOnSuccessListener {
                        ref.setValue(meta)
                            .addOnSuccessListener { done(true, null) }
                            .addOnFailureListener { done(false, it.message) }
                    }.addOnFailureListener { done(false, it.message) }
                }
            } catch (e: Exception) {
                main.post { done(false, e.message) }
            }
        }
    }

    private fun compressImage(ctx: Context, uri: Uri): ByteArray {
        val src = ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            ?: throw Exception("Cannot read image")
        val scale = minOf(1f, 1080f / maxOf(src.width, src.height))
        val bmp = Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 75, it) }.toByteArray()
    }


    fun purgeMyExpired() {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.child("stories").child(me).get().addOnSuccessListener { snap ->
            val now = System.currentTimeMillis()
            snap.children.forEach { s ->
                val exp = s.child("expiresAt").getValue(Long::class.java) ?: 0L
                if (exp <= now) {
                    s.key?.let { db.child("storyMedia").child(it).removeValue() }
                    s.ref.removeValue()
                }
            }
        }
    }
}