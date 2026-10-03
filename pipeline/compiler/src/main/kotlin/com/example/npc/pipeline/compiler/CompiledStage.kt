package com.example.npc.pipeline.compiler

import com.example.npc.pipeline.nodes.api.frame.Frame

interface CompiledStage {
    val stageId: String
    val stageIndex: Int
    fun execute(frame: Frame): Int
}
