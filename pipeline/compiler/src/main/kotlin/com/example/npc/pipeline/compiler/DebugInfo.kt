package com.example.npc.pipeline.compiler

data class DebugInfo(
    val stageNames: Map<String, String>,
    val variableNames: Map<Int, String>
)
