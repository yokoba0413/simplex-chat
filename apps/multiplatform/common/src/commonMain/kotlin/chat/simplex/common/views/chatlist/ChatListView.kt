package chat.simplex.common.views.chatlist

import SectionDivider
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalClipboardManager
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*
import chat.simplex.common.SettingsViewState
import chat.simplex.common.model.*
import chat.simplex.common.model.ChatController.stopRemoteHostAndReloadHosts
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.onboarding.WhatsNewView
import chat.simplex.common.views.onboarding.shouldShowWhatsNew
import chat.simplex.common.views.usersettings.SettingsView
import chat.simplex.common.platform.*
import chat.simplex.common.views.newchat.*
import chat.simplex.res.MR
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.URI

@Composable
fun ChatListView(chatModel: ChatModel, settingsState: SettingsViewState, setPerformLA: (Boolean) -> Unit, stopped: Boolean) {
  val newChatSheetState by rememberSaveable(stateSaver = AnimatedViewState.saver()) { mutableStateOf(MutableStateFlow(AnimatedViewState.GONE)) }
  val showNewChatSheet = {
    newChatSheetState.value = AnimatedViewState.VISIBLE
  }
  val hideNewChatSheet: (animated: Boolean) -> Unit = { animated ->
    if (animated) newChatSheetState.value = AnimatedViewState.HIDING
    else newChatSheetState.value = AnimatedViewState.GONE
  }
  LaunchedEffect(Unit) {
    if (shouldShowWhatsNew(chatModel)) {
      delay(1000L)
      ModalManager.center.showCustomModal { close -> WhatsNewView(close = close) }
    }
  }
  LaunchedEffect(chatModel.clearOverlays.value) {
    if (chatModel.clearOverlays.value && newChatSheetState.value.isVisible()) hideNewChatSheet(false)
  }
  LaunchedEffect(chatModel.appOpenUrl.value) {
    val url = chatModel.appOpenUrl.value
    if (url != null) {
      chatModel.appOpenUrl.value = null
      connectIfOpenedViaUri(chatModel.remoteHostId(), url, chatModel)
    }
  }
  if (appPlatform.isDesktop) {
    KeyChangeEffect(chatModel.chatId.value) {
      if (chatModel.chatId.value != null) {
        ModalManager.end.closeModalsExceptFirst()
      }
      AudioPlayer.stop()
      VideoPlayerHolder.stopAll()
    }
  }
  val endPadding = if (appPlatform.isDesktop) 56.dp else 0.dp
  val searchInList = rememberSaveable { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  val (userPickerState, scaffoldState ) = settingsState
  Scaffold(topBar = { Box(Modifier.padding(end = endPadding)) { ChatListToolbar(chatModel, scaffoldState.drawerState, userPickerState, stopped)} },
    scaffoldState = scaffoldState,
    drawerContent = { SettingsView(chatModel, setPerformLA, scaffoldState.drawerState) },
    drawerScrimColor = MaterialTheme.colors.onSurface.copy(alpha = if (isInDarkTheme()) 0.16f else 0.32f),
    drawerGesturesEnabled = appPlatform.isAndroid,
    floatingActionButton = {
      if (searchInList.value.isEmpty() && !chatModel.desktopNoUserNoRemote) {
        FloatingActionButton(
          onClick = {
            if (!stopped) {
              if (newChatSheetState.value.isVisible()) hideNewChatSheet(true) else showNewChatSheet()
            }
          },
          Modifier.padding(end = DEFAULT_PADDING - 16.dp + endPadding, bottom = DEFAULT_PADDING - 16.dp),
          elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp,
          ),
          backgroundColor = if (!stopped) MaterialTheme.colors.primary else MaterialTheme.colors.secondary,
          contentColor = Color.White
        ) {
          Icon(if (!newChatSheetState.collectAsState().value.isVisible()) painterResource(MR.images.ic_edit_filled) else painterResource(MR.images.ic_close), stringResource(MR.strings.add_contact_or_create_group))
        }
      }
    }
  ) {
    Box(Modifier.padding(it).padding(end = endPadding)) {
      Column(
        modifier = Modifier
          .fillMaxSize()
      ) {
        if (chatModel.chats.isNotEmpty()) {
          val searchShowingSimplexLink = remember { mutableStateOf(false) }
          val searchChatFilteredBySimplexLink = remember { mutableStateOf<String?>(null) }
          ChatListSearchBar(searchInList, searchShowingSimplexLink, searchChatFilteredBySimplexLink)
          SectionDivider()
          ChatList(chatModel, search = searchInList.value, searchShowingSimplexLink = searchShowingSimplexLink, searchChatFilteredBySimplexLink = searchChatFilteredBySimplexLink)
        } else if (!chatModel.switchingUsersAndHosts.value && !chatModel.desktopNoUserNoRemote) {
          Box(Modifier.fillMaxSize()) {
            if (!stopped && !newChatSheetState.collectAsState().value.isVisible()) {
              OnboardingButtons(showNewChatSheet)
            }
            Text(stringResource(MR.strings.you_have_no_chats), Modifier.align(Alignment.Center), color = MaterialTheme.colors.secondary)
          }
        }
      }
    }
  }
  if (searchInList.value.isEmpty()) {
    DesktopActiveCallOverlayLayout(newChatSheetState)
    // TODO disable this button and sheet for the duration of the switch
    NewChatSheet(chatModel, newChatSheetState, stopped, hideNewChatSheet)
  }
  if (appPlatform.isAndroid) {
    UserPicker(chatModel, userPickerState) {
      scope.launch { if (scaffoldState.drawerState.isOpen) scaffoldState.drawerState.close() else scaffoldState.drawerState.open() }
      userPickerState.value = AnimatedViewState.GONE
    }
  }
}

