package com.example.kinnect


import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class comments : AppCompatActivity() {

    private var postId = ""
    private var me = ""

    private var replyToId = ""
    private var attach: Uri? = null

    private lateinit var container: LinearLayout
    private lateinit var input: EditText
    private lateinit var btnSend: View
    private lateinit var btnFilter: TextView
    private lateinit var adapter: CommentsAdapter

    private var currentFilter = CommentsAdapter.FilterMode.MOST_RELEVANT

    private val commentsRef
        get() = PostRepo.db.child("comments").child(postId)

    private val likesRef
        get() = PostRepo.db.child("commentLikes").child(postId)

    private val reactRef
        get() = PostRepo.db.child("reactions").child(postId)

    private var commentsListener: ValueEventListener? = null
    private var likesListener: ValueEventListener? = null
    private var reactionsListener: ValueEventListener? = null

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                attach = uri
                findViewById<View>(R.id.attachRow).visibility = View.VISIBLE
                Img.loadUri(this, findViewById(R.id.attachThumb), uri, 200)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_comments)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                maxOf(bars.bottom, keyboard.bottom)
            )
            insets
        }

        postId = intent.getStringExtra("postId") ?: ""
        me = PostRepo.me() ?: ""

        if (postId.isBlank() || me.isBlank()) {
            finish()
            return
        }

        container = findViewById(R.id.commentsContainer)
        input = findViewById(R.id.commentInput)
        btnSend = findViewById(R.id.btnSend)
        btnFilter = findViewById(R.id.btnFilterComments)

        adapter = CommentsAdapter(
            activity = this,
            postId = postId,
            myUid = me,
            container = container,
            onReply = { rootId, name ->
                setReply(rootId, name)
            }
        )

        findViewById<View>(R.id.less).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.camerabtn).setOnClickListener {
            pickImage.launch("image/*")
        }

        findViewById<View>(R.id.attachRemove).setOnClickListener {
            clearAttach()
        }

        findViewById<View>(R.id.replyCancel).setOnClickListener {
            clearReply()
        }

        btnSend.setOnClickListener {
            send()
        }

        btnFilter.setOnClickListener { view ->
            showFilterMenu(view)
        }

        listenForComments()
        listenForLikes()
        listenForReactions()
    }

    private fun showFilterMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Most relevant")
        popup.menu.add(0, 2, 1, "Less important / Recent")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    currentFilter = CommentsAdapter.FilterMode.MOST_RELEVANT
                    btnFilter.text = "Most relevant ∨"
                    adapter.setFilterMode(currentFilter)
                    true
                }
                2 -> {
                    currentFilter = CommentsAdapter.FilterMode.LESS_IMPORTANT
                    btnFilter.text = "Less important ∨"
                    adapter.setFilterMode(currentFilter)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun listenForComments() {
        commentsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = ArrayList<CommentsAdapter.Comment>()

                for (item in snapshot.children) {
                    val id = item.key ?: continue

                    list.add(
                        CommentsAdapter.Comment(
                            id = id,
                            authorUid = item.child("authorUid").getValue(String::class.java) ?: "",
                            text = item.child("text").getValue(String::class.java) ?: "",
                            parentId = item.child("parentId").getValue(String::class.java) ?: "",
                            createdAt = item.child("createdAt").getValue(Long::class.java) ?: 0L,
                            hasImage = item.child("hasImage").getValue(Boolean::class.java) ?: false
                        )
                    )
                }
                adapter.submitComments(list)
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Could not load comments")
            }
        }
        commentsRef.addValueEventListener(commentsListener!!)
    }

    private fun listenForLikes() {
        likesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                adapter.submitLikes(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                showError("Could not load comment likes")
            }
        }
        likesRef.addValueEventListener(likesListener!!)
    }

    private fun listenForReactions() {
        reactionsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                findViewById<TextView>(R.id.commentsReactText).text =
                    ReactionUi.summarize(snapshot, me).text
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        reactRef.addValueEventListener(reactionsListener!!)
    }

    private fun send() {
        val text = input.text.toString().trim()

        if (text.isEmpty() && attach == null) return

        btnSend.isEnabled = false

        PostRepo.addComment(
            this,
            postId,
            text,
            replyToId,
            attach
        ) { success, error ->
            btnSend.isEnabled = true
            if (success) {
                input.setText("")
                clearReply()
                clearAttach()
            } else {
                showError(error ?: "Failed to comment")
            }
        }
    }

    private fun setReply(rootId: String, name: String) {
        replyToId = rootId
        findViewById<View>(R.id.replyBar).visibility = View.VISIBLE
        findViewById<TextView>(R.id.replyText).text = "Replying to $name"
        input.hint = "Write a reply..."
        input.requestFocus()
    }

    private fun clearReply() {
        replyToId = ""
        findViewById<View>(R.id.replyBar).visibility = View.GONE
        input.hint = "Write a comment..."
    }

    private fun clearAttach() {
        attach = null
        findViewById<View>(R.id.attachRow).visibility = View.GONE
        findViewById<ImageView>(R.id.attachThumb).setImageDrawable(null)
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        commentsListener?.let { commentsRef.removeEventListener(it) }
        likesListener?.let { likesRef.removeEventListener(it) }
        reactionsListener?.let { reactRef.removeEventListener(it) }
        super.onDestroy()
    }
}