package com.example.kinnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class menu : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val dbRef = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_menu)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            openLogin()
            return
        }

        // 1. Dynamic User Profile Loading
        loadUserProfile(uid)

        // 2. Wire Top Bar Icons & Profile Card
        setupTopBarAndProfileClick(uid)

        // 3. Wire Navigation Tabs Bar
        setupNavigationTabs()

        // 4. Wire Shortcuts Grid & Extra Sections
        setupShortcutCards()

        // 5. Logout Button Handler
        findViewById<TextView>(R.id.logout).setOnClickListener {
            performLogout(uid)
        }
    }

    private fun loadUserProfile(uid: String) {
        dbRef.child("users").child(uid).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) return@addOnSuccessListener

            val fullName = snapshot.child("fullName").getValue(String::class.java)
                ?: snapshot.child("firstName").getValue(String::class.java) ?: "User"

            // Update Name and Initials in Profile Card
            val rootLayout = findViewById<LinearLayout>(R.id.main)
            val scrollView = rootLayout.getChildAt(0) as? ScrollView
            val scrollContent = scrollView?.getChildAt(0) as? LinearLayout

            // Profile Card layout is the 3rd child in scrollContent
            val profileCard = scrollContent?.getChildAt(2) as? LinearLayout
            if (profileCard != null) {
                val avatarTv = profileCard.getChildAt(0) as? TextView
                val textContainer = profileCard.getChildAt(1) as? LinearLayout
                val nameTv = textContainer?.getChildAt(0) as? TextView

                nameTv?.text = fullName
                avatarTv?.text = getInitials(fullName)
            }
        }
    }

    private fun setupTopBarAndProfileClick(uid: String) {
        val rootLayout = findViewById<LinearLayout>(R.id.main)
        val scrollView = rootLayout.getChildAt(0) as? ScrollView
        val scrollContent = scrollView?.getChildAt(0) as? LinearLayout

        // Top Header Bar (Search & Chat icons)
        val topHeader = scrollContent?.getChildAt(0) as? LinearLayout
        topHeader?.let {
            val btnSearch = it.getChildAt(1) as? TextView
            val btnChat = it.getChildAt(2) as? TextView

            btnSearch?.setOnClickListener { startActivity(Intent(this, search::class.java)) }
            btnChat?.setOnClickListener { startActivity(Intent(this, chats::class.java)) }
        }

        // Profile Card Click Listener
        val profileCard = scrollContent?.getChildAt(2) as? LinearLayout
        val openProfileListener = View.OnClickListener {
            startActivity(Intent(this, profile::class.java))
        }

        profileCard?.setOnClickListener(openProfileListener)
        findViewById<TextView>(R.id.seeurprofile).setOnClickListener(openProfileListener)
    }

    private fun setupNavigationTabs() {
        val rootLayout = findViewById<LinearLayout>(R.id.main)
        val scrollView = rootLayout.getChildAt(0) as? ScrollView
        val scrollContent = scrollView?.getChildAt(0) as? LinearLayout

        // Navigation Tabs Bar (Index 1 inside scrollContent)
        val navBar = scrollContent?.getChildAt(1) as? LinearLayout ?: return

        val tabHome = navBar.getChildAt(0)
        val tabFriends = navBar.getChildAt(1)
        val tabMarketplace = navBar.getChildAt(2)
        val tabNotifications = navBar.getChildAt(3)

        tabHome?.setOnClickListener {
            startActivity(Intent(this, home_feed::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        tabFriends?.setOnClickListener {
            startActivity(Intent(this, friends::class.java))
        }

        tabMarketplace?.setOnClickListener {
            startActivity(Intent(this, marketplace::class.java))
        }

        tabNotifications?.setOnClickListener {
            startActivity(Intent(this, notifications::class.java))
        }
    }

    private fun setupShortcutCards() {
        val rootLayout = findViewById<LinearLayout>(R.id.main)
        val scrollView = rootLayout.getChildAt(0) as? ScrollView
        val scrollContent = scrollView?.getChildAt(0) as? LinearLayout ?: return

        // Shortcut Rows (Indices 4, 5, 6 in scrollContent)
        val row1 = scrollContent.getChildAt(4) as? LinearLayout
        val row2 = scrollContent.getChildAt(5) as? LinearLayout
        val row3 = scrollContent.getChildAt(6) as? LinearLayout

        // Memories & Saved
        row1?.getChildAt(0)?.setOnClickListener { showToast("Memories coming soon!") }
        row1?.getChildAt(1)?.setOnClickListener { showToast("Saved items coming soon!") }

        // Groups & Marketplace
        row2?.getChildAt(0)?.setOnClickListener { showToast("Groups feature coming soon!") }
        row2?.getChildAt(1)?.setOnClickListener { startActivity(Intent(this, marketplace::class.java)) }

        // Friends & Events
        row3?.getChildAt(0)?.setOnClickListener { startActivity(Intent(this, friends::class.java)) }
        row3?.getChildAt(1)?.setOnClickListener { showToast("Events feature coming soon!") }

        // Expandable bottom options
        scrollContent.getChildAt(7)?.setOnClickListener { showToast("Showing all shortcuts") } // See more
        scrollContent.getChildAt(9)?.setOnClickListener { showToast("Help & Support") }       // Help & support
        scrollContent.getChildAt(11)?.setOnClickListener { showToast("Settings & Privacy") }   // Settings & privacy
        scrollContent.getChildAt(13)?.setOnClickListener { showToast("SMD Apps") }            // Also from SMD
    }

    private fun performLogout(uid: String) {
        PresenceManager.setOffline()

        dbRef.child("users").child(uid).child("online").setValue(false)
            .addOnCompleteListener {
                auth.signOut()
                Toast.makeText(this@menu, "Logged out successfully", Toast.LENGTH_SHORT).show()
                openLogin()
            }
    }

    private fun openLogin() {
        startActivity(Intent(this, Login_activity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun getInitials(name: String): String {
        return name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}