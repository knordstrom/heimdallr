package com.heimdallr.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * OnboardingActivity
 *
 * Requests two roles that together enable full call screening with audio:
 *
 *   1. CALL_SCREENING — lets CallScreenerService intercept calls before they ring.
 *   2. ROLE_DIALER (default phone app) — lets ScreeningInCallService answer calls
 *      programmatically, play a TTS greeting, and record the caller's response.
 *      Without this role, Android will not bind InCallService and the audio
 *      screening step is skipped.
 *
 * The roles are requested sequentially: screening first, then dialer.
 * Navigation to MainActivity happens only after both are held.
 *
 * API 29+: RoleManager (preferred).
 * API 28: TelecomManager fallback for both roles.
 */
class OnboardingActivity : ComponentActivity() {

    private val tag = "OnboardingActivity"

    // Step 1 of onboarding: CALL_SCREENING role
    private val screeningRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isCallScreeningRoleHeld()) {
            Log.d(tag, "Call screening role granted — requesting dialer role")
            requestDialerRole()
        } else {
            Log.w(tag, "Call screening role denied")
            showRoleDeniedState()
        }
    }

    // Step 2 of onboarding: default dialer role (needed for InCallService / audio answering)
    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isDialerRoleHeld()) {
            Log.d(tag, "Dialer role granted")
        } else {
            // Non-fatal: call screening still works; audio answering (Step 2) won't.
            Log.w(tag, "Dialer role denied — audio screening disabled")
        }
        // Proceed regardless; screening-only mode is better than blocking onboarding.
        navigateToMain()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when {
            !isCallScreeningRoleHeld() -> requestCallScreeningRole()
            !isDialerRoleHeld() -> requestDialerRole()
            else -> navigateToMain()
        }
    }

    // ---------------------------------------------------------------------------
    // Role management
    // ---------------------------------------------------------------------------

    private fun isCallScreeningRoleHeld(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java)
                .isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else {
            getSystemService(TelecomManager::class.java)
                .callScreeningAppPackageName == packageName
        }
    }

    private fun isDialerRoleHeld(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java)
                .isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            getSystemService(TelecomManager::class.java)
                .defaultDialerPackage == packageName
        }
    }

    private fun requestCallScreeningRole() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java)
                .createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
        }
        screeningRoleLauncher.launch(intent)
    }

    private fun requestDialerRole() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(RoleManager::class.java)
                .createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
        }
        dialerRoleLauncher.launch(intent)
    }

    // ---------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * TODO: Replace with a Compose UI that explains both permissions and provides
     * "Try Again" buttons. For now the activity appears blank on denial.
     */
    private fun showRoleDeniedState() {
        Log.w(tag, "Show rationale UI: explain why call screening + dialer roles are required")
    }
}
