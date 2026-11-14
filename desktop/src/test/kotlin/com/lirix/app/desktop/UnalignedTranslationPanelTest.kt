package com.lirix.app.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import com.lirix.app.desktop.ui.theme.DesktopFonts
import com.lirix.app.desktop.ui.theme.buildDesktopThemePalette
import com.lirix.app.translations.*
import com.lirix.app.ui.i18n.InterfaceLanguage
import com.lirix.app.ui.i18n.LirixStrings
import com.lirix.app.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.junit.Assert.*
import org.junit.Test

/** Real shared TranslationPanel semantics; synthetic content, no provider/network or screenshot dependency. */
@OptIn(ExperimentalComposeUiApi::class)
class UnalignedTranslationPanelTest {
    private class MemoryDao(document: SongTranslation, private val saveHook: ((SongTranslationEntity) -> Unit)? = null, initiallySaved: Boolean = true) : SongTranslationDao {
        val row = SongTranslationEntity("track", document.language, document.sourceUrl, TranslationDocuments.encode(document), 1)
        val rows = MutableStateFlow(if (initiallySaved) listOf(row) else emptyList())
        var observeGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        var readGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        override fun observe(trackKey: String, language: String) = rows.onStart { observeGate?.await() }.map { values ->
            values.filter { it.trackKey == trackKey && it.language == language }
        }
        override suspend fun get(trackKey: String, language: String): List<SongTranslationEntity> {
            readGate?.await()
            return rows.value.filter { it.trackKey == trackKey && it.language == language }
        }
        override suspend fun save(entity: SongTranslationEntity) {
            val save = saveHook ?: error("These read-only rendering tests must not save")
            save(entity)
            rows.value = listOf(entity) + rows.value.filterNot { it.trackKey == entity.trackKey && it.language == entity.language && it.sourceUrl == entity.sourceUrl }
        }
    }

    private fun runPanel(locale: InterfaceLanguage, width: Int, trackOriginal: String, documentOriginal: String,
                         aligned: Boolean = false, compact: Boolean = false, reading: Boolean = false, loader: (suspend (String) -> SongTranslation)? = null, saveHook: ((SongTranslationEntity) -> Unit)? = null,
                         initiallySaved: Boolean = true, machine: MachineTranslationFallback? = null,
                         searchResult: TranslationSearchResult? = null, searchFails: Boolean = false, lookup: (suspend () -> TranslationSearchResult)? = null,
                         sourceText: (() -> String)? = null, startSettings: Boolean = false, warmMissingThenHuman: Boolean = false, delayedHumanRead: kotlinx.coroutines.CompletableDeferred<Unit>? = null,
                         assertions: PanelProbe.() -> Unit) {
        val previous = LirixStrings.currentLanguage
        LirixStrings.currentLanguage = locale
        val document = SongTranslation("https://example.test/page#section", "Synthetic song", "Synthetic artist", "Synthetic title", "Synthetic translator",
            listOf(TranslationBlock(documentOriginal, "Translation paragraph one"), TranslationBlock("", "Translation paragraph two")),
            language = "ru", sourceName = "Synthetic provider", aligned = aligned)
        val dao = MemoryDao(document, saveHook, initiallySaved)
        val settingsOpen = androidx.compose.runtime.mutableStateOf(startSettings)
        val automatic = if (compact) AutomaticSongTranslations(dao, { _, _, _ -> lookup?.invoke() ?: if (searchFails) throw java.io.IOException("Fixture source unavailable") else searchResult ?: error("No network search") }) else null
        if (warmMissingThenHuman) {
            kotlinx.coroutines.runBlocking { automatic!!.resolve("track", "Synthetic song", "Synthetic artist") }
            dao.rows.value = listOf(dao.row)
            dao.observeGate = delayedHumanRead
            dao.readGate = delayedHumanRead
        }
        val palette = buildDesktopThemePalette(AppThemeType.PAPER_INK)
        val scene = ImageComposeScene(width, 1200, Density(1f)) {
            CompositionLocalProvider(LocalLirixColors provides palette.tokens,
                LocalLirixTypography provides DesktopFonts.createPrompterTypography(),
                LocalUriHandler provides object : UriHandler { override fun openUri(uri: String) = error("No browser calls") }) {
                MaterialTheme(colorScheme = palette.material) {
                    Surface(color = palette.tokens.appBackground) {
                        TranslationPanel("track", "Synthetic artist — Synthetic song", sourceText?.invoke() ?: trackOriginal, dao,
                            loader ?: { error("Saved document must not require a network request") }, automatic = automatic, compact = compact,
                            title = "Synthetic song", artist = "Synthetic artist",
                            settingsExpanded = settingsOpen.value, onDismissSettings = { settingsOpen.value = false },
                            machineFallback = machine, machineAttribution = { Text("powered by Google Translate") },
                            originalLineContent = if (reading) { line -> Text("reading:$line") } else null)
                    }
                }
            }
        }
        try { PanelProbe(scene).assertions() } finally { scene.close(); LirixStrings.currentLanguage = previous }
    }

