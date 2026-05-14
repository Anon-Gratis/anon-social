/*
 * Anon Social — full-screen unlock gate.
 * Ported from gratis.anon.whistle.LockActivity.
 */
package app.pachli.anonsocial

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.pachli.R

class LockActivity : AppCompatActivity() {

    private lateinit var pinField: EditText
    private lateinit var unlockBtn: Button
    private lateinit var biometricBtn: Button
    private lateinit var errorText: TextView
    private lateinit var panicLink: TextView
    private lateinit var pinStore: PinStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_anon_lock)

        pinStore = PinStore(applicationContext)

        pinField = findViewById(R.id.anon_pinField)
        unlockBtn = findViewById(R.id.anon_unlockBtn)
        biometricBtn = findViewById(R.id.anon_biometricBtn)
        errorText = findViewById(R.id.anon_pinError)
        panicLink = findViewById(R.id.anon_panicLink)

        unlockBtn.setOnClickListener { attemptPinUnlock() }
        pinField.setOnEditorActionListener { _, _, _ -> attemptPinUnlock(); true }
        panicLink.setOnClickListener { confirmPanicWipe() }

        if (pinStore.isBiometricEnabled() && BiometricHelper.isAvailable(applicationContext)) {
            biometricBtn.visibility = View.VISIBLE
            biometricBtn.setOnClickListener { promptBiometric() }
            biometricBtn.post { promptBiometric() }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun attemptPinUnlock() {
        val pin = pinField.text.toString().toCharArray()
        if (pin.isEmpty()) return
        val ok = pinStore.verifyPin(pin)
        pin.fill('0')
        pinField.text.clear()
        if (ok) {
            unlock()
        } else {
            errorText.text = "incorrect PIN"
            errorText.visibility = View.VISIBLE
        }
    }

    private fun promptBiometric() {
        BiometricHelper.prompt(
            this,
            title = "Unlock Anon Social",
            subtitle = "Use your biometric to unlock",
            onSuccess = { unlock() },
            onFail = { _, _ -> /* user cancelled or biometric failed; PIN path still available */ },
        )
    }

    private fun confirmPanicWipe() {
        AlertDialog.Builder(this)
            .setTitle("// PANIC WIPE")
            .setMessage("This deletes the Mastodon account, OAuth tokens, drafts, " +
                "media cache, and the PIN itself. The app will look like a fresh " +
                "install on next launch. Server-side data on social.anonymous.gratis " +
                "is NOT affected.")
            .setPositiveButton("Yes, wipe everything") { _, _ ->
                Wipe.nuke(applicationContext)
                // clearApplicationUserData kills the process; finish() is a no-op
                // but kept for clarity.
                finishAndRemoveTask()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun unlock() {
        AppLock.markUnlocked()
        finish()
    }
}
