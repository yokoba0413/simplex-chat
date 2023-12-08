package chat.simplex.common.views.newchat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.*
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun NewChatSheet(chatModel: ChatModel, newChatSheetState: StateFlow<AnimatedViewState>, stopped: Boolean, closeNewChatSheet: (animated: Boolean) -> Unit) {
  // TODO close new chat if remote host changes in model
  if (newChatSheetState.collectAsState().value.isVisible()) BackHandler { closeNewChatSheet(true) }
  NewChatSheetLayout(
    newChatSheetState,
    stopped,
    addContact = {
      closeNewChatSheet(false)
      ModalManager.center.closeModals()
      ModalManager.center.showModalCloseable { close -> NewChatView(chatModel, chatModel.currentRemoteHost.value, CreateLinkTab.INVITE, close) }
    },
    createGroup = {
      closeNewChatSheet(false)
      ModalManager.center.closeModals()
      ModalManager.center.showCustomModal { close -> AddGroupView(chatModel, chatModel.currentRemoteHost.value, close) }
    },
    closeNewChatSheet,
  )
}

private val titles = listOf(
  MR.strings.share_one_time_link, //
//  if (appPlatform.isAndroid) MR.strings.connect_via_link_or_qr else MR.strings.connect_via_link, // LALAL
  MR.strings.create_group
)
private val icons = listOf(MR.images.ic_add_link, MR.images.ic_qr_code, MR.images.ic_group)

@Composable
private fun NewChatSheetLayout(
  newChatSheetState: StateFlow<AnimatedViewState>,
  stopped: Boolean,
  addContact: () -> Unit,
  createGroup: () -> Unit,
  closeNewChatSheet: (animated: Boolean) -> Unit,
) {
  var newChat by remember { mutableStateOf(newChatSheetState.value) }
  val resultingColor = if (isInDarkTheme()) Color.Black.copy(0.64f) else DrawerDefaults.scrimColor
  val animatedColor = remember {
    Animatable(
      if (newChat.isVisible()) Color.Transparent else resultingColor,
      Color.VectorConverter(resultingColor.colorSpace)
    )
  }
  val animatedFloat = remember { Animatable(if (newChat.isVisible()) 0f else 1f) }
  LaunchedEffect(Unit) {
    launch {
      newChatSheetState.collect {
        newChat = it
        launch {
          animatedColor.animateTo(if (newChat.isVisible()) resultingColor else Color.Transparent, newChatSheetAnimSpec())
        }
        launch {
          animatedFloat.animateTo(if (newChat.isVisible()) 1f else 0f, newChatSheetAnimSpec())
          if (newChat.isHiding()) closeNewChatSheet(false)
        }
      }
    }
  }
  val endPadding = if (appPlatform.isDesktop) 56.dp else 0.dp
  val maxWidth = with(LocalDensity.current) { windowWidth() * density }
  Column(
    Modifier
      .fillMaxSize()
      .padding(end = endPadding)
      .offset { IntOffset(if (newChat.isGone()) -maxWidth.value.roundToInt() else 0, 0) }
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { closeNewChatSheet(true) }
      .drawBehind { drawRect(animatedColor.value) },
    verticalArrangement = Arrangement.Bottom,
    horizontalAlignment = Alignment.End
  ) {
    val actions = remember { listOf(addContact, createGroup) }
    val backgroundColor = if (isInDarkTheme())
      blendARGB(MaterialTheme.colors.primary, Color.Black, 0.7F)
    else
      MaterialTheme.colors.background
    LazyColumn(Modifier
      .graphicsLayer {
        alpha = animatedFloat.value
        translationY = (1 - animatedFloat.value) * 20.dp.toPx()
      }) {
      items(actions.size) { index ->
        Row {
          Spacer(Modifier.weight(1f))
          Box(contentAlignment = Alignment.CenterEnd) {
            Button(
              actions[index],
              shape = RoundedCornerShape(21.dp),
              colors = ButtonDefaults.textButtonColors(backgroundColor = backgroundColor),
              elevation = null,
              contentPadding = PaddingValues(horizontal = DEFAULT_PADDING_HALF, vertical = DEFAULT_PADDING_HALF),
              modifier = Modifier.height(42.dp)
            ) {
              Text(
                stringResource(titles[index]),
                Modifier.padding(start = DEFAULT_PADDING_HALF),
                color = if (isInDarkTheme()) MaterialTheme.colors.primary else MaterialTheme.colors.primary,
                fontWeight = FontWeight.Medium,
              )
              Icon(
                painterResource(icons[index]),
                stringResource(titles[index]),
                Modifier.size(42.dp),
                tint = if (isInDarkTheme()) MaterialTheme.colors.primary else MaterialTheme.colors.primary
              )
            }
          }
          Spacer(Modifier.width(DEFAULT_PADDING))
        }
        Spacer(Modifier.height(DEFAULT_PADDING))
      }
    }
    FloatingActionButton(
      onClick = { if (!stopped) closeNewChatSheet(true) },
      Modifier.padding(end = DEFAULT_PADDING, bottom = DEFAULT_PADDING),
      elevation = FloatingActionButtonDefaults.elevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        hoveredElevation = 0.dp,
        focusedElevation = 0.dp,
      ),
      backgroundColor = if (!stopped) MaterialTheme.colors.primary else MaterialTheme.colors.secondary,
      contentColor = Color.White
    ) {
      Icon(
        painterResource(MR.images.ic_edit_filled), stringResource(MR.strings.add_contact_or_create_group),
        Modifier.graphicsLayer { alpha = 1 - animatedFloat.value }
      )
      Icon(
        painterResource(MR.images.ic_close), stringResource(MR.strings.add_contact_or_create_group),
        Modifier.graphicsLayer { alpha = animatedFloat.value }
      )
    }
  }
}

