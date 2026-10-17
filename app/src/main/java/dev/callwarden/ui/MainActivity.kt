package dev.callwarden.ui

import android.app.role.RoleManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.callwarden.data.Store

class MainActivity : ComponentActivity() {
    private var screeningEnabled by mutableStateOf(false)

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshRole() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store.get(this)
        setContent {
            CallWardenTheme {
                CallWardenApp(store, screeningEnabled, onEnableScreening = ::requestRole)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRole()
    }

    private val roleManager get() = getSystemService(RoleManager::class.java)

    private fun refreshRole() {
        screeningEnabled = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun requestRole() {
        roleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
    }
}
