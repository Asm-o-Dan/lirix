package com.example.npc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npc.app.onboarding.OnboardingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onOpenNotificationListener: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenHyperOsAutostart: () -> Unit,
    onRequestRuntimePermissions: () -> Unit,
    onRefreshState: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onBack != null) {
        androidx.activity.compose.BackHandler(onBack = onBack)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (onBack != null) "Системные настройки" else "Первичная настройка",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть настройки"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshState) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Обновить статус"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Для надёжного перехвата и сохранения событий приложению требуются системные разрешения.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Шаг 1: Доступ к уведомлениям
            StepCard(
                title = "1. Доступ к уведомлениям",
                description = "Требуется для чтения входящих уведомлений через системную службу NotificationListenerService.",
                isGranted = state.isNotificationListenerGranted,
                buttonText = if (state.isNotificationListenerGranted) "Предоставлено" else "Настроить доступ",
                onAction = onOpenNotificationListener,
                isMandatory = true
            )

            // Шаг 2: Отключение ограничений батареи
            StepCard(
                title = "2. Работа без ограничений питания",
                description = "Отключение оптимизации Doze/App Standby гарантирует, что система не усыпит фоновый сбор.",
                isGranted = state.isBatteryOptimizationIgnored,
                buttonText = if (state.isBatteryOptimizationIgnored) "Отключено" else "Отключить оптимизацию",
                onAction = onRequestBatteryOptimization,
                isMandatory = true
            )

            // Шаг 3: Автозапуск HyperOS / MIUI (если применимо)
            if (state.isHyperOsDevice) {
                StepCard(
                    title = "3. Автозапуск HyperOS / MIUI",
                    description = "Разрешите приложению автоматический запуск в диспетчере безопасности Xiaomi / Poco.",
                    isGranted = state.isHyperOsAutostartAcknowledged,
                    buttonText = if (state.isHyperOsAutostartAcknowledged) "Подтверждено" else "Открыть автозапуск",
                    onAction = onOpenHyperOsAutostart,
                    isMandatory = true
                )

                // Памятка по закреплению в Недавних
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Рекомендация для Poco / HyperOS",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Откройте меню «Недавние приложения», зажмите карточку этого приложения и нажмите на иконку замка (Lock), чтобы предотвратить выгрузку из памяти.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Шаг 4: Runtime-разрешения (SMS, Уведомления)
            val allRuntimeGranted = state.isPostNotificationsGranted &&
                    state.isSmsPermissionsGranted

            StepCard(
                title = "4. Системные разрешения",
                description = "Чтение SMS и отправка служебных уведомлений.",
                isGranted = allRuntimeGranted,
                buttonText = if (allRuntimeGranted) "Все разрешения выданы" else "Запросить разрешения",
                onAction = onRequestRuntimePermissions,
                isMandatory = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Финальная кнопка
            Button(
                onClick = onRefreshState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = true
            ) {
                Text(
                    text = if (state.isMandatorySetupComplete) "Все настройки готовы! Нажмите для входа" else "Проверить готовность настроек",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    onAction: () -> Unit,
    isMandatory: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFFA000)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isMandatory) {
                        Text(
                            text = if (isGranted) "Готово" else "Обязательно",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!isGranted) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = buttonText)
                }
            } else {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = buttonText)
                }
            }
        }
    }
}