    private class PanelProbe(private val scene: ImageComposeScene) {
        private var nanos = 0L
        private fun nodes(): List<SemanticsNode> {
            val field = ImageComposeScene::class.java.getDeclaredField("scene").apply { isAccessible = true }
            val canvas = field.get(scene)
            val owner = canvas.javaClass.getDeclaredField("mainOwner").apply { isAccessible = true }.get(canvas)
            val layers = canvas.javaClass.getDeclaredField("layers").apply { isAccessible = true }.get(canvas) as List<*>
            val owners = listOf(owner) + layers.map { layer -> layer!!.javaClass.getDeclaredField("owner").apply { isAccessible = true }.get(layer) }
            fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
            return owners.flatMap { obj -> flatten((obj.javaClass.getMethod("getSemanticsOwner").invoke(obj) as SemanticsOwner).rootSemanticsNode) }
        }
        private fun text(node: SemanticsNode) = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("\n") { it.text }
        fun allTexts(): List<String> = nodes().map(::text).filter { it.isNotBlank() }
        fun settle(expected: String) {
            repeat(240) { index ->
                scene.render(nanos).close(); nanos += 16_666_667; Thread.sleep(1)
                if (index >= 30 && allTexts().any { expected in it }) return
            }
            error("Expected '$expected' in ${allTexts()}")
        }
        fun clickDescription(label: String) {
            val node = nodes().first { it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.contains(label) && it.config.contains(SemanticsActions.OnClick) }
            check(node.config[SemanticsActions.OnClick].action!!.invoke())
        }
        fun enterText(value: String) {
            val node = nodes().first { it.config.contains(SemanticsActions.SetText) }
            check(node.config[SemanticsActions.SetText].action!!.invoke(androidx.compose.ui.text.AnnotatedString(value)))
        }
        fun click(label: String) {
            val node = nodes().firstOrNull { text(it) == label && it.config.contains(SemanticsActions.OnClick) }
                ?: error("No action '$label' in ${allTexts()}")
            check(node.config[SemanticsActions.OnClick].action!!.invoke())
        }
    }

    @Test fun unalignedUsesIndependentOriginalAndTranslationInBothLocalesAndWidths() {
        for (locale in InterfaceLanguage.entries) for (width in listOf(420, 900)) {
            val together = if (locale == InterfaceLanguage.RU) "Вместе" else "Together"
            val original = if (locale == InterfaceLanguage.RU) "Оригинал" else "Original"
            runPanel(locale, width, "Track original line\nNext original line", "Provider original must not override") {
                settle("Track original line"); click(together); settle("Translation paragraph two")
                val togetherTexts = allTexts()
                assertEquals("original should be rendered once ($locale/$width)", 1, togetherTexts.count { "Track original line" in it })
                assertTrue(togetherTexts.any { "Translation paragraph one" in it })
                assertTrue(togetherTexts.any { "Translation paragraph two" in it })
                assertFalse(togetherTexts.any { "Provider original must not override" in it })
                assertFalse(togetherTexts.any { it.contains("[Нет строки оригинала]") || it.contains("[Original line missing]") })
                click("Русский"); settle("Translation paragraph one")
                assertFalse("translation-only must not show original", allTexts().any { "Track original line" in it })
                click(original); settle("Track original line")
                assertFalse("original-only must not show translation", allTexts().any { "Translation paragraph one" in it })
            }
        }
    }

    @Test fun customReadingLayerRendersOriginalInBothModes() {
        runPanel(InterfaceLanguage.RU, 900, "Track original line", "Provider original", reading = true) {
            settle("reading:Track original line")
            click("Вместе"); settle("Translation paragraph one")
            assertTrue(allTexts().any { "reading:Track original line" in it })
        }
        runPanel(InterfaceLanguage.RU, 900, "Track original line", "Aligned source original", aligned = true, reading = true) {
            settle("reading:Track original line")
            click("Вместе"); settle("Translation paragraph one")
            assertTrue(allTexts().any { "reading:Aligned source original" in it })
        }
    }