@Composable
private fun OnboardingButtons(openNewChatSheet: () -> Unit) {
  Column(Modifier.fillMaxSize().padding(DEFAULT_PADDING), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Bottom) {
    ConnectButton(generalGetString(MR.strings.tap_to_start_new_chat), openNewChatSheet)
    val color = MaterialTheme.colors.primaryVariant
    Canvas(modifier = Modifier.width(40.dp).height(10.dp), onDraw = {
      val trianglePath = Path().apply {
        moveTo(0.dp.toPx(), 0f)
        lineTo(16.dp.toPx(), 0.dp.toPx())
        lineTo(8.dp.toPx(), 10.dp.toPx())
        lineTo(0.dp.toPx(), 0.dp.toPx())
      }
      drawPath(
        color = color,
        path = trianglePath
      )
    })
    Spacer(Modifier.height(62.dp))
  }
}

@Composable
private fun ConnectButton(text: String, onClick: () -> Unit) {
  Button(
    onClick,
    shape = RoundedCornerShape(21.dp),
    colors = ButtonDefaults.textButtonColors(
      backgroundColor = MaterialTheme.colors.primaryVariant
    ),
    elevation = null,
    contentPadding = PaddingValues(horizontal = DEFAULT_PADDING, vertical = DEFAULT_PADDING_HALF),
    modifier = Modifier.height(42.dp)
  ) {
    Text(text, color = Color.White)
  }
}

@Composable
private fun ChatListToolbar(chatModel: ChatModel, drawerState: DrawerState, userPickerState: MutableStateFlow<AnimatedViewState>, stopped: Boolean) {
  val barButtons = arrayListOf<@Composable RowScope.() -> Unit>()
  if (stopped) {
    barButtons.add {
      IconButton(onClick = {
        AlertManager.shared.showAlertMsg(
          generalGetString(MR.strings.chat_is_stopped_indication),
          generalGetString(MR.strings.you_can_start_chat_via_setting_or_by_restarting_the_app)
        )
      }) {
        Icon(
          painterResource(MR.images.ic_report_filled),
          generalGetString(MR.strings.chat_is_stopped_indication),
          tint = Color.Red,
        )
      }
    }
  }
  val scope = rememberCoroutineScope()
  DefaultTopAppBar(
    navigationButton = {
      if (chatModel.users.isEmpty() && !chatModel.desktopNoUserNoRemote) {
        NavigationButtonMenu { scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() } }
      } else {
        val users by remember { derivedStateOf { chatModel.users.filter { u -> u.user.activeUser || !u.user.hidden } } }
        val allRead = users
          .filter { u -> !u.user.activeUser && !u.user.hidden }
          .all { u -> u.unreadCount == 0 }
        UserProfileButton(chatModel.currentUser.value?.profile?.image, allRead) {
          if (users.size == 1 && chatModel.remoteHosts.isEmpty()) {
            scope.launch { drawerState.open() }
          } else {
            userPickerState.value = AnimatedViewState.VISIBLE
          }
        }
      }
    },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(MR.strings.your_chats),
          color = MaterialTheme.colors.onBackground,
          fontWeight = FontWeight.SemiBold,
        )
        if (chatModel.chats.size > 0) {
          ToggleFilterButton()
        }
      }
    },
    onTitleClick = null,
    showSearch = false,
    onSearchValueChanged = {},
    buttons = barButtons
  )
  Divider(Modifier.padding(top = AppBarHeight))
}

