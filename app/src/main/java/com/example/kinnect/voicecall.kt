package com.example.kinnect

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas

class voicecall : AppCompatActivity() {

    // IMPORTANT: Replace with your Agora App ID from Agora Console
    private val AGORA_APP_ID = "YOUR_AGORA_APP_ID_HERE"

    private var rtcEngine: RtcEngine? = null
    private var isMuted = false
    private var isSpeakerOn = true
    private var isVideoCall = false
    private var channelName = ""
    private var targetName = "User"

    private lateinit var tvAvatarInitials: TextView
    private lateinit var tvCallerName: TextView
    private lateinit var tvCallTimer: TextView
    private lateinit var btnToggleVideo: TextView
    private lateinit var btnMuteMic: TextView
    private lateinit var btnEndCall: TextView
    private lateinit var btnToggleSpeaker: TextView
    private lateinit var avatarContainer: LinearLayout
    private lateinit var videoContainer: FrameLayout
    private lateinit var localVideoView: FrameLayout
    private lateinit var remoteVideoView: FrameLayout

    private var callSeconds = 0
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            callSeconds++
            val minutes = callSeconds / 60
            val seconds = callSeconds % 60
            tvCallTimer.text = String.format("%02d:%02d", minutes, seconds)
            timerHandler.postDelayed(this, 1000)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

        if (recordAudioGranted && (!isVideoCall || cameraGranted)) {
            initAgoraEngineAndJoin()
        } else {
            Toast.makeText(this, "Camera and Microphone permissions are required for calls.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                tvCallTimer.text = "Connected"
                startCallTimer()
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                if (isVideoCall) {
                    setupRemoteVideo(uid)
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                Toast.makeText(this@voicecall, "User disconnected", Toast.LENGTH_SHORT).show()
                endCall()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_voicecall)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        channelName = intent.getStringExtra("channelName") ?: "kinnect_call"
        targetName = intent.getStringExtra("targetName") ?: "User"
        isVideoCall = intent.getBooleanExtra("isVideoCall", false)

        tvAvatarInitials = findViewById(R.id.tvAvatarInitials)
        tvCallerName = findViewById(R.id.tvCallerName)
        tvCallTimer = findViewById(R.id.tvCallTimer)
        btnToggleVideo = findViewById(R.id.btnToggleVideo)
        btnMuteMic = findViewById(R.id.btnMuteMic)
        btnEndCall = findViewById(R.id.btnEndCall)
        btnToggleSpeaker = findViewById(R.id.btnToggleSpeaker)
        avatarContainer = findViewById(R.id.avatarContainer)
        videoContainer = findViewById(R.id.videoContainer)
        localVideoView = findViewById(R.id.localVideoView)
        remoteVideoView = findViewById(R.id.remoteVideoView)

        tvCallerName.text = targetName
        tvAvatarInitials.text = getInitials(targetName)

        setupCallUI()
        checkPermissionsAndStart()
    }

    private fun setupCallUI() {
        if (isVideoCall) {
            avatarContainer.visibility = View.GONE
            videoContainer.visibility = View.VISIBLE
        } else {
            avatarContainer.visibility = View.VISIBLE
            videoContainer.visibility = View.GONE
        }

        btnMuteMic.setOnClickListener {
            isMuted = !isMuted
            rtcEngine?.muteLocalAudioStream(isMuted)
            btnMuteMic.setBackgroundColor(if (isMuted) Color.parseColor("#C62828") else Color.parseColor("#3C8281"))
        }

        btnToggleSpeaker.setOnClickListener {
            isSpeakerOn = !isSpeakerOn
            rtcEngine?.setEnableSpeakerphone(isSpeakerOn)
            btnToggleSpeaker.setBackgroundColor(if (isSpeakerOn) Color.parseColor("#3C8281") else Color.GRAY)
        }

        btnToggleVideo.setOnClickListener {
            isVideoCall = !isVideoCall
            if (isVideoCall) {
                rtcEngine?.enableVideo()
                rtcEngine?.startPreview()
                setupLocalVideo()
                avatarContainer.visibility = View.GONE
                videoContainer.visibility = View.VISIBLE
            } else {
                rtcEngine?.disableVideo()
                avatarContainer.visibility = View.VISIBLE
                videoContainer.visibility = View.GONE
            }
        }

        btnEndCall.setOnClickListener {
            endCall()
        }
    }

    private fun checkPermissionsAndStart() {
        val reqPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (isVideoCall) reqPermissions.add(Manifest.permission.CAMERA)

        val missing = reqPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            initAgoraEngineAndJoin()
        }
    }

    private fun initAgoraEngineAndJoin() {
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = AGORA_APP_ID
                mEventHandler = rtcEventHandler
            }
            rtcEngine = RtcEngine.create(config)

            rtcEngine?.enableAudio()
            rtcEngine?.setEnableSpeakerphone(isSpeakerOn)

            if (isVideoCall) {
                rtcEngine?.enableVideo()
                rtcEngine?.startPreview()
                setupLocalVideo()
            }

            // Join Channel (Token can be null for testing with App ID only)
            rtcEngine?.joinChannel(null, channelName, "", 0)

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initialize call: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupLocalVideo() {
        localVideoView.removeAllViews()
        val surfaceView = SurfaceView(this)
        surfaceView.setZOrderMediaOverlay(true)
        localVideoView.addView(surfaceView)
        rtcEngine?.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
    }

    private fun setupRemoteVideo(uid: Int) {
        remoteVideoView.removeAllViews()
        val surfaceView = SurfaceView(this)
        remoteVideoView.addView(surfaceView)
        rtcEngine?.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    private fun startCallTimer() {
        timerHandler.post(timerRunnable)
    }

    private fun endCall() {
        timerHandler.removeCallbacks(timerRunnable)
        rtcEngine?.leaveChannel()
        RtcEngine.destroy()
        rtcEngine = null
        finish()
    }

    private fun getInitials(name: String): String {
        return name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(timerRunnable)
        rtcEngine?.leaveChannel()
        RtcEngine.destroy()
        rtcEngine = null
        super.onDestroy()
    }
}