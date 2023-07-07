package chat.simplex.app.views.helpers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import chat.simplex.app.SimplexApp
import chat.simplex.app.views.localauth.LocalAuthView
import chat.simplex.res.MR

sealed class LAResult {
  object Success: LAResult()
  class Error(val errString: CharSequence): LAResult()
  class Failed(val errString: CharSequence? = null): LAResult()
  class Unavailable(val errString: CharSequence? = null): LAResult()
}

data class LocalAuthRequest (
  val title: String?,
  val reason: String,
  val password: String,
  val selfDestruct: Boolean,
  val completed: (LAResult) -> Unit
) {
  companion object {
    val sample = LocalAuthRequest(generalGetString(MR.strings.la_enter_app_passcode), generalGetString(MR.strings.la_authenticate), "", selfDestruct = false) { }
  }
}

fun authenticateWithPasscode(
  promptTitle: String,
  promptSubtitle: String,
  selfDestruct: Boolean,
  completed: (LAResult) -> Unit) {
  val password = DatabaseUtils.ksAppPassword.get() ?: return completed(LAResult.Unavailable(generalGetString(MR.strings.la_no_app_password)))
      ModalManager.shared.showPasscodeCustomModal { close ->
    BackHandler {
      close()
      completed(LAResult.Error(generalGetString(MR.strings.authentication_cancelled)))
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
      LocalAuthView(SimplexApp.context.chatModel, LocalAuthRequest(promptTitle, promptSubtitle, password, selfDestruct && SimplexApp.context.chatModel.controller.appPrefs.selfDestruct.get()) {
        close()
        completed(it)
      })
    }
  }
}
