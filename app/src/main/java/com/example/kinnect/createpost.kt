package com.example.kinnect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class createpost : AppCompatActivity() {

    private val selectedImageUris = mutableListOf<Uri>()
    private lateinit var postInput: EditText
    private lateinit var btnPost: TextView
    private lateinit var selectedImagesText: TextView
    private lateinit var previewContainer: LinearLayout
    private lateinit var authorNameTv: TextView
    private lateinit var authorAvatarTv: TextView

    private val bgExecutor = Executors.newSingleThreadExecutor()

    // Android Modern Native Photo Picker Launcher (Safe & Crash-proof)
    private val pickMultipleMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                if (!selectedImageUris.contains(uri)) {
                    selectedImageUris.add(uri)
                }
            }
            updateImagePreviews()
        }
    }

    // Fallback Gallery Launcher for older Android devices
    private val systemGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                if (!selectedImageUris.contains(uri)) {
                    selectedImageUris.add(uri)
                }
            }
            updateImagePreviews()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_createpost)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            finish()
            return
        }

        postInput = findViewById(R.id.postInput)
        btnPost = findViewById(R.id.btnPost)
        selectedImagesText = findViewById(R.id.selectedImagesText)
        previewContainer = findViewById(R.id.previewContainer)
        authorNameTv = findViewById(R.id.authorName)
        authorAvatarTv = findViewById(R.id.authorAvatar)

        Users.name(uid) { name ->
            authorNameTv.text = name
            authorAvatarTv.text = Users.initials(name)
        }

        // Back Button
        findViewById<View>(R.id.back)?.setOnClickListener {
            finish()
        }

        // Gallery Button
        findViewById<View>(R.id.photopicker)?.setOnClickListener {
            openGalleryPicker()
        }

        // Camera Button
        findViewById<View>(R.id.camera)?.setOnClickListener {
            openGalleryPicker()
        }

        // Post Button
        btnPost.setOnClickListener {
            publishPost(uid)
        }
    }

    private fun openGalleryPicker() {
        try {
            pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (e: Exception) {
            try {
                systemGalleryLauncher.launch("image/*")
            } catch (ex: Exception) {
                Toast.makeText(this, "Could not open photo picker", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateImagePreviews() {
        previewContainer.removeAllViews()
        if (selectedImageUris.isEmpty()) {
            selectedImagesText.visibility = View.GONE
            previewContainer.visibility = View.GONE
            return
        }

        selectedImagesText.visibility = View.VISIBLE
        selectedImagesText.text = "${selectedImageUris.size} media item(s) attached"
        previewContainer.visibility = View.VISIBLE

        val density = resources.displayMetrics.density
        val sizePx = (84 * density).toInt()

        selectedImageUris.take(4).forEach { uri ->
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(0, 0, (8 * density).toInt(), 0)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
                background = ContextCompat.getDrawable(this@createpost, R.drawable.bg_bubble)
                clipToOutline = true
            }
            previewContainer.addView(imageView)
        }

        if (selectedImageUris.size > 4) {
            val extraCount = selectedImageUris.size - 4
            val extraTv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
                text = "+$extraCount"
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(0x88000000.toInt())
            }
            previewContainer.addView(extraTv)
        }
    }

    private fun publishPost(uid: String) {
        val text = postInput.text.toString().trim()

        if (text.isEmpty() && selectedImageUris.isEmpty()) {
            Toast.makeText(this, "Please enter caption text or attach media", Toast.LENGTH_SHORT).show()
            return
        }

        btnPost.isEnabled = false
        btnPost.alpha = 0.5f

        val db = FirebaseDatabase.getInstance().reference
        val postRef = db.child("posts").push()
        val postId = postRef.key ?: return

        bgExecutor.execute {
            val encodedImages = mutableListOf<String>()
            selectedImageUris.forEach { uri ->
                uriToBase64(this, uri)?.let { encodedImages.add(it) }
            }

            runOnUiThread {
                val postMap = hashMapOf<String, Any>(
                    "authorUid" to uid,
                    "text" to text,
                    "createdAt" to System.currentTimeMillis(),
                    "imageCount" to encodedImages.size,
                    "commentCount" to 0
                )

                postRef.setValue(postMap).addOnSuccessListener {
                    if (encodedImages.isNotEmpty()) {
                        val imagesMap = hashMapOf<String, Any>()
                        encodedImages.forEachIndexed { index, b64 ->
                            imagesMap[index.toString()] = b64
                        }
                        db.child("postImages").child(postId).setValue(imagesMap)
                    }

                    Toast.makeText(this, "Post published!", Toast.LENGTH_SHORT).show()
                    finish()
                }.addOnFailureListener { e ->
                    btnPost.isEnabled = true
                    btnPost.alpha = 1.0f
                    Toast.makeText(this, "Failed to publish: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            val outputStream = ByteArrayOutputStream()

            val maxDim = 1080
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

            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroy() {
        bgExecutor.shutdown()
        super.onDestroy()
    }
}