    @Test fun alignedDocumentsKeepTheirPairedOriginalInsteadOfTrackOriginal() {
        runPanel(InterfaceLanguage.RU, 900, "Independent track original", "Aligned source original", aligned = true) {
            settle("Independent track original"); click("Вместе"); settle("Translation paragraph one")
            assertTrue(allTexts().any { "Aligned source original" in it })
            assertFalse(allTexts().any { "Independent track original" in it })
        }
    }

    @Test fun compactUnalignedShowsIndependentExplanationAndUnavailableOriginal() {
        for (locale in InterfaceLanguage.entries) {
            val unavailable = if (locale == InterfaceLanguage.RU) "Оригинал пока не сохранён" else "Original lyrics not saved yet"
            val explanation = if (locale == InterfaceLanguage.RU) "Соответствие строк не подтверждено" else "Line matching is unconfirmed"
            runPanel(locale, 420, "", "", compact = true) {
                settle("Translation paragraph two")
                assertEquals(1, allTexts().count { unavailable in it })
                assertTrue(allTexts().any { explanation in it })
            }
        }
    }

    @Test fun unavailableOriginalAndProviderFallbackStayHonest() {
        for (locale in InterfaceLanguage.entries) {
            val unavailable = if (locale == InterfaceLanguage.RU) "Оригинал пока не сохранён" else "Original lyrics not saved yet"
            val together = if (locale == InterfaceLanguage.RU) "Вместе" else "Together"
            runPanel(locale, 420, "", "") {
                settle(unavailable); click(together); settle("Translation paragraph two")
                assertEquals("one explicit unavailable message", 1, allTexts().count { unavailable in it })
            }
            runPanel(locale, 900, "", "Fallback provider original") {
                settle("Fallback provider original"); click(together); settle("Translation paragraph two")
                assertEquals(1, allTexts().count { "Fallback provider original" in it })
                assertFalse(allTexts().any { unavailable in it })
            }
        }
    }
    @Test fun compactSettingsOpenFromOneSummaryAndReturnSpaceToLyrics() {
        for (locale in InterfaceLanguage.entries) for (width in listOf(420, 900)) {
            val together = if (locale == InterfaceLanguage.RU) "Вместе" else "Together"
            val translation = if (locale == InterfaceLanguage.RU) "Перевод" else "Translation"
            val settingsTitle = if (locale == InterfaceLanguage.RU) "Перевод и язык" else "Translation and language"
            val done = if (locale == InterfaceLanguage.RU) "Готово" else "Done"
            runPanel(locale, width, "Track original line", "Provider original", aligned = true, compact = true) {
                val summary = "Русский · $together ▾"
                settle(summary)
                assertEquals(1, allTexts().count { it == summary })
                assertFalse(allTexts().any { "Synthetic provider" in it || "Synthetic translator" in it })
                click(summary); settle(settingsTitle)
                assertTrue(allTexts().any { "Synthetic provider" in it && "Synthetic translator" in it })
                click(translation); click(done)
                settle("Русский · $translation ▾")
                assertTrue(allTexts().any { "Translation paragraph one" in it })
                assertFalse(allTexts().any { "Track original line" in it || it == settingsTitle })
            }
        }
    }

    @Test fun compactImportFailureRetriesTheActualLoadAndKeepsConfirmationRequired() {
        var attempts = 0
        val imported = SongTranslation("https://example.test/imported", "Imported song", "Synthetic artist", "", "Translator",
            listOf(TranslationBlock("Imported original", "Imported translation")), language = "ru", sourceName = "Fixture", aligned = true)
        runPanel(InterfaceLanguage.RU, 420, "Saved track original", "Saved provider original", aligned = true, compact = true,
            loader = { url ->
                assertEquals("https://example.test/imported", url)
                attempts++
                if (attempts == 1) throw java.io.IOException("Fixture offline")
                imported
            }) {
            settle("Русский · Вместе ▾"); click("Русский · Вместе ▾")
            settle("По ссылке"); click("По ссылке")
            settle("Предпросмотр"); enterText("https://example.test/imported"); click("Предпросмотр")
            settle("Fixture offline"); assertEquals(1, attempts)
            click("Повторить"); settle("Предпросмотр — ещё не сохранено")
            assertEquals(2, attempts)
            assertTrue(allTexts().any { "Imported translation" in it })
            assertFalse(allTexts().any { "Fixture offline" in it || it == "Перевод сохранён" })
            assertTrue(allTexts().any { "Это перевод нужной песни и версии записи" in it })
        }
    }

