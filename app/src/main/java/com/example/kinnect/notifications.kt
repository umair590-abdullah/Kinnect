package com.example.kinnect

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class notifications : AppCompatActivity() {

    private lateinit var newContainer: LinearLayout
    private lateinit var scrollContent: LinearLayout
    private var earlierContainer: LinearLayout? = null

    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val dbRef = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notifications)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        newContainer = findViewById(R.id.newContainer)
        scrollContent = findViewById(R.id.scrollContent)

        setupEarlierNotificationsContainer()
        listenPendingFriendRequests()
        listenActivityNotifications()
    }

    private fun setupEarlierNotificationsContainer() {
        earlierContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scrollContent.addView(earlierContainer)
    }

    // 1. DYNAMIC FRIEND REQUEST NOTIFICATIONS
    private fun listenPendingFriendRequests() {
        if (currentUid.isEmpty()) return

        dbRef.child("friend_requests").child("received").child(currentUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    newContainer.removeAllViews()
                    if (!snapshot.exists()) {
                        val emptyTv = TextView(this@notifications).apply {
                            text = "No new friend requests"
                            textSize = 13f
                            setTextColor(Color.parseColor("#777777"))
                            setPadding(12, 12, 12, 12)
                        }
                        newContainer.addView(emptyTv)
                        return
                    }

                    for (child in snapshot.children) {
                        val senderUid = child.key ?: continue
                        dbRef.child("users").child(senderUid).get().addOnSuccessListener { userSnap ->
                            val name = userSnap.child("fullName").getValue(String::class.java) ?: "Someone"
                            createFriendRequestRow(senderUid, name)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun createFriendRequestRow(senderUid: String, name: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 12, 8, 12)
            gravity = Gravity.CENTER_VERTICAL
        }

        val textTv = TextView(this).apply {
            text = "$name sent you a friend request"
            textSize = 13f
            setTextColor(Color.parseColor("#222222"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                startActivity(Intent(this@notifications, otherprofile::class.java).apply {
                    putExtra("targetUid", senderUid)
                })
            }
        }

        val btnConfirm = Button(this).apply {
            text = "Confirm"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0F5C63"))
            setOnClickListener {
                val updates = mapOf<String, Any?>(
                    "/friend_requests/sent/$senderUid/$currentUid" to null,
                    "/friend_requests/received/$currentUid/$senderUid" to null,
                    "/friends/$currentUid/$senderUid" to true,
                    "/friends/$senderUid/$currentUid" to true
                )
                dbRef.updateChildren(updates).addOnSuccessListener {
                    Toast.makeText(this@notifications, "Request accepted", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnDelete = Button(this).apply {
            text = "Delete"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#C0482C"))
            setOnClickListener {
                val updates = mapOf<String, Any?>(
                    "/friend_requests/sent/$senderUid/$currentUid" to null,
                    "/friend_requests/received/$currentUid/$senderUid" to null
                )
                dbRef.updateChildren(updates).addOnSuccessListener {
                    Toast.makeText(this@notifications, "Request deleted", Toast.LENGTH_SHORT).show()
                }
            }
        }

        row.addView(textTv)
        row.addView(btnConfirm)
        row.addView(btnDelete)
        newContainer.addView(row)
    }

    // 2. DYNAMIC GENERAL SYSTEM & ACTIVITY NOTIFICATIONS
    private fun listenActivityNotifications() {
        if (currentUid.isEmpty()) return

        dbRef.child("notifications").child(currentUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    earlierContainer?.removeAllViews()

                    for (child in snapshot.children) {
                        val title = child.child("title").getValue(String::class.java) ?: "Notification"
                        val body = child.child("body").getValue(String::class.java) ?: ""
                        val senderUid = child.child("senderUid").getValue(String::class.java) ?: ""

                        createActivityNotificationRow(title, body, senderUid)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun createActivityNotificationRow(title: String, body: String, senderUid: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER_VERTICAL
            if (senderUid.isNotEmpty()) {
                setOnClickListener {
                    startActivity(Intent(this@notifications, otherprofile::class.java).apply {
                        putExtra("targetUid", senderUid)
                    })
                }
            }
        }

        val avatar = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(90, 90)
            text = title.take(2).uppercase()
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#407B99"))
        }

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(16, 0, 0, 0)
            }
        }

        val titleTv = TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(Color.parseColor("#222222"))
            setTypeface(null, Typeface.BOLD)
        }

        val bodyTv = TextView(this).apply {
            text = body
            textSize = 12f
            setTextColor(Color.parseColor("#555555"))
        }

        infoLayout.addView(titleTv)
        infoLayout.addView(bodyTv)

        row.addView(avatar)
        row.addView(infoLayout)

        earlierContainer?.addView(row, 0)
    }
}