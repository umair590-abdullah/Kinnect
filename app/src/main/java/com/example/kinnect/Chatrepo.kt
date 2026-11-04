package com.example.kinnect

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import java.util.concurrent.Executors

/**
 * RTDB layout
 *  messages/{chatId}/{msgId}  = { senderUid, type:"text"|"image"|"post", text, postId?, createdAt(server time),
 *                                 edited?, editedAt?, replyTo?, replyToSender?, replyText?, hearts:{uid:true} }
 *  messageImages/{msgId}      = base64 JPEG
 *  userChats/{uid}/{chatId}   = { otherUid, lastMessage, lastTime, lastSender, unread }   (drives the Chats list)
 *  chatId = the two uids sorted and joined with "_"
 */
object ChatRepo {
    const val EDIT_WINDOW_MS = 5 * 60 * 1000L

    private val db get() = PostRepo.db
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    class Reply(val msgId: String, val senderUid: String, val text: String)

    fun chatId(a: String, b: String) = if (a < b) "${a}_$b" else "${b}_$a"

    private fun summary(type: String, text: String) = when (type) {
        "image" -> "📷 Photo"
        "post" -> "Shared a post"
        else -> text
    }

    fun send(
        ctx: Context, other: String, type: String, text: String = "",
        postId: String? = null, image: Uri? = null, reply: Reply? = null,
        done: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val me = PostRepo.me() ?: return done(false, "Not signed in")
        val app = ctx.applicationContext
        val chatId = chatId(me, other)
        io.execute {
            try {
                val id = db.child("messages").child(chatId).push().key!!
                val msg = HashMap<String, Any?>()
                msg["senderUid"] = me
                msg["type"] = type
                msg["text"] = text
                msg["createdAt"] = ServerValue.TIMESTAMP
                if (postId != null) msg["postId"] = postId
                if (reply != null) {
                    msg["replyTo"] = reply.msgId
                    msg["replyToSender"] = reply.senderUid
                    msg["replyText"] = reply.text
                }
                val u = HashMap<String, Any?>()
                u["messages/$chatId/$id"] = msg
                if (image != null) {
                    u["messageImages/$id"] =
                        Base64.encodeToString(Img.compressUriToBytes(app, image, 900, 75), Base64.NO_WRAP)
                }
                val s = summary(type, text)
                for ((owner, peer) in listOf(me to other, other to me)) {
                    val base = "userChats/$owner/$chatId"
                    u["$base/otherUid"] = peer
                    u["$base/lastMessage"] = s
                    u["$base/lastTime"] = ServerValue.TIMESTAMP
                    u["$base/lastSender"] = me
                }
                db.updateChildren(u)
                    .addOnSuccessListener { bumpUnread(other, chatId); done(true, null) }
                    .addOnFailureListener { done(false, it.message) }
            } catch (e: Exception) {
                main.post { done(false, e.message) }
            }
        }
    }

    private fun bumpUnread(other: String, chatId: String) {
        db.child("userChats").child(other).child(chatId).child("unread").runTransaction(object : Transaction.Handler {
            override fun doTransaction(d: MutableData): Transaction.Result {
                d.value = (d.getValue(Int::class.java) ?: 0) + 1
                return Transaction.success(d)
            }
            override fun onComplete(e: DatabaseError?, committed: Boolean, s: DataSnapshot?) {}
        })
    }

    /** Called while a chat is open: clears my unread counter. */
    fun markRead(chatId: String) {
        val me = PostRepo.me() ?: return
        val ref = db.child("userChats").child(me).child(chatId)
        ref.get().addOnSuccessListener { s ->
            if (s.exists() && (s.child("unread").getValue(Int::class.java) ?: 0) > 0) ref.child("unread").setValue(0)
        }
    }

    fun toggleHeart(chatId: String, msgId: String, on: Boolean) {
        val me = PostRepo.me() ?: return
        val ref = db.child("messages").child(chatId).child(msgId).child("hearts").child(me)
        if (on) ref.setValue(true) else ref.removeValue()
    }

