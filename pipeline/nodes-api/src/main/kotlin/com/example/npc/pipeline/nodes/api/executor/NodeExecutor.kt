package com.example.npc.pipeline.nodes.api.executor

import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.frame.Frame

/**
 * Исполняемый контракт скомпилированного узла на горячем пути.
 * Экземпляры NodeExecutor иммутабельны, потокобезопасны и могут переиспользоваться
 * множеством параллельных рабочих потоков.
 */
fun interface NodeExecutor {
    /**
     * Выполняет обработку события над регистровым фреймом.
     *
     * @param frame Рабочий регистровый фрейм текущего потока.
     * @param buffer Предвыделенный буфер для постановки побочных эффектов.
     * @return [StepResult] с инструкцией для диспетчера рантайма.
     */
    fun execute(frame: Frame, buffer: EffectBuffer): StepResult
}
