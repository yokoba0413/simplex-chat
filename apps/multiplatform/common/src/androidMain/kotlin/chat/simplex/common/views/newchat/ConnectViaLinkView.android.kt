package chat.simplex.common.views.newchat

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatModel
import chat.simplex.common.model.RemoteHostInfo
import chat.simplex.res.MR

@Composable
actual fun ConnectViaLinkView(m: ChatModel, rh: RemoteHostInfo?, close: () -> Unit) {
  // TODO this should close if remote host changes in model
  val selection = remember {
    mutableStateOf(
      runCatching { ConnectViaLinkTab.valueOf(m.controller.appPrefs.connectViaLinkTab.get()!!) }.getOrDefault(ConnectViaLinkTab.SCAN)
    )
  }
  val tabTitles = ConnectViaLinkTab.values().map {
    when (it) {
      ConnectViaLinkTab.SCAN -> stringResource(MR.strings.scan_QR_code)
      ConnectViaLinkTab.PASTE -> stringResource(MR.strings.paste_the_link_you_received)
    }
  }
  Column(
    Modifier.fillMaxHeight(),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Column(Modifier.weight(1f)) {
      when (selection.value) {
        NewChatMenuOption.NEW_CONTACT -> {
          ScanToConnectView(m, rh, close)
        }
        NewChatMenuOption. -> {
          PasteToConnectView(m, rh, close)
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
            selection.value = ConnectViaLinkTab.values()[index]
            m.controller.appPrefs.connectViaLinkTab.set(selection.value .name)
          },
          text = { Text(it, fontSize = 13.sp) },
          icon = {
            Icon(
              if (ConnectViaLinkTab.SCAN.ordinal == index) painterResource(MR.images.ic_qr_code) else painterResource(MR.images.ic_article),
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
