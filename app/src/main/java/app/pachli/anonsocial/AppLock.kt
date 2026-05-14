/*
 * Anon Social — process-wide app-lock state.
 * Ported from gratis.anon.whistle.AppLock (same design as Anon PGP 0.3.6 /
 * Anon Mail 0.8.5 / Anon WhistleBlower).
 */
package app.pachli.anonsocial

object AppLock {

    /** Grace window after backgrounding during which we DON'T re-lock. */
    const val GRACE_MS: Long = 60_000L

    @Volatile private var locked: Boolean = false
    @Volatile private var backgroundedAt: Long = 0L

    fun isLocked(): Boolean = locked

    /** Called from LockActivity after correct PIN / biometric. */
    fun markUnlocked() {
        locked = false
        backgroundedAt = 0L
    }

    /** Force lock — used by SecurityActivity when toggling, or after panic. */
    fun markLocked() { locked = true }

    /** Called when the app moves to background (last activity stopped). */
    fun onAppBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    /**
     * Called when any activity comes to foreground; returns true if the caller
     * should redirect to LockActivity.
     */
    fun onAppForegrounded(pinConfigured: Boolean): Boolean {
        if (!pinConfigured) {
            locked = false
            return false
        }
        if (locked) return true
        val bg = backgroundedAt
        if (bg > 0L && System.currentTimeMillis() - bg > GRACE_MS) {
            locked = true
        }
        backgroundedAt = 0L
        return locked
    }

    /** Called from Application.onCreate when a PIN is configured. */
    fun lockOnProcessStart() {
        locked = true
    }
}
