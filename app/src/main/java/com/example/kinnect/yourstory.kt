package com.example.kinnect


import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.util.concurrent.Executors


class yourstory : AppCompatActivity() {

    private data class Story(val id: String, val type: String, val label: String, val createdAt: Long)

    private val db = FirebaseDatabase.getInstance().reference
    private val bg = Executors.newSingleThreadExecutor()
    private val stories = mutableListOf<Story>()
    private val bars = mutableListOf<ProgressBar>()
    private var index = 0
    private var anim: ObjectAnimator? = null
    private var token = 0                      // invalidates stale async loads

    private lateinit var me: String
    private lateinit var author: String
    private val isMine get() = author == me

    private lateinit var image: ImageView
    private lateinit var video: VideoView
    private lateinit var sticker: TextView
    private lateinit var metaTv: TextView
    private lateinit var seenCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_yourstory)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }

        me = FirebaseAuth.getInstance().currentUser?.uid ?: run { finish(); return }
        author = intent.getStringExtra("authorUid") ?: me

        image = findViewById(R.id.storyImage)
        video = findViewById(R.id.storyVideo)
        sticker = findViewById(R.id.stickerText)
        metaTv = findViewById(R.id.storyMeta)
        seenCount = findViewById(R.id.seenCount)

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<View>(R.id.tapNext).setOnClickListener { show(index + 1) }
        findViewById<View>(R.id.tapPrev).setOnClickListener { show(index - 1) }
        findViewById<View>(R.id.btnCreate).setOnClickListener {
            startActivity(Intent(this, storyeditor::class.java)); finish()
        }
        findViewById<View>(R.id.btnMore).setOnClickListener { if (isMine) confirmDelete() }
        findViewById<View>(R.id.btnSeenBy).visibility = if (isMine) View.VISIBLE else View.INVISIBLE

        loadHeader()
        loadStories()
    }

    private fun loadHeader() {
        val name = findViewById<TextView>(R.id.storyName)
        val avatar = findViewById<TextView>(R.id.avatar)
        db.child("users").child(author).get().addOnSuccessListener { u ->
            val full = u.child("fullName").getValue(String::class.java)
                ?: u.child("username").getValue(String::class.java) ?: ""
            name.text = if (isMine) "Your story" else full
            avatar.text = full.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                .take(2).joinToString("") { it.take(1).uppercase() }
        }
    }

    private fun loadStories() {
        db.child("stories").child(author).get().addOnSuccessListener { snap ->
            val now = System.currentTimeMillis()
            snap.children.forEach { s ->
                val exp = s.child("expiresAt").getValue(Long::class.java) ?: 0L
                val allowed = isMine || s.child("visibleTo").child(me).exists()
                if (exp > now && allowed) stories.add(
                    Story(
                        s.key!!,
                        s.child("type").getValue(String::class.java) ?: "image",
                        s.child("label").getValue(String::class.java) ?: "",
                        s.child("createdAt").getValue(Long::class.java) ?: 0L
                    )
                )
            }
            stories.sortBy { it.createdAt }
            if (stories.isEmpty()) {
                Toast.makeText(this, "No active stories", Toast.LENGTH_SHORT).show(); finish(); return@addOnSuccessListener
            }
            buildBars()
            show(0)
        }.addOnFailureListener { finish() }
    }

    private fun buildBars() {
        val box = findViewById<LinearLayout>(R.id.progressContainer)
        box.removeAllViews(); bars.clear()
        stories.forEachIndexed { i, _ ->
            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000; progress = 0
                progressTintList = ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = ColorStateList.valueOf(0x60FFFFFF)
            }
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            if (i > 0) lp.marginStart = 4
            box.addView(pb, lp); bars.add(pb)
        }
    }

    private fun show(i: Int) {
        anim?.cancel(); video.stopPlayback()
        if (i >= stories.size) { finish(); return }
        index = maxOf(i, 0)
        val my = ++token
        val s = stories[index]

        bars.forEachIndexed { k, b -> b.progress = if (k < index) 1000 else 0 }
        sticker.text = s.label
        sticker.visibility = if (s.label.isBlank()) View.GONE else View.VISIBLE
        metaTv.text = timeAgo(s.createdAt)

        if (isMine) {
            db.child("stories").child(author).child(s.id).child("seenBy").get()
                .addOnSuccessListener { seenCount.text = "Seen by ${it.childrenCount}" }
        } else {
            db.child("stories").child(author).child(s.id).child("seenBy").child(me).setValue(true)
        }

        db.child("storyMedia").child(s.id).get().addOnSuccessListener { m ->
            val b64 = m.getValue(String::class.java)
            if (b64 == null) { show(index + 1); return@addOnSuccessListener }
            bg.execute {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                if (s.type == "video") {
                    val f = File(cacheDir, "story_${s.id}.mp4").also { it.writeBytes(bytes) }
                    runOnUiThread { if (my == token && !isDestroyed) playVideo(f.absolutePath) }
                } else {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    runOnUiThread { if (my == token && !isDestroyed) showImage(bmp) }
                }
            }
        }.addOnFailureListener { show(index + 1) }
    }

    private fun showImage(bmp: android.graphics.Bitmap?) {
        video.visibility = View.GONE; image.visibility = View.VISIBLE
        image.setImageBitmap(bmp)
        runProgress(5000)
    }

    private fun playVideo(path: String) {
        image.visibility = View.GONE; video.visibility = View.VISIBLE
        video.setOnPreparedListener { mp -> video.start(); runProgress(mp.duration.toLong().coerceAtLeast(1000)) }
        video.setOnErrorListener { _, _, _ -> show(index + 1); true }
        video.setVideoPath(path)
    }

    private fun runProgress(durationMs: Long) {
        anim?.cancel()
        val bar = bars.getOrNull(index) ?: return
        val target = index
        anim = ObjectAnimator.ofInt(bar, "progress", 0, 1000).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(a: Animator) { cancelled = true }
                override fun onAnimationEnd(a: Animator) { if (!cancelled && target == index) show(index + 1) }
            })
            start()
        }
    }

    private fun confirmDelete() {
        val s = stories.getOrNull(index) ?: return
        AlertDialog.Builder(this).setTitle("Delete this story?")
            .setPositiveButton("Delete") { _, _ ->
                db.child("storyMedia").child(s.id).removeValue()
                db.child("stories").child(author).child(s.id).removeValue()
                finish()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun timeAgo(t: Long): String {
        val m = (System.currentTimeMillis() - t) / 60000
        return if (m < 1) "now" else if (m < 60) "${m}m" else "${m / 60}h"
    }

    override fun onPause() { super.onPause(); anim?.pause(); if (video.isPlaying) video.pause() }
    override fun onResume() { super.onResume(); anim?.resume(); if (video.visibility == View.VISIBLE) video.start() }
    override fun onDestroy() { anim?.cancel(); bg.shutdown(); super.onDestroy() }
}