    /** Only the sender, and only within 5 minutes (also enforced by the database rules). */
    fun edit(chatId: String, msgId: String, other: String, newText: String, onError: (String?) -> Unit) {
        val u = hashMapOf<String, Any?>(
            "messages/$chatId/$msgId/text" to newText,
            "messages/$chatId/$msgId/edited" to true,
            "messages/$chatId/$msgId/editedAt" to ServerValue.TIMESTAMP
        )
        db.updateChildren(u).addOnSuccessListener { refreshLast(chatId, other) }.addOnFailureListener { onError(it.message) }
    }

    fun delete(chatId: String, msgId: String, other: String, onError: (String?) -> Unit) {
        val u = hashMapOf<String, Any?>("messages/$chatId/$msgId" to null, "messageImages/$msgId" to null)
        db.updateChildren(u).addOnSuccessListener { refreshLast(chatId, other) }.addOnFailureListener { onError(it.message) }
    }

    /** Rewrites both chat-list summaries from the newest remaining message (after edit/delete). */
    private fun refreshLast(chatId: String, other: String) {
        val me = PostRepo.me() ?: return
        db.child("messages").child(chatId).orderByChild("createdAt").limitToLast(1).get().addOnSuccessListener { s ->
            val last = s.children.firstOrNull()
            val u = HashMap<String, Any?>()
            for (owner in listOf(me, other)) {
                val base = "userChats/$owner/$chatId"
                if (last == null) {
                    u[base] = null
                } else {
                    u["$base/lastMessage"] = summary(
                        last.child("type").getValue(String::class.java) ?: "text",
                        last.child("text").getValue(String::class.java) ?: ""
                    )
                    u["$base/lastTime"] = last.child("createdAt").getValue(Long::class.java) ?: 0L
                    u["$base/lastSender"] = last.child("senderUid").getValue(String::class.java) ?: ""
                }
            }
            db.updateChildren(u)
        }
    }
}

/**
 * Who counts as a "friend": friends/{me}/{uid}. The project has no friend-adding screen yet
 * (friends.kt is empty), so when that list is empty we fall back to every other registered user.
 * That way chats, stories and the feed work today, and start using real friends as soon as
 * friends/{me} is filled in.
 */
object FriendsRepo {
    fun load(cb: (List<String>) -> Unit) {
        val me = PostRepo.me() ?: return cb(emptyList())
        PostRepo.db.child("friends").child(me).get().addOnSuccessListener { s ->
            val ids = s.children.mapNotNull { it.key }
            if (ids.isNotEmpty()) {
                cb(ids)
            } else {
                PostRepo.db.child("users").limitToFirst(100).get()
                    .addOnSuccessListener { u -> cb(u.children.mapNotNull { it.key }.filter { it != me }) }
                    .addOnFailureListener { cb(emptyList()) }
            }
        }.addOnFailureListener { cb(emptyList()) }
    }
}

object ChatUi {
    private val palette = intArrayOf(0xFFA63A50.toInt(), 0xFF2E7D5B.toInt(), 0xFF5B5BA6.toInt(), 0xFF8C5A2B.toInt(), 0xFF7B4B94.toInt())
    fun avatarColor(uid: String) = palette[Math.floorMod(uid.hashCode(), palette.size)]

    /** Dialog listing my friends (see FriendsRepo). */
    fun pickFriend(act: AppCompatActivity, title: String, onPick: (uid: String, name: String) -> Unit) {
        FriendsRepo.load { uids ->
            if (act.isFinishing || act.isDestroyed) return@load
            if (uids.isEmpty()) {
                Toast.makeText(act, "No other users found yet", Toast.LENGTH_SHORT).show()
                return@load
            }
            val names = Array(uids.size) { "User" }
            var loaded = 0
            uids.forEachIndexed { i, u ->
                Users.name(u) { n ->
                    names[i] = n
                    loaded++
                    if (loaded == uids.size && !act.isFinishing && !act.isDestroyed) {
                        AlertDialog.Builder(act).setTitle(title)
                            .setItems(names) { _, w -> onPick(uids[w], names[w]) }.show()
                    }
                }
            }
        }
    }

    /** Share a feed post into a chat: pick a friend, send the post as a message. */
    fun sharePost(act: AppCompatActivity, postId: String) {
        pickFriend(act, "Send post to…") { uid, name ->
            ChatRepo.send(act, uid, "post", postId = postId) { ok, err ->
                Toast.makeText(act, if (ok) "Sent to $name" else (err ?: "Failed to send"), Toast.LENGTH_SHORT).show()
            }
        }
    }
}