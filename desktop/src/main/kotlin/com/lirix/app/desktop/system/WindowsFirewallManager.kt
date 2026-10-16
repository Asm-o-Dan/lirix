package com.lirix.app.desktop.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Статус правила брандмауэра Windows для Lirix Connect (SPEC-CONNECT-01 §6, TASK-CON-WIN-12).
 */
enum class FirewallRuleStatus {
    /**
     * Входящий UDP-трафик разрешён в профилях Private/Domain.
     */
    ALLOWED,

    /**
     * Правило существует, но заблокировано или отключено.
     */
    BLOCKED,

    /**
     * Правило для Lirix Connect отсутствует в брандмауэре.
     */
    NOT_CONFIGURED,

    /**
     * Операционная система не является Windows (Linux/macOS/Android).
     */
    NOT_SUPPORTED,

    /**
     * Ошибка при выполнении проверки.
     */
    ERROR
}

/**
 * Результат проверки состояния брандмауэра.
 */
data class FirewallCheckResult(
    val status: FirewallRuleStatus,
    val ruleName: String,
    val message: String,
    val recommendedCommand: String? = null
)

/**
 * Абстракция выполнения консольных команд для безопасного тестирования без вызова реальной ОС.
 */
interface CommandExecutor {
    fun execute(command: List<String>): CommandResult
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

class ProcessCommandExecutor : CommandExecutor {
    override fun execute(command: List<String>): CommandResult {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            CommandResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Unknown process execution error")
        }
    }
}

/**
 * WindowsFirewallManager: Проверка и регистрация разрешающих правил Windows Defender Firewall
 * для протокола Lirix Connect (P2P QUIC Iroh и Audio Cast, UDP порт 4433 / путь к бинарнику).
 */
