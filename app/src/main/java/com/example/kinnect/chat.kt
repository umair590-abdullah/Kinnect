package com.example.kinnect

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener

/**
 * Screen 21. Launch with putExtra("otherUid", uid).
 * Double-tap a bubble = heart. Long-press = Reply / Heart / (Edit, Delete if yours and < 5 min old).
 */
class chat : AppCompatActivity() {

    private class Msg(
        val id: String, val sender: String, val type: String, val text: String, val postId: String,
        val createdAt: Long, val edited: Boolean, val replyTo: String, val replyToSender: String,
        val replyText: String, val hearts: Set<String>
    )

    private var me = ""
    private var other = ""
    private var chatId = ""
    private val msgs = ArrayList<Msg>()
    private var replyingTo: Msg? = null
    private var lastCount = 0

    private lateinit var container: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var btnSend: TextView

    private var query: Query? = null
    private var listener: ValueEventListener? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) ChatRepo.send(this, other, "image", image = uri, reply = currentReply()) { ok, err ->
            if (!ok) Toast.makeText(this, err ?: "Failed to send image", Toast.LENGTH_LONG).show()
        }.also { clearReply() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, maxOf(b.bottom, ime.bottom)); insets
        }
        other = intent.getStringExtra("otherUid") ?: run { finish(); return }
        me = PostRepo.me() ?: run { finish(); return }
        chatId = ChatRepo.chatId(me, other)

        container = findViewById(R.id.messagesContainer)
        scroll = findViewById(R.id.messagesScroll)
        input = findViewById(R.id.msgInput)
        btnSend = findViewById(R.id.btnSend)

        // Launch Voice or Video Call via Call Button
        findViewById<View>(R.id.callicon1)?.setOnClickListener {
            val targetName = findViewById<TextView>(R.id.chatName).text.toString().ifBlank { "User" }
            val options = arrayOf("Voice Call", "Video Call")
            AlertDialog.Builder(this)
                .setTitle("Start Call with $targetName")
                .setItems(options) { _, which ->
                    val isVideo = (which == 1)
                    val intent = Intent(this, voicecall::class.java).apply {
                        putExtra("channelName", chatId)
                        putExtra("targetName", targetName)
                        putExtra("isVideoCall", isVideo)
                    }
                    startActivity(intent)
                }
                .show()
        }

        findViewById<View>(R.id.chatBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnImage).setOnClickListener { pickImage.launch("image/*") }
        findViewById<View>(R.id.btnCam).setOnClickListener { pickImage.launch("image/*") }
        findViewById<View>(R.id.btnAttachPost).setOnClickListener { pickPostToShare() }
        findViewById<View>(R.id.replyBarCancel).setOnClickListener { clearReply() }
        btnSend.setOnClickListener { sendText() }
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { btnSend.text = if (s.isNullOrBlank()) "👍" else "➤" }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // header + intro
        Users.name(other) { n ->
            findViewById<TextView>(R.id.chatName).text = n
            findViewById<TextView>(R.id.introName).text = n
            for (id in intArrayOf(R.id.chatAvatar, R.id.introAvatar)) {
                findViewById<TextView>(id).apply {
                    text = Users.initials(n)
                    backgroundTintList = ColorStateList.valueOf(ChatUi.avatarColor(other))
                }
            }
        }

        query = PostRepo.db.child("messages").child(chatId).orderByChild("createdAt").limitToLast(200)
        listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                msgs.clear()
                s.children.forEach { c ->
                    msgs.add(Msg(
                        c.key ?: return@forEach,
                        c.child("senderUid").getValue(String::class.java) ?: "",
                        c.child("type").getValue(String::class.java) ?: "text",
                        c.child("text").getValue(String::class.java) ?: "",
                        c.child("postId").getValue(String::class.java) ?: "",
                        c.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis(),
                        c.child("edited").getValue(Boolean::class.java) ?: false,
                        c.child("replyTo").getValue(String::class.java) ?: "",
                        c.child("replyToSender").getValue(String::class.java) ?: "",
                        c.child("replyText").getValue(String::class.java) ?: "",
                        c.child("hearts").children.mapNotNull { it.key }.toSet()
                    ))
                }
                msgs.sortBy { it.createdAt }
                render()
                ChatRepo.markRead(chatId)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        query!!.addValueEventListener(listener!!)
    }

    // ---------------- sending ----------------
    private fun snippet(m: Msg) = when (m.type) {
        "image" -> "📷 Photo"
        "post" -> "Shared a post"
        else -> m.text.take(80)
    }

    private fun currentReply() = replyingTo?.let { ChatRepo.Reply(it.id, it.sender, snippet(it)) }

    private fun sendText() {
        val t = input.text.toString().trim().ifEmpty { "👍" }
        ChatRepo.send(this, other, "text", t, reply = currentReply()) { ok, err ->
            if (!ok) Toast.makeText(this, err ?: "Failed to send", Toast.LENGTH_LONG).show()
        }
        input.setText("")
        clearReply()
    }

    private fun setReply(m: Msg) {
        replyingTo = m
        findViewById<View>(R.id.replyBar).visibility = View.VISIBLE
        val who = if (m.sender == me) "yourself" else (findViewById<TextView>(R.id.chatName).text.toString())
        findViewById<TextView>(R.id.replyBarText).text = "Replying to $who: ${snippet(m)}"
        input.requestFocus()
    }

    private fun clearReply() {
        replyingTo = null
        findViewById<View>(R.id.replyBar).visibility = View.GONE
    }

    private fun pickPostToShare() {
        PostRepo.db.child("posts").orderByChild("createdAt").limitToLast(15).get().addOnSuccessListener { s ->
            val list = s.children.toList().reversed()
            if (list.isEmpty()) { Toast.makeText(this, "No posts to share yet", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
            val labels = list.map {
                val t = it.child("text").getValue(String::class.java) ?: ""
                val n = it.child("imageCount").getValue(Int::class.java) ?: 0
                (if (t.isBlank()) "Photo post" else t.take(40)) + (if (n > 0) "  📷$n" else "")
            }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Share a post")
                .setItems(labels) { _, w ->
                    ChatRepo.send(this, other, "post", postId = list[w].key, reply = currentReply())
                    clearReply()
                }.show()
        }
    }

    // ---------------- rendering ----------------
    private fun render() {
        if (isFinishing || isDestroyed) return
        val grew = msgs.size > lastCount
        lastCount = msgs.size
        container.removeAllViews()
        msgs.forEach { bind(it) }
        if (grew) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun bind(m: Msg) {
        val v = LayoutInflater.from(this).inflate(R.layout.item_message, container, false) as LinearLayout
        container.addView(v)
        val mine = m.sender == me
        val d = resources.displayMetrics.density
        v.gravity = if (mine) android.view.Gravity.END else android.view.Gravity.START

        val bubble = v.findViewById<LinearLayout>(R.id.bubble)
        val text = v.findViewById<TextView>(R.id.msgText)
        val image = v.findViewById<ImageView>(R.id.msgImage)
        val card = v.findViewById<LinearLayout>(R.id.postCard)

        bubble.setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())

        if (mine) {
            bubble.setBackgroundColor(Color.parseColor("#1B6B5A"))
            text.setTextColor(Color.WHITE)
        } else {
            bubble.setBackgroundResource(R.drawable.rounded_card)
            text.setTextColor(Color.parseColor("#111111"))
        }

        // reply (stored + displayed)
        if (m.replyTo.isNotBlank()) {
            val label = v.findViewById<TextView>(R.id.replyLabel)
            val quote = v.findViewById<TextView>(R.id.replyQuote)
            label.visibility = View.VISIBLE
            quote.visibility = View.VISIBLE
            quote.text = m.replyText
            Users.name(m.sender) { s ->
                Users.name(m.replyToSender) { t ->
                    label.text = when {
                        mine && m.replyToSender == me -> "You replied to yourself"
                        mine -> "You replied to $t"
                        m.replyToSender == me -> "$s replied to you"
                        m.sender == m.replyToSender -> "$s replied to themselves"
                        else -> "$s replied to $t"
                    }
                }
            }
        }

        // content
        when (m.type) {
            "image" -> {
                text.visibility = View.GONE
                image.visibility = View.VISIBLE
                if (!Img.setIfCached(image, "m/${m.id}")) {
                    PostRepo.db.child("messageImages").child(m.id).get().addOnSuccessListener { s ->
                        val b64 = s.getValue(String::class.java) ?: return@addOnSuccessListener
                        Img.loadBase64(image, "m/${m.id}", b64, 720)
                    }
                }
            }
            "post" -> {
                text.visibility = View.GONE
                card.visibility = View.VISIBLE
                bindPostCard(card, m.postId, mine)
            }
            else -> text.text = m.text
        }

        // hearts
        val heart = v.findViewById<TextView>(R.id.heartBadge)
        if (m.hearts.isNotEmpty()) {
            heart.visibility = View.VISIBLE
            heart.text = if (m.hearts.size > 1) "❤ ${m.hearts.size}" else "❤"
            if (!mine) { // heart sits on the far side of the bubble
                val row = heart.parent as LinearLayout
                row.removeView(heart)
                row.addView(heart)
            }
        }

        v.findViewById<TextView>(R.id.msgMeta).text =
            PostRepo.timeAgo(m.createdAt) + if (m.edited) " · Edited" else ""

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                ChatRepo.toggleHeart(chatId, m.id, me !in m.hearts)
                return true
            }
            override fun onLongPress(e: MotionEvent) { showMenu(m) }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (m.type == "post") startActivity(Intent(this@chat, reactionpicker::class.java).putExtra("postId", m.postId))
                return true
            }
        })
        bubble.setOnTouchListener { _, ev -> gd.onTouchEvent(ev) }
    }

    private fun bindPostCard(card: LinearLayout, postId: String, mine: Boolean) {
        val author = card.findViewById<TextView>(R.id.cardAuthor)
        val body = card.findViewById<TextView>(R.id.cardText)
        val thumb = card.findViewById<ImageView>(R.id.cardThumb)
        val color = if (mine) Color.WHITE else Color.parseColor("#111111")
        author.setTextColor(color)
        body.setTextColor(color)
        PostRepo.db.child("posts").child(postId).get().addOnSuccessListener { p ->
            if (!p.exists()) { body.text = "Post unavailable"; return@addOnSuccessListener }
            Users.name(p.child("authorUid").getValue(String::class.java) ?: "") { author.text = it }
            body.text = (p.child("text").getValue(String::class.java) ?: "").take(80)
            if ((p.child("imageCount").getValue(Int::class.java) ?: 0) > 0) {
                thumb.visibility = View.VISIBLE
                PostRepo.db.child("postImages").child(postId).child("0").get().addOnSuccessListener { s ->
                    val b64 = s.getValue(String::class.java) ?: return@addOnSuccessListener
                    Img.loadBase64(thumb, "post/$postId/0", b64, 480)
                }
            }
        }
    }

    // ---------------- actions ----------------
    private fun showMenu(m: Msg) {
        val opts = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        opts.add("Reply"); acts.add { setReply(m) }
        val hearted = me in m.hearts
        opts.add(if (hearted) "Remove heart" else "❤ Heart"); acts.add { ChatRepo.toggleHeart(chatId, m.id, !hearted) }

        // Edit / Delete exist only for my own message and only for 5 minutes after sending
        if (m.sender == me && System.currentTimeMillis() - m.createdAt <= ChatRepo.EDIT_WINDOW_MS) {
            if (m.type == "text") { opts.add("Edit"); acts.add { editDialog(m) } }
            opts.add("Delete"); acts.add { confirmDelete(m) }
        }
        AlertDialog.Builder(this).setItems(opts.toTypedArray()) { _, w -> acts[w]() }.show()
    }

    private fun editDialog(m: Msg) {
        val et = EditText(this).apply { setText(m.text); setSelection(text.length) }
        AlertDialog.Builder(this).setTitle("Edit message").setView(et)
            .setPositiveButton("Save") { _, _ ->
                val t = et.text.toString().trim()
                when {
                    t.isEmpty() -> {}
                    System.currentTimeMillis() - m.createdAt > ChatRepo.EDIT_WINDOW_MS ->
                        Toast.makeText(this, "You can only edit within 5 minutes", Toast.LENGTH_SHORT).show()
                    t != m.text -> ChatRepo.edit(chatId, m.id, other, t) {
                        Toast.makeText(this, it ?: "Could not edit", Toast.LENGTH_SHORT).show()
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDelete(m: Msg) {
        AlertDialog.Builder(this).setTitle("Delete this message?")
            .setPositiveButton("Delete") { _, _ ->
                if (System.currentTimeMillis() - m.createdAt > ChatRepo.EDIT_WINDOW_MS) {
                    Toast.makeText(this, "You can only delete within 5 minutes", Toast.LENGTH_SHORT).show()
                } else ChatRepo.delete(chatId, m.id, other) {
                    Toast.makeText(this, it ?: "Could not delete", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    override fun onDestroy() {
        listener?.let { query?.removeEventListener(it) }
        super.onDestroy()
    }
}