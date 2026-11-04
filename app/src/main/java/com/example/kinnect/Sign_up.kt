package com.example.kinnect


import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class Sign_up : AppCompatActivity() {

    private var passVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up) // change if your layout file has another name

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        val first = findViewById<EditText>(R.id.firstname)
        val last = findViewById<EditText>(R.id.secondname)
        val day = findViewById<EditText>(R.id.day)
        val month = findViewById<EditText>(R.id.month)
        val year = findViewById<EditText>(R.id.year)
        val genderGroup = findViewById<RadioGroup>(R.id.genderGroup)
        val email = findViewById<EditText>(R.id.email)
        val password = findViewById<EditText>(R.id.password)
        val agree = findViewById<CheckBox>(R.id.agree)
        val btn = findViewById<Button>(R.id.createaccount)
        val bars = listOf<View>(
            findViewById(R.id.bar1), findViewById(R.id.bar2), findViewById(R.id.bar3)
        )
        val label = findViewById<TextView>(R.id.strengthLabel)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
        findViewById<View>(R.id.logintext).setOnClickListener { finish() }

        // Show / hide password
        findViewById<ImageView>(R.id.togglePassword).setOnClickListener {
            passVisible = !passVisible
            password.inputType = if (passVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            password.setSelection(password.text.length)
        }

        // Live strength check: Weak / Medium / Strong
        password.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val level = strength(s.toString())
                val (text, color) = when (level) {
                    0 -> "" to "#E3E3E3"
                    1 -> "Weak" to "#D93025"
                    2 -> "Medium" to "#F9A825"
                    else -> "Strong" to "#2E7D32"
                }
                bars.forEachIndexed { i, v ->
                    v.setBackgroundColor(Color.parseColor(if (i < level) color else "#E3E3E3"))
                }
                label.text = text
                label.setTextColor(Color.parseColor(color))
            }
        })

        btn.setOnClickListener {
            val f = first.text.toString().trim()
            val l = last.text.toString().trim()
            val e = email.text.toString().trim()
            val p = password.text.toString()

            if (f.isEmpty()) { first.error = "Required"; return@setOnClickListener }
            if (l.isEmpty()) { last.error = "Required"; return@setOnClickListener }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
                email.error = "Invalid email"; return@setOnClickListener
            }
            if (strength(p) < 2) {
                password.error = "Too weak (min 8 chars, letters + numbers)"
                return@setOnClickListener
            }
            if (!agree.isChecked) {
                Toast.makeText(this, "Please accept the Terms", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val gender = when (genderGroup.checkedRadioButtonId) {
                R.id.female -> "Female"
                R.id.male -> "Male"
                R.id.custom -> "Custom"
                else -> ""
            }

            btn.isEnabled = false

            // 1. Create auth user
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(e, p)
                .addOnSuccessListener { result ->
                    val uid = result.user!!.uid
                    val full = "$f $l"

                    // 2. Immediately save to /users/{uid}
                    val data = hashMapOf<String, Any>(
                        "email" to e,
                        "createdAt" to System.currentTimeMillis(),
                        "status" to "online",
                        "firstName" to f,
                        "lastName" to l,
                        "fullName" to full,
                        "nameLower" to full.lowercase(),
                        "birthday" to "${day.text}/${month.text}/${year.text}",
                        "gender" to gender,
                        "username" to "" // empty -> forces Edit Profile
                    )

                    FirebaseDatabase.getInstance().reference
                        .child("users").child(uid).setValue(data)
                        .addOnSuccessListener {
                            // 3. Mark as Recent Login
                            getSharedPreferences("kinnect_prefs", MODE_PRIVATE).edit()
                                .putString("last_email", e)
                                .putString("last_name", full)
                                .apply()

                            Session.start()

                            // First-time user -> Edit Profile
                            startActivity(
                                Intent(this, editprofile::class.java)
                                    .putExtra("firstTime", true)
                                    .addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    )
                            )
                            finish()
                        }
                        .addOnFailureListener { err ->
                            btn.isEnabled = true
                            Toast.makeText(
                                this, "Could not save profile: ${err.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .addOnFailureListener { err ->
                    btn.isEnabled = true
                    Toast.makeText(
                        this, err.message ?: "Sign up failed", Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    /** 0 = empty, 1 = Weak, 2 = Medium, 3 = Strong */
    private fun strength(p: String): Int {
        if (p.isEmpty()) return 0
        val strong = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$")
        val medium = Regex("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")
        return when {
            strong.matches(p) -> 3
            medium.matches(p) -> 2
            else -> 1
        }
    }
}