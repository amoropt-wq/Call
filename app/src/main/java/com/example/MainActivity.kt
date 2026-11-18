package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.QueueStatus
import com.example.ui.components.ActiveCallConsoleOverlay
import com.example.ui.components.AddEditQueueContactSheet
import com.example.ui.components.AddEditWhatsAppAccountSheet
import com.example.ui.components.AudioSpeakerPlayerSheet
import com.example.ui.components.EmailTemplateEditorOverlay
import com.example.ui.components.SendEmailPreviewSheet
import com.example.ui.components.WhatsAppWebOverlay
import com.example.ui.screens.CaptureSettingsScreen
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.ManualEmailScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.WhatsAppAccountsScreen
import com.example.ui.theme.HaloAntriTheme
import com.example.ui.theme.LocalHaloColors
import com.example.ui.viewmodel.HaloAntriUiState
import com.example.ui.viewmodel.HaloAntriViewModel
import com.example.ui.viewmodel.MainTab

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      HaloAntriTheme {
        HaloAntriApp()
      }
    }
  }
}

private data class NavDestination(
  val tab: MainTab,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaloAntriApp(viewModel: HaloAntriViewModel = viewModel()) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSystemStates()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(Unit) {
    viewModel.toastEvents.collect { message ->
      snackbarHostState.showSnackbar(message)
    }
  }

  if (
    uiState.selectedTab != MainTab.QUEUE &&
      uiState.activeCallSession == null &&
      uiState.whatsAppWebSession == null &&
      !uiState.showEmailTemplateEditor
  ) {
    BackHandler { viewModel.selectTab(MainTab.QUEUE) }
  }

  val destinations = remember {
    listOf(
      NavDestination(
        tab = MainTab.QUEUE,
        labelRes = R.string.tab_queue,
        selectedIcon = Icons.Rounded.ListAlt,
        unselectedIcon = Icons.Outlined.ListAlt,
        testTag = "nav_tab_queue",
      ),
      NavDestination(
        tab = MainTab.DIAL,
        labelRes = R.string.tab_dial,
        selectedIcon = Icons.Rounded.Dialpad,
        unselectedIcon = Icons.Outlined.Dialpad,
        testTag = "nav_tab_dial",
      ),
      NavDestination(
        tab = MainTab.EMAIL,
        labelRes = R.string.tab_email,
        selectedIcon = Icons.Rounded.Email,
        unselectedIcon = Icons.Outlined.Email,
        testTag = "nav_tab_email",
      ),
      NavDestination(
        tab = MainTab.ACCOUNTS,
        labelRes = R.string.tab_accounts,
        selectedIcon = Icons.Rounded.SimCard,
        unselectedIcon = Icons.Outlined.SimCard,
        testTag = "nav_tab_accounts",
      ),
      NavDestination(
        tab = MainTab.CAPTURE,
        labelRes = R.string.tab_capture,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_capture",
      ),
    )
  }

  val waitingCount =
    remember(uiState.queueItems) {
      uiState.queueItems.count {
        it.status == QueueStatus.WAITING || it.status == QueueStatus.CALLING
      }
    }

  val haloColors = LocalHaloColors.current

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        CenterAlignedTopAppBar(
          title = {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          },
          actions = {
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(end = 8.dp),
            ) {
              Surface(
                shape = MaterialTheme.shapes.small,
                color =
                  if (uiState.audioPlaybackState.isPlaying)
                    MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant,
              ) {
                IconButton(
                  onClick = { viewModel.openAudioSpeakerSheet() },
                  modifier = Modifier.testTag("topbar_audio_speaker_button"),
                ) {
                  Icon(
                    imageVector =
                      if (uiState.audioPlaybackState.isPlaying) Icons.Rounded.GraphicEq
                      else Icons.Rounded.RecordVoiceOver,
                    contentDescription = "Pemutar audio pembicara",
                    tint =
                      if (uiState.audioPlaybackState.isPlaying) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              Surface(
                shape = MaterialTheme.shapes.small,
                color =
                  if (uiState.isStealthMuted) haloColors.stealthContainer
                  else MaterialTheme.colorScheme.surfaceVariant,
              ) {
                IconButton(
                  onClick = { viewModel.toggleStealthMute() },
                  modifier = Modifier.testTag("topbar_stealth_mute_button"),
                ) {
                  Icon(
                    imageVector =
                      if (uiState.isStealthMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    contentDescription =
                      if (uiState.isStealthMuted) "Bisu senyap aktif" else "Mikrofon aktif",
                    tint =
                      if (uiState.isStealthMuted) haloColors.stealthAccent
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          },
          colors =
            TopAppBarDefaults.centerAlignedTopAppBarColors(
              containerColor = MaterialTheme.colorScheme.background
            ),
        )
      },
      bottomBar = {
        if (!useRail) {
          NavigationBar(windowInsets = WindowInsets.navigationBars) {
            destinations.forEach { dest ->
              val selected = uiState.selectedTab == dest.tab
              NavigationBarItem(
                selected = selected,
                onClick = { viewModel.selectTab(dest.tab) },
                icon = {
                  NavIconWithBadge(
                    dest = dest,
                    selected = selected,
                    waitingCount = waitingCount,
                    accountsCount = uiState.accounts.size,
                  )
                },
                label = {
                  Text(
                    text = stringResource(dest.labelRes),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                modifier = Modifier.testTag(dest.testTag),
              )
            }
          }
        }
      },
      floatingActionButton = {
        when (uiState.selectedTab) {
          MainTab.QUEUE,
          MainTab.DIAL -> {
            ExtendedFloatingActionButton(
              onClick = { viewModel.openAddManualContact() },
              icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
              text = { Text(stringResource(R.string.action_add_manual)) },
              modifier = Modifier.testTag("fab_add_manual"),
            )
          }
          MainTab.ACCOUNTS -> {
            ExtendedFloatingActionButton(
              onClick = { viewModel.openAddWhatsAppAccount() },
              icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
              text = { Text(stringResource(R.string.action_add_wa)) },
              modifier = Modifier.testTag("fab_add_wa_account"),
            )
          }
          MainTab.EMAIL,
          MainTab.CAPTURE -> Unit
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            destinations.forEach { dest ->
              val selected = uiState.selectedTab == dest.tab
              NavigationRailItem(
                selected = selected,
                onClick = { viewModel.selectTab(dest.tab) },
                icon = {
                  NavIconWithBadge(
                    dest = dest,
                    selected = selected,
                    waitingCount = waitingCount,
                    accountsCount = uiState.accounts.size,
                  )
                },
                label = {
                  Text(
                    text = stringResource(dest.labelRes),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                modifier = Modifier.testTag(dest.testTag),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
          MainTabContent(uiState = uiState, viewModel = viewModel)
        }
      }
    }

    // Active Call Console Overlay (Full-Screen Stealth Mute & Queue Session)
    AnimatedVisibility(
      visible = uiState.activeCallSession != null,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      val session = uiState.activeCallSession
      if (session != null) {
        ActiveCallConsoleOverlay(
          session = session,
          isStealthMuted = uiState.isStealthMuted,
          audioModeLabel = uiState.audioModeLabel,
          audioPlaybackState = uiState.audioPlaybackState,
          savedAudioCount = uiState.audioClips.size,
          waitingQueueCount = waitingCount,
          onToggleStealthMute = { viewModel.toggleStealthMute() },
          onToggleQuickSpeaker = { viewModel.toggleQuickSpeakerPlayback() },
          onOpenAudioSpeaker = { viewModel.openAudioSpeakerSheet() },
          onReopenWhatsApp = { viewModel.reopenWhatsAppForActiveCall() },
          onOpenWhatsAppWeb = {
            viewModel.openWhatsAppWeb(session.usingAccount, session.phoneNumber)
          },
          onCompleteAndNext = {
            viewModel.finishActiveCall(markCompleted = true, callNextAutomatically = true)
          },
          onSkipCall = {
            viewModel.finishActiveCall(markCompleted = false, callNextAutomatically = true)
          },
          onDismissBackToQueue = { viewModel.dismissActiveCallSessionWithoutCompleting() },
        )
      }
    }

    // In-App WhatsApp Web Overlay (Per-Account Isolated WebView + Auto-Capture)
    AnimatedVisibility(
      visible = uiState.whatsAppWebSession != null,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      val webSession = uiState.whatsAppWebSession
      if (webSession != null) {
        WhatsAppWebOverlay(
          account = webSession.account,
          targetPhoneNumber = webSession.targetPhoneNumber,
          isStealthMuted = uiState.isStealthMuted,
          isAudioPlaying = uiState.audioPlaybackState.isPlaying,
          onToggleStealthMute = { viewModel.toggleStealthMute() },
          onOpenAudioSpeaker = { viewModel.openAudioSpeakerSheet() },
          onCapturedWebMessage = viewModel::enqueueFromWhatsAppWeb,
          onClose = { viewModel.closeWhatsAppWeb() },
        )
      }
    }
    // Full-Screen HTML & Visual Email Template Editor Overlay
    AnimatedVisibility(
      visible = uiState.showEmailTemplateEditor,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      EmailTemplateEditorOverlay(
        existingTemplate = uiState.editingEmailTemplate,
        onSave = viewModel::saveEmailTemplate,
        onDismiss = viewModel::dismissEmailTemplateEditor,
      )
    }
  }

  if (uiState.showManualContactSheet) {
    AddEditQueueContactSheet(
      existingItem = uiState.editingQueueItem,
      onDismiss = { viewModel.dismissManualContactSheet() },
      onSave = { name, phone, idNum, email, notes, priority ->
        viewModel.saveManualContact(
          existingId = uiState.editingQueueItem?.id,
          name = name,
          phoneNumber = phone,
          idNumber = idNum,
          email = email,
          notes = notes,
          priority = priority,
        )
      },
    )
  }

  if (uiState.showAccountSheet) {
    AddEditWhatsAppAccountSheet(
      existingAccount = uiState.editingAccount,
      onDismiss = { viewModel.dismissAccountSheet() },
      onSave = { label, phone, flavor, makeActive ->
        viewModel.saveWhatsAppAccount(
          existing = uiState.editingAccount,
          label = label,
          phoneNumber = phone,
          flavor = flavor,
          makeActive = makeActive,
        )
      },
    )
  }

  val sendingContact = uiState.sendingEmailContact
  if (sendingContact != null && !uiState.showEmailTemplateEditor) {
    SendEmailPreviewSheet(
      contact = sendingContact,
      templates = uiState.emailTemplates,
      defaultTemplate = uiState.defaultEmailTemplate,
      smtpConfig = uiState.settings.smtpConfig,
      isSending = uiState.isSendingEmail,
      onDismiss = { viewModel.dismissSendEmailSheet() },
      onOpenSmtpSettings = {
        viewModel.dismissSendEmailSheet()
        viewModel.selectTab(MainTab.CAPTURE)
      },
      onEditTemplate = { tpl -> viewModel.openEmailTemplateEditor(tpl) },
      onSendViaSmtp = { recipient, subject, html ->
        viewModel.sendEmailViaSmtp(sendingContact, recipient, subject, html)
      },
      onSendViaDeviceApp = { recipient, subject, html ->
        viewModel.sendEmailViaDeviceApp(sendingContact, recipient, subject, html)
      },
    )
  }

  if (uiState.showAudioSpeakerSheet) {
    AudioSpeakerPlayerSheet(
      audioClips = uiState.audioClips,
      playbackState = uiState.audioPlaybackState,
      recordingState = uiState.audioRecordingState,
      pendingRecordedDraft = uiState.pendingRecordedDraft,
      isStealthMuted = uiState.isStealthMuted,
      audioModeLabel = uiState.audioModeLabel,
      onDismiss = viewModel::dismissAudioSpeakerSheet,
      onImportAndSaveAudio = viewModel::importAndSaveAudioClip,
      onStartRecording = viewModel::startAudioRecording,
      onStopRecording = viewModel::stopAudioRecording,
      onCancelRecording = viewModel::cancelAudioRecording,
      onSaveRecordedDraft = viewModel::saveRecordedAudioDraft,
      onDiscardRecordedDraft = viewModel::discardRecordedAudioDraft,
      onRenameClip = viewModel::renameAudioClip,
      onDeleteClip = viewModel::deleteAudioClip,
      onTogglePlayPause = viewModel::togglePlayPauseAudio,
      onStopPlayback = viewModel::stopAudioPlayback,
      onSeekTo = viewModel::seekAudioTo,
      onToggleSpeakerphone = viewModel::setAudioSpeakerphoneEnabled,
      onToggleLoop = viewModel::setAudioLoopEnabled,
      onToggleVoiceBoost = viewModel::setAudioVoiceBoostEnabled,
      onToggleAutoUnmuteMic = viewModel::setAudioAutoUnmuteMicEnabled,
      onSelectOutputRoute = viewModel::setAudioOutputRoute,
      onCheckRootAccess = viewModel::checkRootAudioAccess,
    )
  }
}

@Composable
private fun NavIconWithBadge(
  dest: NavDestination,
  selected: Boolean,
  waitingCount: Int,
  accountsCount: Int,
) {
  val iconVector = if (selected) dest.selectedIcon else dest.unselectedIcon
  val badgeText =
    when {
      dest.tab == MainTab.QUEUE && waitingCount > 0 -> waitingCount.toString()
      dest.tab == MainTab.ACCOUNTS && accountsCount > 0 -> accountsCount.toString()
      else -> null
    }

  if (badgeText != null) {
    BadgedBox(badge = { Badge { Text(badgeText) } }) {
      Icon(iconVector, contentDescription = stringResource(dest.labelRes))
    }
  } else {
    Icon(iconVector, contentDescription = stringResource(dest.labelRes))
  }
}

@Composable
private fun MainTabContent(uiState: HaloAntriUiState, viewModel: HaloAntriViewModel) {
  when (uiState.selectedTab) {
    MainTab.QUEUE ->
      QueueScreen(
        uiState = uiState,
        onSelectFilter = viewModel::setQueueFilter,
        onSelectSenderFilter = viewModel::setSenderFilter,
        onSearchQueryChange = viewModel::setSearchQuery,
        onToggleStealthMute = viewModel::toggleStealthMute,
        onOpenAudioSpeaker = viewModel::openAudioSpeakerSheet,
        onSelectActiveAccount = viewModel::activateAccount,
        onOpenAccountsTab = { viewModel.selectTab(MainTab.ACCOUNTS) },
        onOpenCaptureTab = { viewModel.selectTab(MainTab.CAPTURE) },
        onOpenManualEntry = viewModel::openAddManualContact,
        onCallNext = viewModel::callNextInQueue,
        onCallItem = viewModel::startCallForQueueItem,
        onSendEmail = viewModel::openSendEmailSheet,
        onMarkCompleted = { item ->
          viewModel.markQueueItemStatus(item, QueueStatus.COMPLETED)
        },
        onSkipItem = { item ->
          viewModel.markQueueItemStatus(item, QueueStatus.SKIPPED)
        },
        onRequeueItem = viewModel::requeueItem,
        onEditItem = viewModel::openEditQueueItem,
        onDeleteItem = { item -> viewModel.deleteQueueItem(item) },
        onClearFinished = viewModel::clearFinishedQueue,
      )
    MainTab.DIAL ->
      DialerScreen(
        uiState = uiState,
        onDirectCall = viewModel::startDirectCall,
        onAddToQueue = { name, phone, idNum ->
          viewModel.saveManualContact(
            existingId = null,
            name = name,
            phoneNumber = phone,
            idNumber = idNum,
            email = "",
            notes = "",
            priority = 0,
          )
        },
        onToggleStealthMute = viewModel::toggleStealthMute,
        onOpenAudioSpeaker = viewModel::openAudioSpeakerSheet,
        onRedial = { log ->
          viewModel.startDirectCall(log.contactName, log.phoneNumber, log.idNumber)
        },
        onClearLogs = viewModel::clearCallLogs,
      )
    MainTab.EMAIL ->
      ManualEmailScreen(
        uiState = uiState,
        onOpenSmtpSettings = { viewModel.selectTab(MainTab.CAPTURE) },
        onCreateTemplate = { viewModel.openEmailTemplateEditor(null) },
        onEditTemplate = { tpl -> viewModel.openEmailTemplateEditor(tpl) },
        onSendViaSmtp = viewModel::sendEmailViaSmtp,
        onSendViaDeviceApp = viewModel::sendEmailViaDeviceApp,
      )
    MainTab.ACCOUNTS ->
      WhatsAppAccountsScreen(
        uiState = uiState,
        onAddAccount = viewModel::openAddWhatsAppAccount,
        onActivateAccount = viewModel::activateAccount,
        onOpenWhatsAppWeb = { account -> viewModel.openWhatsAppWeb(account) },
        onEditAccount = viewModel::openEditWhatsAppAccount,
        onDeleteAccount = viewModel::deleteAccount,
      )
    MainTab.CAPTURE ->
      CaptureSettingsScreen(
        uiState = uiState,
        onUpdateSettings = viewModel::updateSettings,
        onToggleStealthMute = viewModel::toggleStealthMute,
        onOpenAudioSpeaker = viewModel::openAudioSpeakerSheet,
        onTestCaptureMessage = viewModel::testCaptureMessage,
        onSaveSmtpConfig = viewModel::saveSmtpConfig,
        onTestSmtpConnection = viewModel::testSmtpConnection,
        onCreateEmailTemplate = { viewModel.openEmailTemplateEditor(null) },
        onEditEmailTemplate = { tpl -> viewModel.openEmailTemplateEditor(tpl) },
        onSetDefaultEmailTemplate = viewModel::setDefaultEmailTemplate,
        onDeleteEmailTemplate = viewModel::deleteEmailTemplate,
        onOpenManualEmail = { viewModel.selectTab(MainTab.EMAIL) },
      )
  }
}
