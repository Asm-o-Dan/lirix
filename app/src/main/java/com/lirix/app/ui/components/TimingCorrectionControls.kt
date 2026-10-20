package com.lirix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.lirix.app.ingestion.SyncOffsetStore
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Positive offset advances text; negative delays it. Always display the persisted value. */
@Composable
fun TimingCorrectionControls(offsetMs: Long, onChange: (Long)->Unit, onDetails: ()->Unit, modifier:Modifier=Modifier) {
    val current by rememberUpdatedState(offsetMs)
    val change by rememberUpdatedState(onChange)
    val tokens=LirixThemeTokens.colors
    Row(modifier,verticalAlignment=Alignment.CenterVertically) {
        RepeatTimingButton("−", "Текст позже на 50 миллисекунд") {
            change((current-50).coerceIn(SyncOffsetStore.MIN_OFFSET_MS,SyncOffsetStore.MAX_OFFSET_MS))
        }
        TextButton(onClick=onDetails,contentPadding=PaddingValues(horizontal=4.dp)) {
            Text("${if(offsetMs>0) "+" else ""}$offsetMs мс",color=tokens.textSecondary)
        }
        RepeatTimingButton("+", "Текст раньше на 50 миллисекунд") {
            change((current+50).coerceIn(SyncOffsetStore.MIN_OFFSET_MS,SyncOffsetStore.MAX_OFFSET_MS))
        }
    }
}

@Composable
private fun RepeatTimingButton(label:String,description:String,onStep:()->Unit) {
    val step by rememberUpdatedState(onStep)
    val tokens=LirixThemeTokens.colors
    Box(Modifier.size(48.dp).background(tokens.surfaceElevated,RoundedCornerShape(8.dp))
        .semantics { role=Role.Button;contentDescription=description;onClick {step();true} }
        .pointerInput(Unit) { detectTapGestures(onPress={
            step()
            coroutineScope {
                val repeating=launch { delay(400);while(true) {step();delay(100)} }
                try { tryAwaitRelease() } finally { repeating.cancel() }
            }
        }) },contentAlignment=Alignment.Center) { Text(label,color=tokens.textPrimary) }
}
