package com.example.kinnect

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.google.firebase.database.DataSnapshot
import com.example.kinnect.R

class CommentsAdapter(
    private val activity: AppCompatActivity,
    private val postId: String,
    private val myUid: String,
    private val container: LinearLayout,
    private val onReply: (rootId: String, name: String) -> Unit
) {
    enum class FilterMode {
        MOST_RELEVANT,
        LESS_IMPORTANT
    }

    data class Comment(
        val id: String,
        val authorUid: String,
        val text: String,
        val parentId: String,
        val createdAt: Long,
        val hasImage: Boolean
    )

    private var comments = emptyList<Comment>()
    private var likes: DataSnapshot? = null
    private var filterMode = FilterMode.MOST_RELEVANT

    fun submitComments(newComments: List<Comment>) {
        comments = newComments
        render()
    }

    fun submitLikes(snapshot: DataSnapshot) {
        likes = snapshot
        render()
    }

    fun setFilterMode(mode: FilterMode) {
        filterMode = mode
        render()
    }

    private fun render() {
        if (activity.isFinishing || activity.isDestroyed) return

        container.removeAllViews()

        val replies = comments
            .filter { it.parentId.isNotBlank() }
            .groupBy { it.parentId }

        val parents = comments.filter { it.parentId.isBlank() }

        val sortedParents = when (filterMode) {
            FilterMode.MOST_RELEVANT -> parents.sortedWith(
                compareByDescending<Comment> { c ->
                    val lCount = likes?.child(c.id)?.childrenCount?.toInt() ?: 0
                    val rCount = replies[c.id]?.size ?: 0
                    (lCount * 2) + rCount
                }.thenByDescending { it.createdAt }
            )
            FilterMode.LESS_IMPORTANT -> parents.sortedWith(
                compareBy<Comment> { c ->
                    val lCount = likes?.child(c.id)?.childrenCount?.toInt() ?: 0
                    val rCount = replies[c.id]?.size ?: 0
                    (lCount * 2) + rCount
                }.thenByDescending { it.createdAt }
            )
        }

        sortedParents.forEach { comment ->
            addItem(container, comment, false, replies)
        }
    }

    private fun addItem(
        parent: LinearLayout,
        comment: Comment,
        isReply: Boolean,
        replies: Map<String, List<Comment>>
    ) {
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.item_comment, parent, false)

        parent.addView(view)

        val avatar = view.findViewById<TextView>(R.id.cAvatar)
        val nameView = view.findViewById<TextView>(R.id.cName)
        val textView = view.findViewById<TextView>(R.id.cText)
        val imageView = view.findViewById<ImageView>(R.id.cImage)
        val density = activity.resources.displayMetrics.density

        if (isReply) {
            val sizePx = (28 * density).toInt()
            val lp = avatar.layoutParams as LinearLayout.LayoutParams
            lp.width = sizePx
            lp.height = sizePx
            avatar.layoutParams = lp
            avatar.textSize = 10f
        }

        var authorName = "User"

        Users.loadUserProfile(comment.authorUid) { name, picBase64 ->
            authorName = name
            nameView.text = name
            avatar.text = Users.initials(name)

            if (!picBase64.isNullOrEmpty()) {
                try {
                    val bytes = Base64.decode(picBase64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        val drawable = RoundedBitmapDrawableFactory.create(activity.resources, bmp)
                        drawable.isCircular = true
                        avatar.background = drawable
                        avatar.text = ""
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                val colors = intArrayOf(
                    0xFF8A3B47.toInt(),
                    0xFF6B3FA0.toInt(),
                    0xFF2D627A.toInt(),
                    0xFF8C5F2B.toInt(),
                    0xFF566928.toInt()
                )
                val index = Math.floorMod(comment.authorUid.hashCode(), colors.size)
                avatar.backgroundTintList = ColorStateList.valueOf(colors[index])
            }
        }

        if (comment.text.contains("@")) {
            val spannable = SpannableString(comment.text)
            val words = comment.text.split(" ")
            var searchStart = 0

            for (word in words) {
                if (word.startsWith("@") && word.length > 1) {
                    val start = comment.text.indexOf(word, searchStart)
                    val end = start + word.length
                    if (start >= 0) {
                        spannable.setSpan(
                            ForegroundColorSpan(Color.parseColor("#136B68")),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        spannable.setSpan(
                            StyleSpan(Typeface.BOLD),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                searchStart += word.length + 1
            }
            textView.text = spannable
        } else {
            textView.text = comment.text
        }

        textView.visibility = if (comment.text.isBlank()) View.GONE else View.VISIBLE
        imageView.visibility = if (comment.hasImage) View.VISIBLE else View.GONE

        if (comment.hasImage) {
            if (!Img.setIfCached(imageView, "c/${comment.id}")) {
                PostRepo.db.child("commentImages")
                    .child(comment.id)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val base64 = snapshot.getValue(String::class.java)
                            ?: return@addOnSuccessListener

                        Img.loadBase64(
                            imageView,
                            "c/${comment.id}",
                            base64,
                            720
                        )
                    }
            }
        }

        view.findViewById<TextView>(R.id.cTime).text = PostRepo.timeAgo(comment.createdAt)

        val likeNode = likes?.child(comment.id)
        val likeCount = likeNode?.childrenCount?.toInt() ?: 0
        val liked = likeNode?.hasChild(myUid) == true

        val likeView = view.findViewById<TextView>(R.id.cLike)
        likeView.setTextColor(
            if (liked) Color.parseColor("#136B68")
            else Color.parseColor("#333333")
        )
        likeView.setTypeface(null, Typeface.BOLD)

        likeView.setOnClickListener {
            PostRepo.setCommentLike(postId, comment.id, !liked)
        }

        val badge = view.findViewById<TextView>(R.id.cLikeBadge)
        badge.text = "❤️ $likeCount"
        badge.visibility = if (likeCount > 0) View.VISIBLE else View.GONE

        view.findViewById<View>(R.id.cReply).setOnClickListener {
            val rootId = if (isReply) comment.parentId else comment.id
            onReply(rootId, authorName)
        }

        if (!isReply) {
            val replyContainer = view.findViewById<LinearLayout>(R.id.repliesContainer)

            replies[comment.id]
                ?.sortedBy { it.createdAt }
                ?.forEach { reply ->
                    addItem(replyContainer, reply, true, replies)
                }
        }
    }
}