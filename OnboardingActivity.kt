package com.callscreener.ui

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
 * Android requires the user to explicitly grant this app the
 * CALL_SCREENING role before CallScreenerService is invoked.
 *
 * This activity handles that flow on launch. Once granted, it
 * navigates to MainActivity and never shows again.
 *
 * API 29+ uses RoleManager (preferred).
 * API 28 falls back to TelecomManager.requestChangeDefaultCallScreeningApp().
 */
class OnboardingActivity : ComponentActivity() {

    private val tag = "OnboardingActivity"

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (isCallScreeningRoleHeld()) {
            Log.d(tag, "Call screening role granted")
            navigateToMain()
        } else {
            Log.w(tag, "Call screening role denied — showing rationale")
            showRoleDeniedState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isCallScreeningRoleHeld()) {
            navigateToMain()
            return
        }

        requestCallScreeningRole()
    }

    // ---------------------------------------------------------------------------
    // Role management
    // ---------------------------------------------------------------------------

    private fun isCallScreeningRoleHeld(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else {
            // API 28: check via TelecomManager
            val telecom = getSystemService(TelecomManager::class.java)
            telecom.callScreeningAppPackageName == packageName
        }
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            roleRequestLauncher.launch(intent)
        } else {
            // API 28 fallback — direct intent to system settings
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            roleRequestLauncher.launch(intent)
        }
    }

    // ---------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * TODO: Replace with a proper Compose UI explaining why the permission is needed
     * and offering a "Try Again" button that re-calls requestCallScreeningRole().
     *
     * For Step 1, just logging — the activity will appear blank if denied.
     */
    private fun showRoleDeniedState() {
        Log.w(tag, "Show rationale UI: explain why call screening is required")
    }
}
