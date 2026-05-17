package app.pachli.core.network

import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Response

// Decouples "is the embedded Tor up?" from the module that owns Tor.
// Owner module (the app's TorBridge) flips `ready` to true on STATUS_ON.
// core/network's OkHttp interceptor reads it. No tor-android dep needed
// in core/network.
object TorReadiness {
    private const val TAG = "AnonSocialTor"

    @Volatile private var ready: Boolean = false
    private val waiters = mutableListOf<CountDownLatch>()

    val isReady: Boolean get() = ready

    @Synchronized
    fun markReady() {
        if (ready) return
        ready = true
        waiters.forEach { it.countDown() }
        waiters.clear()
    }

    @Synchronized
    fun markNotReady() {
        ready = false
    }

    fun awaitReady(timeoutMs: Long): Boolean {
        if (ready) return true
        val latch = CountDownLatch(1)
        synchronized(this) {
            if (ready) return true
            waiters.add(latch)
        }
        val ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        if (!ok) {
            synchronized(this) { waiters.remove(latch) }
            Log.w(TAG, "TorReadiness.awaitReady: timed out after ${timeoutMs}ms")
        }
        return ok
    }
}

// Blocks each outbound request until embedded Tor is bootstrapped.
// On cold launch the first request lands within ~1s of process start
// while Tor is still building a circuit (5–30s typical, longer on bad
// networks). Without this gate, OkHttp connects to the embedded Tor SOCKS5 but
// the SOCKS5 tunnel can't open a stream yet, and the request fails with
// "Connect timed out" before Tor is ready to serve it.
//
// Once Tor flips to ON the wait is skipped for the lifetime of the
// process, so this is a no-op for every request after the first one.
class TorReadyInterceptor(
    private val maxWaitMs: Long = 120_000L,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!TorReadiness.isReady) {
            TorReadiness.awaitReady(maxWaitMs)
        }
        return chain.proceed(chain.request())
    }
}
