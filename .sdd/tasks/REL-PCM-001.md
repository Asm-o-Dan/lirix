# REL-PCM-001 — Android release version

Основание: пользователь просит релиз в конце сессии. Статус APPROVED после CON-PCM-003/007 и исправлений review.
Файл: app/build.gradle.kts. Одна конфигурационная область android.defaultConfig (исключение к function card, это version metadata).
Изменить только versionName на1.4.0 и versionCode на17. Текущий16, локальный tagv1.3.0 имел15; номер17 монотонен. Не менять applicationId, signing, minify, deps и прочую конфигурацию.
Проверка: assembleDebug/assembleRelease по имеющимся окружению/ключам. Не извлекать passwords из файлов/логов, не менять ключ/сертификат. В отчете указать чем подписан output и пригодность к обновлению.
