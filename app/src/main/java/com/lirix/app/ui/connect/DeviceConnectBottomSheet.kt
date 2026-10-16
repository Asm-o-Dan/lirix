package com.lirix.app.ui.connect

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.lirix.app.connect.cast.AndroidCastSourceManager
import com.lirix.app.connect.cast.AudioCastForegroundService
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.connect.AndroidConnectManager
import com.lirix.app.connect.handoff.HandoffError
import com.lirix.app.connect.handoff.HandoffState
import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.proto.TrackRef
import com.lirix.app.connect.registry.ConnectedDeviceInfo
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Android Connect BottomSheet (SPEC-CONNECT-01 §5.1, §5.2, §5.3, TASK-CON-UI-07).
 *
 * Предоставляет выбор целевого устройства Lirix Connect:
 * 1. Текущее устройство («● Этот телефон (Android)» со слайдером системной громкости).
 * 2. Доступные устройства из DeviceRegistry с индикацией статуса онлайн/офлайн и спиннером передачи (до 5 с).
 * 3. Сканирование QR-кода сопряжения с камерой / ручным вводом.
 * 4. Fallback-диалог на Audio Cast при сбое Handoff.
 * 5. Управление доверенными устройствами (переименование, удаление).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConnectBottomSheet(
    onDismissRequest: () -> Unit,
    currentTrackTitle: String = "Текущий трек",
    currentTrackArtist: String = "Исполнитель",
    playbackPositionMs: Long = 0L,
    modifier: Modifier = Modifier,
    connectManager: AndroidConnectManager = AndroidConnectManager.getInstance(LocalContext.current)
) {
    val tokens = LirixThemeTokens.colors
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val devices by connectManager.deviceRegistry.devices.collectAsState()
    val handoffState by connectManager.handoffStateMachine.state.collectAsState()
    val isLocalPlayback by connectManager.handoffStateMachine.isLocalPlayback.collectAsState()
    val lastHandoffError by connectManager.handoffStateMachine.lastError.collectAsState()
    val localVolume by connectManager.localVolumePercent.collectAsState()

    var showScannerDialog by remember { mutableStateOf(false) }
    var targetConnectingEndpoint by remember { mutableStateOf<Bytes?>(null) }
    var deviceToRename by remember { mutableStateOf<ConnectedDeviceInfo?>(null) }
    var renameTextFieldValue by remember { mutableStateOf("") }
    var deviceToForget by remember { mutableStateOf<ConnectedDeviceInfo?>(null) }
    var showSuggestCastDialog by remember { mutableStateOf<ConnectedDeviceInfo?>(null) }

    val context = LocalContext.current
    val isCasting by AndroidCastSourceManager.getInstance().isCasting.collectAsState()
    var targetDeviceForCast by remember { mutableStateOf<ConnectedDeviceInfo?>(null) }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val target = targetDeviceForCast ?: devices.firstOrNull()
            val targetName = target?.deviceName ?: "Компьютер"
            AudioCastForegroundService.start(
                context = context,
                deviceName = targetName,
                resultCode = result.resultCode,
                data = result.data!!,
                endpointHex = target?.deviceId
            )
        }
    }

    val audioRecordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val captureIntent = AndroidCastSourceManager.createScreenCaptureIntent(context)
            mediaProjectionLauncher.launch(captureIntent)
        }
    }

    val requestAudioCastForTarget: (ConnectedDeviceInfo) -> Unit = { target ->
        connectManager.handoffStateMachine.resumeLocally()
        targetDeviceForCast = target
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val captureIntent = AndroidCastSourceManager.createScreenCaptureIntent(context)
            mediaProjectionLauncher.launch(captureIntent)
        } else {
            audioRecordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Управление состоянием ошибки Handoff
    androidx.compose.runtime.LaunchedEffect(lastHandoffError) {
        val err = lastHandoffError ?: return@LaunchedEffect
        when (err) {
            is HandoffError.Rejected -> {
                if (err.suggestCast && targetConnectingEndpoint != null) {
                    val target = devices.firstOrNull { it.endpointId == targetConnectingEndpoint }
                    showSuggestCastDialog = target
                }
            }
            HandoffError.Timeout -> {
                if (targetConnectingEndpoint != null) {
                    val target = devices.firstOrNull { it.endpointId == targetConnectingEndpoint }
                    showSuggestCastDialog = target
                }
            }
            else -> {}
        }
        targetConnectingEndpoint = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = tokens.surfaceElevated,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(tokens.borderSubtle)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Заголовок
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(tokens.accentPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DevicesOther,
                            contentDescription = null,
                            tint = tokens.accentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Воспроизвести на устройстве",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = tokens.textPrimary
                            )
                        )
                        Text(
                            text = "Lirix Connect",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = tokens.accentPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Закрыть шторку устройств"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = tokens.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ==========================================
            // 1. БЛОК: ТЕКУЩЕЕ УСТРОЙСТВО (§5.2)
            // ==========================================
            Text(
                text = "ТЕКУЩЕЕ УСТРОЙСТВО",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = tokens.textMuted
                )
            )

            if (isCasting) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF00E676)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Трансляция звука активна (Audio Cast)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.textPrimary
                                    )
                                )
                                Text(
                                    text = "Захват звука запущен; проверьте воспроизведение на ПК",
                                    style = MaterialTheme.typography.labelSmall.copy(color = tokens.textSecondary)
                                )
                            }
                        }
                        Button(
                            onClick = { connectManager.stopAudioCast() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Стоп", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tokens.surfaceCard,
                border = BorderStroke(1.dp, tokens.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Зелёный LED-индикатор активности
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isLocalPlayback) Color(0xFF00E676) else tokens.textMuted)
                        )

                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = tokens.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Этот телефон (Android)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary
                                )
                            )
                            Text(
                                text = if (isLocalPlayback) "Локальный аудиовыход • Источник звука" else "Пульт управления • Звук на внешнем хосте",
                                style = MaterialTheme.typography.labelSmall.copy(color = tokens.textSecondary)
                            )
                        }

                        if (!isLocalPlayback) {
                            OutlinedButton(
                                onClick = {
                                    connectManager.handoffStateMachine.resumeLocally()
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, tokens.borderSubtle),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.accentPrimary),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PhoneForwarded,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Вернуть звук",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Регулятор системной громкости телефона
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Громкость воспроизведения: ${(localVolume * 100).toInt()}%"
                            }
                    ) {
                        Icon(
                            imageVector = if (localVolume > 0.4f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = null,
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )

                        Slider(
                            value = localVolume,
                            onValueChange = { connectManager.setLocalVolumePct(it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = tokens.accentPrimary,
                                activeTrackColor = tokens.accentPrimary,
                                inactiveTrackColor = tokens.borderSubtle
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "${(localVolume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = tokens.textPrimary
                            ),
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            // ==========================================
            // 2. БЛОК: ДОСТУПНЫЕ УСТРОЙСТВА В СЕТИ (§5.2)
            // ==========================================
            Text(
                text = "ДОСТУПНЫЕ УСТРОЙСТВА В СЕТИ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = tokens.textMuted
                )
            )

            if (devices.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.surfaceCard.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, tokens.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет сопряжённых устройств. Отсканируйте QR-код на ПК для подключения.",
                            style = MaterialTheme.typography.bodySmall.copy(color = tokens.textMuted),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    devices.forEach { device ->
                        val isTargetConnecting = targetConnectingEndpoint == device.endpointId &&
                            (handoffState == HandoffState.REQUESTED || handoffState == HandoffState.TARGET_STARTING)
                        val isPlayingHere = isCasting && AndroidCastSourceManager.getInstance().activeDeviceName.value == device.deviceName

                        DeviceCardItem(
                            device = device,
                            isConnecting = isTargetConnecting,
                            isPlayingHere = isPlayingHere,
                            onDeviceClick = {
                                if (!isCasting) requestAudioCastForTarget(device)
                            },
                            onRenameClick = {
                                deviceToRename = device
                                renameTextFieldValue = device.deviceName
                            },
                            onForgetClick = {
                                deviceToForget = device
                            },
                            onStartCastClick = {
                                requestAudioCastForTarget(device)
                            }
                        )
                    }
                }
            }

            // ==========================================
            // 3. БЛОК: ПОДКЛЮЧИТЬ НОВОЕ УСТРОЙСТВО (§5.2)
            // ==========================================
            Button(
                onClick = { showScannerDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Сканировать QR-код для подключения нового устройства"
                    },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = tokens.appBackground,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Сканировать QR-код",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = tokens.appBackground
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // QR-сканер в полноэкранном диалоге
    if (showScannerDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScannerDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            QrScannerDialog(
                onDismissRequest = { showScannerDialog = false },
                onPairingPayloadDecoded = { payload ->
                    connectManager.pairWithPayload(payload)
                    showScannerDialog = false
                }
            )
        }
    }

    // Диалог переименования устройства
    if (deviceToRename != null) {
        val target = deviceToRename!!
        AlertDialog(
            onDismissRequest = { deviceToRename = null },
            title = {
                Text(
                    text = "Переименовать устройство",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Введите понятное название для этого устройства:",
                        style = MaterialTheme.typography.bodySmall.copy(color = tokens.textSecondary)
                    )
                    OutlinedTextField(
                        value = renameTextFieldValue,
                        onValueChange = { renameTextFieldValue = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.accentPrimary,
                            unfocusedBorderColor = tokens.borderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = renameTextFieldValue.trim()
                        if (name.isNotEmpty()) {
                            connectManager.renameDevice(target.endpointId, name)
                        }
                        deviceToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary)
                ) {
                    Text("Сохранить", color = tokens.appBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRename = null }) {
                    Text("Отмена", color = tokens.textSecondary)
                }
            },
            containerColor = tokens.surfaceElevated
        )
    }

    // Диалог подтверждения «Забыть устройство»
    if (deviceToForget != null) {
        val target = deviceToForget!!
        AlertDialog(
            onDismissRequest = { deviceToForget = null },
            title = {
                Text(
                    text = "Забыть устройство?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                )
            },
            text = {
                Text(
                    text = "Связь с устройством «${target.deviceName}» будет аннулирована. Для повторного подключения потребуется заново отсканировать QR-код.",
                    style = MaterialTheme.typography.bodySmall.copy(color = tokens.textSecondary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        connectManager.forgetDevice(target.endpointId)
                        deviceToForget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Забыть", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToForget = null }) {
                    Text("Отмена", color = tokens.textSecondary)
                }
            },
            containerColor = tokens.surfaceElevated
        )
    }

    // Диалог ошибки Handoff и fallback на Audio Cast (§5.3)
    if (showSuggestCastDialog != null) {
        val target = showSuggestCastDialog!!
        AlertDialog(
            onDismissRequest = { showSuggestCastDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Не удалось передать трек",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary
                    )
                )
            },
            text = {
                Text(
                    text = "Устройство «${target.deviceName}» не смогло открыть трек из каталога. Хотите включить прямую трансляцию звука (Audio Cast)?",
                    style = MaterialTheme.typography.bodySmall.copy(color = tokens.textSecondary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuggestCastDialog = null
                        requestAudioCastForTarget(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cast,
                            contentDescription = null,
                            tint = tokens.appBackground,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Включить Audio Cast", color = tokens.appBackground)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuggestCastDialog = null }) {
                    Text("Отмена", color = tokens.textSecondary)
                }
            },
            containerColor = tokens.surfaceElevated
        )
    }
}

/**
 * Карточка устройства в списке Connect.
 */
@Composable
private fun DeviceCardItem(
    device: ConnectedDeviceInfo,
    isConnecting: Boolean,
    isPlayingHere: Boolean,
    onDeviceClick: () -> Unit,
    onRenameClick: () -> Unit,
    onForgetClick: () -> Unit,
    onStartCastClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LirixThemeTokens.colors
    var menuExpanded by remember { mutableStateOf(false) }

    val icon: ImageVector = when (device.deviceType) {
        DeviceType.WINDOWS_PC -> Icons.Default.Computer
        DeviceType.ANDROID_TABLET -> Icons.Default.TabletAndroid
        DeviceType.ANDROID_PHONE -> Icons.Default.PhoneAndroid
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isPlayingHere) tokens.accentPrimary.copy(alpha = 0.12f) else tokens.surfaceCard,
        border = BorderStroke(
            1.dp,
            if (isPlayingHere) tokens.accentPrimary else tokens.borderSubtle
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onDeviceClick)
            .semantics {
                role = Role.Button
                contentDescription = "${device.deviceName}, ${if (device.isOnline) "в сети" else "не в сети"}"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Иконка типа устройства
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tokens.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPlayingHere) tokens.accentPrimary else tokens.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Название и статус
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (device.isOnline) Color(0xFF00E676) else tokens.textMuted)
                    )
                    Text(
                        text = when {
                            isPlayingHere -> "Играет на этом устройстве"
                            device.isOnline -> "В сети • Нажмите для передачи"
                            else -> "Не в сети"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isPlayingHere) tokens.accentPrimary else tokens.textSecondary
                        )
                    )
                }
            }

            // Спиннер подключения или меню
            if (isConnecting) {
                CircularProgressIndicator(
                    color = tokens.accentPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Меню устройства ${device.deviceName}"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(tokens.surfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Транслировать звук (Audio Cast)", color = tokens.textPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Cast,
                                    contentDescription = null,
                                    tint = tokens.accentPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onStartCastClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Переименовать", color = tokens.textPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = tokens.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRenameClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Забыть устройство", color = Color(0xFFEF4444)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onForgetClick()
                            }
                        )
                    }
                }
            }
        }
    }
}
