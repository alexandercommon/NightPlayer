package app.nightplayer

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

data class TimerStatus(val elapsedMs: Long, val fadeAtMs: Long, val stopAtMs: Long)

enum class TimerCommand { Restart, Cancel }

object Store {
    private lateinit var prefs: SharedPreferences
    val speed = MutableStateFlow(1f)
    val sleepMinutes = MutableStateFlow(60)
    val fadeMinutes = MutableStateFlow(15)
    val pauseAtEnd = MutableStateFlow(false)
    val folder = MutableStateFlow<String?>(null)
    val timer = MutableStateFlow<TimerStatus?>(null)
    val commands = MutableSharedFlow<TimerCommand>(extraBufferCapacity = 8)

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("nightplayer", Context.MODE_PRIVATE)
        speed.value = prefs.getFloat("speed", 1f)
        sleepMinutes.value = prefs.getInt("sleep", 60)
        fadeMinutes.value = prefs.getInt("fade", 15)
        pauseAtEnd.value = prefs.getBoolean("pauseAtEnd", false)
        folder.value = prefs.getString("folder", null)
    }

    fun setSpeed(v: Float) {
        speed.value = v
        prefs.edit().putFloat("speed", v).apply()
    }

    fun setSleepMinutes(v: Int) {
        sleepMinutes.value = v
        prefs.edit().putInt("sleep", v).apply()
    }

    fun setFadeMinutes(v: Int) {
        fadeMinutes.value = v
        prefs.edit().putInt("fade", v).apply()
    }

    fun setPauseAtEnd(v: Boolean) {
        pauseAtEnd.value = v
        prefs.edit().putBoolean("pauseAtEnd", v).apply()
    }

    fun setFolder(v: String?) {
        folder.value = v
        prefs.edit().putString("folder", v).apply()
    }

    var lastTrack: String?
        get() = prefs.getString("lastTrack", null)
        set(v) {
            prefs.edit().putString("lastTrack", v).apply()
        }

    fun order(folder: String): List<String> =
        prefs.getString("order:$folder", null)?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()

    fun saveOrder(folder: String, ids: List<String>?) {
        if (ids == null) prefs.edit().remove("order:$folder").apply()
        else prefs.edit().putString("order:$folder", ids.joinToString("\n")).apply()
    }

    fun position(id: String): Long = prefs.getLong("pos:$id", 0L)

    fun savePosition(id: String, ms: Long) {
        prefs.edit().putLong("pos:$id", ms.coerceAtLeast(0L)).apply()
    }
}
