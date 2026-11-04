package com.example.kinnect

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class search : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnClear: ImageView
    private lateinit var peopleContainer: LinearLayout
    private lateinit var btnSeeAllPeople: TextView
    private lateinit var rowGroup: LinearLayout
    private lateinit var recent1: LinearLayout
    private lateinit var recent2: LinearLayout
    private lateinit var recent3: LinearLayout

    private lateinit var chipAll: TextView
    private lateinit var chipPeople: TextView
    private lateinit var chipPosts: TextView
    private lateinit var chipGroups: TextView
    private lateinit var chipMarketplace: TextView

    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val dbRef = FirebaseDatabase.getInstance().reference
    private var activeFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnBack = findViewById<TextView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        etSearch = findViewById(R.id.etSearch)
        btnClear = findViewById(R.id.btnClear)
        peopleContainer = findViewById(R.id.peopleContainer)
        btnSeeAllPeople = findViewById(R.id.btnSeeAllPeople)
        rowGroup = findViewById(R.id.rowGroup)
        recent1 = findViewById(R.id.recent1)
        recent2 = findViewById(R.id.recent2)
        recent3 = findViewById(R.id.recent3)

        chipAll = findViewById(R.id.chipAll)
        chipPeople = findViewById(R.id.chipPeople)
        chipPosts = findViewById(R.id.chipPosts)
        chipGroups = findViewById(R.id.chipGroups)
        chipMarketplace = findViewById(R.id.chipMarketplace)

        setupChips()
        setupRecentSearches()

        btnClear.setOnClickListener {
            etSearch.setText("")
            peopleContainer.removeAllViews()
            toggleRecentSearches(true)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().lowercase()
                if (query.isEmpty()) {
                    peopleContainer.removeAllViews()
                    toggleRecentSearches(true)
                } else {
                    toggleRecentSearches(false)
                    performSearch(query)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupChips() {
        val chips = listOf(chipAll, chipPeople, chipPosts, chipGroups, chipMarketplace)
        chips.forEach { chip ->
            chip.setOnClickListener {
                chips.forEach { c ->
                    c.setBackgroundColor(Color.parseColor("#EDE9E2"))
                    c.setTextColor(Color.parseColor("#111111"))
                }
                chip.setBackgroundColor(Color.parseColor("#D5EBEB"))
                chip.setTextColor(Color.parseColor("#0F5C63"))
                activeFilter = chip.text.toString()

                val query = etSearch.text.toString().trim().lowercase()
                if (query.isNotEmpty()) performSearch(query)
            }
        }
    }

    private fun setupRecentSearches() {
        listOf(recent1, recent2, recent3).forEach { row ->
            val closeBtn = row.getChildAt(2) as? ImageView
            closeBtn?.setOnClickListener { row.visibility = View.GONE }
        }
    }

    private fun toggleRecentSearches(show: Boolean) {
        val visibility = if (show) View.VISIBLE else View.GONE
        recent1.visibility = visibility
        recent2.visibility = visibility
        recent3.visibility = visibility
    }

    private fun performSearch(query: String) {
        peopleContainer.removeAllViews()

        if (activeFilter == "Posts" || activeFilter == "Marketplace") {
            rowGroup.visibility = View.GONE
            return
        }

        rowGroup.visibility = if (activeFilter == "All" || activeFilter == "Groups") View.VISIBLE else View.GONE

        if (activeFilter == "All" || activeFilter == "People") {
            dbRef.child("users").get().addOnSuccessListener { snapshot ->
                var count = 0
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    if (uid == currentUid) continue

                    val fullName = child.child("fullName").getValue(String::class.java) ?: ""
                    val username = child.child("username").getValue(String::class.java) ?: ""
                    val nameLower = child.child("nameLower").getValue(String::class.java) ?: fullName.lowercase()
                    val usernameLower = child.child("usernameLower").getValue(String::class.java) ?: username.lowercase()

                    if (nameLower.contains(query) || usernameLower.contains(query)) {
                        createSearchUserRow(uid, fullName, username)
                        count++
                    }
                }
                btnSeeAllPeople.visibility = if (count > 3) View.VISIBLE else View.GONE
            }
        }
    }

    private fun createSearchUserRow(targetUid: String, fullName: String, username: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        val avatar = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(110, 110)
            text = getInitials(fullName)
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F5C63"))
        }

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(20, 0, 0, 0)
            }
        }

        val tvName = TextView(this).apply {
            text = fullName
            textSize = 14f
            setTextColor(Color.parseColor("#111111"))
            setTypeface(null, Typeface.BOLD)
        }

        val tvUsername = TextView(this).apply {
            text = "@$username"
            textSize = 12f
            setTextColor(Color.parseColor("#5F5F5F"))
        }

        infoLayout.addView(tvName)
        infoLayout.addView(tvUsername)

        val actionBtn = TextView(this).apply {
            text = "View"
            textSize = 12f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(28, 14, 28, 14)
            setBackgroundColor(Color.parseColor("#0F5C63"))
        }

        row.setOnClickListener {
            val intent = Intent(this@search, otherprofile::class.java).apply {
                putExtra("targetUid", targetUid)
            }
            startActivity(intent)
        }

        row.addView(avatar)
        row.addView(infoLayout)
        row.addView(actionBtn)

        peopleContainer.addView(row)
    }

    private fun getInitials(name: String): String {
        return name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
    }
}