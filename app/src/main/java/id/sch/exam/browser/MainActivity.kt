package id.sch.exam.browser

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var server: ExamServer? = null
    private var pollTask: Runnable? = null
    private lateinit var root: LinearLayout
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun base(title: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(Color.rgb(246, 248, 252))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        root.addView(TextView(this).apply {
            text = title; textSize = 25f; setTextColor(Color.rgb(20, 55, 100))
        })
        root.addView(TextView(this).apply {
            text = "Ujian berbasis Google Form"; textSize = 14f; setTextColor(Color.DKGRAY)
            setPadding(0, dp(4), 0, dp(18))
        })
    }

    private fun label(text: String) {
        root.addView(TextView(this).apply { this.text = text; textSize = 15f; setTextColor(Color.DKGRAY); setPadding(0, dp(10), 0, dp(4)) })
    }
    private fun field(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint; setText(value); textSize = 16f
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = getDrawable(android.R.drawable.edit_text)
        root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }
    private fun button(text: String, action: () -> Unit) {
        root.addView(Button(this).apply { this.text = text; setOnClickListener { action() } },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(4) })
    }
    private fun showHome() {
        base("EXAM BROWSER SD")
        label("Pilih peran untuk memulai")
        button("MODE HOST — Pengawas") { showHostSetup() }
        button("MODE PESERTA — Siswa") { showParticipantSetup() }
        label("Catatan: perangkat harus berada pada jaringan Wi-Fi/LAN yang sama.")
    }

    private fun showHostSetup() {
        base("HOST / PENGAWAS")
        val duration = field("Durasi ujian (menit)", "60")
        val form = field("Tempel tautan Google Form")
        val s = ExamServer()
        try {
            s.start()
            server = s
        } catch (e: Exception) {
            label("Server gagal dijalankan: ${e.message}")
            return
        }
        val address = localIp()
        label("SERVER AKTIF")
        label("Alamat peserta: http://$address:8080")
        label("Kode sesi: ${s.sessionCode()}")
        val status = TextView(this).apply { textSize = 16f; setTextColor(Color.BLACK) }
        root.addView(status)
        val people = TextView(this).apply { textSize = 15f; setTextColor(Color.DKGRAY) }
        root.addView(people)
        button("MULAI UJIAN") {
            val mins = duration.text.toString().toIntOrNull()?.coerceIn(1, 300) ?: 60
            val url = form.text.toString().trim()
            io.execute {
                val result = request("http://127.0.0.1:8080/start", JSONObject().put("adminToken", s.hostToken()).put("minutes", mins).put("url", url))
                runOnUiThread { status.text = result?.optString("error") ?: if (result?.optBoolean("ok") == true) "Ujian sedang berlangsung" else "Gagal memulai ujian" }
            }
        }
        button("HENTIKAN UJIAN") {
            io.execute {
                request("http://127.0.0.1:8080/stop", JSONObject().put("adminToken", s.hostToken()))
                runOnUiThread { status.text = "Ujian dihentikan oleh pengawas" }
            }
        }
        button("KEMBALI") { s.stop(); server = null; showHome() }
        val refresh = object : Runnable {
            override fun run() {
                if (server !== s) return
                people.text = "Peserta bergabung (${s.participantNames().size}):\n" + s.participantNames().joinToString("\n").ifBlank { "Belum ada peserta" }
                handler.postDelayed(this, 2000)
            }
        }
        handler.post(refresh)
    }

    private fun showParticipantSetup() {
        base("PESERTA / SISWA")
        val host = field("Alamat host, contoh 192.168.1.10")
        val code = field("Kode sesi 6 digit")
        val name = field("Nama peserta")
        button("GABUNG UJIAN") {
            val baseUrl = "http://" + host.text.toString().trim().removePrefix("http://").removeSuffix("/")
            val payload = JSONObject().put("code", code.text.toString().trim()).put("name", name.text.toString().trim())
            io.execute {
                val result = request("$baseUrl/join", payload)
                runOnUiThread {
                    if (result?.optBoolean("ok") == true) showWaiting(baseUrl, code.text.toString().trim())
                    else Toast.makeText(this, result?.optString("error") ?: "Tidak dapat terhubung ke host", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showWaiting(baseUrl: String, code: String) {
        base("MENUNGGU PENGAWAS")
        val status = TextView(this).apply { text = "Anda berhasil bergabung. Tunggu sampai pengawas memulai ujian."; textSize = 17f }
        root.addView(status)
        val web = WebView(this).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }
        root.addView(web, LinearLayout.LayoutParams(-1, dp(620)))
        val task = object : Runnable {
            override fun run() {
                io.execute {
                    val state = getJson("$baseUrl/state?code=$code")
                    runOnUiThread {
                        if (state == null) status.text = "Koneksi ke host terputus."
                        else if (state.optBoolean("running")) {
                            val target = state.optString("url")
                            if (web.visibility != View.VISIBLE) {
                                status.text = "Ujian dimulai. Sisa waktu: ${state.optLong("remainingSeconds") / 60} menit"
                                web.visibility = View.VISIBLE
                                web.loadUrl(target)
                            } else status.text = "Ujian berlangsung — sisa ${state.optLong("remainingSeconds") / 60} menit"
                        } else if (web.visibility == View.VISIBLE) {
                            web.visibility = View.GONE
                            web.loadUrl("about:blank")
                            status.text = "Ujian telah dihentikan atau waktu habis."
                        }
                    }
                }
                handler.postDelayed(this, 3000)
            }
        }
        pollTask = task
        handler.post(task)
        button("KELUAR") { pollTask?.let { handler.removeCallbacks(it) }; showHome() }
    }

    private fun request(url: String, data: JSONObject): JSONObject? = try {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 5000; readTimeout = 5000
            doOutput = true; setRequestProperty("Content-Type", "application/json")
        }
        c.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
        val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        c.disconnect()
        JSONObject(text)
    } catch (_: Exception) { null }

    private fun getJson(url: String): JSONObject? = try {
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 5000 }
        val text = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        JSONObject(text)
    } catch (_: Exception) { null }

    private fun localIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .first { it is Inet4Address && !it.isLoopbackAddress && it.hostAddress.startsWith("192.") || it is Inet4Address && !it.isLoopbackAddress }
            .hostAddress
    } catch (_: Exception) { "IP-ANDROID-HOST" }

    override fun onDestroy() {
        pollTask?.let { handler.removeCallbacks(it) }
        server?.stop()
        io.shutdownNow()
        super.onDestroy()
    }
}
