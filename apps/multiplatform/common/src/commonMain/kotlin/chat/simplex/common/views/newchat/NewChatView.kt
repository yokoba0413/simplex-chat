package chat.simplex.common.views.newchat

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.usersettings.UserAddressView
import chat.simplex.res.MR

enum class CreateLinkTab {
  INVITE, CONNECT
}

@Composable
fun NewChatView(m: ChatModel, rh: RemoteHostInfo?, selection: CreateLinkTab, showQRCodeScanner: Boolean, close: () -> Unit) {
  val selection = remember { mutableStateOf(selection) }
  val connReqInvitation = rememberSaveable { m.showingInvitation }
  val contactConnection: MutableState<PendingContactConnection?> = rememberSaveable(stateSaver = serializableSaver()) { mutableStateOf(null) }
  val creatingConnReq = rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(selection.value) {
    if (
      selection.value == CreateLinkTab.INVITE
      && connReqInvitation.value.isNullOrEmpty()
      && contactConnection.value == null
      && !creatingConnReq.value
    ) {
      createInvitation(m, rh?.remoteHostId, creatingConnReq, connReqInvitation, contactConnection)
    }
  }
  /** When [AddContactView] is open, we don't need to drop [chatModel.connReqInv].
   * Otherwise, it will be called here AFTER [AddContactView] is launched and will clear the value too soon.
   * It will be dropped automatically when connection established or when user goes away from this screen.
   **/
  DisposableEffect(Unit) {
    onDispose {
      if (!ModalManager.center.hasModalsOpen()) {
        m.showingInvitation.value = null
      }
    }
  }
  val tabTitles = CreateLinkTab.values().map {
    when {
      it == CreateLinkTab.INVITE && connReqInvitation.value.isNullOrEmpty() && contactConnection.value == null ->
        stringResource(MR.strings.create_one_time_link)
      it == CreateLinkTab.INVITE ->
        stringResource(MR.strings.one_time_link)
      it == CreateLinkTab.CONNECT ->
        stringResource(MR.strings.your_simplex_contact_address)
      else -> ""
    }
  }
  Column(
    Modifier
      .fillMaxHeight(),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(Modifier.weight(1f)) {
      when (selection.value) {
        CreateLinkTab.INVITE -> {
          AddContactView(m, rh,connReqInvitation.value ?: "", contactConnection)
        }
        CreateLinkTab.CONNECT -> {
          UserAddressView(m, viaCreateLinkView = true, close = {})
        }
      }
    }
    TabRow(
      selectedTabIndex = selection.value.ordinal,
      backgroundColor = Color.Transparent,
      contentColor = MaterialTheme.colors.primary,
    ) {
      tabTitles.forEachIndexed { index, it ->
        Tab(
          selected = selection.value.ordinal == index,
          onClick = {
            selection.value = CreateLinkTab.values()[index]
          },
          text = { Text(it, fontSize = 13.sp) },
          icon = {
            Icon(
              if (CreateLinkTab.INVITE.ordinal == index) painterResource(MR.images.ic_repeat_one) else painterResource(MR.images.ic_all_inclusive),
              it
            )
          },
          selectedContentColor = MaterialTheme.colors.primary,
          unselectedContentColor = MaterialTheme.colors.secondary,
        )
      }
    }
  }
}

private fun createInvitation(
  m: ChatModel,
  rhId: Long?,
  creatingConnReq: MutableState<Boolean>,
  connReqInvitation: MutableState<String?>,
  contactConnection: MutableState<PendingContactConnection?>
) {
  creatingConnReq.value = true
  withApi {
    val r = m.controller.apiAddContact(rhId, incognito = m.controller.appPrefs.incognito.get())
    if (r != null) {
      m.updateContactConnection(rhId, r.second)
      connReqInvitation.value = r.first
      contactConnection.value = r.second
    } else {
      creatingConnReq.value = false
    }
  }
}

fun strIsSimplexLink(str: String): Boolean {
  val parsedMd = parseToMarkdown(str)
  return parsedMd != null && parsedMd.size == 1 && parsedMd[0].format is Format.SimplexLink
}

fun strHasSingleSimplexLink(str: String): FormattedText? {
  val parsedMd = parseToMarkdown(str) ?: return null
  val parsedLinks = parsedMd.filter { it.format?.isSimplexLink ?: false }
  if (parsedLinks.size != 1) return null

  return parsedLinks[0]
}
