/*
 * Anon Social — app-lock settings.
 * Ported from gratis.anon.whistle.SecurityActivity.
 */
package app.pachli.anonsocial

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.pachli.R
import com.google.android.material.materialswitch.MaterialSwitch

class SecurityActivity : AppCompatActivity() {

    private lateinit var pinStore: PinStore
    private lateinit var enableSwitch: MaterialSwitch
    private lateinit var changePinBtn: Button
    private lateinit var biometricSwitch: MaterialSwitch
    private lateinit var biometricHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_anon_security)

        pinStore = PinStore(applicationContext)
        enableSwitch    = findViewById(R.id.anon_enableLockSwitch)
        changePinBtn    = findViewById(R.id.anon_changePinBtn)
        biometricSwitch = findViewById(R.id.anon_biometricSwitch)
        biometricHint   = findViewById(R.id.anon_biometricHint)

        changePinBtn.setOnClickListener { promptChangePin() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val hasPin = pinStore.hasPin()
        enableSwitch.setOnCheckedChangeListener(null)
        enableSwitch.isChecked = hasPin
        enableSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && !pinStore.hasPin()) promptNewPin { refresh() }
            else if (!checked && pinStore.hasPin()) promptDisable()
        }
        changePinBtn.visibility = if (hasPin) View.VISIBLE else View.GONE

        val bioReason = BiometricHelper.unavailableReason(this)
        biometricSwitch.setOnCheckedChangeListener(null)
        when {
            hasPin && bioReason == null -> {
                biometricSwitch.visibility = View.VISIBLE
                biometricSwitch.isChecked = pinStore.isBiometricEnabled()
                biometricSwitch.setOnCheckedChangeListener { _, c -> pinStore.setBiometricEnabled(c) }
                biometricHint.visibility = View.GONE
            }
            hasPin -> {
                biometricSwitch.visibility = View.GONE
                biometricHint.text = bioReason ?: ""
                biometricHint.visibility = View.VISIBLE
            }
            else -> {
                biometricSwitch.visibility = View.GONE
                biometricHint.visibility = View.GONE
            }
        }
    }

    private fun promptNewPin(onCancel: () -> Unit) {
        val (pinField, container) = pinDialog("new PIN (4-12 digits)")
        AlertDialog.Builder(this)
            .setTitle("Set app PIN")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("SET") { _, _ ->
                val pin = pinField.text.toString().toCharArray()
                if (pin.size < 4) {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    pin.fill('0'); onCancel(); return@setPositiveButton
                }
                pinStore.setPin(pin)
                pin.fill('0')
                AppLock.markUnlocked()
                Toast.makeText(this, "PIN set", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .show()
    }

    private fun promptChangePin() {
        val (oldField, oldC) = pinDialog("current PIN")
        AlertDialog.Builder(this)
            .setTitle("Change PIN")
            .setView(oldC)
            .setPositiveButton("NEXT") { _, _ ->
                val old = oldField.text.toString().toCharArray()
                val ok = pinStore.verifyPin(old)
                old.fill('0')
                if (!ok) { Toast.makeText(this, "incorrect PIN", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                promptNewPin {}
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptDisable() {
        val (field, container) = pinDialog("current PIN")
        AlertDialog.Builder(this)
            .setTitle("Disable app lock")
            .setMessage("Enter current PIN to disable.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("DISABLE") { _, _ ->
                val pin = field.text.toString().toCharArray()
                val ok = pinStore.verifyPin(pin)
                pin.fill('0')
                if (!ok) { Toast.makeText(this, "incorrect PIN", Toast.LENGTH_SHORT).show(); refresh(); return@setPositiveButton }
                pinStore.clearPin()
                AppLock.markUnlocked()
                Toast.makeText(this, "App lock disabled", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> refresh() }
            .show()
    }

    private fun pinDialog(hint: String): Pair<EditText, LinearLayout> {
        val field = EditText(this).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(12))
        }
        val pad = (resources.displayMetrics.density * 16).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(field)
        }
        return field to container
    }
}