@Composable
fun ActionButton(
  text: String?,
  comment: String?,
  icon: Painter,
  disabled: Boolean = false,
  click: () -> Unit = {}
) {
  Surface(shape = RoundedCornerShape(18.dp), color = Color.Transparent) {
    Column(
      Modifier
        .clickable(onClick = click)
        .padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      val tint = if (disabled) MaterialTheme.colors.secondary else MaterialTheme.colors.primary
      Icon(
        icon, text,
        tint = tint,
        modifier = Modifier
          .size(40.dp)
          .padding(bottom = 8.dp)
      )
      if (text != null) {
        Text(
          text,
          textAlign = TextAlign.Center,
          fontWeight = FontWeight.Bold,
          color = tint,
          modifier = Modifier.padding(bottom = 4.dp)
        )
      }
      if (comment != null) {
        Text(
          comment,
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.body2
        )
      }
    }
  }
}

@Composable
fun ActionButton(
  modifier: Modifier,
  text: String?,
  comment: String?,
  icon: Painter,
  tint: Color = MaterialTheme.colors.primary,
  disabled: Boolean = false,
  click: () -> Unit = {}
) {
  Surface(modifier, shape = RoundedCornerShape(18.dp)) {
    Column(
      Modifier
        .fillMaxWidth()
        .clickable(onClick = click)
        .padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      val tint = if (disabled) MaterialTheme.colors.secondary else tint
      Icon(
        icon, text,
        tint = tint,
        modifier = Modifier
          .size(40.dp)
          .padding(bottom = 8.dp)
      )
      if (text != null) {
        Text(
          text,
          textAlign = TextAlign.Center,
          fontWeight = FontWeight.Bold,
          color = tint,
          modifier = Modifier.padding(bottom = 4.dp)
        )
      }
      if (comment != null) {
        Text(
          comment,
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.body2
        )
      }
    }
  }
}

@Preview
@Composable
private fun PreviewNewChatSheet() {
  SimpleXTheme {
    NewChatSheetLayout(
      MutableStateFlow(AnimatedViewState.VISIBLE),
      stopped = false,
      addContact = {},
      createGroup = {},
      closeNewChatSheet = {},
    )
  }
}
