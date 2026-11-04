package com.example.kinnect

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class friends : AppCompatActivity() {

    private lateinit var tvRequestCount: TextView
    private lateinit var requestsContainer: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var chipSuggestions: TextView
    private lateinit var chipYourFriends: TextView

    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val dbRef = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_friends)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvRequestCount = findViewById(R.id.tvRequestCount)
        requestsContainer = findViewById(R.id.requestsContainer)
        listContainer = findViewById(R.id.listContainer)
        chipSuggestions = findViewById(R.id.chipSuggestions)
        chipYourFriends = findViewById(R.id.chipYourFriends)

        findViewById<ImageView>(R.id.btnSearch)?.setOnClickListener {
            startActivity(Intent(this, search::class.java))
        }

        chipSuggestions.setOnClickListener {
            highlightChip(chipSuggestions, chipYourFriends)
            loadSuggestions()
        }

        chipYourFriends.setOnClickListener {
            highlightChip(chipYourFriends, chipSuggestions)
            loadYourFriends()
        }

        listenToPendingRequests()
        loadSuggestions()
    }

    private fun highlightChip(active: TextView, inactive: TextView) {
        active.setBackgroundColor(Color.parseColor("#0F5C63"))
        active.setTextColor(Color.WHITE)
        inactive.setBackgroundColor(Color.parseColor("#EDE9E2"))
        inactive.setTextColor(Color.parseColor("#111111"))
    }

    private fun listenToPendingRequests() {
        if (currentUid.isEmpty()) return
        val requestsRef = dbRef.child("friend_requests").child("received").child(currentUid)

        requestsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                tvRequestCount.text = snapshot.childrenCount.toString()
                requestsContainer.removeAllViews()

                for (child in snapshot.children) {
                    val requesterUid = child.key ?: continue
                    dbRef.child("users").child(requesterUid).get().addOnSuccessListener { userSnap ->
                        val name = userSnap.child("fullName").getValue(String::class.java) ?: "User"
                        createRequestRow(requesterUid, name)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun createRequestRow(uid: String, name: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        val nameTv = TextView(this).apply {
            text = name
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openOtherProfile(uid) }
        }

        val btnConfirm = Button(this).apply {
            text = "Confirm"
            setBackgroundColor(Color.parseColor("#0F5C63"))
            setTextColor(Color.WHITE)
            setOnClickListener { confirmRequest(uid) }
        }

        val btnDelete = Button(this).apply {
            text = "Delete"
            setBackgroundColor(Color.parseColor("#C0482C"))
            setTextColor(Color.WHITE)
            setOnClickListener { deleteRequest(uid) }
        }

        row.addView(nameTv)
        row.addView(btnConfirm)
        row.addView(btnDelete)
        requestsContainer.addView(row)
    }

    private fun confirmRequest(targetUid: String) {
        val updates = mapOf<String, Any?>(
            "/friend_requests/sent/$targetUid/$currentUid" to null,
            "/friend_requests/received/$currentUid/$targetUid" to null,
            "/friends/$currentUid/$targetUid" to true,
            "/friends/$targetUid/$currentUid" to true
        )
        dbRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Friend request confirmed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteRequest(targetUid: String) {
        val updates = mapOf<String, Any?>(
            "/friend_requests/sent/$targetUid/$currentUid" to null,
            "/friend_requests/received/$currentUid/$targetUid" to null
        )
        dbRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Friend request deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadYourFriends() {
        listContainer.removeAllViews()
        dbRef.child("friends").child(currentUid).get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                val friendUid = child.key ?: continue
                dbRef.child("users").child(friendUid).get().addOnSuccessListener { userSnap ->
                    val name = userSnap.child("fullName").getValue(String::class.java) ?: "User"
                    createFriendItemRow(friendUid, name)
                }
            }
        }
    }

    private fun loadSuggestions() {
        listContainer.removeAllViews()
        dbRef.child("users").limitToFirst(20).get().addOnSuccessListener { snapshot ->
            for (child in snapshot.children) {
                val uid = child.key ?: continue
                if (uid == currentUid) continue
                val name = child.child("fullName").getValue(String::class.java) ?: "User"
                createFriendItemRow(uid, name)
            }
        }
    }

    private fun createFriendItemRow(uid: String, name: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        val nameTv = TextView(this).apply {
            text = name
            textSize = 15f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openOtherProfile(uid) }
        }

        row.addView(nameTv)
        listContainer.addView(row)
    }

    private fun openOtherProfile(uid: String) {
        val intent = Intent(this, otherprofile::class.java).apply {
            putExtra("targetUid", uid)
        }
        startActivity(intent)
    }
}