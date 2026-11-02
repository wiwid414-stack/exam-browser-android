package id.sch.exam.browser

import org.json.JSONArray
import org.json.JSONObject
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.util.Collections

class ExamServer : NanoHTTPD(8080) {
    @Volatile private var code = ""
    @Volatile private var running = false
    @Volatile private var formUrl = ""
    @Volatile private var endAt = 0L
    private val adminToken = java.util.UUID.randomUUID().toString()
    private val participants = Collections.synchronizedSet(linkedSetOf<String>())

    fun sessionCode(): String = code
    fun hostToken(): String = adminToken
    fun participantNames(): List<String> = synchronized(participants) { participants.toList() }

    override fun serve(session: IHTTPSession): Response {
        val params = session.parameters
        fun value(key: String) = params[key]?.firstOrNull().orEmpty()
        fun body(): JSONObject {
            val map = HashMap<String, String>()
            return try {
                session.parseBody(map)
                JSONObject(map["postData"] ?: "{}")
            } catch (_: Exception) { JSONObject() }
        }
        fun json(obj: JSONObject, status: Response.IStatus = Response.Status.OK) =
            newFixedLengthResponse(status, "application/json; charset=utf-8", obj.toString())

        if (session.uri == "/state" && session.method == Method.GET) {
            if (value("code") != code || code.isBlank()) return json(JSONObject().put("error", "Kode sesi salah"), Response.Status.FORBIDDEN)
            if (running && System.currentTimeMillis() >= endAt) running = false
            return json(JSONObject().put("running", running).put("url", formUrl)
                .put("remainingSeconds", if (running) ((endAt - System.currentTimeMillis()).coerceAtLeast(0) / 1000) else 0))
        }

        if (session.method == Method.POST) {
            val data = body()
            when (session.uri) {
                "/join" -> {
                    if (data.optString("code") != code || code.isBlank()) return json(JSONObject().put("error", "Kode sesi salah"), Response.Status.FORBIDDEN)
                    val name = data.optString("name").trim().take(60)
                    if (name.isBlank()) return json(JSONObject().put("error", "Nama wajib diisi"), Response.Status.BAD_REQUEST)
                    participants.add(name)
                    return json(JSONObject().put("ok", true).put("message", "Berhasil bergabung"))
                }
                "/start" -> {
                    if (data.optString("adminToken") != adminToken) return json(JSONObject().put("error", "Akses pengawas ditolak"), Response.Status.FORBIDDEN)
                    val url = data.optString("url").trim()
                    val minutes = data.optInt("minutes", 60).coerceIn(1, 300)
                    if (!url.startsWith("https://") && !url.startsWith("http://")) return json(JSONObject().put("error", "URL tidak valid"), Response.Status.BAD_REQUEST)
                    formUrl = url
                    endAt = System.currentTimeMillis() + minutes * 60_000L
                    running = true
                    return json(JSONObject().put("ok", true))
                }
                "/stop" -> {
                    if (data.optString("code") != code) return json(JSONObject().put("error", "Kode host salah"), Response.Status.FORBIDDEN)
                    running = false
                    return json(JSONObject().put("ok", true))
                }
            }
        }
        if (session.uri == "/participants" && session.method == Method.GET) {
            if (value("code") != code) return json(JSONObject().put("error", "Kode sesi salah"), Response.Status.FORBIDDEN)
            val arr = JSONArray()
            participantNames().forEach { arr.put(it) }
            return json(JSONObject().put("participants", arr).put("running", running))
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
    }

    companion object {
        fun newCode(): String = (100000..999999).random().toString()
    }

    init { code = newCode() }
}
