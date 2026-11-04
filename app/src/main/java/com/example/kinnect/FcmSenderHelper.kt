package com.example.kinnect

import com.google.firebase.database.FirebaseDatabase
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object FcmSenderHelper {

    fun sendMessageNotification(recipientUid: String, senderName: String, messageText: String, senderUid: String) {
        val dbRef = FirebaseDatabase.getInstance().reference

        dbRef.child("users").child(recipientUid).child("fcmToken").get()
            .addOnSuccessListener { snapshot ->
                val fcmToken = snapshot.getValue(String::class.java) ?: return@addOnSuccessListener

                // Write message notification record to database for fallback
                val notifId = dbRef.child("notifications").child(recipientUid).push().key ?: return@addOnSuccessListener
                val notifData = mapOf(
                    "type" to "new_message",
                    "senderUid" to senderUid,
                    "title" to senderName,
                    "body" to messageText,
                    "timestamp" to System.currentTimeMillis()
                )
                dbRef.child("notifications").child(recipientUid).child(notifId).setValue(notifData)
            }
    }
}