package com.example.kinnect


import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class photopicker : AppCompatActivity() {

    enum class MediaFilter { ALL, PHOTOS, VIDEOS }

    data class MediaItem(
        val uri: Uri,
        val isVideo: Boolean,
        val durationStr: String?
    )

    private lateinit var btnCancel: TextView
    private lateinit var btnNext: TextView
    private lateinit var previewImage: ImageView
    private lateinit var btnSelectMultiple: TextView
    private lateinit var gridContainer: GridLayout

    private lateinit var tabGallery: LinearLayout
    private lateinit var tabPhoto: LinearLayout
    private lateinit var tabVideo: LinearLayout
    private lateinit var lineGallery: View
    private lateinit var linePhoto: View
    private lateinit var lineVideo: View

    private val allMediaList = mutableListOf<MediaItem>()
    private val selectedItems = mutableListOf<MediaItem>()
    private var isMultiSelectEnabled = true
    private var currentFilter = MediaFilter.ALL

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            loadDeviceMedia()
        } else {
            Toast.makeText(this, "Permission required to access media", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_photopicker)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        btnCancel = findViewById(R.id.btnCancel)
        btnNext = findViewById(R.id.btnNext)
        previewImage = findViewById(R.id.previewImage)
        btnSelectMultiple = findViewById(R.id.btnSelectMultiple)
        gridContainer = findViewById(R.id.mediaGrid)

        tabGallery = findViewById(R.id.tabGallery)
        tabPhoto = findViewById(R.id.tabPhoto)
        tabVideo = findViewById(R.id.tabVideo)
        lineGallery = findViewById(R.id.lineGallery)
        linePhoto = findViewById(R.id.linePhoto)
        lineVideo = findViewById(R.id.lineVideo)

        btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        btnNext.setOnClickListener {
            returnSelectedImages()
        }

        btnSelectMultiple.setOnClickListener {
            isMultiSelectEnabled = !isMultiSelectEnabled
            btnSelectMultiple.alpha = if (isMultiSelectEnabled) 1.0f else 0.5f
        }

        setupTabListeners()
        checkAndRequestPermissions()
    }

    private fun setupTabListeners() {
        tabGallery.setOnClickListener { switchFilter(MediaFilter.ALL) }
        tabPhoto.setOnClickListener { switchFilter(MediaFilter.PHOTOS) }
        tabVideo.setOnClickListener { switchFilter(MediaFilter.VIDEOS) }
    }

    private fun switchFilter(filter: MediaFilter) {
        currentFilter = filter
        lineGallery.visibility = if (filter == MediaFilter.ALL) View.VISIBLE else View.INVISIBLE
        linePhoto.visibility = if (filter == MediaFilter.PHOTOS) View.VISIBLE else View.INVISIBLE
        lineVideo.visibility = if (filter == MediaFilter.VIDEOS) View.VISIBLE else View.INVISIBLE

        renderGrid()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            loadDeviceMedia()
        }
    }

    private fun loadDeviceMedia() {
        allMediaList.clear()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val durationColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val mediaType = cursor.getInt(typeColumn)
                val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

                val contentUri = if (isVideo) {
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                } else {
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                }

                var durationStr: String? = null
                if (isVideo && durationColumn != -1) {
                    val durationMs = cursor.getLong(durationColumn)
                    val seconds = (durationMs / 1000) % 60
                    val minutes = (durationMs / (1000 * 60)) % 60
                    durationStr = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
                }

                allMediaList.add(MediaItem(contentUri, isVideo, durationStr))
            }
        }

        renderGrid()
    }

    private fun renderGrid() {
        gridContainer.removeAllViews()

        val filteredList = when (currentFilter) {
            MediaFilter.ALL -> allMediaList
            MediaFilter.PHOTOS -> allMediaList.filter { !it.isVideo }
            MediaFilter.VIDEOS -> allMediaList.filter { it.isVideo }
        }

        if (filteredList.isNotEmpty() && selectedItems.isEmpty()) {
            previewImage.setImageURI(filteredList[0].uri)
        }

        val displayMetrics = resources.displayMetrics
        val itemSize = displayMetrics.widthPixels / 4

        filteredList.forEach { item ->
            val frame = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = itemSize
                    height = itemSize
                }
            }

            val thumbnail = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(item.uri)
            }

            val badgeSize = (22 * displayMetrics.density).toInt()
            val badge = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.END
                    topMargin = (4 * displayMetrics.density).toInt()
                    marginEnd = (6 * displayMetrics.density).toInt()
                }
                gravity = android.view.Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }

            val durationTv = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                    bottomMargin = (4 * displayMetrics.density).toInt()
                    marginEnd = (6 * displayMetrics.density).toInt()
                }
                setBackgroundColor(0x88000000.toInt())
                setPadding((4 * displayMetrics.density).toInt(), 0, (4 * displayMetrics.density).toInt(), 0)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                visibility = if (item.isVideo) View.VISIBLE else View.GONE
                text = item.durationStr ?: "VIDEO"
            }

            updateItemBadge(item, badge)

            frame.addView(thumbnail)
            frame.addView(badge)
            frame.addView(durationTv)

            frame.setOnClickListener {
                previewImage.setImageURI(item.uri)

                if (selectedItems.contains(item)) {
                    selectedItems.remove(item)
                } else {
                    if (!isMultiSelectEnabled) {
                        selectedItems.clear()
                    }
                    selectedItems.add(item)
                }

                btnNext.text = "Next (${selectedItems.size})"
                renderGrid()
            }

            gridContainer.addView(frame)
        }
    }

    private fun updateItemBadge(item: MediaItem, badge: TextView) {
        val index = selectedItems.indexOf(item)
        if (index >= 0) {
            badge.text = (index + 1).toString()
            badge.setBackgroundResource(R.drawable.bg_badge_selected)
        } else {
            badge.text = ""
            badge.setBackgroundResource(R.drawable.bg_badge_empty)
        }
    }

    private fun returnSelectedImages() {
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "Select at least one photo or video", Toast.LENGTH_SHORT).show()
            return
        }

        val resultIntent = Intent()
        val clipData = ClipData.newUri(contentResolver, "selected_media", selectedItems[0].uri)

        for (i in 1 until selectedItems.size) {
            clipData.addItem(ClipData.Item(selectedItems[i].uri))
        }

        resultIntent.clipData = clipData
        resultIntent.data = selectedItems[0].uri
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}