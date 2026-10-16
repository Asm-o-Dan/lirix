package com.lirix.app.desktop

import com.lirix.app.desktop.system.CommandExecutor
import com.lirix.app.desktop.system.CommandResult
import com.lirix.app.desktop.system.FirewallRuleStatus
import com.lirix.app.desktop.system.WindowsFirewallManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsFirewallManagerTest {

    private class MockCommandExecutor(
        private val handler: (List<String>) -> CommandResult
    ) : CommandExecutor {
        val executedCommands = mutableListOf<List<String>>()

        override fun execute(command: List<String>): CommandResult {
            executedCommands.add(command)
            return handler(command)
        }
    }

    @Test
    fun testCheckFirewallRule_AllowedEnglish() = runBlocking {
        val englishOutput = """
            Rule Name:                            Lirix Connect
            ----------------------------------------------------------------------
            Enabled:                              Yes
            Direction:                            In
            Profiles:                             Private,Domain
            Grouping:                             
            LocalIP:                              Any
            RemoteIP:                             Any
            Protocol:                             UDP
            LocalPort:                            Any
            RemotePort:                           Any
            Edge traversal:                       No
            Action:                               Allow
            Ok.
        """.trimIndent()

        val mock = MockCommandExecutor { CommandResult(0, englishOutput, "") }
        val manager = WindowsFirewallManager(commandExecutor = mock, osName = "Windows 11")

        val result = manager.checkFirewallRuleExists("Lirix Connect")
        assertEquals(FirewallRuleStatus.ALLOWED, result.status)
        assertTrue(result.message.contains("активно", ignoreCase = true))
    }

    @Test
    fun testCheckFirewallRule_AllowedRussian() = runBlocking {
        val russianOutput = """
            Имя правила:                          Lirix Connect
            ----------------------------------------------------------------------
            Включено:                             Да
            Направление:                          Входящие
            Профили:                              Частный,Доменный
            Группировка:                          
            Локальный IP:                         Любой
            Удаленный IP:                         Любой
            Протокол:                             UDP
            Локальный порт:                       Любой
            Удаленный порт:                       Любой
            Обход границ:                         Нет
            Действие:                             Разрешить
            Ok.
        """.trimIndent()

        val mock = MockCommandExecutor { CommandResult(0, russianOutput, "") }
        val manager = WindowsFirewallManager(commandExecutor = mock, osName = "Windows 10")

        val result = manager.checkFirewallRuleExists("Lirix Connect")
        assertEquals(FirewallRuleStatus.ALLOWED, result.status)
    }

    @Test
    fun testCheckFirewallRule_Blocked() = runBlocking {
        val blockedOutput = """
            Rule Name:                            Lirix Connect
            ----------------------------------------------------------------------
            Enabled:                              Yes
            Action:                               Block
        """.trimIndent()

        val mock = MockCommandExecutor { CommandResult(0, blockedOutput, "") }
        val manager = WindowsFirewallManager(commandExecutor = mock, osName = "Windows 11")

        val result = manager.checkFirewallRuleExists("Lirix Connect")
        assertEquals(FirewallRuleStatus.BLOCKED, result.status)
        assertNotNull(result.recommendedCommand)
    }

    @Test
    fun testCheckFirewallRule_Disabled() = runBlocking {
        val disabledOutput = """
            Rule Name:                            Lirix Connect
            ----------------------------------------------------------------------
            Enabled:                              No
            Action:                               Allow
        """.trimIndent()

        val mock = MockCommandExecutor { CommandResult(0, disabledOutput, "") }
        val manager = WindowsFirewallManager(commandExecutor = mock, osName = "Windows 11")

        val result = manager.checkFirewallRuleExists("Lirix Connect")
        assertEquals(FirewallRuleStatus.BLOCKED, result.status)
    }

    @Test
    fun testCheckFirewallRule_NotConfigured() = runBlocking {
        val notFoundOutput = "No rules match the specified criteria."
        val mock = MockCommandExecutor { CommandResult(1, notFoundOutput, "") }
        val manager = WindowsFirewallManager(commandExecutor = mock, osName = "Windows 11")

        val result = manager.checkFirewallRuleExists("Lirix Connect")
        assertEquals(FirewallRuleStatus.NOT_CONFIGURED, result.status)
        assertNotNull(result.recommendedCommand)
        assertTrue(result.recommendedCommand!!.contains("netsh advfirewall firewall add rule"))
    }

    @Test
    fun testCheckFirewallRule_NotSupportedOnNonWindows() = runBlocking {
        val mock = MockCommandExecutor { CommandResult(0, "", "") }
        val manager = WindowsFirewallManager(commandExecutor = mock, osName = "Linux")

        val result = manager.checkFirewallRuleExists("Lirix Connect")
        assertEquals(FirewallRuleStatus.NOT_SUPPORTED, result.status)
        assertEquals(0, mock.executedCommands.size)
    }

    @Test
    fun testBuildAddRuleNetshCommand() {
        val manager = WindowsFirewallManager(osName = "Windows 11")
        val cmd = manager.buildAddRuleNetshCommand(
            ruleName = "Lirix Connect",
            programPath = "C:\\Program Files\\Lirix\\Lirix.exe"
        )

        assertTrue(cmd.startsWith("netsh advfirewall firewall add rule"))
        assertTrue(cmd.contains("name=\"Lirix Connect\""))
        assertTrue(cmd.contains("protocol=UDP"))
        assertTrue(cmd.contains("dir=in"))
        assertTrue(cmd.contains("action=allow"))
        assertTrue(cmd.contains("profile=private,domain"))
        assertTrue(cmd.contains("program=\"C:\\Program Files\\Lirix\\Lirix.exe\""))
    }

    @Test
    fun testBuildElevatedPowerShellCommand() {
        val manager = WindowsFirewallManager(osName = "Windows 11")
        val cmd = manager.buildElevatedPowerShellCommand(
            ruleName = "Lirix Connect",
            programPath = "C:\\Program Files\\Lirix\\Lirix.exe"
        )

        assertTrue(cmd.startsWith("powershell -Command"))
        assertTrue(cmd.contains("Start-Process netsh"))
        assertTrue(cmd.contains("-Verb RunAs"))
    }

    @Test
    fun testAddFirewallRule_Success() = runBlocking {
        var callCount = 0
        val mock = MockCommandExecutor { command ->
            callCount++
            if (command.contains("show")) {
                CommandResult(0, "Enabled: Yes\nAction: Allow\n", "")
            } else {
                CommandResult(0, "Ok.\n", "")
            }
        }

        val manager = WindowsFirewallManager(commandExecutor = mock, osName = "Windows 11")
        val success = manager.addFirewallRule("Lirix Connect", "C:\\Lirix.exe", elevated = true)

        assertTrue(success)
        assertTrue(mock.executedCommands.isNotEmpty())
    }
}
