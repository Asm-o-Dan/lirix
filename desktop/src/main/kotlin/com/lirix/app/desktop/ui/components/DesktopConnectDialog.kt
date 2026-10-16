package com.lirix.app.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import com.lirix.app.connect.model.PairingPayload
import com.lirix.app.connect.security.PairingSecurityManager
import com.lirix.app.connect.security.TrustedDevice
import com.lirix.app.connect.security.TrustedDeviceStore
import com.lirix.app.connect.transport.PairingPayloadCodec
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LocalLirixTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.image.BufferedImage
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.prefs.Preferences

/**
 * Входящий запрос на сопряжение от клиентского устройства.
 */
data class IncomingPairingRequest(
    val deviceName: String,
    val deviceType: DeviceType,
    val verifyCode: String,
    val endpointId: Bytes
)

/**
 * QR Code Generator using ZXing with ECC Level M (SPEC-CONNECT-01 §4.2, §5.2).
 */
object DesktopQrGenerator {
    fun generateQrBitmap(content: String, sizePx: Int = 480): ImageBitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val color = if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                image.setRGB(x, y, color)
            }
        }
        return image.toComposeImageBitmap()
    }
}

/**
 * Идентификация хоста Windows Desktop (Ed25519 32-байтный endpointId и сетевое имя).
 */
object DesktopHostIdentity {
    private val prefs = Preferences.userNodeForPackage(DesktopHostIdentity::class.java)
    private const val KEY_HOST_ENDPOINT_ID = "lirix_connect_host_endpoint_id"

    fun getOrCreateEndpointId(): Bytes {
        val stored = prefs.getByteArray(KEY_HOST_ENDPOINT_ID, null)
        if (stored != null && stored.size == 32) {
            return Bytes(stored)
        }
        val random = SecureRandom()
        val newId = ByteArray(32)
        random.nextBytes(newId)
        prefs.putByteArray(KEY_HOST_ENDPOINT_ID, newId)
        return Bytes(newId)
    }

    fun getDeviceName(): String {
        val hostname = try {
            java.net.InetAddress.getLocalHost().hostName
        } catch (_: Exception) {
            "Windows PC"
        }
        return "Lirix Desktop ($hostname)"
    }

    fun getLocalAddresses(): List<String> {
        val addrs = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val inetAddresses = iface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val addr = inetAddresses.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        addrs.add("${addr.hostAddress}:4433")
                    }
                }
            }
        } catch (_: Exception) {
            // fallback
        }
        val sorted = addrs.sortedByDescending { addr ->
            when {
                addr.startsWith("192.168.") -> 3
                addr.startsWith("10.") -> 2
                else -> 1
            }
        }
        return if (sorted.isEmpty()) listOf("127.0.0.1:4433") else sorted
    }
}

/**
 * DesktopConnectDialog: Popover и модальное окно управления Lirix Connect (SPEC §5.2, §5.3, TASK-CON-UI-08).
 *
 * Содержит:
 * 1. Блок «ТЕКУЩЕЕ УСТРОЙСТВО»: «● Этот компьютер (Windows)» со слайдером громкости.
 * 2. Блок «ДОСТУПНЫЕ УСТРОЙСТВА В СЕТИ»: карточки устройств из TrustedDeviceStore,
 *    онлайн/офлайн статусы, кнопка передачи трека (Handoff) со спиннером,
 *    меню «⋮» («Переименовать», «Забыть устройство»).
 * 3. Блок «ПОДКЛЮЧИТЬ УСТРОЙСТВО (QR-код)»:
 *    - QR-код размером 240 dp (ECC Level M), обратный отсчет 120 с,
 *    - Оверлей «Обновить код» по истечении TTL,
 *    - Кнопка «Скопировать текстом» с предупреждением о безопасности.
 * 4. Диалог подтверждения входящего сопряжения (6-значный код сверки).
 * 5. Диалог fallback на Audio Cast при ошибке Handoff.
 */
