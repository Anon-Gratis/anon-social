/*
 * Anon Social — BiometricPrompt wrapper, Class 3 (strong) biometrics only.
 * Ported from gratis.anon.whistle.BiometricHelper.
 */
package app.pachli.anonsocial

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object BiometricHelper {

    fun isAvailable(context: Context): Boolean {
        val mgr = BiometricManager.from(context)
        return mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun unavailableReason(context: Context): String? {
        val mgr = BiometricManager.from(context)
        return when (mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware unavailable"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometric enrolled in system settings"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Biometric requires security update"
            else -> "Biometric unavailable"
        }
    }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFail: (errCode: Int, errMessage: CharSequence) -> Unit,
    ) {
        val executor: Executor = Executors.newSingleThreadExecutor()
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    activity.runOnUiThread { onSuccess() }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    activity.runOnUiThread { onFail(errorCode, errString) }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }
}
