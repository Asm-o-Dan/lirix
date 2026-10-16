package com.lirix.app.ui.connect

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import timber.log.Timber
import java.util.concurrent.Executors
import com.lirix.app.connect.model.PairingPayload
import com.lirix.app.connect.transport.PairingExpiredException
import com.lirix.app.connect.transport.PairingPayloadCodec
import com.lirix.app.ui.theme.LirixThemeTokens

/**
 * QR-сканер сопряжения Lirix Connect (SPEC-CONNECT-01 §5.2, TASK-CON-UI-07).
 *
 * Обеспечивает:
 * 1. Проверку и запрос разрешения android.permission.CAMERA.
 * 2. При наличии разрешения: анимированный видоискатель со сканирующим лазером и подсветкой границ.
 * 3. При отказе или невозможности открыть камеру: graceful fallback на ручной ввод ссылки или кодового токена.
 * 4. Валидацию QR-payload через PairingPayloadCodec с понятной обработкой истекшего TTL.
 */
@Composable
fun QrScannerDialog(
    onDismissRequest: () -> Unit,
    onPairingPayloadDecoded: (PairingPayload) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tokens = LirixThemeTokens.colors

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var manualInputMode by remember { mutableStateOf(!hasCameraPermission) }
    var manualUriText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            manualInputMode = false
            errorMessage = null
        } else {
            manualInputMode = true
        }
    }

    fun tryDecodePayload(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            errorMessage = "Введите ссылку или токен сопряжения"
            return
        }

        val nowEpochSec = System.currentTimeMillis() / 1000L
        try {
            // Поддержка как полной схемы lirix://pair?d=..., так и чистого base64url токена
            val fullUri = if (trimmed.startsWith(PairingPayloadCodec.URI_PREFIX)) {
                trimmed
            } else if (trimmed.startsWith("lirix://")) {
                trimmed
            } else {
                "${PairingPayloadCodec.URI_PREFIX}$trimmed"
            }

            val payload = PairingPayloadCodec.decode(fullUri, nowEpochSec)
            errorMessage = null
            onPairingPayloadDecoded(payload)
        } catch (e: PairingExpiredException) {
            errorMessage = "Срок действия QR-кода истёк. Обновите код на ПК."
        } catch (e: IllegalArgumentException) {
            errorMessage = "Неверный формат кода. Проверьте правильность ссылки."
        } catch (e: Exception) {
            errorMessage = "Ошибка распознавания: ${e.message ?: "неизвестный сбой"}"
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Верхняя плашка управления
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = tokens.accentPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Сопряжение Lirix Connect",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Закрыть сканер"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Основная зона
            if (!hasCameraPermission || manualInputMode) {
                // Экран отсутствия разрешения или ручного ввода
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(top = 70.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(tokens.accentPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (!hasCameraPermission) Icons.Default.CameraAlt else Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = tokens.accentPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (!hasCameraPermission) "Требуется доступ к камере" else "Ручной ввод кода сопряжения",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (!hasCameraPermission) {
                            "Камера используется исключительно для мгновенного сканирования QR-кода на экране ПК. Вы также можете ввести код вручную."
                        } else {
                            "Вставьте ссылку «lirix://pair?d=...» или скопированный текст из диалога подключения на ПК."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = tokens.textSecondary,
                            lineHeight = 18.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (!hasCameraPermission) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary)
                            ) {
                                Text(
                                    text = "Разрешить камеру",
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.appBackground
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, tokens.borderSubtle),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Настройки приложения",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Поле ввода вручную
                    OutlinedTextField(
                        value = manualUriText,
                        onValueChange = {
                            manualUriText = it
                            errorMessage = null
                        },
                        label = { Text("Ссылка или токен сопряжения") },
                        placeholder = { Text("lirix://pair?d=...") },
                        singleLine = true,
                        isError = errorMessage != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.accentPrimary,
                            unfocusedBorderColor = tokens.borderSubtle,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = tokens.accentPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Поле ввода ссылки сопряжения"
                            }
                    )

                    AnimatedVisibility(visible = errorMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFEF4444))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { tryDecodePayload(manualUriText) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary)
                    ) {
                        Text(
                            text = "Подключить",
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.appBackground
                        )
                    }

                    if (hasCameraPermission && manualInputMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { manualInputMode = false },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = "Вернуться к сканированию камерой",
                                color = tokens.accentPrimary
                            )
                        }
                    }
                }
            } else {
                // Видоискатель сканера камеры
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Наведите видоискатель на QR-код на ПК",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Рамка видоискателя 260x260 dp с анимированным лазером
                    val infiniteTransition = rememberInfiniteTransition(label = "scannerLaser")
                    val laserY by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 240f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "laserPosition"
                    )

                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .border(
                                width = 2.dp,
                                color = tokens.accentPrimary,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .semantics {
                                contentDescription = "Видоискатель камеры для сканирования QR-кода"
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Живой видеопоток CameraX с автоматическим распознаванием QR
                        CameraPreviewScanner(
                            onQrScanned = { rawText ->
                                tryDecodePayload(rawText)
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Анимированный лазер поверх превью
                        Box(
                            modifier = Modifier
                                .offset(y = laserY.dp)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            tokens.accentPrimary,
                                            Color.White,
                                            tokens.accentPrimary,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    TextButton(
                        onClick = { manualInputMode = true },
                        modifier = Modifier
                            .height(48.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Перейти к ручному вводу ссылки сопряжения"
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = tokens.accentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Ввести код вручную",
                                color = tokens.accentPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Встроенный компонент видоискателя CameraX с распознаванием QR-кодов через ZXing.
 */
@Composable
fun CameraPreviewScanner(
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var hasDetected by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val reader = MultiFormatReader().apply {
                        setHints(
                            mapOf(
                                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                DecodeHintType.CHARACTER_SET to "UTF-8"
                            )
                        )
                    }

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (hasDetected) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        try {
                            val yPlane = imageProxy.planes[0]
                            val buffer = yPlane.buffer
                            val rowStride = yPlane.rowStride
                            val width = imageProxy.width
                            val height = imageProxy.height
                            val data = ByteArray(buffer.remaining())
                            buffer.get(data)

                            var decodedText: String? = null

                            // 1. Прямая ориентация с учетом rowStride
                            try {
                                val source = PlanarYUVLuminanceSource(
                                    data, rowStride, height, 0, 0, width, height, false
                                )
                                val bitmap = BinaryBitmap(HybridBinarizer(source))
                                val result = reader.decodeWithState(bitmap)
                                decodedText = result?.text
                            } catch (_: NotFoundException) {
                                // 2. Повернутая ориентация (для вертикального сенсора смартфона)
                                try {
                                    val rotatedData = ByteArray(width * height)
                                    for (y in 0 until height) {
                                        for (x in 0 until width) {
                                            rotatedData[x * height + (height - y - 1)] = data[x + y * rowStride]
                                        }
                                    }
                                    val rotatedSource = PlanarYUVLuminanceSource(
                                        rotatedData, height, width, 0, 0, height, width, false
                                    )
                                    val rotatedBitmap = BinaryBitmap(HybridBinarizer(rotatedSource))
                                    val result = reader.decodeWithState(rotatedBitmap)
                                    decodedText = result?.text
                                } catch (_: Exception) {}
                            } catch (_: Exception) {}

                            if (!decodedText.isNullOrBlank() && !hasDetected) {
                                hasDetected = true
                                Handler(Looper.getMainLooper()).post {
                                    onQrScanned(decodedText)
                                }
                            }
                        } finally {
                            reader.reset()
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Camera binding failed")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}