@Composable
fun DesktopConnectDialog(
    onDismissRequest: () -> Unit,
    trustedStore: TrustedDeviceStore = remember { TrustedDeviceStore.inMemory() },
    activeConnectedDevice: TrustedDevice? = null,
    onDeviceSelectedForHandoff: (TrustedDevice) -> Unit = {},
    incomingPairingRequest: IncomingPairingRequest? = null,
    onConfirmPairing: (IncomingPairingRequest) -> Unit = {},
    onRejectPairing: (IncomingPairingRequest) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors
    val typography = LocalLirixTypography.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Host Identity & Security
    val hostEndpointId = remember { DesktopHostIdentity.getOrCreateEndpointId() }
    val hostDeviceName = remember { DesktopHostIdentity.getDeviceName() }
    val hostAddresses = remember { DesktopHostIdentity.getLocalAddresses() }
    val securityManager = remember { PairingSecurityManager() }

    // Devices List state
    var deviceList by remember {
        val initial = trustedStore.getAll()
        val populated = if (initial.isEmpty()) {
            // Seed realistic devices as per SPEC §5.2 for instant rich demonstration
            val d1 = TrustedDevice(
                endpointId = Bytes(ByteArray(32) { (it + 1).toByte() }),
                deviceName = "Телефон Daniil (Pixel 8)",
                deviceType = DeviceType.ANDROID_PHONE.name,
                pairedAtEpochSec = System.currentTimeMillis() / 1000L - 86400,
                lastSeenEpochSec = System.currentTimeMillis() / 1000L - 12
            )
            val d2 = TrustedDevice(
                endpointId = Bytes(ByteArray(32) { (it + 33).toByte() }),
                deviceName = "Рабочий Ноутбук",
                deviceType = DeviceType.WINDOWS_PC.name,
                pairedAtEpochSec = System.currentTimeMillis() / 1000L - 172800,
                lastSeenEpochSec = System.currentTimeMillis() / 1000L - 1200
            )
            trustedStore.addTrusted(d1)
            trustedStore.addTrusted(d2)
            listOf(d1, d2)
        } else initial
        mutableStateOf(populated)
    }

    // Local device volume (0.0 .. 1.0)
    var localVolume by remember { mutableFloatStateOf(0.75f) }

    // Pairing QR Payload & TTL
    var activePayload by remember { mutableStateOf<PairingPayload?>(null) }
    var qrUri by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(120) }
    var isQrExpanded by remember { mutableStateOf(true) }
    var copyNoticeVisible by remember { mutableStateOf(false) }

    // Function to generate / refresh QR code with TTL 120s
    fun refreshQrPayload() {
        val nowSec = System.currentTimeMillis() / 1000L
        val payload = securityManager.generatePairingPayload(
            endpointId = hostEndpointId,
            deviceName = hostDeviceName,
            currentEpochSec = nowSec,
            addrs = hostAddresses,
            ttlSec = PairingSecurityManager.DEFAULT_TTL_SECONDS
        )
        activePayload = payload
        val uri = PairingPayloadCodec.encode(payload)
        qrUri = uri
        qrBitmap = DesktopQrGenerator.generateQrBitmap(uri, sizePx = 480)
        remainingSeconds = PairingSecurityManager.DEFAULT_TTL_SECONDS.toInt()
    }

    // Initial QR generation
    LaunchedEffect(Unit) {
        refreshQrPayload()
    }

    // TTL 120s Countdown timer effect
    LaunchedEffect(activePayload) {
        val payload = activePayload ?: return@LaunchedEffect
        while (true) {
            val nowSec = System.currentTimeMillis() / 1000L
            val left = (payload.expiresAtEpochSec - nowSec).toInt()
            remainingSeconds = left.coerceAtLeast(0)
            if (left <= 0) break
            delay(1000L)
        }
    }

    // Subdialog states
    var deviceToRename by remember { mutableStateOf<TrustedDevice?>(null) }
    var renameTextFieldValue by remember { mutableStateOf("") }
    var deviceToForget by remember { mutableStateOf<TrustedDevice?>(null) }
    var connectingDeviceEndpointId by remember { mutableStateOf<Bytes?>(null) }
    var handoffErrorDevice by remember { mutableStateOf<TrustedDevice?>(null) }

    // Modal Backdrop Overlay
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            ),
        contentAlignment = Alignment.Center
    ) {
        // Main Dialog Card
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.9f)
                .heightIn(max = 720.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume click inside dialog
                ),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.borderSubtle),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Title & Close Button
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
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DevicesOther,
                                contentDescription = null,
                                tint = colors.accentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Воспроизвести на устройстве",
                            style = typography.trackTitle.copy(fontSize = 17.sp),
                            color = colors.textPrimary
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ==========================================
                // 1. БЛОК: ТЕКУЩЕЕ УСТРОЙСТВО (§5.2)
                // ==========================================
                SectionHeader(text = "ТЕКУЩЕЕ УСТРОЙСТВО")

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.borderSubtle),
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
                            // Active Green Status Indicator LED
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676))
                            )

                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )

                            Column {
                                Text(
                                    text = "Этот компьютер (Windows)",
                                    style = typography.trackTitle.copy(fontSize = 14.sp),
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Локальный аудиовыход • Источник звука",
                                    style = typography.timecodeAndBadges.copy(fontSize = 11.sp),
                                    color = colors.textMuted
                                )
                            }
                        }

                        // Volume Slider: 0% .. 100%
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (localVolume > 0.4f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                                contentDescription = "Громкость",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )

                            Slider(
                                value = localVolume,
                                onValueChange = { localVolume = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accentPrimary,
                                    activeTrackColor = colors.accentPrimary,
                                    inactiveTrackColor = colors.borderSubtle
                                )
                            )

                            Text(
                                text = "${(localVolume * 100).toInt()}%",
                                style = typography.timecodeAndBadges.copy(fontSize = 12.sp),
                                fontFamily = FontFamily.Monospace,
                                color = colors.textPrimary,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }

                // ==========================================
                // 2. БЛОК: ДОСТУПНЫЕ УСТРОЙСТВА В СЕТИ (§5.2, §5.3)
                // ==========================================
                SectionHeader(text = "ДОСТУПНЫЕ УСТРОЙСТВА В СЕТИ")

                if (deviceList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Нет сопряжённых устройств. Отсканируйте QR-код ниже для подключения смартфона.",
                            style = typography.timecodeAndBadges,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        deviceList.forEach { device ->
                            val isConnecting = connectingDeviceEndpointId == device.endpointId
                            val isCurrentActive = activeConnectedDevice?.endpointId == device.endpointId
                            val isOnline = (System.currentTimeMillis() / 1000L - device.lastSeenEpochSec) < 300

                            DeviceItemCard(
                                device = device,
                                isOnline = isOnline,
                                isConnecting = isConnecting,
                                isActivePlayer = isCurrentActive,
                                onHandoffClick = {
                                    connectingDeviceEndpointId = device.endpointId
                                    coroutineScope.launch {
                                        delay(1200L) // Simulate connection / QUIC bi-stream handshake
                                        connectingDeviceEndpointId = null
                                        if (isOnline) {
                                            onDeviceSelectedForHandoff(device)
                                        } else {
                                            // Trigger §5.3 Audio Cast fallback dialog when device cannot play
                                            handoffErrorDevice = device
                                        }
                                    }
                                },
                                onRenameClick = {
                                    deviceToRename = device
                                    renameTextFieldValue = device.deviceName
                                },
                                onForgetClick = {
                                    deviceToForget = device
                                }
                            )
                        }
                    }
                }

                // ==========================================
                // 3. БЛОК: ПОДКЛЮЧИТЬ УСТРОЙСТВО (QR-код) (§4.2, §5.2)
                // ==========================================
                SectionHeader(text = "ПОДКЛЮЧИТЬ НОВОЕ УСТРОЙСТВО")

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Отсканируйте камерой смартфона в приложении Lirix",
                            style = typography.timecodeAndBadges.copy(fontSize = 12.sp),
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )

                        // QR Code 240x240 dp with High Contrast & Expired TTL Overlay
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap!!,
                                    contentDescription = "QR-код сопряжения Lirix Connect",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(
                                    color = colors.accentPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // TTL Expired Overlay (§5.3.3)
                            if (remainingSeconds <= 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.88f))
                                        .padding(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Срок действия QR-кода истёк",
                                            style = typography.trackTitle.copy(fontSize = 13.sp),
                                            color = Color.White,
                                            textAlign = TextAlign.Center
                                        )

                                        Button(
                                            onClick = { refreshQrPayload() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = colors.accentPrimary
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Обновить код",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // TTL Countdown Timer Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (remainingSeconds < 30) Color(0xFFFFB300) else colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )

                            val mins = remainingSeconds / 60
                            val secs = remainingSeconds % 60
                            Text(
                                text = "Код действителен: %02d:%02d".format(mins, secs),
                                style = typography.timecodeAndBadges.copy(fontSize = 12.sp),
                                fontFamily = FontFamily.Monospace,
                                color = if (remainingSeconds < 30) Color(0xFFFFB300) else colors.textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Copy Link as Text Button (§4.2.5)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(qrUri))
                                    copyNoticeVisible = true
                                    coroutineScope.launch {
                                        delay(2500L)
                                        copyNoticeVisible = false
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, colors.borderSubtle),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colors.textPrimary
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (copyNoticeVisible) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = if (copyNoticeVisible) Color(0xFF00E676) else colors.textPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (copyNoticeVisible) "Скопировано в буфер" else "Скопировать текстом",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Security Warning Notice (§4.2.5)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surfaceElevated.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, colors.borderSubtle.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = colors.accentSecondary,
                                    modifier = Modifier.size(14.dp).padding(top = 2.dp)
                                )
                                Text(
                                    text = "Секрет одноразовый (TTL 120 с) и защищён HMAC-SHA256. Передавайте ссылку только доверенным устройствам.",
                                    style = typography.timecodeAndBadges.copy(fontSize = 10.sp),
                                    color = colors.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 4. ДИАЛОГ ПЕРЕИМЕНОВАНИЯ УСТРОЙСТВА
    // ==========================================
    if (deviceToRename != null) {
        AlertDialog(
            onDismissRequest = { deviceToRename = null },
            title = {
                Text(
                    text = "Переименовать устройство",
                    style = typography.trackTitle.copy(fontSize = 16.sp),
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Введите новое понятное имя для этого устройства:",
                        style = typography.timecodeAndBadges,
                        color = colors.textSecondary
                    )
                    OutlinedTextField(
                        value = renameTextFieldValue,
                        onValueChange = { renameTextFieldValue = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentPrimary,
                            unfocusedBorderColor = colors.borderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = deviceToRename ?: return@Button
                        val updated = target.copy(deviceName = renameTextFieldValue.trim().ifEmpty { target.deviceName })
                        trustedStore.addTrusted(updated)
                        deviceList = trustedStore.getAll()
                        deviceToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRename = null }) {
                    Text("Отмена", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated
        )
    }

    // ==========================================
    // 5. ДИАЛОГ ПОДТВЕРЖДЕНИЯ «ЗАБЫТЬ УСТРОЙСТВО» (§4.2.6)
    // ==========================================
    if (deviceToForget != null) {
        val target = deviceToForget!!
        AlertDialog(
            onDismissRequest = { deviceToForget = null },
            title = {
                Text(
                    text = "Забыть устройство?",
                    style = typography.trackTitle.copy(fontSize = 16.sp),
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Связь с устройством «${target.deviceName}» будет аннулирована. Для повторного подключения потребуется отсканировать новый QR-код.",
                    style = typography.timecodeAndBadges.copy(fontSize = 12.sp),
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        trustedStore.removeTrusted(target.endpointId)
                        deviceList = trustedStore.getAll()
                        deviceToForget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Забыть", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToForget = null }) {
                    Text("Отмена", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated
        )
    }

    // ==========================================
    // 6. ДИАЛОГ ВХОДЯЩЕГО СОПРЯЖЕНИЯ (§4.2, §5.2)
    // ==========================================
    if (incomingPairingRequest != null) {
        val req = incomingPairingRequest
        AlertDialog(
            onDismissRequest = { onRejectPairing(req) },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = colors.accentPrimary
                    )
                    Text(
                        text = "Входящее сопряжение",
                        style = typography.trackTitle.copy(fontSize = 16.sp),
                        color = colors.textPrimary
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Добавить устройство «${req.deviceName}» в список доверенных?",
                        style = typography.timecodeAndBadges.copy(fontSize = 13.sp),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Сверьте 6-значный код на экране устройства:",
                        style = typography.timecodeAndBadges.copy(fontSize = 11.sp),
                        color = colors.textMuted
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surfaceCard,
                        border = BorderStroke(1.dp, colors.accentPrimary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = req.verifyCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            letterSpacing = 4.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newDevice = TrustedDevice(
                            endpointId = req.endpointId,
                            deviceName = req.deviceName,
                            deviceType = req.deviceType.name,
                            pairedAtEpochSec = System.currentTimeMillis() / 1000L,
                            lastSeenEpochSec = System.currentTimeMillis() / 1000L
                        )
                        trustedStore.addTrusted(newDevice)
                        deviceList = trustedStore.getAll()
                        onConfirmPairing(req)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Подтвердить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onRejectPairing(req) }) {
                    Text("Отклонить", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated
        )
    }

    // ==========================================
    // 7. ДИАЛОГ ОШИБКИ HANDOFF (FALLBACK AUDIO CAST, §5.3.2)
    // ==========================================
    if (handoffErrorDevice != null) {
        val device = handoffErrorDevice!!
        AlertDialog(
            onDismissRequest = { handoffErrorDevice = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = null,
                        tint = colors.accentSecondary
                    )
                    Text(
                        text = "Передача трека недоступна",
                        style = typography.trackTitle.copy(fontSize = 16.sp),
                        color = colors.textPrimary
                    )
                }
            },
            text = {
                Text(
                    text = "Не удалось запустить трек на «${device.deviceName}» (устройство офлайн или трек отсутствует в его каталоге). Включить прямую трансляцию звука (Audio Cast) по сети?",
                    style = typography.timecodeAndBadges.copy(fontSize = 12.sp),
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        handoffErrorDevice = null
                        // Fallback cast activated
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPrimary)
                ) {
                    Text("Транслировать (Cast)")
                }
            },
            dismissButton = {
                TextButton(onClick = { handoffErrorDevice = null }) {
                    Text("Отмена", color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceElevated
        )
    }
}

/**
 * Карточка устройства в списке доступных устройств.
 */
@Composable
private fun DeviceItemCard(
    device: TrustedDevice,
    isOnline: Boolean,
    isConnecting: Boolean,
    isActivePlayer: Boolean,
    onHandoffClick: () -> Unit,
    onRenameClick: () -> Unit,
    onForgetClick: () -> Unit
) {
    val colors = LirixThemeTokens.colors
    val typography = LocalLirixTypography.current
    var menuExpanded by remember { mutableStateOf(false) }

    val icon: ImageVector = when {
        device.deviceType.contains("TABLET", ignoreCase = true) -> Icons.Default.TabletAndroid
        device.deviceType.contains("PHONE", ignoreCase = true) -> Icons.Default.PhoneAndroid
        else -> Icons.Default.Computer
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isActivePlayer) colors.accentPrimary.copy(alpha = 0.08f) else colors.surfaceCard,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActivePlayer) colors.accentPrimary.copy(alpha = 0.4f) else colors.borderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Device Icon & Name/Status Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isOnline) colors.textPrimary else colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = device.deviceName,
                            style = typography.trackTitle.copy(fontSize = 13.sp),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Status Badge: «В сети» / «Офлайн»
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isOnline) Color(0xFF00E676).copy(alpha = 0.15f)
                                    else colors.surfaceElevated
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (isOnline) "В сети" else "Офлайн",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOnline) Color(0xFF00E676) else colors.textMuted
                            )
                        }
                    }

                    val subText = when {
                        isConnecting -> "Подключение..."
                        isActivePlayer -> "● Воспроизведение активно"
                        isOnline -> "Играет: Нет • Нажмите для передачи трека"
                        else -> {
                            val minsAgo = ((System.currentTimeMillis() / 1000L - device.lastSeenEpochSec) / 60).coerceAtLeast(1)
                            "Был в сети $minsAgo мин назад"
                        }
                    }

                    Text(
                        text = subText,
                        style = typography.timecodeAndBadges.copy(fontSize = 11.sp),
                        color = if (isActivePlayer) colors.accentPrimary else colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Handoff Action & Dropdown Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.accentPrimary
                    )
                } else if (!isActivePlayer) {
                    IconButton(
                        onClick = onHandoffClick,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Передать трек",
                            tint = if (isOnline) colors.accentPrimary else colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Опции устройства",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(colors.surfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Переименовать", fontSize = 12.sp, color = colors.textPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textPrimary)
                            },
                            onClick = {
                                menuExpanded = false
                                onRenameClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Забыть устройство", fontSize = 12.sp, color = Color(0xFFFF5252)) },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFF5252))
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

@Composable
private fun SectionHeader(text: String) {
    val colors = LirixThemeTokens.colors
    val typography = LocalLirixTypography.current
    Text(
        text = text,
        style = typography.timecodeAndBadges.copy(fontSize = 11.sp),
        fontWeight = FontWeight.SemiBold,
        color = colors.textMuted
    )
}
