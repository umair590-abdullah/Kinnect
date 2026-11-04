package com.example.kinnect


import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.messaging.FirebaseMessaging

object Session {

    fun start() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().reference.child("users").child(uid)

        ref.child("online").setValue(true)
        ref.child("online").onDisconnect().setValue(false)
        ref.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)

        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            ref.child("fcmToken").setValue(token)
        }
    }

    fun end(onDone: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { onDone(); return }

        val ref = FirebaseDatabase.getInstance().reference.child("users").child(uid)
        ref.child("fcmToken").removeValue()
        ref.child("lastSeen").setValue(ServerValue.TIMESTAMP)
        ref.child("online").setValue(false).addOnCompleteListener {
            FirebaseAuth.getInstance().signOut()
            onDone()
        }
    }
}