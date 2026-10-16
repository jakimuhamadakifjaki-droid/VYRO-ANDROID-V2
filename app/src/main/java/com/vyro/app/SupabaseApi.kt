package com.vyro.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

data class VyroVideo(
    val id: String,
    val userId: String,
    val username: String,
    val caption: String,
    val videoUrl: String,
    val likes: Int,
    val comments: Int
)

class SupabaseApi(private val context: Context) {
    private val prefs = context.getSharedPreferences("vyro_session", Context.MODE_PRIVATE)
    var accessToken: String?
        get() = prefs.getString("access_token", null)
        private set(value) { prefs.edit().putString("access_token", value).apply() }
    var userId: String?
        get() = prefs.getString("user_id", null)
        private set(value) { prefs.edit().putString("user_id", value).apply() }
    var email: String?
        get() = prefs.getString("email", null)
        private set(value) { prefs.edit().putString("email", value).apply() }

    private val base get() = SupabaseConfig.SUPABASE_URL.trimEnd('/')
    private val key get() = SupabaseConfig.SUPABASE_PUBLISHABLE_KEY

    private fun request(
        method: String,
        path: String,
        body: ByteArray? = null,
        contentType: String = "application/json",
        extraHeaders: Map<String, String> = emptyMap(),
        authenticated: Boolean = true
    ): String {
        require(!base.contains("YOUR_PROJECT_ID") && key != "YOUR_SUPABASE_PUBLISHABLE_KEY") {
            "Isi SUPABASE_URL dan SUPABASE_PUBLISHABLE_KEY di SupabaseConfig.kt terlebih dahulu."
        }
        val conn = (URL("$base$path").openConnection() as HttpURLConnection)
        conn.requestMethod = method
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.setRequestProperty("apikey", key)
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Content-Type", contentType)
        conn.setRequestProperty("Authorization", "Bearer ${if (authenticated) accessToken ?: key else key}")
        extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { input ->
            val out = ByteArrayOutputStream()
            input.copyTo(out)
            out.toString(Charsets.UTF_8.name())
        } ?: ""
        conn.disconnect()
        if (code !in 200..299) {
            val message = try { JSONObject(text).optString("message", text) } catch (_: Exception) { text }
            throw IllegalStateException("HTTP $code: $message")
        }
        return text
    }

    fun signUp(emailValue: String, password: String): String {
        val response = request("POST", "/auth/v1/signup",
            JSONObject().put("email", emailValue).put("password", password).toString().toByteArray(),
            authenticated = false)
        val obj = JSONObject(response)
        val user = obj.optJSONObject("user") ?: obj
        val id = user.optString("id")
        if (id.isNotBlank()) {
            userId = id
            email = emailValue
            obj.optString("access_token").takeIf { it.isNotBlank() }?.let { accessToken = it }
        }
        return if (obj.optString("access_token").isNullOrBlank()) {
            "Pendaftaran berhasil. Periksa email untuk verifikasi, lalu login."
        } else "Pendaftaran berhasil."
    }

    fun signIn(emailValue: String, password: String): String {
        val response = request("POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", emailValue).put("password", password).toString().toByteArray(),
            authenticated = false)
        val obj = JSONObject(response)
        accessToken = obj.getString("access_token")
        userId = obj.getJSONObject("user").getString("id")
        email = emailValue
        return "Login berhasil."
    }

    fun signOut() {
        try { request("POST", "/auth/v1/logout", "{}".toByteArray()) } catch (_: Exception) {}
        prefs.edit().clear().apply()
    }

    fun feed(search: String = ""): List<VyroVideo> {
        val q = if (search.isBlank()) "" else "&caption=ilike.*${URLEncoder.encode(search, "UTF-8")}*"
        val json = request("GET", "/rest/v1/videos?select=id,user_id,caption,video_url,like_count,comment_count&status=eq.published&order=created_at.desc&limit=40$q")
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            VyroVideo(
                id = o.getString("id"),
                userId = o.getString("user_id"),
                username = o.optJSONObject("profiles")?.optString("username")?.takeIf { !it.isNullOrBlank() } ?: "kreator",
                caption = o.optString("caption"),
                videoUrl = o.optString("video_url"),
                likes = o.optInt("like_count"),
                comments = o.optInt("comment_count")
            )
        }
    }

    fun toggleLike(videoId: String) {
        val uid = userId ?: error("Login dulu.")
        val existing = request("GET", "/rest/v1/likes?select=id&video_id=eq.$videoId&user_id=eq.$uid")
        if (JSONArray(existing).length() > 0) {
            request("DELETE", "/rest/v1/likes?video_id=eq.$videoId&user_id=eq.$uid")
        } else {
            request("POST", "/rest/v1/likes", JSONObject().put("video_id", videoId).put("user_id", uid).toString().toByteArray(),
                extraHeaders = mapOf("Prefer" to "return=minimal"))
        }
    }

    fun addComment(videoId: String, comment: String) {
        val uid = userId ?: error("Login dulu.")
        request("POST", "/rest/v1/comments",
            JSONObject().put("video_id", videoId).put("user_id", uid).put("body", comment).toString().toByteArray(),
            extraHeaders = mapOf("Prefer" to "return=minimal"))
    }

    fun toggleFollow(targetUserId: String) {
        val uid = userId ?: error("Login dulu.")
        require(uid != targetUserId) { "Tidak bisa mengikuti akun sendiri." }
        val existing = request("GET", "/rest/v1/follows?select=follower_id&follower_id=eq.$uid&following_id=eq.$targetUserId")
        if (JSONArray(existing).length() > 0) {
            request("DELETE", "/rest/v1/follows?follower_id=eq.$uid&following_id=eq.$targetUserId")
        } else {
            request("POST", "/rest/v1/follows",
                JSONObject().put("follower_id", uid).put("following_id", targetUserId).toString().toByteArray(),
                extraHeaders = mapOf("Prefer" to "return=minimal"))
        }
    }

    fun uploadVideo(uri: Uri, caption: String): String {
        val uid = userId ?: error("Login dulu sebelum upload.")
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "video/mp4"
        val filename = "${UUID.randomUUID()}.${if (mime.contains("webm")) "webm" else "mp4"}"
        val input = resolver.openInputStream(uri) ?: error("Tidak bisa membaca video.")
        val bytes = input.use { it.readBytes() }
        require(bytes.size <= 50 * 1024 * 1024) { "Video lebih dari 50 MB. Pilih video yang lebih kecil untuk MVP." }
        val path = "$uid/$filename"
        request("POST", "/storage/v1/object/${SupabaseConfig.VIDEO_BUCKET}/$path",
            bytes, mime, mapOf("x-upsert" to "false"))
        val publicUrl = "$base/storage/v1/object/public/${SupabaseConfig.VIDEO_BUCKET}/$path"
        request("POST", "/rest/v1/videos",
            JSONObject().put("user_id", uid).put("caption", caption)
                .put("video_url", publicUrl).put("storage_path", path).put("status", "published")
                .toString().toByteArray(),
            extraHeaders = mapOf("Prefer" to "return=minimal"))
        return "Video berhasil diunggah."
    }
}