class WindowsFirewallManager(
    private val commandExecutor: CommandExecutor = ProcessCommandExecutor(),
    private val osName: String = System.getProperty("os.name") ?: ""
) {
    companion object {
        const val DEFAULT_RULE_NAME = "Lirix Connect"
        const val DEFAULT_UDP_PORT = 4433
    }

    fun isWindows(): Boolean = osName.lowercase().contains("win")

    /**
     * Определяет путь к текущему исполняемому файлу приложения или JVM runner.
     */
    fun getCurrentExecutablePath(): String {
        val jpackagePath = System.getProperty("jpackage.app-path")
        if (!jpackagePath.isNullOrBlank() && File(jpackagePath).exists()) {
            return jpackagePath
        }

        val processCmd = try {
            ProcessHandle.current().info().command().orElse(null)
        } catch (_: Throwable) {
            null
        }
        if (!processCmd.isNullOrBlank() && File(processCmd).exists()) {
            return processCmd
        }

        val javaHome = System.getProperty("java.home")
        if (!javaHome.isNullOrBlank()) {
            val javaw = File(javaHome, "bin/javaw.exe")
            if (javaw.exists()) {
                return javaw.absolutePath
            }
        }

        return "%ProgramFiles%\\Lirix\\Lirix.exe"
    }

    /**
     * Формирует команду netsh для добавления правила брандмауэра.
     */
    fun buildAddRuleNetshCommand(
        ruleName: String = DEFAULT_RULE_NAME,
        programPath: String = getCurrentExecutablePath(),
        port: Int = DEFAULT_UDP_PORT
    ): String {
        return "netsh advfirewall firewall add rule name=\"$ruleName\" dir=in action=allow protocol=UDP program=\"$programPath\" profile=private,domain"
    }

    /**
     * Формирует команду запуска PowerShell с повышенными привилегиями (UAC Elevation).
     */
    fun buildElevatedPowerShellCommand(
        ruleName: String = DEFAULT_RULE_NAME,
        programPath: String = getCurrentExecutablePath()
    ): String {
        val netshArgs = "advfirewall firewall add rule name=\\\"$ruleName\\\" dir=in action=allow protocol=UDP program=\\\"$programPath\\\" profile=private,domain"
        return "powershell -Command \"Start-Process netsh -ArgumentList '$netshArgs' -Verb RunAs\""
    }

    /**
     * Проверяет наличие и статус правила брандмауэра Windows.
     * Выполняется асинхронно на Dispatchers.IO.
     */
    suspend fun checkFirewallRuleExists(ruleName: String = DEFAULT_RULE_NAME): FirewallCheckResult = withContext(Dispatchers.IO) {
        if (!isWindows()) {
            return@withContext FirewallCheckResult(
                status = FirewallRuleStatus.NOT_SUPPORTED,
                ruleName = ruleName,
                message = "Брандмауэр Windows не поддерживается на ОС $osName (требуется только для Windows Deck)."
            )
        }

        val command = listOf("netsh", "advfirewall", "firewall", "show", "rule", "name=$ruleName")
        val result = commandExecutor.execute(command)

        val status = parseNetshOutput(result.stdout, result.exitCode)
        val recCmd = if (status != FirewallRuleStatus.ALLOWED) buildAddRuleNetshCommand(ruleName) else null

        val message = when (status) {
            FirewallRuleStatus.ALLOWED -> "Правило брандмауэра «$ruleName» активно. Входящие UDP соединения разрешены."
            FirewallRuleStatus.BLOCKED -> "Правило «$ruleName» отключено или блокирует входящий трафик. Lirix Connect может работать со сбоями в локальной сети."
            FirewallRuleStatus.NOT_CONFIGURED -> "Правило «$ruleName» не найдено в Windows Firewall. Рекомендуется разрешить входящий UDP-трафик для прямого P2P сопряжения."
            FirewallRuleStatus.NOT_SUPPORTED -> "Не поддерживается."
            FirewallRuleStatus.ERROR -> "Ошибка проверки брандмауэра: ${result.stderr}"
        }

        FirewallCheckResult(
            status = status,
            ruleName = ruleName,
            message = message,
            recommendedCommand = recCmd
        )
    }

    /**
     * Запрашивает создание правила через PowerShell с UAC-повышением или напрямую через netsh.
     */
    suspend fun addFirewallRule(
        ruleName: String = DEFAULT_RULE_NAME,
        programPath: String = getCurrentExecutablePath(),
        elevated: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isWindows()) return@withContext false

        val command = if (elevated) {
            val netshArgs = "advfirewall firewall add rule name=\\\"$ruleName\\\" dir=in action=allow protocol=UDP program=\\\"$programPath\\\" profile=private,domain"
            listOf("powershell", "-NoProfile", "-NonInteractive", "-Command", "Start-Process netsh -ArgumentList '$netshArgs' -Verb RunAs -Wait")
        } else {
            listOf("netsh", "advfirewall", "firewall", "add", "rule", "name=$ruleName", "dir=in", "action=allow", "protocol=UDP", "program=$programPath", "profile=private,domain")
        }

        val result = commandExecutor.execute(command)
        if (result.exitCode == 0) {
            val verify = checkFirewallRuleExists(ruleName)
            verify.status == FirewallRuleStatus.ALLOWED
        } else {
            false
        }
    }

    /**
     * Парсинг вывода netsh advfirewall firewall show rule.
     * Поддерживает английскую и русскую локали Windows.
     */
    fun parseNetshOutput(output: String, exitCode: Int): FirewallRuleStatus {
        if (exitCode != 0 ||
            output.contains("No rules match", ignoreCase = true) ||
            output.contains("не найдены", ignoreCase = true) ||
            output.contains("отвечающие указанным критериям", ignoreCase = true)
        ) {
            return FirewallRuleStatus.NOT_CONFIGURED
        }

        val lower = output.lowercase()

        val isActionBlock = lower.contains("action: block") ||
                lower.contains("action:                               block") ||
                lower.contains("действие: блокировать") ||
                lower.contains("действие:                             блокировать")

        val isEnabledNo = lower.contains("enabled: no") ||
                lower.contains("enabled:                              no") ||
                lower.contains("включено: нет") ||
                lower.contains("включено:                             нет")

        if (isActionBlock || isEnabledNo) {
            return FirewallRuleStatus.BLOCKED
        }

        val isActionAllow = lower.contains("action: allow") ||
                lower.contains("action:                               allow") ||
                lower.contains("действие: разрешить") ||
                lower.contains("действие:                             разрешить")

        val isEnabledYes = lower.contains("enabled: yes") ||
                lower.contains("enabled:                              yes") ||
                lower.contains("включено: да") ||
                lower.contains("включено:                             да")

        if (isActionAllow && isEnabledYes) {
            return FirewallRuleStatus.ALLOWED
        }

        if (isActionAllow || lower.contains("allow") || lower.contains("разрешить")) {
            return FirewallRuleStatus.ALLOWED
        }

        return FirewallRuleStatus.NOT_CONFIGURED
    }
}
