package com.example.kinnect



import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

object Reactions {
    val keys = listOf("like", "love", "haha", "wow", "sad", "angry")

    fun emoji(key: String?): String = when (key) {
        "like" -> "👍"
        "love" -> "❤️"
        "haha" -> "😆"
        "wow" -> "😮"
        "sad" -> "😢"
        "angry" -> "😡"
        else -> "👍"
    }

    fun label(key: String?): String = when (key) {
        "like" -> "Like"
        "love" -> "Love"
        "haha" -> "Haha"
        "wow" -> "Wow"
        "sad" -> "Sad"
        "angry" -> "Angry"
        else -> "Like"
    }
}

data class ReactionSummary(
    val text: String,
    val mine: String?
)

object ReactionUi {
    fun summarize(snapshot: DataSnapshot, currentUid: String): ReactionSummary {
        var mine: String? = null
        var count = 0

        for (child in snapshot.children) {
            count++
            if (child.key == currentUid) {
                mine = child.getValue(String::class.java)
            }
        }

        val text = if (count == 0) "Reactions" else "$count Reactions"
        return ReactionSummary(text, mine)
    }
}

object Users {
    // 60 s cache so lists (chat, comments) don't hit the database once per row
    private val cache = HashMap<String, Triple<String, String?, Long>>()

    fun loadUserProfile(uid: String, onResult: (name: String, profilePic: String?) -> Unit) {
        if (uid.isBlank()) {
            onResult("User", null)
            return
        }
        val hit = cache[uid]
        if (hit != null && System.currentTimeMillis() - hit.third < 60_000) {
            onResult(hit.first, hit.second)
            return
        }
        FirebaseDatabase.getInstance().reference.child("users").child(uid)
            .get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    onResult("User", null)
                    return@addOnSuccessListener
                }
                val fullName = snapshot.child("fullName").getValue(String::class.java)
                val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                val username = snapshot.child("username").getValue(String::class.java) ?: "User"
                val pic = snapshot.child("profilePic").getValue(String::class.java)

                val name = fullName?.takeIf { it.isNotBlank() }
                    ?: "$firstName $lastName".trim().takeIf { it.isNotBlank() }
                    ?: username

                cache[uid] = Triple(name, pic, System.currentTimeMillis())
                onResult(name, pic)
            }.addOnFailureListener {
                onResult("User", null)
            }
    }

    fun name(uid: String, onResult: (String) -> Unit) {
        loadUserProfile(uid) { name, _ -> onResult(name) }
    }

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+"))
        return when {
            parts.isEmpty() || name.isBlank() -> "U"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts[0].first()}${parts[1].first()}".uppercase()
        }
    }
}

object Img {
    private val memoryCache = mutableMapOf<String, Bitmap>()

    fun setIfCached(imageView: ImageView, path: String): Boolean {
        val cachedBmp = memoryCache[path]
        if (cachedBmp != null && !cachedBmp.isRecycled) {
            imageView.setImageBitmap(cachedBmp)
            return true
        }
        return false
    }

    fun loadUri(context: Context, imageView: ImageView, uri: Uri, size: Int) {
        imageView.setImageURI(uri)
    }

    /** Downsamples + JPEG-compresses an image (used for chat images). */
    fun compressUriToBytes(context: Context, uri: Uri, maxSide: Int, quality: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw Exception("Cannot read image")
        val m = maxOf(src.width, src.height)
        val bmp = if (m > maxSide) {
            val f = maxSide.toFloat() / m
            Bitmap.createScaledBitmap(src, (src.width * f).toInt(), (src.height * f).toInt(), true)
        } else src
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }

    fun loadBase64(imageView: ImageView, path: String, base64Str: String, size: Int) {
        if (setIfCached(imageView, path)) return
        try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            if (bitmap != null) {
                memoryCache[path] = bitmap
                imageView.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

object PostRepo {
    private val executor = Executors.newSingleThreadExecutor()

    val db: DatabaseReference
        get() = FirebaseDatabase.getInstance().reference

    fun me(): String? = FirebaseAuth.getInstance().currentUser?.uid

    fun timeAgo(timestamp: Long): String {
        val diff = (System.currentTimeMillis() - timestamp) / 1000
        return when {
            diff < 60 -> "Just now"
            diff < 3600 -> "${diff / 60}m"
            diff < 86400 -> "${diff / 3600}h"
            else -> "${diff / 86400}d"
        }
    }

    fun setReaction(postId: String, reaction: String?) {
        val uid = me() ?: return
        val ref = db.child("reactions").child(postId).child(uid)
        if (reaction == null) {
            ref.removeValue()
        } else {
            ref.setValue(reaction)
        }
    }

    fun setCommentLike(postId: String, commentId: String, liked: Boolean) {
        val uid = me() ?: return
        val ref = db.child("commentLikes").child(postId).child(commentId).child(uid)
        if (liked) ref.setValue(true) else ref.removeValue()
    }

    fun addComment(
        context: Context,
        postId: String,
        text: String,
        parentId: String,
        imageUri: Uri?,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val uid = me() ?: run {
            onComplete(false, "User not authenticated")
            return
        }

        val commentRef = db.child("comments").child(postId).push()
        val commentId = commentRef.key ?: run {
            onComplete(false, "Failed to generate comment key")
            return
        }

        executor.execute {
            var b64Image: String? = null
            if (imageUri != null) {
                b64Image = uriToBase64(context, imageUri)
            }

            val commentMap = hashMapOf<String, Any>(
                "authorUid" to uid,
                "text" to text,
                "parentId" to parentId,
                "createdAt" to System.currentTimeMillis(),
                "hasImage" to (b64Image != null)
            )

            commentRef.setValue(commentMap).addOnSuccessListener {
                if (b64Image != null) {
                    db.child("commentImages").child(commentId).setValue(b64Image)
                }

                db.child("posts").child(postId).child("commentCount")
                    .get().addOnSuccessListener { snap ->
                        val curr = snap.getValue(Int::class.java) ?: 0
                        db.child("posts").child(postId).child("commentCount").setValue(curr + 1)
                    }

                onComplete(true, null)
            }.addOnFailureListener { error ->
                onComplete(false, error.message)
            }
        }
    }

    private fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            val outputStream = ByteArrayOutputStream()

            val maxDim = 800
            val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                var w = maxDim
                var h = maxDim
                if (ratio > 1) {
                    h = (maxDim / ratio).toInt()
                } else {
                    w = (maxDim * ratio).toInt()
                }
                Bitmap.createScaledBitmap(bitmap, w, h, true)
            } else {
                bitmap
            }

            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}