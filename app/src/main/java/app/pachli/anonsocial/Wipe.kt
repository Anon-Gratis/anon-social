/*
 * Anon Social — nuclear panic-wipe via OS clearApplicationUserData().
 *
 * Anon Social's wipe is simpler than AnonWhistle's: every piece of state
 * (Mastodon account, tokens, drafts, image cache, OAuth secrets, PIN) lives
 * in app-private storage Android can clean in one call. The system also
 * kills the process and force-stops the app, so there's no need to clear
 * in-memory state ourselves.
 */
package app.pachli.anonsocial

import android.app.ActivityManager
import android.content.Context
import android.util.Log

object Wipe {
    /**
     * Wipes ALL of Anon Social's app data — Mastodon account DB, OAuth tokens,
     * drafts, media cache, the PIN itself. The app will look like a fresh
     * install on next launch. Calls Android's ActivityManager.clearApplicationUserData(),
     * which kills the process and force-stops the app as part of the operation.
     */
    fun nuke(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val ok = am.clearApplicationUserData()
        Log.w("AnonSocialWipe", "clearApplicationUserData -> $ok (process exit imminent)")
    }
}
