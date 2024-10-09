package spam.blocker.util

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Util.doOnce


abstract class IPermission {
    abstract fun name(): String
    abstract fun isGranted(ctx: Context): Boolean
    abstract val isOptional: Boolean
}

open class NormalPermission(
    open val name: String,
    override val isOptional: Boolean = false,
    val prompt: String? = null // show a prompt dialog before asking for permission
) : IPermission() {
    override fun name(): String{ return name }
    override fun isGranted(ctx: Context): Boolean {
        return Permissions.isPermissionGranted(ctx, name)
    }
}

// For those permissions that will launch an Intent Activity, such as UsageStats and AllFileAccess
open class IntentPermission(
    isOptional: Boolean = false,
    prompt: String? = null,

    // intent for opening system setting page
    val intent: Intent,
    val checkGranted: (ctx: Context) -> Boolean
) : NormalPermission(intent.action!!, isOptional, prompt) {

    override fun name(): String{ return intent.action!! }
    override fun isGranted(ctx: Context): Boolean {
        return checkGranted(ctx)
    }
}

// For UsageStats
class AppOpsPermission(
    override val name: String,
    isOptional: Boolean = false,
    prompt: String? = null,
    intent: Intent,
) : IntentPermission(
    isOptional, prompt, intent, { ctx ->
        Permissions.isAppOpsPermissionGranted(ctx, name)
    }
) {

    override fun name(): String{ return name }
}

/*
    Convenient class for asking for multiple permissions,

    callback is triggered with param:
    - true: all required permissions are granted
    - false: at least one of required permissions failed.

    Must be created during the creation of the fragment
 */
class PermissionChain(
) {
    // for non-protected permission, e.g.: CALL_LOG
    private lateinit var launcherNormal: ManagedActivityResultLauncher<String, Boolean>

    // for protected permission, e.g.: USAGE_STATS
    private lateinit var launcherProtected: ManagedActivityResultLauncher<Intent, ActivityResult>


    // final callback
    private lateinit var onResult: (Boolean) -> Unit

    private lateinit var currList: MutableList<NormalPermission>
    private lateinit var curr: NormalPermission

    private lateinit var popupTrigger: MutableState<Boolean>

    @Composable
    fun Compose() {
        val ctx = LocalContext.current

        popupTrigger = remember { mutableStateOf(false) }
        if (popupTrigger.value) {
            PopupDialog(
                trigger = popupTrigger,
                content = {
                    Text(curr.prompt!!, color = LocalPalette.current.textGrey)
                },
                buttons = {
                    GreyButton("cancel") {
                        popupTrigger.value = false
                        onResult(false)
                    }
                    Spacer(modifier = M.width(10.dp))

                    StrokeButton("ok", Teal200) {
                        popupTrigger.value = false
                        handle()
                    }
                }
            )
        }

        launcherNormal = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted || curr.isOptional) {
                checkNext(ctx)
            } else {
                onResult(false)
            }
        }

        launcherProtected = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            val isGranted = curr.isGranted(ctx)
            if (isGranted || curr.isOptional) {
                checkNext(ctx)
            } else {
                onResult(false)
            }
        }
    }

    fun ask(
        ctx: Context,
        permissions: List<NormalPermission>,
        onResult: (Boolean) -> Unit
    ) {
        this.onResult = onResult
        currList = ArrayList(permissions) // make a copy
        checkNext(ctx)
    }

    private fun checkNext(ctx: Context) {
        if (currList.isEmpty()) {
            onResult(true)
            return
        }

        curr = currList.first()
        currList.removeAt(0)

        if (curr.isGranted(ctx)) { // already granted
            checkNext(ctx)
            return
        }

        if (curr.isOptional) {
            val isFirstTime = doOnce(ctx, "ask_once_${curr.name}") {
                handleCurrPermission()
            }
            if (!isFirstTime) {
                checkNext(ctx)
            }
            return
        }

        handleCurrPermission()
    }

    private fun handleCurrPermission() {
        if (curr.prompt == null) {
            handle()
        } else { // show prompt dialog
            popupTrigger.value = true
        }
    }

    private fun handle() {
        if (curr is IntentPermission) {
            val protected = curr as IntentPermission
            launcherProtected.launch(protected.intent)
        } else {
            launcherNormal.launch(curr.name)
        }
    }
}

