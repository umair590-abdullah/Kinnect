package com.example.kinnect


import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class reactionpicker : AppCompatActivity() {

    private var postId = ""
    private var me = ""
    private var myReaction: String? = null
    private var imageLoaded = false

    private val reactRef get() = PostRepo.db.child("reactions").child(postId)
    private val postRef get() = PostRepo.db.child("posts").child(postId)
    private var reactListener: ValueEventListener? = null
    private var postListener: ValueEventListener? = null

    private lateinit var pickerRow: LinearLayout
    private lateinit var bottom: View
    private lateinit var btnLike: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_reactionpicker)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        postId = intent.getStringExtra("postId") ?: run { finish(); return }
        me = PostRepo.me() ?: run { finish(); return }

        pickerRow = findViewById(R.id.reactionPickerRow)
        bottom = findViewById(R.id.bottomSection)
        btnLike = findViewById(R.id.btnLike)

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        buildPicker()

        btnLike.setOnClickListener { PostRepo.setReaction(postId, if (myReaction == null) "like" else null) }
        btnLike.setOnLongClickListener { showPicker(); true }
        findViewById<View>(R.id.reactionSummary).setOnClickListener { showPicker() }
        val openComments = View.OnClickListener {
            startActivity(Intent(this, comments::class.java).putExtra("postId", postId))
        }
        findViewById<View>(R.id.btnComment).setOnClickListener(openComments)
        findViewById<View>(R.id.btnViewComments).setOnClickListener(openComments)

        postListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { if (s.exists()) bindPost(s) }
            override fun onCancelled(e: DatabaseError) {}
        }
        postRef.addValueEventListener(postListener!!)

        reactListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val sum = ReactionUi.summarize(s, me)
                myReaction = sum.mine
                findViewById<TextView>(R.id.summaryText).text = sum.text
                if (sum.mine == null) {
                    btnLike.text = "👍  Like"
                    btnLike.setTextColor(Color.parseColor("#555555"))
                } else {
                    btnLike.text = "${Reactions.emoji(sum.mine)}  ${Reactions.label(sum.mine)}"
                    btnLike.setTextColor(Color.parseColor("#087F7B"))
                }
                for (i in 0 until pickerRow.childCount) {
                    val c = pickerRow.getChildAt(i)
                    c.setBackgroundColor(if (c.tag == sum.mine) 0x33087F7B else 0)
                }
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        reactRef.addValueEventListener(reactListener!!)

        if (intent.getBooleanExtra("openPicker", false)) showPicker()
    }

    private fun buildPicker() {
        val d = resources.displayMetrics.density
        Reactions.keys.forEach { k ->
            val tv = TextView(this).apply {
                text = Reactions.emoji(k)
                textSize = 30f
                gravity = Gravity.CENTER
                tag = k
                setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
                setOnClickListener {
                    PostRepo.setReaction(postId, if (myReaction == k) null else k)
                    hidePicker()
                }
            }
            pickerRow.addView(tv)
        }
    }

    private fun showPicker() { pickerRow.visibility = View.VISIBLE; bottom.alpha = 0.35f }
    private fun hidePicker() { pickerRow.visibility = View.GONE; bottom.alpha = 1f }

    private fun bindPost(s: DataSnapshot) {
        val author = s.child("authorUid").getValue(String::class.java) ?: ""
        Users.name(author) { n ->
            findViewById<TextView>(R.id.rAuthor).text = n
            findViewById<TextView>(R.id.rAvatar).text = Users.initials(n)
            findViewById<TextView>(R.id.rTitle).text = "$n's post"
        }
        findViewById<TextView>(R.id.rTime).text =
            "${PostRepo.timeAgo(s.child("createdAt").getValue(Long::class.java) ?: 0L)} · 🌐"
        val text = s.child("text").getValue(String::class.java) ?: ""
        findViewById<TextView>(R.id.rCaption).apply {
            this.text = text; visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        val cc = s.child("commentCount").getValue(Int::class.java) ?: 0
        findViewById<TextView>(R.id.commentsCountTv).text = "$cc comment${if (cc == 1) "" else "s"}"

        val photo = findViewById<ImageView>(R.id.rPhoto)
        if ((s.child("imageCount").getValue(Int::class.java) ?: 0) == 0) {
            photo.visibility = View.GONE
        } else if (!imageLoaded) {
            imageLoaded = true
            photo.visibility = View.VISIBLE
            PostRepo.db.child("postImages").child(postId).child("0").get().addOnSuccessListener { snap ->
                val b64 = snap.getValue(String::class.java) ?: return@addOnSuccessListener
                Img.loadBase64(photo, "post/$postId/0", b64, 1080)
            }
        }
    }

    override fun onDestroy() {
        reactListener?.let { reactRef.removeEventListener(it) }
        postListener?.let { postRef.removeEventListener(it) }
        super.onDestroy()
    }
}