package com.example.kinnect


import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

/** Screen 20: real conversations from userChats/{me}: last message, time, unread indicator. */
class chats : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var empty: View
    private lateinit var search: EditText
    private var me = ""
    private var snap: DataSnapshot? = null
    private var listener: ValueEventListener? = null
    private val ref get() = PostRepo.db.child("userChats").child(me)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chats)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
        me = PostRepo.me() ?: run { finish(); return }

        container = findViewById(R.id.chatsContainer)
        empty = findViewById(R.id.chatsEmpty)
        search = findViewById(R.id.chatSearch)

        findViewById<View>(R.id.chatsBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnNewChat).setOnClickListener {
            ChatUi.pickFriend(this, "New message") { uid, _ -> openChat(uid) }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applyFilter() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { snap = s; render() }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(listener!!)
    }

    private fun openChat(uid: String) =
        startActivity(Intent(this, chat::class.java).putExtra("otherUid", uid))

    private fun render() {
        val s = snap ?: return
        if (isFinishing || isDestroyed) return
        container.removeAllViews()

        val rows = s.children.filter {
            !(it.child("otherUid").getValue(String::class.java).isNullOrBlank()) &&
                    (it.child("lastTime").getValue(Long::class.java) ?: 0L) > 0L
        }.sortedByDescending { it.child("lastTime").getValue(Long::class.java) ?: 0L }

        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

        rows.forEach { r ->
            val other = r.child("otherUid").getValue(String::class.java) ?: return@forEach
            val last = r.child("lastMessage").getValue(String::class.java) ?: ""
            val time = r.child("lastTime").getValue(Long::class.java) ?: 0L
            val sender = r.child("lastSender").getValue(String::class.java) ?: ""
            val unread = r.child("unread").getValue(Int::class.java) ?: 0

            val v = LayoutInflater.from(this).inflate(R.layout.item_chat, container, false)
            val avatar = v.findViewById<TextView>(R.id.chAvatar)
            val nameTv = v.findViewById<TextView>(R.id.chName)
            val lastTv = v.findViewById<TextView>(R.id.chLast)
            val timeTv = v.findViewById<TextView>(R.id.chTime)
            val dot = v.findViewById<View>(R.id.chDot)

            avatar.backgroundTintList = ColorStateList.valueOf(ChatUi.avatarColor(other))
            lastTv.text = (if (sender == me) "You: " else "") + last
            timeTv.text = PostRepo.timeAgo(time)

            val isUnread = unread > 0 && sender != me
            dot.visibility = if (isUnread) View.VISIBLE else View.INVISIBLE
            val style = if (isUnread) Typeface.BOLD else Typeface.NORMAL
            lastTv.setTypeface(null, style)
            nameTv.setTypeface(null, style)
            lastTv.setTextColor(if (isUnread) 0xFF111111.toInt() else 0xFF666666.toInt())

            Users.name(other) { n ->
                nameTv.text = n
                avatar.text = Users.initials(n)
                v.tag = n
                applyFilter()
            }
            v.setOnClickListener { openChat(other) }
            container.addView(v)
        }
    }

    private fun applyFilter() {
        val q = search.text.toString().trim().lowercase()
        for (i in 0 until container.childCount) {
            val c = container.getChildAt(i)
            val n = (c.tag as? String)?.lowercase() ?: ""
            c.visibility = if (q.isEmpty() || n.contains(q)) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroy() {
        listener?.let { ref.removeEventListener(it) }
        super.onDestroy()
    }
}