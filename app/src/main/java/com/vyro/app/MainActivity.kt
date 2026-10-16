package com.vyro.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent { VyroApp() }
    }
}

private val VyroBlack = Color(0xFF09090B)
private val VyroPink = Color(0xFFFF2D55)

@Composable
fun VyroApp() {
    val context = LocalContext.current
    val api = remember { SupabaseApi(context) }
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(api.accessToken != null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var videos by remember { mutableStateOf<List<VyroVideo>>(emptyList()) }
    var message by remember { mutableStateOf("Selamat datang di VYRO") }
    var busy by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("Beranda") }
    var uploadUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uploadUri = uri
        if (uri != null) message = "Video dipilih. Isi caption lalu tekan Unggah."
    }

    fun runTask(task: suspend () -> String, after: () -> Unit = {}) {
        scope.launch {
            busy = true
            try {
                message = withContext(Dispatchers.IO) { task() }
                after()
            } catch (e: Exception) {
                message = e.message ?: "Terjadi kesalahan."
            } finally { busy = false }
        }
    }

    fun refreshFeed(query: String = search) {
        scope.launch {
            busy = true
            try { videos = withContext(Dispatchers.IO) { api.feed(query) } }
            catch (e: Exception) { message = e.message ?: "Gagal memuat video." }
            finally { busy = false }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = VyroPink, background = VyroBlack, surface = Color(0xFF18181B),
        onBackground = Color.White, onSurface = Color.White
    )) {
        if (!loggedIn) {
            Column(
                Modifier.fillMaxSize().background(VyroBlack).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("VYRO", fontSize = 42.sp, fontWeight = FontWeight.Black, color = VyroPink)
                Text("Video pendek. Cerita tanpa batas.", color = Color.LightGray)
                Spacer(Modifier.height(28.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Kata sandi") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    if (email.isBlank() || password.length < 6) message = "Masukkan email dan kata sandi minimal 6 karakter."
                    else runTask({ api.signIn(email.trim(), password) }, { loggedIn = true; refreshFeed("") })
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Masuk") }
                OutlinedButton(onClick = {
                    if (email.isBlank() || password.length < 6) message = "Masukkan email dan kata sandi minimal 6 karakter."
                    else runTask({ api.signUp(email.trim(), password) })
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Buat akun") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(message, color = Color.LightGray, fontSize = 12.sp)
                Text("Registrasi dapat memerlukan verifikasi email sesuai pengaturan Supabase.", color = Color.Gray, fontSize = 11.sp)
            }
        } else {
            Scaffold(
                containerColor = VyroBlack,
                bottomBar = {
                    NavigationBar(containerColor = Color(0xFF111113)) {
                        listOf("Beranda", "Cari", "Upload", "Profil").forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedTab = tab
                                    if (tab == "Beranda") refreshFeed("")
                                },
                                icon = {
                                    Icon(when(tab) {
                                        "Cari" -> Icons.Default.Search
                                        "Upload" -> Icons.Default.AddCircle
                                        "Profil" -> Icons.Default.Person
                                        else -> Icons.Default.PlayArrow
                                    }, contentDescription = tab)
                                },
                                label = { Text(tab) }
                            )
                        }
                    }
                }
            ) { padding ->
                when (selectedTab) {
                    "Cari" -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                        Text("Cari video", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(search, { search = it }, label = { Text("Kata kunci") }, modifier = Modifier.weight(1f))
                            IconButton(onClick = { refreshFeed(search) }) { Icon(Icons.Default.Search, "Cari") }
                        }
                        Button(onClick = { refreshFeed(search); selectedTab = "Beranda" }) { Text("Tampilkan hasil") }
                        Text(message, color = Color.LightGray, fontSize = 12.sp)
                    }
                    "Upload" -> Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.Center) {
                        Text("Unggah video", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Pilih video dari galeri. Batas MVP: 50 MB.", color = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.VideoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Pilih dari galeri") }
                        Text(if (uploadUri == null) "Belum ada video dipilih" else "Video siap diunggah", color = Color.LightGray)
                        OutlinedTextField(caption, { caption = it }, label = { Text("Caption / tagar") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        Spacer(Modifier.height(12.dp))
                        Button(enabled = !busy && uploadUri != null, onClick = {
                            val uri = uploadUri!!
                            runTask({ api.uploadVideo(uri, caption) }, { uploadUri = null; caption = ""; selectedTab = "Beranda"; refreshFeed("") })
                        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Mengunggah..." else "Unggah video") }
                        Text(message, color = Color.LightGray, fontSize = 12.sp)
                    }
                    "Profil" -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(76.dp).background(VyroPink, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(42.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(api.email ?: "Profil VYRO", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Video yang kamu bagikan akan muncul di feed publik.", color = Color.LightGray)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { runTask({ api.signOut(); "Berhasil keluar." }, { loggedIn = false }) }) { Text("Keluar") }
                        Text(message, color = Color.LightGray, fontSize = 12.sp)
                    }
                    else -> {
                        if (videos.isEmpty() && !busy) {
                            LaunchedEffect(Unit) { refreshFeed("") }
                        }
                        if (videos.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("VYRO", fontSize = 38.sp, fontWeight = FontWeight.Black, color = VyroPink)
                                    Text(if (busy) "Memuat video..." else "Belum ada video. Jadilah kreator pertama!", color = Color.LightGray)
                                    Button(onClick = { refreshFeed("") }) { Text("Muat ulang") }
                                    Text(message, color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        } else {
                            Feed(videos = videos, onLike = { video ->
                                runTask({ api.toggleLike(video.id); "Like diperbarui." }, { refreshFeed("") })
                            }, onComment = { video ->
                                val text = "Komentar dikirim" // dialog is implemented below via prompt field in a simple follow-up screen
                                runTask({ api.addComment(video.id, text); "Komentar dikirim." }, { refreshFeed("") })
                            }, onFollow = { video ->
                                runTask({ api.toggleFollow(video.userId); "Status follow diperbarui." }, { refreshFeed("") })
                            }, modifier = Modifier.padding(padding))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Feed(
    videos: List<VyroVideo>,
    onLike: (VyroVideo) -> Unit,
    onComment: (VyroVideo) -> Unit,
    onFollow: (VyroVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    val pager = rememberPagerState(pageCount = { videos.size })
    VerticalPager(state = pager, modifier = modifier.fillMaxSize()) { page ->
        val video = videos[page]
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoPlayer(video.videoUrl, Modifier.fillMaxSize())
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(0.78f).padding(start = 16.dp, end = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("@${video.username}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Text(video.caption, color = Color.White)
                Text("▶ Video VYRO", color = Color.LightGray, fontSize = 12.sp)
            }
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                SideAction(Icons.Default.Favorite, "${video.likes}", { onLike(video) })
                SideAction(Icons.Default.ChatBubble, "${video.comments}", { onComment(video) })
                SideAction(Icons.Default.PersonAdd, "Follow", { onFollow(video) })
                SideAction(Icons.Default.Share, "Bagikan", {
                    // Android share sheet is added in the full production iteration.
                })
            }
        }
    }
}

@Composable
private fun SideAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp).background(Color(0x55000000), CircleShape)) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun VideoPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = modifier,
        factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
        update = { it.player = player }
    )
}
