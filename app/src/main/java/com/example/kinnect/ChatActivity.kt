package com.example.kinnect

import android.app.Activity
import android.database.ContentObserver
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ChatActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var viewOnlineDot: View
    private lateinit var tvHeaderName: TextView

    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var targetUid: String = ""
    private var chatId: String = ""
    private var currentUserName: String = "User"
    private val dbRef = FirebaseDatabase.getInstance().reference

    // Android 14 Screen Capture Callback
    private var screenCaptureCallback: Activity.ScreenCaptureCallback? = null
    // Fallback ContentObserver
    private var screenshotObserver: ContentObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)

        targetUid = intent.getStringExtra("targetUid") ?: ""
        chatId = getChatRoomId(currentUid, targetUid)

        tvStatus = findViewById(R.id.tvStatus)
        viewOnlineDot = findViewById(R.id.viewOnlineDot)
        tvHeaderName = findViewById(R.id.tvHeaderName)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        loadCurrentUserName()
        listenToTargetUserPresence()
        registerScreenshotDetection()
    }

    private fun getChatRoomId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    private fun loadCurrentUserName() {
        if (currentUid.isEmpty()) return
        dbRef.child("users").child(currentUid).child("fullName").get().addOnSuccessListener {
            currentUserName = it.getValue(String::class.java) ?: "User"
        }
    }

    // 1. ONLINE / OFFLINE REALTIME STATUS
    private fun listenToTargetUserPresence() {
        if (targetUid.isEmpty()) return

        dbRef.child("users").child(targetUid).child("online")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isOnline = snapshot.getValue(Boolean::class.java) ?: false
                    if (isOnline) {
                        tvStatus.text = "Active now"
                        tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                        viewOnlineDot.visibility = View.VISIBLE
                    } else {
                        tvStatus.text = "Offline"
                        tvStatus.setTextColor(Color.parseColor("#888888"))
                        viewOnlineDot.visibility = View.GONE
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // 2. SCREENSHOT DETECTION & SYSTEM NOTIFICATION MESSAGE
    private fun registerScreenshotDetection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            screenCaptureCallback = Activity.ScreenCaptureCallback {
                onScreenshotTaken()
            }
            registerScreenCaptureCallback(mainExecutor, screenCaptureCallback!!)
        } else {
            // Fallback for Android 13 and below using MediaStore ContentObserver
            screenshotObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    if (uri != null && uri.toString().contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
                        onScreenshotTaken()
                    }
                }
            }
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                screenshotObserver!!
            )
        }
    }

    private fun onScreenshotTaken() {
        Toast.makeText(this, "Screenshot detected", Toast.LENGTH_SHORT).show()
        sendScreenshotSystemMessage()
    }

    private fun sendScreenshotSystemMessage() {
        if (chatId.isEmpty()) return

        val msgRef = dbRef.child("chats").child(chatId).child("messages").push()
        val messageData = mapOf(
            "senderId" to "system",
            "text" to "$currentUserName took a screenshot",
            "type" to "system",
            "timestamp" to ServerValue.TIMESTAMP
        )

        msgRef.setValue(messageData)

        // Send push notification trigger to recipient
        FcmSenderHelper.sendMessageNotification(
            recipientUid = targetUid,
            senderName = "Security Alert",
            messageText = "$currentUserName took a screenshot of your chat.",
            senderUid = currentUid
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && screenCaptureCallback != null) {
            unregisterScreenCaptureCallback(screenCaptureCallback!!)
        }
        if (screenshotObserver != null) {
            contentResolver.unregisterContentObserver(screenshotObserver!!)
        }
    }
}