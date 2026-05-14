/*
 * Anon Social — embedded Tor lifecycle.
 * Ported from gratis.anon.whistle.TorBridge (same lib + same boot pattern as
 * Anon Mail / Anon Mumble / Anon WhistleBlower).
 */
package app.pachli.anonsocial

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Owns the embedded Tor lifecycle and surfaces its bootstrap state.
 *
 * Underlying library: Guardian Project's tor-android (same one Anon XMPP /
 * Anon Mail / Anon Mumble / Anon WhistleBlower use). Broadcasts the same
 *   org.torproject.android.intent.action.STATUS
 * actions Orbot does so apps treat embedded Tor and Orbot uniformly.
 *
 * Why bindService instead of startForegroundService:
 *   The 5-second startForeground deadline on Android 14+ is uncatchable
 *   and crashes the app silently. bindService keeps the service alive via
 *   ref-counting and the AAR's manifest already declares the service.
 */
object TorBridge {

    const val TAG = "AnonSocialTor"

    const val ACTION_STATUS = "org.torproject.android.intent.action.STATUS"
    const val EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS"

    const val STATUS_ON = "ON"
    const val STATUS_OFF = "OFF"
    const val STATUS_STARTING = "STARTING"
    const val STATUS_STOPPING = "STOPPING"

    /** SOCKS5 host:port the embedded Tor binds to. */
    const val SOCKS_HOST = "127.0.0.1"
    const val SOCKS_PORT = 9050

    @Volatile var status: String = "UNKNOWN"
        private set

    val isReady: Boolean get() = status == STATUS_ON

    private var connection: ServiceConnection? = null
    private var globalReceiver: BroadcastReceiver? = null
    private val listeners = mutableListOf<(String) -> Unit>()

    fun start(context: Context) {
        if (connection != null) return
        installStatusReceiver(context.applicationContext)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        Log.d(TAG, "embedded Tor bound: $name")
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.w(TAG, "embedded Tor disconnected: $name")
                    }
                }
                connection = conn
                val intent = Intent(context, org.torproject.jni.TorService::class.java)
                val ok = context.applicationContext.bindService(
                    intent, conn, Context.BIND_AUTO_CREATE
                )
                Log.d(TAG, "embedded Tor bindService -> $ok")
            } catch (t: Throwable) {
                Log.e(TAG, "could not bind embedded Tor", t)
            }
        }, 2000L)
    }

    private fun installStatusReceiver(ctx: Context) {
        if (globalReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val s = intent?.getStringExtra(EXTRA_STATUS) ?: return
                status = s
                synchronized(listeners) { listeners.toList() }.forEach { it(s) }
            }
        }
        globalReceiver = r
        ContextCompat.registerReceiver(
            ctx, r, IntentFilter(ACTION_STATUS), ContextCompat.RECEIVER_EXPORTED
        )
    }

    /** Subscribe; receives current status immediately + on every change. */
    fun observe(listener: (String) -> Unit): () -> Unit {
        synchronized(listeners) { listeners.add(listener) }
        listener(status)
        return { synchronized(listeners) { listeners.remove(listener) } }
    }
}
