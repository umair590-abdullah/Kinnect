package com.example.kinnect


import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class splash : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private val navigate = Runnable {
        if (isFinishing || isDestroyed) return@Runnable

        val target = if (FirebaseAuth.getInstance().currentUser != null)
            home_feed::class.java
        else
            Login_activity::class.java

        openScreen(target)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash)
        handler.postDelayed(navigate, 5000)
    }

    // CLEAR_TASK + finish() = pushReplacement (cannot go back to Splash)
    private fun openScreen(screen: Class<*>) {
        val intent = Intent(this, screen)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigate)
        super.onDestroy()
    }
}