package com.example.kinnect

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

object PresenceManager {

    fun setupPresence() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val rootRef = FirebaseDatabase.getInstance().reference
        val userPresenceRef = rootRef.child("users").child(uid).child("online")
        val lastSeenRef = rootRef.child("users").child(uid).child("lastSeen")
        val connectedRef = rootRef.child(".info/connected")

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isConnected = snapshot.getValue(Boolean::class.java) ?: false
                if (isConnected) {
                    userPresenceRef.setValue(true)
                    userPresenceRef.onDisconnect().setValue(false)
                    lastSeenRef.onDisconnect().setValue(ServerValue.TIMESTAMP)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun setOffline() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val rootRef = FirebaseDatabase.getInstance().reference
        rootRef.child("users").child(uid).child("online").setValue(false)
        rootRef.child("users").child(uid).child("lastSeen").setValue(ServerValue.TIMESTAMP)
    }
}