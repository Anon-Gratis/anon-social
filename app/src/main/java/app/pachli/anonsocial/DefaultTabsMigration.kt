package app.pachli.anonsocial

import android.content.Context
import app.pachli.core.data.repository.AccountManager
import app.pachli.core.model.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

// One-shot tab migration for accounts that signed in before v0.1.4.
// Their tabPreferences row in the DB was saved at first login with the
// old default (Home, Notifications, Trending, Conversations) and is not
// rewritten by changing defaultTabs() in code. This injects PublicLocal
// as the first tab once, guarded by a SharedPreferences flag.
//
// accountsFlow is eagerly collected but starts at emptyList() — so we
// wait (bounded) for at least one account to surface before acting.
object DefaultTabsMigration {
    private const val PREFS = "anon_social_migrations"
    private const val FLAG = "default_tabs_v1_local_first"
    private const val WAIT_MS = 8_000L

    fun runIfNeeded(context: Context, accountManager: AccountManager, scope: CoroutineScope) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(FLAG, false)) return

        scope.launch(Dispatchers.IO) {
            try {
                val accounts = withTimeoutOrNull(WAIT_MS) {
                    accountManager.accountsFlow.filter { it.isNotEmpty() }.first()
                } ?: run {
                    // No accounts surfaced — either fresh install (defaultTabs()
                    // change covers it) or DB is slow. Don't set the flag so
                    // the next launch retries.
                    Timber.i("DefaultTabsMigration: no accounts yet, deferring")
                    return@launch
                }

                accounts.forEach { account ->
                    if (account.tabPreferences.contains(Timeline.PublicLocal)) return@forEach
                    val rebuilt = listOf(Timeline.PublicLocal) + account.tabPreferences
                    accountManager.setTabPreferences(account.id, rebuilt)
                    Timber.i("DefaultTabsMigration: prepended PublicLocal for account %d", account.id)
                }
                prefs.edit().putBoolean(FLAG, true).apply()
            } catch (t: Throwable) {
                Timber.w(t, "DefaultTabsMigration failed; will retry on next launch")
            }
        }
    }
}