@Composable
fun UserProfileButton(image: String?, allRead: Boolean, onButtonClicked: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onButtonClicked) {
      Box {
        ProfileImage(
          image = image,
          size = 37.dp
        )
        if (!allRead) {
          unreadBadge()
        }
      }
    }
    if (appPlatform.isDesktop) {
      val h by remember { chatModel.currentRemoteHost }
      if (h != null) {
        Spacer(Modifier.width(12.dp))
        HostDisconnectButton {
          stopRemoteHostAndReloadHosts(h!!, true)
        }
      }
    }
  }
}

@Composable
private fun BoxScope.unreadBadge(text: String? = "") {
  Text(
    text ?: "",
    color = MaterialTheme.colors.onPrimary,
    fontSize = 6.sp,
    modifier = Modifier
      .background(MaterialTheme.colors.primary, shape = CircleShape)
      .badgeLayout()
      .padding(horizontal = 3.dp)
      .padding(vertical = 1.dp)
      .align(Alignment.TopEnd)
  )
}

@Composable
private fun ToggleFilterButton() {
  val pref = remember { ChatController.appPrefs.showUnreadAndFavorites }
  IconButton(onClick = { pref.set(!pref.get()) }) {
    Icon(
      painterResource(MR.images.ic_filter_list),
      null,
      tint = if (pref.state.value) MaterialTheme.colors.background else MaterialTheme.colors.primary,
      modifier = Modifier
        .padding(3.dp)
        .background(color = if (pref.state.value) MaterialTheme.colors.primary else MaterialTheme.colors.background, shape = RoundedCornerShape(50))
        .border(width = 1.dp, color = MaterialTheme.colors.primary, shape = RoundedCornerShape(50))
        .padding(3.dp)
        .size(16.dp)
    )
  }
}

@Composable
expect fun DesktopActiveCallOverlayLayout(newChatSheetState: MutableStateFlow<AnimatedViewState>)

fun connectIfOpenedViaUri(rhId: Long?, uri: URI, chatModel: ChatModel) {
  Log.d(TAG, "connectIfOpenedViaUri: opened via link")
  if (chatModel.currentUser.value == null) {
    chatModel.appOpenUrl.value = uri
  } else {
    withApi {
      planAndConnect(chatModel, rhId, uri, incognito = null, close = null)
    }
  }
}

@Composable
private fun ChatListSearchBar(searchText: MutableState<String>, searchShowingSimplexLink: MutableState<Boolean>, searchChatFilteredBySimplexLink: MutableState<String?>) {
  setupClipboardListener()
  var focused by remember { mutableStateOf(false) }
  val searchTextState = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(searchText.value)) }
  SearchTextField(
    Modifier.fillMaxWidth().padding(start = 14.dp).onFocusChanged { focused = it.hasFocus },
    placeholder = stringResource(MR.strings.search_or_paste_simplex_link),
    alwaysVisible = true,
    searchText = searchTextState,
    trailingContent = {
      Row {
        val clipboard = LocalClipboardManager.current
        if (remember(focused) { chatModel.clipboardHasText }.value) {
          IconButton(
            { searchText.value = clipboard.getText()?.text ?: return@IconButton },
            Modifier.size(16.dp).desktopPointerHoverIconHand()) {
            Icon(painterResource(MR.images.ic_article), null)
          }
        }
        IconButton({ ModalManager.center.showModalCloseable { close ->
          NewChatView(chatModel, null, selection = CreateLinkTab.CONNECT, showQRCodeScanner = true, close = close) }
        },
          Modifier.size(16.dp).desktopPointerHoverIconHand()
        ) {
          Icon(painterResource(MR.images.ic_qr_code), null)
        }
      }
    }
  ) {
    searchTextState.value = searchTextState.value.copy(it.trim())
    searchText.value = it.trim()
  }
  LaunchedEffect(Unit) {
    snapshotFlow { searchText.value }
      .distinctUntilChanged()
      .collect {
        if (searchTextState.value.text != it) {
          searchTextState.value = searchTextState.value.copy(it.trim())
          val link = strHasSingleSimplexLink(it.trim())
          if (link != null) { // if SimpleX link is pasted, show connection dialogue
            //searchFocussed = false
            if (link.format is Format.SimplexLink) {
              val linkText = link.simplexLinkText(link.format.linkType, link.format.smpHosts)
              searchTextState.value = searchTextState.value.copy(linkText)
              searchText.value = linkText
            }
            searchShowingSimplexLink.value = true
            searchChatFilteredBySimplexLink.value = null
            // LALAL
            connect(null/*rhId*/, link.text, searchChatFilteredBySimplexLink)
          } else {
            if (it != "") { // if some other text is pasted, enter search mode
              //searchFocussed = true
            }
            searchShowingSimplexLink.value = false
            searchChatFilteredBySimplexLink.value = null
          }
        }
      }
  }
}