    @Test fun failedSaveHasRetryAndSuccessOnlyAfterDurableSave() {
        var saveAttempts = 0
        val persisted = mutableListOf<SongTranslationEntity>()
        val imported = SongTranslation("https://example.test/imported", "Imported song", "Synthetic artist", "", "Translator",
            listOf(TranslationBlock("Imported original", "Imported translation")), language = "ru", sourceName = "Fixture", aligned = true)
        runPanel(InterfaceLanguage.RU, 420, "Saved track original", "Saved provider original", aligned = true, compact = true,
            loader = { imported }, saveHook = { row ->
                saveAttempts++
                if (saveAttempts == 1) throw java.io.IOException("Fixture disk failure")
                persisted += row
            }) {
            settle("Русский · Вместе ▾"); click("Русский · Вместе ▾")
            settle("По ссылке"); click("По ссылке")
            settle("Предпросмотр"); enterText("https://example.test/imported"); click("Предпросмотр")
            settle("Предпросмотр — ещё не сохранено")
            clickDescription("Подтвердить соответствие перевода текущему треку")
            click("Сохранить перевод офлайн"); settle("Fixture disk failure")
            assertEquals(1, saveAttempts); assertTrue(persisted.isEmpty())
            assertFalse(allTexts().any { it == "Перевод сохранён" })
            click("Повторить"); settle("Перевод сохранён")
            assertEquals(2, saveAttempts); assertEquals(1, persisted.size)
            assertEquals("track", persisted.single().trackKey)
            assertEquals("ru", persisted.single().language)
            assertEquals("https://example.test/imported", persisted.single().sourceUrl)
            assertFalse(allTexts().any { "Fixture disk failure" in it || "Предпросмотр — ещё не сохранено" in it })
        }
    }

    private class Machine(initiallyEnabled: Boolean = true, val action: suspend (MachineTranslationRequest, Boolean) -> MachineTranslationResult) : MachineTranslationFallback {
        private val preference = MutableStateFlow(initiallyEnabled)
        override val enabled = preference
        val requests = mutableListOf<Pair<MachineTranslationRequest, Boolean>>()
        override fun setEnabled(value: Boolean) { preference.value = value }
        override suspend fun translate(request: MachineTranslationRequest, downloadModels: Boolean): MachineTranslationResult {
            requests += request to downloadModels
            return action(request, downloadModels)
        }
    }
    private fun machineDocument(request: MachineTranslationRequest) = SongTranslation("", request.title, request.artist, "", "",
        request.original.split('\n').map { TranslationBlock(it, if (it.isBlank()) "" else "Translated:$it") },
        language = request.targetLanguage, sourceName = "Google Translate", aligned = true)

