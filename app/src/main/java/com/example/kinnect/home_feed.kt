package com.example.kinnect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class home_feed : AppCompatActivity() {

    private var userRef: DatabaseReference? = null
    private var profileListener: ValueEventListener? = null
    private var redirected = false

    private lateinit var composerAvatar: TextView
    private lateinit var storyAvatar: TextView

    private val bgExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var lastPic: String? = null

    // ---- Stories ----
    private var myUid: String = ""
    private var storiesRef: DatabaseReference? = null
    private var storiesListener: ValueEventListener? = null
    private var lastStoriesSnap: DataSnapshot? = null
    private lateinit var storiesRow: LinearLayout

    // ---- Feed Posts ----
    private var postsRef: DatabaseReference? = null
    private var postsListener: ValueEventListener? = null
    private lateinit var feedContainer: LinearLayout
    private val friendUids = mutableSetOf<String>()
    private val cardListeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    private fun trackListener(ref: DatabaseReference, l: ValueEventListener) {
        ref.addValueEventListener(l)
        cardListeners.add(ref to l)
    }

    private fun clearCardListeners() {
        cardListeners.forEach { (ref, l) -> ref.removeEventListener(l) }
        cardListeners.clear()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_feed)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            startActivity(
                Intent(this, Login_activity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }
        myUid = uid

        Session.start()

        composerAvatar = findViewById(R.id.composerAvatar)
        storyAvatar = findViewById(R.id.storyAvatar)
        storiesRow = findViewById(R.id.storiesRow)
        feedContainer = findViewById(R.id.feedContainer)

        val postTitle = findViewById<TextView>(R.id.txt1)
        val commentShare = findViewById<TextView>(R.id.commentshare)
        val commentBtn = findViewById<TextView>(R.id.comment)
        val colors = findViewById<TextView>(R.id.colors)
        val cp = findViewById<ImageView>(R.id.createpost)
        val cam = findViewById<TextView>(R.id.story)
        val btn = findViewById<ImageView>(R.id.searchimg)
        val btn1 = findViewById<ImageView>(R.id.friends)
        val btn2 = findViewById<ImageView>(R.id.img6)
        val btn3 = findViewById<ImageView>(R.id.not1)
        val btn4 = findViewById<ImageView>(R.id.chatbble)
        val btn5 = findViewById<ImageView>(R.id.marktplace)

        boldParts(postTitle, "Lina Marsh is at Clifton Beach", "Lina Marsh", "Clifton Beach")

        val demoCard = findViewById<View>(R.id.postCard)
        findViewById<View>(R.id.dots)?.setOnClickListener { view ->
            showPostOptionsMenu(view, "post_clifton_beach", "demo_user", demoCard)
        }
        findViewById<View>(R.id.cross)?.setOnClickListener {
            demoCard?.visibility = View.GONE
        }

        userRef = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        profileListener = object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                if (redirected || isFinishing || isDestroyed || !snap.exists()) return
                val username = snap.child("username").getValue(String::class.java) ?: ""
                if (username.isBlank()) {
                    redirected = true
                    startActivity(Intent(this@home_feed, editprofile::class.java).putExtra("firstTime", true))
                    finish()
                    return
                }
                val firstName = snap.child("firstName").getValue(String::class.java) ?: ""
                val lastName = snap.child("lastName").getValue(String::class.java) ?: ""
                val fullName = snap.child("fullName").getValue(String::class.java)
                    ?: "$firstName $lastName".trim()
                val pic = snap.child("profilePic").getValue(String::class.java) ?: ""
                showProfile(fullName.ifBlank { username }, pic)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        userRef?.addValueEventListener(profileListener!!)

        StoryRepo.purgeMyExpired()
        listenStories()
        loadFriendsAndListenPosts()

        colors.setOnClickListener {
            startActivity(Intent(this, reactionpicker::class.java).apply {
                putExtra("postId", "post_clifton_beach")
                putExtra("openPicker", true)
            })
        }

        val openDemoComments = {
            startActivity(Intent(this, comments::class.java).apply {
                putExtra("postId", "post_clifton_beach")
            })
        }
        commentShare.setOnClickListener { openDemoComments() }
        commentBtn.setOnClickListener { openDemoComments() }

        cp.setOnClickListener { startActivity(Intent(this, createpost::class.java)) }
        cam.setOnClickListener { startActivity(Intent(this, storyeditor::class.java)) }
        btn.setOnClickListener { startActivity(Intent(this, search::class.java)) }
        btn1.setOnClickListener { startActivity(Intent(this, friends::class.java)) }
        btn2.setOnClickListener { startActivity(Intent(this, menu::class.java)) }
        btn3.setOnClickListener { startActivity(Intent(this, notifications::class.java)) }
        btn4.setOnClickListener { startActivity(Intent(this, chats::class.java)) }
        btn5.setOnClickListener { startActivity(Intent(this, marketplace::class.java)) }
    }

    override fun onStart() {
        super.onStart()
        PresenceManager.setupPresence()
    }

    private fun listenStories() {
        storiesListener?.let { storiesRef?.removeEventListener(it) }
        storiesRef = FirebaseDatabase.getInstance().reference.child("stories")
        storiesListener = object : ValueEventListener {
            override fun onDataChange(root: DataSnapshot) {
                lastStoriesSnap = root
                renderStories()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        storiesRef?.addValueEventListener(storiesListener!!)
    }

    private fun renderStories() {
        val root = lastStoriesSnap ?: return
        if (isFinishing || isDestroyed) return

        if (storiesRow.childCount > 1) {
            storiesRow.removeViews(1, storiesRow.childCount - 1)
        }

        val now = System.currentTimeMillis()
        val authors = root.children.mapNotNull { a ->
            val valid = a.children.filter { s ->
                val exp = s.child("expiresAt").getValue(Long::class.java) ?: 0L
                exp > now && (a.key == myUid || s.child("visibleTo").child(myUid).exists())
            }
            if (valid.isEmpty()) null
            else Pair(a.key!!, valid.maxOf { it.child("createdAt").getValue(Long::class.java) ?: 0L })
        }.sortedWith(compareByDescending<Pair<String, Long>> { it.first == myUid }.thenByDescending { it.second })

        authors.forEach { addStoryTile(it.first) }
    }

    private fun addStoryTile(authorUid: String) {
        val tile = LayoutInflater.from(this).inflate(R.layout.item_story, storiesRow, false)
        val ini = tile.findViewById<TextView>(R.id.storyInitials)
        val name = tile.findViewById<TextView>(R.id.storyName)
        name.text = if (authorUid == myUid) "Your story" else ""
        storiesRow.addView(tile)

        Users.loadUserProfile(authorUid) { fullName, picBase64 ->
            ini.text = initials(fullName)
            if (authorUid != myUid) name.text = fullName
            if (!picBase64.isNullOrEmpty()) {
                bgExecutor.execute {
                    val bmp = decodePicture(picBase64)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && bmp != null) {
                            ini.background = circle(bmp)
                            ini.text = ""
                        }
                    }
                }
            }
        }

        tile.setOnClickListener {
            startActivity(Intent(this, yourstory::class.java).putExtra("authorUid", authorUid))
        }
    }

    private fun loadFriendsAndListenPosts() {
        friendUids.clear()
        friendUids.add(myUid)

        FriendsRepo.load { uids ->
            friendUids.addAll(uids)
            listenPosts()
        }
    }

    private fun listenPosts() {
        postsListener?.let { postsRef?.removeEventListener(it) }
        postsRef = FirebaseDatabase.getInstance().reference.child("posts")
        postsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                renderPosts(snapshot)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        postsRef?.addValueEventListener(postsListener!!)
    }

    private fun renderPosts(snapshot: DataSnapshot) {
        if (isFinishing || isDestroyed) return
        clearCardListeners()
        feedContainer.removeAllViews()

        val postList = mutableListOf<Pair<String, DataSnapshot>>()

        for (item in snapshot.children) {
            val authorUid = item.child("authorUid").getValue(String::class.java) ?: ""
            if (friendUids.contains(authorUid) || authorUid == myUid) {
                postList.add(Pair(item.key ?: "", item))
            }
        }

        postList.sortByDescending { it.second.child("createdAt").getValue(Long::class.java) ?: 0L }

        for ((postId, postSnap) in postList) {
            addPostCard(postId, postSnap)
        }
    }

    private fun addPostCard(postId: String, s: DataSnapshot) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_post_card, feedContainer, false)
        feedContainer.addView(view)

        val avatar = view.findViewById<TextView>(R.id.postAvatar)
        val authorTv = view.findViewById<TextView>(R.id.postAuthor)
        val timeTv = view.findViewById<TextView>(R.id.postTime)
        val captionTv = view.findViewById<TextView>(R.id.postCaption)
        val reactionSummaryTv = view.findViewById<TextView>(R.id.postReactionSummary)
        val commentsCountTv = view.findViewById<TextView>(R.id.postCommentsCount)
        val btnLike = view.findViewById<TextView>(R.id.btnPostLike)
        val btnComment = view.findViewById<TextView>(R.id.btnPostComment)
        val postDots = view.findViewById<TextView>(R.id.postDots)
        val postCross = view.findViewById<TextView>(R.id.postCross)

        val authorUid = s.child("authorUid").getValue(String::class.java) ?: ""

        Users.loadUserProfile(authorUid) { name, picBase64 ->
            authorTv.text = name
            if (!picBase64.isNullOrEmpty()) {
                bgExecutor.execute {
                    val bmp = decodePicture(picBase64)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && bmp != null) {
                            avatar.background = circle(bmp)
                            avatar.text = ""
                        } else {
                            avatar.text = initials(name)
                        }
                    }
                }
            } else {
                avatar.text = initials(name)
            }
        }

        val createdAt = s.child("createdAt").getValue(Long::class.java) ?: 0L
        timeTv.text = "${PostRepo.timeAgo(createdAt)} · 🌐"

        val caption = s.child("text").getValue(String::class.java) ?: ""
        captionTv.text = caption
        captionTv.visibility = if (caption.isBlank()) View.GONE else View.VISIBLE

        val imageCount = s.child("imageCount").getValue(Int::class.java) ?: 0
        bindImages(view, postId, imageCount)

        postDots?.setOnClickListener { anchor ->
            showPostOptionsMenu(anchor, postId, authorUid, view)
        }

        postCross?.setOnClickListener {
            view.visibility = View.GONE
        }

        trackListener(FirebaseDatabase.getInstance().reference.child("reactions").child(postId),
            object : ValueEventListener {
                override fun onDataChange(reactSnap: DataSnapshot) {
                    val summary = ReactionUi.summarize(reactSnap, myUid)
                    reactionSummaryTv.text = summary.text
                    if (summary.mine != null) {
                        btnLike.text = "${Reactions.emoji(summary.mine)} ${Reactions.label(summary.mine)}"
                        btnLike.setTextColor(0xFF087F7B.toInt())
                    } else {
                        btnLike.text = "👍 Like"
                        btnLike.setTextColor(0xFF444444.toInt())
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        trackListener(FirebaseDatabase.getInstance().reference.child("comments").child(postId),
            object : ValueEventListener {
                override fun onDataChange(commSnap: DataSnapshot) {
                    val count = commSnap.childrenCount
                    commentsCountTv.text = "$count comments"
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        val openPicker = {
            startActivity(Intent(this, reactionpicker::class.java).apply {
                putExtra("postId", postId)
                putExtra("openPicker", true)
            })
        }

        btnLike.setOnClickListener { openPicker() }
        btnLike.setOnLongClickListener { openPicker(); true }
        reactionSummaryTv.setOnClickListener { openPicker() }

        val openComments = {
            startActivity(Intent(this, comments::class.java).apply {
                putExtra("postId", postId)
            })
        }
        btnComment.setOnClickListener { openComments() }
        commentsCountTv.setOnClickListener { openComments() }
    }

    private fun showPostOptionsMenu(anchor: View, postId: String, authorUid: String, cardView: View?) {
        val popup = PopupMenu(this, anchor)

        if (authorUid == myUid) {
            popup.menu.add(0, 1, 0, "Delete Post")
            popup.menu.add(0, 2, 1, "Copy Link")
        } else {
            popup.menu.add(0, 3, 0, "Hide Post")
            popup.menu.add(0, 4, 1, "Report Post")
            popup.menu.add(0, 2, 2, "Copy Link")
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val db = FirebaseDatabase.getInstance().reference
                    db.child("posts").child(postId).removeValue()
                    db.child("postImages").child(postId).removeValue()
                    db.child("reactions").child(postId).removeValue()
                    db.child("comments").child(postId).removeValue()
                    cardView?.visibility = View.GONE
                    Toast.makeText(this, "Post deleted", Toast.LENGTH_SHORT).show()
                    true
                }
                2 -> {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Post Link", "https://kinnect.app/post/$postId")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Post link copied to clipboard", Toast.LENGTH_SHORT).show()
                    true
                }
                3 -> {
                    cardView?.visibility = View.GONE
                    Toast.makeText(this, "Post hidden from feed", Toast.LENGTH_SHORT).show()
                    true
                }
                4 -> {
                    Toast.makeText(this, "Post reported. Thank you.", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun bindImages(cardView: View, postId: String, imageCount: Int) {
        val gridContainer = cardView.findViewById<LinearLayout>(R.id.gridContainer)
        val img1 = cardView.findViewById<ImageView>(R.id.pImg1)
        val img2 = cardView.findViewById<ImageView>(R.id.pImg2)
        val img3 = cardView.findViewById<ImageView>(R.id.pImg3)
        val rightColumn = img2.parent as? LinearLayout
        val frame3 = img3.parent as? FrameLayout
        val moreOverlay = cardView.findViewById<TextView>(R.id.pMorePhotos)

        if (imageCount == 0) {
            gridContainer.visibility = View.GONE
            return
        }

        gridContainer.visibility = View.VISIBLE

        when (imageCount) {
            1 -> {
                rightColumn?.visibility = View.GONE
                img1.visibility = View.VISIBLE
            }
            2 -> {
                rightColumn?.visibility = View.VISIBLE
                img1.visibility = View.VISIBLE
                img2.visibility = View.VISIBLE
                frame3?.visibility = View.GONE
            }
            else -> {
                rightColumn?.visibility = View.VISIBLE
                img1.visibility = View.VISIBLE
                img2.visibility = View.VISIBLE
                frame3?.visibility = View.VISIBLE
            }
        }

        val imgNodes = listOf(img1 to "0", img2 to "1", img3 to "2")
        imgNodes.forEachIndexed { index, (imageView, key) ->
            if (index < imageCount) {
                PostRepo.db.child("postImages").child(postId).child(key).get()
                    .addOnSuccessListener { snap ->
                        val b64 = snap.getValue(String::class.java) ?: return@addOnSuccessListener
                        Img.loadBase64(imageView, "post/$postId/$key", b64, 720)
                    }
            }
        }

        if (imageCount > 3) {
            moreOverlay.visibility = View.VISIBLE
            moreOverlay.text = "+${imageCount - 3}"
        } else {
            moreOverlay.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        renderStories()
        loadFriendsAndListenPosts()
    }

    private fun boldParts(tv: TextView, full: String, vararg parts: String) {
        val sp = SpannableString(full)
        for (part in parts) {
            val start = full.indexOf(part)
            if (start >= 0) {
                sp.setSpan(StyleSpan(Typeface.BOLD), start, start + part.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        tv.text = sp
    }

    private fun showProfile(fullName: String, picBase64: String) {
        if (picBase64 == lastPic) return
        lastPic = picBase64

        val ini = initials(fullName)
        composerAvatar.text = ini
        storyAvatar.text = ini
        if (picBase64.isEmpty()) return

        bgExecutor.execute {
            val bmp = decodePicture(picBase64)
            runOnUiThread {
                if (isFinishing || isDestroyed || bmp == null) return@runOnUiThread
                composerAvatar.background = circle(bmp)
                composerAvatar.text = ""
                storyAvatar.background = circle(bmp)
                storyAvatar.text = ""
            }
        }
    }

    private fun circle(bmp: Bitmap) =
        RoundedBitmapDrawableFactory.create(resources, bmp).apply { isCircular = true }

    private fun decodePicture(base64: String): Bitmap? = try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val size = minOf(bmp.width, bmp.height)
        Bitmap.createBitmap(bmp, (bmp.width - size) / 2, (bmp.height - size) / 2, size, size)
    } catch (e: Exception) {
        null
    }

    private fun initials(name: String): String =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.take(1).uppercase() }

    override fun onDestroy() {
        profileListener?.let { userRef?.removeEventListener(it) }
        storiesListener?.let { storiesRef?.removeEventListener(it) }
        postsListener?.let { postsRef?.removeEventListener(it) }
        clearCardListeners()
        bgExecutor.shutdown()
        super.onDestroy()
    }
}