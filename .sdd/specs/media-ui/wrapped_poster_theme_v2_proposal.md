# SPEC-WRAPPED-POSTER-02 — тема, данные и экспорт

Версия0.1. Статус: DRAFT, проектное предложение по запросу пользователя. Автор: Codex. Основание: .sdd/reports/WRAPPED_POSTER_THEME_AUDIT.md. Это не ready карточка кодеру; перед реализацией разложить функции/API и заморозить контракт.

## Архитектура

AnalyticsSnapshot → PosterContent → measured layout → renderer → Bitmap → cache/ShareSheet.

ThemeTokens → PosterThemeSnapshot → все стадии layout/render.

- AnalyticsSnapshot неизменяем: periodKind, fromInclusive, toExclusive/referenceTimestampMs, zoneId, stats, coverageProvenance, isEmpty. Один экземпляр используется и preview, и export.
- PosterContent типизирован по композиции SUMMARY/TRACKS/NOW_PLAYING. Сохраняет trackKey для каждой строки и обложки; bitmap mapping по ключу. Renderer не читает БД, файловую систему, Compose locals или системные часы.
- PosterThemeSnapshot — сериализуемый набор ARGB/background/surface/textPrimary/textSecondary/textMuted/border/accent/onAccent + themeId + resolved typography handles на Android-границе. Снимать полные tokens из актуальной темы, не bool isDark. Он не зависит от случайного трека при экспорте итогов. NowPlaying может иметь один явно переданный clamped cover accent.
- ArtworkLoader читает content:// и file:// через соответствующий источник, один раз, с bounded decode под реальные размеры. Отсутствующая/битая обложка дает типографический fallback именно того же trackKey.
- PosterTypographyResolver использует те же локальные font assets для Compose и Canvas. Реальные файлы Literata/Golos включить в Android resources, сначала проверить лицензию исходных assets. До этого использовать честно названный generic fallback, а не обещать Literata.
- Renderer не имеет object-global COLOR_* fallback и random(). Каждая helper получает explicit theme/paint. Background/cover treatment не меняют семантику статистики.

## Внешний вид

- Форматы:1080×1920 и1080×1080. Сетка12колонок, базовый шаг8px, горизонтальные поля72px. Для Stories выбран безопасный контентный диапазон y180..1740; это правило нашего макета, не универсальная гарантия всех соцсетей. Для square поля72px по всем сторонам.
- Literata: ведущая фраза/цитата; GolosText: подписи/списки/числа. Не более3 начертаний. Sentence case, без эмодзи и служебного английского слогана. Бренд «Lirix», точный диапазон дат вместо жесткого2026.
- Summary: одна hero метрика, максимум3 факта и3 трека. Полный набор аналитики остается на экране, не уменьшается до нечитаемого текста.
- Tracks: максимум5 строк в Stories,3 в Square; rank/title/artist/count/cover по одному trackKey. Длинный title до2строк, высота измеряется; сокращение по заранее утвержденной схеме, без наложения footer.
- NowPlaying: обложка/нейтральная виниловая рамка, title до2строк, artist до2строк, опциональная цитата до4строк. Источник отображать человекочитаемым именем или убрать, не package suffix.
- Graphite: точные tokens текущего GraphiteChalk; Paper: точные tokens PaperInk. Spotify/Yandex/Obsidian/Pastel тоже сохраняют свои текущие токены, а не преобразуются в Graphite/Paper. Стили макета едины.
- Контраст основного/вторичного текста >=4.5:1 на фактическом фоне; декоративные штрихи могут быть слабее. Фото размещать в своей области, под текстом — сплошной фон. Цветом не кодировать единственную различимую характеристику рейтинга.

## Экспорт и состояния

Показать bottom sheet с preview, выбором композиции и формата, темой «Как в приложении». Контролы min48dp. При generation показать прогресс, запретить дублирующий запуск; errors явные с retry. ДекодированиеIO, renderDefault, launch shareMain. Не блокироватьMain bitmap decode/render/PNG compression. Отказ ShareManager не закрывает composer как успех.

Снимок темы и данных делается до generation. Смена темы/периода во время рендера не смешивает два состояния; результат относится к зафиксированному request. Preview и PNG строятся одним layout, без отдельной имитации preview.

## Честность данных первого этапа

- lyricsCoverageRatio подписывать «Треки с сохранённым текстом», учитывать только nonblank текст; не называть просмотром/пением.
- Существующий timeOfDayDistribution — доля запусков, так и подписывать до отдельного изменения движка.
- Пустые данные не создают архетип/вывод о равномерности; 0времени не становится «только начали путешествие» для любого периода без данных.
- WEEK/MONTH в текущей rolling-семантике подписывать «7 дней»/«30 дней». Диапазон дат брать из snapshot, не сочинять по enum label.
- Архетип — опциональная игровая интерпретация с явным условием, не главный обязательный headline и не фактический жанр.
- Изменение расчета активной сессии, overlap/pauses, календарных периодов и настоящего lyricsViewed event — отдельный analytics контракт и регрессионные tests; не мигрировать историю предположениями.

## Acceptance перед freeze/реализацией

- Визуальная матрица всех6тем×2формата для трехкомпозиций; отдельно empty, noart, длинная кириллица/латиница, большие счетчики, цитата4строки.
- Автотест покрывает key-artwork mapping при пропуске обложки1-го трека; dark/light current theme; отсутствие старых global footer colors; неизменность frozen snapshot; ошибки decode/render/share.
- Layout сообщает bounds для всех текстовых/графических элементов; проверять отсутствие пересечений и выходов в footer/safe fields. Размер bitmap сам по себе не acceptance.
- Реальные PNG приложить к отчёту и посмотреть визуально. Проверить экран и PNG на Android, в том числе Paper/Graphite, fontscale1.0/1.3 экрана.
- No new cloud, accounts, analytics SDK; данные и изображения локальны. Новых runtime dependencies без отдельного решения не добавлять.

## Следующие артефакты

До кодинга подготовить точные сигнатуры snapshot/theme/loader/layout/renderer/composer, отдельные карточки1функция/1файл, QA tests до реализации. Данная версия фиксирует направление и критерии; API еще не заморожены.