    @Test fun machineFallbackRequiresOptInMissingAndDoesNotRunForHumanCandidatesOrFailure() {
        val candidate = TranslationCandidate("https://example.test/candidate", "Different song", "Other artist")
        val cases = listOf(
            Triple(false, TranslationSearchResult.Missing, false),
            Triple(true, TranslationSearchResult.Candidates(listOf(candidate)), false),
            Triple(true, TranslationSearchResult.Missing, true)
        )
        for ((enabled, result, fails) in cases) {
            val machine = Machine(enabled) { _, _ -> error("Machine must not be called") }
            runPanel(InterfaceLanguage.RU, 420, "Original words", "", compact = true,
                initiallySaved = false, machine = machine, searchResult = result, searchFails = fails) {
                settle(when {
                    fails -> "Fixture source unavailable"
                    result is TranslationSearchResult.Candidates -> "Есть несколько вариантов"
                    else -> "Перевод на выбранный язык не найден"
                })
                assertTrue(machine.requests.isEmpty())
                assertTrue(allTexts().any { "Original words" in it })
            }
        }
        val machine = Machine { _, _ -> error("Human version must win") }
        runPanel(InterfaceLanguage.RU, 420, "Original words", "Saved human words", compact = true, machine = machine) {
            settle("Translation paragraph one"); assertTrue(machine.requests.isEmpty())
        }
    }
    @Test fun explicitModelDownloadShowsMachineProvenanceAndPreservesStanzaBreaks() {
        val machine = Machine(false) { request, download ->
            if (!download) MachineTranslationResult.ModelRequired("en") else MachineTranslationResult.Translated(machineDocument(request))
        }
        runPanel(InterfaceLanguage.RU, 420, "Line one\n\nLine two", "", compact = true,
            initiallySaved = false, machine = machine, searchResult = TranslationSearchResult.Missing, startSettings = true) {
            settle("Машинный перевод · Google Translate")
            assertTrue(machine.requests.isEmpty())
            clickDescription("Машинный перевод · Google Translate"); click("Готово")
            settle("Нужны языковые модели")
            assertEquals(listOf(false), machine.requests.map { it.second })
            click("Скачать модели и перевести с Google Translate"); settle("Translated:Line two")
            assertEquals(listOf(false, true), machine.requests.map { it.second })
            assertTrue(allTexts().any { it == "Машинный перевод" })
            assertTrue(allTexts().any { it == "powered by Google Translate" })
            assertFalse(allTexts().any { "[Нет строки оригинала]" in it || "[Нет перевода строки]" in it })
            assertFalse(allTexts().any { "Сохранить перевод офлайн" in it })
        }
    }
    @Test fun changedOriginalCancelsOldMachineResult() {
        val original = androidx.compose.runtime.mutableStateOf("Old line")
        val old = kotlinx.coroutines.CompletableDeferred<MachineTranslationResult>()
        val machine = Machine { request, _ ->
            if (request.original == "Old line") old.await() else MachineTranslationResult.Translated(machineDocument(request))
        }
        runPanel(InterfaceLanguage.RU, 420, "", "", compact = true, initiallySaved = false,
            machine = machine, searchResult = TranslationSearchResult.Missing, sourceText = { original.value }) {
            settle("Переводим с Google Translate…")
            assertEquals("Old line", machine.requests.single().first.original)
            original.value = "New line"
            settle("Translated:New line")
            old.complete(MachineTranslationResult.Translated(machineDocument(MachineTranslationRequest("track", "Old", "Artist", "Old line", "ru"))))
            settle("Translated:New line")
            assertFalse(allTexts().any { "Old line" in it })
            assertEquals(2, machine.requests.size)
        }
    }
    @Test fun machineFailureKeepsOriginalAndRetriesOnlyOnRequest() {
        var attempts = 0
        val machine = Machine { request, _ ->
            attempts++
            if (attempts == 1) throw java.io.IOException("Fixture model failure")
            MachineTranslationResult.Translated(machineDocument(request))
        }
        runPanel(InterfaceLanguage.RU, 420, "Original words", "", compact = true, initiallySaved = false,
            machine = machine, searchResult = TranslationSearchResult.Missing) {
            settle("Машинный перевод не выполнен")
            assertEquals(1, attempts)
            assertTrue(allTexts().any { it == "Original words" })
            click("Перевести с Google Translate ещё раз"); settle("Translated:Original words")
            assertEquals(2, attempts)
        }
    }

    @Test fun rememberedMissingCannotRaceFirstReadOfExistingHumanTranslation() {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val machine = Machine { _, _ -> error("Never translate before saved documents are loaded") }
        runPanel(InterfaceLanguage.RU, 420, "Original words", "Saved human words", compact = true,
            initiallySaved = false, machine = machine, searchResult = TranslationSearchResult.Missing, warmMissingThenHuman = true, delayedHumanRead = gate) {
            settle("Original words")
            assertTrue(machine.requests.isEmpty())
            gate.complete(Unit)
            settle("Translation paragraph one")
            assertTrue(machine.requests.isEmpty())
            assertFalse(allTexts().any { it == "Машинный перевод" })
        }
    }

    @Test fun publishedTranslationCanBeRetriedAndReplacesMachineWithoutLosingOriginal() {
        val machine = Machine { request, _ -> MachineTranslationResult.Translated(machineDocument(request)) }
        var published: SongTranslation? = null
        val saved = mutableListOf<SongTranslationEntity>()
        runPanel(InterfaceLanguage.RU, 420, "Original words", "", compact = true, initiallySaved = false,
            machine = machine, lookup = { published?.let { TranslationSearchResult.Found(it) } ?: TranslationSearchResult.Missing },
            saveHook = { saved += it }) {
            settle("Translated:Original words")
            assertEquals(1, machine.requests.size)
            published = SongTranslation("https://example.test/human", "Synthetic song", "Synthetic artist", "", "Author",
                listOf(TranslationBlock("Original words", "Human translation")), sourceName = "Published provider")
            click("Русский · Вместе ▾"); settle("Искать готовый перевод снова")
            click("Искать готовый перевод снова"); click("Готово")
            settle("Human translation")
            assertFalse(allTexts().any { it == "Машинный перевод" || it == "powered by Google Translate" || it == "Translated:Original words" })
            assertTrue(allTexts().any { it == "Original words" })
            assertEquals(1, saved.size)
            assertEquals("https://example.test/human", saved.single().sourceUrl)
        }
    }

}
