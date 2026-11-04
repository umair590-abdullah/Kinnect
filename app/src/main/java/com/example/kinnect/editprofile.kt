package com.example.kinnect

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import java.io.ByteArrayOutputStream

class editprofile : AppCompatActivity() {

    private var profilePicBase64 = ""
    private var coverPhotoBase64 = ""
    private var isFirstTime = false
    private var oldUsernameLower = ""

    private lateinit var etName: EditText
    private lateinit var etUsername: EditText
    private lateinit var etBio: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etGender: EditText

    private lateinit var btnSave: TextView
    private lateinit var btnCancel: TextView
    private lateinit var avatar: TextView

    // Photo Launchers
    private val profilePicLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri = result.data?.data ?: return@registerForActivityResult
            val encoded = encodeImage(uri, 256)
            if (encoded.isNotEmpty()) {
                profilePicBase64 = encoded
                Toast.makeText(this, "Profile picture selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val coverPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri = result.data?.data ?: return@registerForActivityResult
            val encoded = encodeImage(uri, 512)
            if (encoded.isNotEmpty()) {
                coverPhotoBase64 = encoded
                Toast.makeText(this, "Cover photo selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_editprofile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        isFirstTime = intent.getBooleanExtra("firstTime", false)

        etName = findViewById(R.id.etName)
        etUsername = findViewById(R.id.etUsername)
        etBio = findViewById(R.id.etBio)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etGender = findViewById(R.id.etGender)

        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
        avatar = findViewById(R.id.avatar)

        if (isFirstTime) {
            btnCancel.visibility = View.GONE
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    Toast.makeText(this@editprofile, "Please complete your profile", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            btnCancel.setOnClickListener { finish() }
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            openLogin()
            return
        }

        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(uid)

        userRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                Toast.makeText(this, "Profile not found", Toast.LENGTH_LONG).show()
                FirebaseAuth.getInstance().signOut()
                openLogin()
                return@addOnSuccessListener
            }

            val fullName = snapshot.child("fullName").getValue(String::class.java) ?: ""
            val username = snapshot.child("username").getValue(String::class.java) ?: ""
            val bio = snapshot.child("bio").getValue(String::class.java) ?: ""
            val email = snapshot.child("email").getValue(String::class.java) ?: ""
            val phone = snapshot.child("phone").getValue(String::class.java) ?: ""
            val gender = snapshot.child("gender").getValue(String::class.java) ?: ""
            profilePicBase64 = snapshot.child("profilePic").getValue(String::class.java) ?: ""
            coverPhotoBase64 = snapshot.child("coverPhoto").getValue(String::class.java) ?: ""

            etName.setText(fullName)
            etUsername.setText(username)
            etBio.setText(bio)
            etEmail.setText(email)
            etPhone.setText(phone)
            etGender.setText(gender)

            avatar.text = getInitials(fullName)
            oldUsernameLower = username.lowercase()
        }

        findViewById<TextView>(R.id.btnEditPicture).setOnClickListener {
            profilePicLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"))
        }

        val editCoverBtn = findViewById<TextView>(R.id.btnEditCover)
        val coverCamBtn = findViewById<ImageView>(R.id.btnCoverCamera)
        val pickCoverListener = View.OnClickListener {
            coverPhotoLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"))
        }
        editCoverBtn?.setOnClickListener(pickCoverListener)
        coverCamBtn?.setOnClickListener(pickCoverListener)

        btnSave.setOnClickListener { saveProfile(uid) }
    }

    private fun saveProfile(uid: String) {
        val name = etName.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val bio = etBio.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (name.isEmpty()) { etName.error = "Name required"; return }
        if (username.isEmpty()) { etUsername.error = "Username required"; return }
        if (bio.isEmpty()) { etBio.error = "Bio required"; return }

        btnSave.isEnabled = false
        val usernameLower = username.lowercase()
        val root = FirebaseDatabase.getInstance().reference

        root.child("usernames").child(usernameLower).runTransaction(object : Transaction.Handler {
            override fun doTransaction(d: MutableData): Transaction.Result {
                val owner = d.getValue(String::class.java)
                if (owner != null && owner != uid) return Transaction.abort()
                d.value = uid
                return Transaction.success(d)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snap: DataSnapshot?) {
                if (error != null || !committed) {
                    btnSave.isEnabled = true
                    etUsername.error = "Username is already taken"
                    return
                }

                val parts = name.split(Regex("\\s+"))
                val updates = hashMapOf<String, Any>(
                    "firstName" to parts.first(),
                    "lastName" to parts.drop(1).joinToString(" "),
                    "fullName" to name,
                    "nameLower" to name.lowercase(),
                    "username" to username,
                    "usernameLower" to usernameLower,
                    "bio" to bio,
                    "phone" to phone
                )
                if (profilePicBase64.isNotEmpty()) updates["profilePic"] = profilePicBase64
                if (coverPhotoBase64.isNotEmpty()) updates["coverPhoto"] = coverPhotoBase64

                root.child("users").child(uid).updateChildren(updates).addOnSuccessListener {
                    if (oldUsernameLower.isNotEmpty() && oldUsernameLower != usernameLower) {
                        root.child("usernames").child(oldUsernameLower).removeValue()
                    }
                    Toast.makeText(this@editprofile, "Profile updated", Toast.LENGTH_SHORT).show()
                    finish()
                }.addOnFailureListener { err ->
                    btnSave.isEnabled = true
                    Toast.makeText(this@editprofile, "Failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun getInitials(name: String): String {
        return name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }
    }

    private fun encodeImage(uri: Uri, maxDimension: Int): String {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return ""
            val bitmap = BitmapFactory.decodeStream(input) ?: return ""
            val scale = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
            val resized = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
            } else bitmap
            val output = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 70, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }

    private fun openLogin() {
        startActivity(Intent(this, Login_activity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}