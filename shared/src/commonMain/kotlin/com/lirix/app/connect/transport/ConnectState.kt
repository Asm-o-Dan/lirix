package com.lirix.app.connect.transport

/**
 * Состояния жизненного цикла транспортного соединения Lirix Connect (SPEC-CONNECT-01 §4.1, §4.6).
 */
enum class ConnectState {
    /**
     * Соединение не установлено / разорвано штатно.
     */
    DISCONNECTED,

    /**
     * Первичное подключение к удаленному узлу (QUIC handshake, ALPN lirix/connect/1).
     */
    CONNECTING,

    /**
     * Соединение активно, bi-stream управления и канал датаграмм готовы к обмену.
     */
    CONNECTED,

    /**
     * Автоматическое переподключение после потери связи с экспоненциальным backoff.
     */
    RECONNECTING,

    /**
     * Попытки переподключения исчерпаны (лимит времени 10с / число попыток) либо критическая ошибка.
     */
    FAILED
}
