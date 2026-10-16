# REL-PCM-002 — Desktop release version and packages

Основание: пользователь просит релиз в конце сессии. Статус APPROVED после CON-PCM-003/007 и исправлений review.
Файл: desktop/build.gradle.kts. Одна область nativeDistributions.packageVersion (version metadata support card).
Изменить только packageVersion на1.4.0. Сохранить upgradeUuid и все installer settings/dependencies.
После REL-PCM-001 и green tests собрать :desktop:packageDistributionForCurrentOS (EXE/MSI) или существующие явные packageExe/packageMsi если доступны. В отчете записать точные абсолютные пути выходов, размеры и checksums. Не использовать старые1.2.1 binaries как новый релиз, не публиковать и не commit (это зона координатора).