private fun connect(rhId: Long?, link: String, searchChatFilteredBySimplexLink: MutableState<String?>) {
  withBGApi {
    planAndConnect(
      chatModel,
      rhId,
      URI.create(link),
      dismiss = false,
      incognito = null,
      filterKnownContact = { searchChatFilteredBySimplexLink.value = it.id },
      filterKnownGroup = { searchChatFilteredBySimplexLink.value = it.id },
      close = null
    )
  }
}

private var lazyListState = 0 to 0

@Composable
private fun ChatList(chatModel: ChatModel, search: String, searchShowingSimplexLink: State<Boolean>, searchChatFilteredBySimplexLink: State<String?>) {
  val listState = rememberLazyListState(lazyListState.first, lazyListState.second)
  DisposableEffect(Unit) {
    onDispose { lazyListState = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
  }
  val showUnreadAndFavorites = remember { ChatController.appPrefs.showUnreadAndFavorites.state }.value
  val allChats = remember { chatModel.chats }
  // In some not always reproducible situations this code produce IndexOutOfBoundsException on Compose's side
  // which is related to [derivedStateOf]. Using safe alternative instead
  // val chats by remember(search, showUnreadAndFavorites) { derivedStateOf { filteredChats(showUnreadAndFavorites, search, allChats.toList()) } }
  val chats = filteredChats(showUnreadAndFavorites, searchShowingSimplexLink, searchChatFilteredBySimplexLink, search, allChats.toList())
  LazyColumn(
    modifier = Modifier.fillMaxWidth(),
    listState
  ) {
    items(chats) { chat ->
      ChatListNavLinkView(chat, chatModel)
    }
  }
  if (chats.isEmpty() && !chatModel.chats.isEmpty()) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(generalGetString(MR.strings.no_filtered_chats), color = MaterialTheme.colors.secondary)
    }
  }
}

private fun filteredChats(
  showUnreadAndFavorites: Boolean,
  searchShowingSimplexLink: State<Boolean>,
  searchChatFilteredBySimplexLink: State<String?>,
  searchText: String,
  chats: List<Chat>
): List<Chat> {
  val linkChatId = searchChatFilteredBySimplexLink.value
  return if (linkChatId != null) {
    return chatModel.chats.filter { it.id == linkChatId }
  } else {
    val s = if (searchShowingSimplexLink.value) "" else searchText.trim().lowercase()
    if (s.isEmpty() && !showUnreadAndFavorites)
      chats
    else {
      chats.filter { chat ->
        when (val cInfo = chat.chatInfo) {
          is ChatInfo.Direct -> if (s.isEmpty()) {
            filtered(chat)
          } else {
            (viewNameContains(cInfo, s) ||
                cInfo.contact.profile.displayName.lowercase().contains(s) ||
                cInfo.contact.fullName.lowercase().contains(s))
          }
          is ChatInfo.Group -> if (s.isEmpty()) {
            (filtered(chat) || cInfo.groupInfo.membership.memberStatus == GroupMemberStatus.MemInvited)
          } else {
            viewNameContains(cInfo, s)
          }
          is ChatInfo.ContactRequest -> s.isEmpty() || viewNameContains(cInfo, s)
          is ChatInfo.ContactConnection -> s.isNotEmpty() && cInfo.contactConnection.localAlias.lowercase().contains(s)
          is ChatInfo.InvalidJSON -> false
        }
      }
    }
  }
}

private fun filtered(chat: Chat): Boolean =
  (chat.chatInfo.chatSettings?.favorite ?: false) || chat.chatStats.unreadCount > 0 || chat.chatStats.unreadChat

private fun viewNameContains(cInfo: ChatInfo, s: String): Boolean =
  cInfo.chatViewName.lowercase().contains(s.lowercase())
