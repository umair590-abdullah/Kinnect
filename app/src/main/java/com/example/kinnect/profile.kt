package com.example.kinnect

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class profile : AppCompatActivity() {

    private lateinit var tvHeaderName: TextView
    private lateinit var tvName: TextView
    private lateinit var tvCounts: TextView
    private lateinit var tvBio: TextView
    private lateinit var avatar: TextView
    private lateinit var btnEditProfile: TextView

    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val dbRef = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<TextView>(R.id.btnBack)?.setOnClickListener { finish() }

        tvHeaderName = findViewById(R.id.tvHeaderName)
        tvName = findViewById(R.id.tvName)
        tvCounts = findViewById(R.id.tvCounts)
        tvBio = findViewById(R.id.tvBio)
        avatar = findViewById(R.id.avatar)
        btnEditProfile = findViewById(R.id.btnEditProfile)

        btnEditProfile.setOnClickListener {
            startActivity(Intent(this, editprofile::class.java))
        }

        if (currentUid.isNotEmpty()) {
            loadMyProfileData()
            loadMyFriendCount()
        }
    }

    private fun loadMyProfileData() {
        dbRef.child("users").child(currentUid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                if (!snap.exists()) return

                val name = snap.child("fullName").getValue(String::class.java)
                    ?: snap.child("firstName").getValue(String::class.java) ?: "User"
                val bio = snap.child("bio").getValue(String::class.java) ?: ""

                tvHeaderName.text = name
                tvName.text = name
                tvBio.text = bio
                avatar.text = getInitials(name)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadMyFriendCount() {
        dbRef.child("friends").child(currentUid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val friendCount = snapshot.childrenCount
                tvCounts.text = "$friendCount friends"
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun getInitials(name: String): String {
        return name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
    }
}