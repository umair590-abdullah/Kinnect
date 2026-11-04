package com.example.kinnect

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class otherprofile : AppCompatActivity() {

    private lateinit var tvHeaderName: TextView
    private lateinit var tvName: TextView
    private lateinit var tvCounts: TextView
    private lateinit var tvBio: TextView
    private lateinit var avatar: TextView
    private lateinit var btnAddFriend: TextView
    private lateinit var btnMessage: TextView
    private lateinit var btnBack: TextView
    private lateinit var rowMutual: LinearLayout
    private lateinit var tvMutual: TextView

    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var targetUid: String = ""
    private val dbRef = FirebaseDatabase.getInstance().reference

    private enum class RelationState { NONE, SENT, RECEIVED, FRIENDS }
    private var currentState = RelationState.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_otherprofile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        targetUid = intent.getStringExtra("targetUid") ?: ""

        tvHeaderName = findViewById(R.id.tvHeaderName)
        tvName = findViewById(R.id.tvName)
        tvCounts = findViewById(R.id.tvCounts)
        tvBio = findViewById(R.id.tvBio)
        avatar = findViewById(R.id.avatar)
        btnAddFriend = findViewById(R.id.btnAddFriend)
        btnMessage = findViewById(R.id.btnMessage)
        btnBack = findViewById(R.id.btnBack)
        rowMutual = findViewById(R.id.rowMutual)
        tvMutual = findViewById(R.id.tvMutual)

        btnBack.setOnClickListener { finish() }

        if (targetUid.isNotEmpty()) {
            loadTargetUserProfile()
            loadFriendCountsAndMutuals()
            checkRelationState()
        }

        btnAddFriend.setOnClickListener { handleActionButtonClick() }

        // Wire Message button to open chat screen with target user
        btnMessage.setOnClickListener {
            if (targetUid.isNotEmpty()) {
                val intent = Intent(this, chat::class.java).apply {
                    putExtra("otherUid", targetUid)
                }
                startActivity(intent)
            }
        }
    }

    private fun loadTargetUserProfile() {
        dbRef.child("users").child(targetUid).get().addOnSuccessListener { snap ->
            val name = snap.child("fullName").getValue(String::class.java) ?: "User"
            val bio = snap.child("bio").getValue(String::class.java) ?: ""

            tvHeaderName.text = name
            tvName.text = name
            tvBio.text = bio
            avatar.text = getInitials(name)
        }
    }

    private fun loadFriendCountsAndMutuals() {
        dbRef.child("friends").child(targetUid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(targetFriendsSnap: DataSnapshot) {
                val totalFriends = targetFriendsSnap.childrenCount

                if (currentUid.isEmpty()) {
                    tvCounts.text = "$totalFriends friends"
                    return
                }

                dbRef.child("friends").child(currentUid).get().addOnSuccessListener { myFriendsSnap ->
                    var mutualCount = 0
                    val mutualNames = mutableListOf<String>()

                    for (friend in targetFriendsSnap.children) {
                        val friendKey = friend.key ?: continue
                        if (myFriendsSnap.hasChild(friendKey)) {
                            mutualCount++
                        }
                    }

                    tvCounts.text = "$totalFriends friends · $mutualCount mutual"

                    if (mutualCount > 0) {
                        rowMutual.visibility = View.VISIBLE
                        tvMutual.text = "$mutualCount mutual friend(s) in common"
                    } else {
                        rowMutual.visibility = View.GONE
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun checkRelationState() {
        if (currentUid.isEmpty() || targetUid.isEmpty()) return

        dbRef.child("friends").child(currentUid).child(targetUid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                if (snap.exists()) {
                    updateStateUI(RelationState.FRIENDS)
                } else {
                    dbRef.child("friend_requests").child("sent").child(currentUid).child(targetUid)
                        .addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(sentSnap: DataSnapshot) {
                                if (sentSnap.exists()) {
                                    updateStateUI(RelationState.SENT)
                                } else {
                                    dbRef.child("friend_requests").child("received").child(currentUid).child(targetUid)
                                        .addValueEventListener(object : ValueEventListener {
                                            override fun onDataChange(receivedSnap: DataSnapshot) {
                                                if (receivedSnap.exists()) {
                                                    updateStateUI(RelationState.RECEIVED)
                                                } else {
                                                    updateStateUI(RelationState.NONE)
                                                }
                                            }
                                            override fun onCancelled(error: DatabaseError) {}
                                        })
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateStateUI(state: RelationState) {
        currentState = state
        when (state) {
            RelationState.NONE -> {
                btnAddFriend.text = "♙  Add friend"
                btnAddFriend.setBackgroundColor(Color.parseColor("#08716D"))
                btnAddFriend.setTextColor(Color.WHITE)
            }
            RelationState.SENT -> {
                btnAddFriend.text = "✓  Requested"
                btnAddFriend.setBackgroundColor(Color.GRAY)
                btnAddFriend.setTextColor(Color.WHITE)
            }
            RelationState.RECEIVED -> {
                btnAddFriend.text = "✓  Confirm Request"
                btnAddFriend.setBackgroundColor(Color.parseColor("#2E7D32"))
                btnAddFriend.setTextColor(Color.WHITE)
            }
            RelationState.FRIENDS -> {
                btnAddFriend.text = "👥  Friends"
                btnAddFriend.setBackgroundColor(Color.parseColor("#455A64"))
                btnAddFriend.setTextColor(Color.WHITE)
            }
        }
    }

    private fun handleActionButtonClick() {
        when (currentState) {
            RelationState.NONE -> sendFriendRequest()
            RelationState.SENT -> cancelFriendRequest()
            RelationState.RECEIVED -> confirmFriendRequest()
            RelationState.FRIENDS -> unfriendUser()
        }
    }

    private fun sendFriendRequest() {
        val updates = mapOf<String, Any?>(
            "/friend_requests/sent/$currentUid/$targetUid" to true,
            "/friend_requests/received/$targetUid/$currentUid" to true
        )
        dbRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Friend request sent", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelFriendRequest() {
        val updates = mapOf<String, Any?>(
            "/friend_requests/sent/$currentUid/$targetUid" to null,
            "/friend_requests/received/$currentUid/$targetUid" to null
        )
        dbRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Request canceled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmFriendRequest() {
        val updates = mapOf<String, Any?>(
            "/friend_requests/sent/$targetUid/$currentUid" to null,
            "/friend_requests/received/$currentUid/$targetUid" to null,
            "/friends/$currentUid/$targetUid" to true,
            "/friends/$targetUid/$currentUid" to true
        )
        dbRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Request confirmed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unfriendUser() {
        val updates = mapOf<String, Any?>(
            "/friends/$currentUid/$targetUid" to null,
            "/friends/$targetUid/$currentUid" to null
        )
        dbRef.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Unfriended", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getInitials(name: String): String {
        return name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
            .joinToString("") { it.take(1).uppercase() }
    }
}