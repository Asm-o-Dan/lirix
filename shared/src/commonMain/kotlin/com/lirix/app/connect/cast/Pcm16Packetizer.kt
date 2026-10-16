package com.lirix.app.connect.cast

/**
 * Пакетная нарезка PCM16 Stereo аудиопотока для LAN Audio Cast (SPEC-CON-PCM-01 v1.0, CON-PCM-001).
 *
 * @param pcmData Сырые байты signed PCM16 little-endian, stereo interleaved (4 байта на stereo sample frame).
 * @param initialSeq 16-битный начальный порядковый номер датаграммы (0..65535).
 * @param initialTimestamp48k 32-битная начальная временная метка аудио в сэмплах 48 кГц (0..4294967295).
 * @return Список QUIC-датаграмм [CastDatagram] с размером payload не более 960 байт (кратных 4 байтам).
 *
 * @throws IllegalArgumentException если нарушены диапазоны seq/timestamp или размер pcmData не кратен 4 байтам.
 */
fun packetizePcm16Stereo(
    pcmData: ByteArray,
    initialSeq: Int,
    initialTimestamp48k: Long
): List<CastDatagram> {
    require(initialSeq in 0..65535) {
        "Invalid PCM sequence"
    }
    require(initialTimestamp48k in 0L..4294967295L) {
        "Invalid PCM timestamp"
    }

    if (pcmData.isEmpty()) {
        return emptyList()
    }

    require(pcmData.size % 4 == 0) {
        "PCM payload must contain complete stereo frames"
    }

    val maxChunkSize = 960 // 240 stereo sample frames = 5 ms при 48 кГц
    val result = mutableListOf<CastDatagram>()

    var offset = 0
    var currentSeq = initialSeq
    var currentTimestamp = initialTimestamp48k

    val moduloSeq = 65536
    val moduloTimestamp = 4294967296L

    while (offset < pcmData.size) {
        val chunkSize = minOf(maxChunkSize, pcmData.size - offset)
        val payload = pcmData.copyOfRange(offset, offset + chunkSize)

        val datagram = CastDatagram(
            seq = currentSeq,
            timestamp48k = currentTimestamp,
            flags = 0,
            opusPayload = payload
        )
        result.add(datagram)

        offset += chunkSize
        currentSeq = (currentSeq + 1) % moduloSeq
        val sampleFramesInChunk = chunkSize / 4
        currentTimestamp = (currentTimestamp + sampleFramesInChunk) % moduloTimestamp
    }

    return result
}
