
package com.vyro.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.InputType
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable

class VyroHomeActivity : Activity() {

    private lateinit var api: SupabaseApi
    private lateinit var root: LinearLayout
    private lateinit var page: FrameLayout
    private lateinit var nav: LinearLayout

    private var videos = listOf<VyroVideo>()
    private var index = 0
    private var selectedTab = 0
    private var searchQuery = ""
    private var loading = false

    private val bg = Color.rgb(8, 8, 12)
    private val panel = Color.rgb(24, 24, 30)
    private val white = Color.WHITE
    private val pink = Color.rgb(254, 44, 85)
    private val muted = Color.rgb(175, 175, 185)

    private val tabs = listOf("Beranda", "Temukan", "Buat", "Inbox", "Profil")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0

        api = SupabaseApi(this)
        buildShell()
        loadFeed()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        page = FrameLayout(this).apply {
            setBackgroundColor(bg)
        }

        root.addView(
            page,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(0, 7, 0, 7)
        }

        root.addView(
            nav,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 62.dp()
            )
        )

        setContentView(root)
        renderNavigation()
    }

    private fun renderNavigation() {
        nav.removeAllViews()

        val icons = listOf("⌂", "⌕", "+", "✉", "●")

        tabs.forEachIndexed { i, label ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { navigate(i) }
            }

            val icon = TextView(this).apply {
                text = icons[i]
                textSize = if (i == 2) 27f else 23f
                gravity = Gravity.CENTER
                setTextColor(if (i == selectedTab) white else muted)
                typeface = Typeface.DEFAULT_BOLD
            }

            val text = TextView(this).apply {
                this.text = label
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(if (i == selectedTab) white else muted)
            }

            if (i == 2) {
                icon.background = GradientDrawable().apply {
                    cornerRadius = 7.dp().toFloat()
                    setColor(pink)
                }
                icon.setPadding(12.dp(), 0, 12.dp(), 0)
            }

            item.addView(icon)
            item.addView(text)

            nav.addView(
                item,
                LinearLayout.LayoutParams(0, 48.dp(), 1f)
            )
        }
    }

    private fun navigate(tab: Int) {
        selectedTab = tab
        renderNavigation()

        when (tab) {
            0 -> showFeed()
            1 -> showDiscover()
            2 -> chooseVideo()
            3 -> showInbox()
            4 -> showProfile()
        }
    }

    private fun loadFeed(query: String = "") {
        if (loading) return
        loading = true
        searchQuery = query

        page.removeAllViews()
        page.addView(centerMessage("Memuat video..."))

        Thread {
            try {
                val result = api.feed(query)
                runOnUiThread {
                    videos = result
                    index = 0
                    loading = false

                    if (selectedTab == 0) {
                        showFeed()
                    } else if (selectedTab == 1) {
                        showDiscoverResults()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading = false
                    showError("Tidak dapat memuat video", e.message ?: "")
                }
            }
        }.start()
    }

    private fun showFeed() {
        page.removeAllViews()

        if (videos.isEmpty()) {
            page.addView(
                centerMessage(
                    if (searchQuery.isBlank())
                        "Belum ada video\n\nJadilah yang pertama mengunggah video!"
                    else
                        "Video tidak ditemukan"
                )
            )
            return
        }

        index = index.coerceIn(0, videos.lastIndex)
        val video = videos[index]

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // Video fills the screen.
        val player = VideoView(this)
        container.addView(
            player,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        try {
            player.setVideoURI(Uri.parse(video.videoUrl))
            player.setOnPreparedListener {
                it.isLooping = true
                player.start()
            }
            player.setOnErrorListener { _, _, _ ->
                Toast.makeText(
                    this, "Video tidak dapat diputar", Toast.LENGTH_SHORT
                ).show()
                true
            }
        } catch (_: Exception) {
            // Keep controls available even if a video URL is invalid.
        }

        // Top tabs.
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
        }

        val following = topLabel("Mengikuti")
        val forYou = topLabel("Untuk Anda").apply {
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        }

        top.addView(following)
        top.addView(space(20))
        top.addView(forYou)

        container.addView(
            top,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        // Right-side interaction buttons.
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
            }
        }

        val avatar = TextView(this).apply {
            text = "V"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pink)
                setStroke(2.dp(), white)
            }
        }

        actions.addView(avatar, LinearLayout.LayoutParams(48.dp(), 48.dp()))
        actions.addView(space(18))

        val like = actionButton("♥", video.likes.toString())
        like.first.setTextColor(pink)
        like.second.setOnClickListener {
            runAction("Menyukai video") {
                api.toggleLike(video.id)
                loadFeed(searchQuery)
            }
        }
        actions.addView(like.first)
        actions.addView(like.second)
        actions.addView(space(15))

        val comment = actionButton("●", video.comments.toString())
        comment.first.setTextColor(white)
        comment.second.setOnClickListener { showCommentDialog(video) }
        actions.addView(comment.first)
        actions.addView(comment.second)
        actions.addView(space(15))

        val share = actionButton("➦", "Bagikan")
        share.second.setOnClickListener { shareVideo(video) }
        actions.addView(share.first)
        actions.addView(share.second)
        actions.addView(space(15))

        val next = actionButton("↓", "Berikut")
        next.second.setOnClickListener { nextVideo(1) }
        actions.addView(next.first)
        actions.addView(next.second)

        container.addView(
            actions,
            FrameLayout.LayoutParams(
                70.dp(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT or Gravity.CENTER_VERTICAL
            )
        )

        // Caption overlay at the bottom.
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 40.dp(), 90.dp(), 20.dp())
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.BLACK, Color.TRANSPARENT)
            )
        }

        details.addView(TextView(this).apply {
            text = "@${video.username.ifBlank { "pengguna_vyro" }}"
            textSize = 15f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        })

        details.addView(TextView(this).apply {
            text = video.caption.ifBlank { "Video di VYRO" }
            textSize = 14f
            setTextColor(white)
            setPadding(0, 7.dp(), 0, 7.dp())
        })

        details.addView(TextView(this).apply {
            text = "♫  Suara asli · VYRO"
            textSize = 12f
            setTextColor(white)
        })

        container.addView(
            details,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        // Swipe left/right or up/down to change videos.
        var startX = 0f
        var startY = 0f

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val dx = event.x - startX
                    val dy = event.y - startY

                    if (kotlin.math.abs(dy) > 70 &&
                        kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                        nextVideo(if (dy < 0) 1 else -1)
                    } else if (kotlin.math.abs(dx) > 100) {
                        nextVideo(if (dx < 0) 1 else -1)
                    }
                    true
                }

                else -> true
            }
        }

        page.addView(
            container,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun nextVideo(direction: Int) {
        if (videos.isEmpty()) return
        index = (index + direction + videos.size) % videos.size
        showFeed()
    }

    private fun showDiscover() {
        page.removeAllViews()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 18.dp(), 16.dp(), 8.dp())
            setBackgroundColor(bg)
        }

        layout.addView(title("Temukan"))

        val search = EditText(this).apply {
            hint = "Cari video..."
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT
            background = rounded(panel, 14)
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
        }

        layout.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 50.dp()
            )
        )

        val button = Button(this).apply {
            text = "Cari"
            setOnClickListener {
                selectedTab = 1
                renderNavigation()
                loadFeed(search.text.toString().trim())
            }
        }
        layout.addView(button)

        layout.addView(
            TextView(this).apply {
                text = "Video terbaru"
                textSize = 17f
                setTextColor(white)
                setPadding(0, 18.dp(), 0, 10.dp())
            }
        )

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        videos.forEachIndexed { i, v ->
            val row = TextView(this).apply {
                text = "▶  ${v.caption.ifBlank { "Video VYRO" }}\n     @${v.username}  ·  ♥ ${v.likes}"
                textSize = 14f
                setTextColor(white)
                setPadding(14.dp(), 18.dp(), 14.dp(), 18.dp())
                background = rounded(panel, 12)
                setOnClickListener {
                    index = i
                    selectedTab = 0
                    renderNavigation()
                    showFeed()
                }
            }

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = 8.dp()
            list.addView(row, params)
        }

        scroll.addView(list)
        layout.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        page.addView(layout)
    }

    private fun showDiscoverResults() {
        if (selectedTab == 1) showDiscover()
    }

    private fun chooseVideo() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
        }
        startActivityForResult(
            Intent.createChooser(intent, "Pilih video"),
            1001
        )
    }

    @Deprecated("Use Activity Result API when modernizing the project")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001 &&
            resultCode == RESULT_OK &&
            data?.data != null) {
            showUploadDialog(data.data!!)
        }
    }

    private fun showUploadDialog(uri: Uri) {
        val input = EditText(this).apply {
            hint = "Tulis caption video..."
            setTextColor(white)
            setHintTextColor(muted)
            background = rounded(panel, 12)
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
        }

        AlertDialog.Builder(this)
            .setTitle("Unggah ke VYRO")
            .setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Publikasikan") { _, _ ->
                val caption = input.text.toString().trim()
                uploadVideo(uri, caption)
            }
            .show()
    }

    private fun uploadVideo(uri: Uri, caption: String) {
        page.removeAllViews()
        page.addView(centerMessage("Mengunggah video..."))

        Thread {
            try {
                api.uploadVideo(uri, caption)
                runOnUiThread {
                    Toast.makeText(
                        this, "Video berhasil diunggah", Toast.LENGTH_LONG
                    ).show()
                    selectedTab = 0
                    renderNavigation()
                    loadFeed()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showError(
                        "Unggah gagal",
                        e.message ?: "Periksa koneksi dan izin Supabase Storage."
                    )
                }
            }
        }.start()
    }

    private fun showCommentDialog(video: VyroVideo) {
        val input = EditText(this).apply {
            hint = "Tulis komentar..."
            setTextColor(white)
            setHintTextColor(muted)
        }

        AlertDialog.Builder(this)
            .setTitle("Komentar")
            .setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Kirim") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    runAction("Mengirim komentar") {
                        api.addComment(video.id, text)
                        runOnUiThread {
                            Toast.makeText(
                                this, "Komentar terkirim", Toast.LENGTH_SHORT
                            ).show()
                            loadFeed(searchQuery)
                        }
                    }
                }
            }
            .show()
    }

    private fun shareVideo(video: VyroVideo) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Lihat video ini di VYRO!\n${video.caption}\n${video.videoUrl}"
            )
        }
        startActivity(Intent.createChooser(intent, "Bagikan video"))
    }

    private fun showInbox() {
        page.removeAllViews()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 24.dp(), 20.dp(), 20.dp())
            setBackgroundColor(bg)
        }

        layout.addView(title("Kotak masuk"))
        layout.addView(space(20))

        layout.addView(infoCard(
            "Aktivitas",
            "Notifikasi komentar, suka, dan pengikut akan ditampilkan di sini setelah backend notifikasi terhubung."
        ))

        layout.addView(space(12))

        layout.addView(infoCard(
            "Pesan",
            "Pesan langsung memerlukan tabel percakapan dan kebijakan akses Supabase."
        ))

        page.addView(layout)
    }

    private fun showProfile() {
        page.removeAllViews()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(18.dp(), 30.dp(), 18.dp(), 18.dp())
            setBackgroundColor(bg)
        }

        layout.addView(TextView(this).apply {
            text = "V"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(pink)
            }
        }, LinearLayout.LayoutParams(88.dp(), 88.dp()))

        layout.addView(TextView(this).apply {
            text = "@akun_vyro"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            setPadding(0, 14.dp(), 0, 6.dp())
        })

        layout.addView(TextView(this).apply {
            text = "Profil VYRO"
            textSize = 14f
            setTextColor(muted)
        })

        layout.addView(space(18))

        val edit = Button(this).apply {
            text = "Muat ulang video"
            setOnClickListener { loadFeed() }
        }
        layout.addView(edit)

        layout.addView(space(18))

        layout.addView(infoCard(
         
            "Video di VYRO",
            "Feed video tersedia dari Supabase. Daftar video khusus milik akun, foto profil, bio, statistik, dan edit profil perlu dihubungkan dengan tabel profil pengguna."
        ))

        page.addView(
            ScrollView(this).apply {
                addView(layout)
            }
        )
    }

    private fun runAction(label: String, action: () -> Unit) {
        Toast.makeText(this, "$label...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                action()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        e.message ?: "Terjadi kesalahan",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun showError(title: String, detail: String) {
        page.removeAllViews()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())
            setBackgroundColor(bg)
        }

        layout.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(white)
            gravity = Gravity.CENTER
        })

        layout.addView(TextView(this).apply {
            text = detail
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 12.dp(), 0, 20.dp())
        })

        layout.addView(Button(this).apply {
            text = "Coba lagi"
            setOnClickListener {
                loadFeed(searchQuery)
            }
        })

        page.addView(layout)
    }

    private fun centerMessage(message: String): View {
        return TextView(this).apply {
            text = message
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(white)
            setPadding(24.dp(), 24.dp(), 24.dp(), 24.dp())

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun title(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            setPadding(0, 0, 0, 16.dp())
        }
    }

    private fun topLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(muted)
        }
    }

    private fun actionButton(
        symbol: String,
        caption: String
    ): Pair<TextView, TextView> {
        val icon = TextView(this).apply {
            text = symbol
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(white)
        }

        val label = TextView(this).apply {
            text = caption
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(white)
        }

        return Pair(icon, label)
    }

    private fun infoCard(
        heading: String,
        description: String
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            background = rounded(panel, 14)
        }

        card.addView(TextView(this).apply {
            text = heading
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
        })

        card.addView(TextView(this).apply {
            text = description
            textSize = 13f
            setTextColor(muted)
            setPadding(0, 8.dp(), 0, 0)
        })

        return card
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.dp().toFloat()
        }
    }

    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                height.dp()
            )
        }
    }

    private fun Int.dp(): Int {
        return (
            this * resources.displayMetrics.density
        ).toInt()
    }
}

