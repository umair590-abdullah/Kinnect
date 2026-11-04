package com.example.kinnect



import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class storyeditor : AppCompatActivity() {

    private var mediaUri: Uri? = null
    private var isVideo = false
    private lateinit var preview: ImageView
    private lateinit var video: VideoView
    private lateinit var sticker: TextView
    private var posting = false

    // Gallery fallback (also used by Camera screen if it launches us without a uri)
    private val pick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) { if (mediaUri == null) finish() } else setMedia(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_storyeditor)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom); insets
        }

        preview = findViewById(R.id.storyPreview)
        video = findViewById(R.id.storyVideo)
        sticker = findViewById(R.id.stickerText)
        sticker.text = ""; sticker.visibility = View.GONE

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<View>(R.id.btnText).setOnClickListener { editLabel() }
        sticker.setOnClickListener { editLabel() }
        findViewById<View>(R.id.btnYourStory).setOnClickListener { share(false) }
        findViewById<View>(R.id.btnCloseFriends).setOnClickListener { share(true) }
        findViewById<View>(R.id.btnSend).setOnClickListener { share(false) }

        val passed = intent.getStringExtra("mediaUri")
        if (passed != null) setMedia(Uri.parse(passed)) else pick.launch("*/*")
    }

    private fun setMedia(uri: Uri) {
        mediaUri = uri
        val type = contentResolver.getType(uri) ?: ""
        isVideo = type.startsWith("video") || intent.getBooleanExtra("isVideo", false)
        if (isVideo) {
            preview.visibility = View.GONE; video.visibility = View.VISIBLE
            video.setVideoURI(uri)
            video.setOnPreparedListener { it.isLooping = true; video.start() }
        } else {
            video.visibility = View.GONE; preview.visibility = View.VISIBLE
            preview.setImageURI(uri)
        }
    }

    private fun editLabel() {
        val input = EditText(this).apply { setText(sticker.text); hint = "Add text" }
        AlertDialog.Builder(this).setTitle("Story text").setView(input)
            .setPositiveButton("OK") { _, _ ->
                val t = input.text.toString().trim()
                sticker.text = t
                sticker.visibility = if (t.isEmpty()) View.GONE else View.VISIBLE
            }.setNegativeButton("Cancel", null).show()
    }

    private fun share(closeFriendsOnly: Boolean) {
        val uri = mediaUri ?: return Toast.makeText(this, "Pick media first", Toast.LENGTH_SHORT).show()
        if (posting) return
        posting = true
        Toast.makeText(this, "Sharing…", Toast.LENGTH_SHORT).show()
        StoryRepo.post(this, uri, isVideo, sticker.text.toString(), closeFriendsOnly) { ok, err ->
            runOnUiThread {
                posting = false
                if (ok) {
                    startActivity(Intent(this, home_feed::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    finish()
                } else Toast.makeText(this, err ?: "Failed to share", Toast.LENGTH_LONG).show()
            }
        }
    }
}