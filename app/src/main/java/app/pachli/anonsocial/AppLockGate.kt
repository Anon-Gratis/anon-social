/*
 * Anon Social — single ActivityLifecycleCallbacks that gates every activity.
 *
 * Why this design vs. per-activity gateOnLock() (AnonWhistle's pattern):
 *   Pachli has 20+ activities scattered across modules — main, compose, image
 *   viewer, account list, intent routers, share targets, drafts, etc. A
 *   per-activity gate would mean editing 20+ files and likely missing one;
 *   share-intent / notification-tap entry points get past the lock that way.
 *   The same problem Conversations had — solved with the AppLockGate pattern
 *   per `reference_anon_apps_lock.md`. One callback covers everything: every
 *   foregrounded activity goes through here, no activity-side change required.
 *
 * Also applies FLAG_SECURE on every onActivityCreated, independent of lock
 * state, so screenshots + Recent Apps thumbnails are always blocked.
 */
package app.pachli.anonsocial

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager

class AppLockGate(private val app: Application) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }

    override fun onActivityResumed(activity: Activity) {
        // Don't gate the lock screen itself (would infinite-loop) or the
        // splash screen if we ever add one.
        if (activity is LockActivity) return

        val pinConfigured = PinStore(app).hasPin()
        if (pinConfigured && AppLock.isLocked()) {
            activity.startActivity(Intent(activity, LockActivity::class.java))
        }
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
