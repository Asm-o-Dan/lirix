package com.example.npc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.npc.app.onboarding.OnboardingManager
import com.example.npc.app.onboarding.OnboardingState
import com.example.npc.app.ui.OnboardingScreen
import com.example.npc.core.storage.StorageGateway
import com.example.npc.feature.analytics.FinancialAnalyticsScreen
import com.example.npc.feature.analytics.FinancialAnalyticsViewModel
import com.example.npc.feature.diagnostics.OrchestratorDiagnosticsScreen
import com.example.npc.ui.timeline.ui.TimelineScreen
import com.example.npc.ui.timeline.ui.TimelineViewModel
import com.example.npc.core.storage.bank.TemplateBankManager
import com.example.npc.core.storage.dao.DynamicTemplateDao
import com.example.npc.domain.usecase.TemplateBackfillEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

enum class MainNavigationTab {
    TIMELINE,
    ANALYTICS,
    SETTINGS
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var onboardingManager: OnboardingManager
    @Inject lateinit var storageGateway: StorageGateway
    @Inject lateinit var templateBankManager: TemplateBankManager
    @Inject lateinit var backfillEngine: TemplateBackfillEngine
    @Inject lateinit var dynamicTemplateDao: DynamicTemplateDao

    private var onboardingState by mutableStateOf<OnboardingState?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshState()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state = onboardingState
                    if (state == null || !state.isMandatorySetupComplete) {
                        OnboardingScreen(
                            state = state ?: onboardingManager.getOnboardingState(),
                            onOpenNotificationListener = {
                                startActivity(onboardingManager.createNotificationListenerSettingsIntent())
                            },
                            onRequestBatteryOptimization = {
                                startActivity(onboardingManager.createBatteryOptimizationIntent())
                            },
                            onOpenHyperOsAutostart = {
                                startActivity(onboardingManager.createHyperOsAutostartIntent())
                                refreshState()
                            },
                            onRequestRuntimePermissions = {
                                permissionLauncher.launch(onboardingManager.getRequiredRuntimePermissions())
                            },
                            onRefreshState = { refreshState() }
                        )
                    } else {
                        var selectedTab by remember { mutableStateOf(MainNavigationTab.TIMELINE) }
                        var isDiagnosticsOpen by remember { mutableStateOf(false) }
                        val timelineViewModel = remember {
                            TimelineViewModel(
                                storageGateway = storageGateway,
                                templateBankManager = templateBankManager,
                                backfillEngine = backfillEngine
                            )
                        }
                        val analyticsViewModel = remember { FinancialAnalyticsViewModel(storageGateway) }

                        if (isDiagnosticsOpen) {
                            BackHandler {
                                isDiagnosticsOpen = false
                            }
                            OrchestratorDiagnosticsScreen(
                                templateBankManager = templateBankManager,
                                dynamicTemplateDao = dynamicTemplateDao,
                                onBackClick = { isDiagnosticsOpen = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                bottomBar = {
                                    NavigationBar {
                                        NavigationBarItem(
                                            selected = selectedTab == MainNavigationTab.TIMELINE,
                                            onClick = { selectedTab = MainNavigationTab.TIMELINE },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.List,
                                                    contentDescription = "Лента"
                                                )
                                            },
                                            label = { Text("Лента") }
                                        )
                                        NavigationBarItem(
                                            selected = selectedTab == MainNavigationTab.ANALYTICS,
                                            onClick = { selectedTab = MainNavigationTab.ANALYTICS },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                                    contentDescription = "Аналитика"
                                                )
                                            },
                                            label = { Text("Аналитика") }
                                        )
                                        NavigationBarItem(
                                            selected = selectedTab == MainNavigationTab.SETTINGS,
                                            onClick = { selectedTab = MainNavigationTab.SETTINGS },
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = "Настройки"
                                                )
                                            },
                                            label = { Text("Настройки") }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = innerPadding.calculateBottomPadding())
                                ) {
                                    when (selectedTab) {
                                        MainNavigationTab.TIMELINE -> {
                                            TimelineScreen(
                                                viewModel = timelineViewModel,
                                                onOpenSettings = { selectedTab = MainNavigationTab.SETTINGS },
                                                onOpenAnalytics = { selectedTab = MainNavigationTab.ANALYTICS },
                                                onOpenDiagnostics = { isDiagnosticsOpen = true },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        MainNavigationTab.ANALYTICS -> {
                                            BackHandler {
                                                selectedTab = MainNavigationTab.TIMELINE
                                            }
                                            FinancialAnalyticsScreen(
                                                viewModel = analyticsViewModel,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        MainNavigationTab.SETTINGS -> {
                                            OnboardingScreen(
                                                state = state,
                                                onOpenNotificationListener = {
                                                    startActivity(onboardingManager.createNotificationListenerSettingsIntent())
                                                },
                                                onRequestBatteryOptimization = {
                                                    startActivity(onboardingManager.createBatteryOptimizationIntent())
                                                },
                                                onOpenHyperOsAutostart = {
                                                    startActivity(onboardingManager.createHyperOsAutostartIntent())
                                                    refreshState()
                                                },
                                                onRequestRuntimePermissions = {
                                                    permissionLauncher.launch(onboardingManager.getRequiredRuntimePermissions())
                                                },
                                                onRefreshState = { refreshState() },
                                                onBack = { selectedTab = MainNavigationTab.TIMELINE },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        onboardingState = onboardingManager.getOnboardingState()
    }
}
