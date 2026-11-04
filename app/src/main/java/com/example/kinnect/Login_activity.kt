package com.example.kinnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.kinnect.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class Login_activity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("kinnect_prefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        val mainView = findViewById<View>(R.id.main)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        val email = findViewById<EditText>(R.id.email)
        val password = findViewById<EditText>(R.id.password)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val card = findViewById<View>(R.id.card)
        val label = findViewById<View>(R.id.t2)

        // ---- Recent login card ----
        val lastEmail = prefs.getString("last_email", null)
        val lastName = prefs.getString("last_name", null)

        if (lastEmail.isNullOrBlank()) {
            card?.visibility = View.GONE
            label?.visibility = View.GONE
        } else {
            val shown = if (lastName.isNullOrBlank()) lastEmail else lastName

            findViewById<TextView>(R.id.tvRecentName)?.text = shown
            findViewById<TextView>(R.id.tvInitials)?.text =
                shown.trim().split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString("") { it.take(1).uppercase() }

            // Tap -> auto-fill email
            card?.setOnClickListener {
                email.setText(lastEmail)
                password.requestFocus()
            }
        }

        // ---- Go to Sign Up ----
        findViewById<Button>(R.id.newaccount)?.setOnClickListener {
            startActivity(Intent(this, Sign_up::class.java))
        }

        // ---- Login ----
        btnLogin?.setOnClickListener {
            val e = email.text.toString().trim()
            val p = password.text.toString()

            if (e.isEmpty()) {
                email.error = "Required"
                return@setOnClickListener
            }
            if (p.isEmpty()) {
                password.error = "Required"
                return@setOnClickListener
            }

            btnLogin.isEnabled = false

            FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(e, p)
                .addOnSuccessListener { result ->

                    val uid = result.user?.uid
                    if (uid == null) {
                        btnLogin.isEnabled = true
                        Toast.makeText(this, "Login failed", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val userRef = FirebaseDatabase.getInstance()
                        .reference.child("users").child(uid)

                    userRef.get()
                        .addOnSuccessListener { snap ->

                            if (!snap.exists()) {
                                btnLogin.isEnabled = true
                                FirebaseAuth.getInstance().signOut()
                                Toast.makeText(
                                    this, "User profile not found", Toast.LENGTH_LONG
                                ).show()
                                return@addOnSuccessListener
                            }

                            val firstName =
                                snap.child("firstName").getValue(String::class.java) ?: ""
                            val lastNameDb =
                                snap.child("lastName").getValue(String::class.java) ?: ""
                            val username =
                                snap.child("username").getValue(String::class.java) ?: ""
                            val fullName =
                                snap.child("fullName").getValue(String::class.java)
                                    ?: "$firstName $lastNameDb".trim()

                            // Save as Recent Login
                            prefs.edit()
                                .putString("last_email", e)
                                .putString("last_name", fullName)
                                .apply()

                            // Set user online
                            userRef.updateChildren(mapOf("status" to "online"))
                            Session.start()

                            // No username -> profile setup not done
                            val next = if (username.isBlank()) {
                                Intent(this, editprofile::class.java)
                                    .putExtra("firstTime", true)
                            } else {
                                Intent(this, home_feed::class.java)
                            }

                            next.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                            startActivity(next)
                            finish()
                        }
                        .addOnFailureListener { error ->
                            btnLogin.isEnabled = true
                            Toast.makeText(
                                this,
                                "Could not load profile: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .addOnFailureListener { error ->
                    btnLogin.isEnabled = true
                    Toast.makeText(
                        this, error.message ?: "Login failed", Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
}