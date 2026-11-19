package com.example.showme3d.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.showme3d.studio.examples.fossilQuestions
import com.example.showme3d.studio.examples.roverSteps
import com.pico.spatial.ui.design.Text

@Composable
internal fun ExampleChallengePanel(pageId: String, seatedParts: Int) {
    val answers = remember { mutableStateMapOf<String, Int>() }
    val step = roverSteps[pageId]
    val question = fossilQuestions[pageId]
    val summary = pageId == "dinosaur_page_7"
    if (step == null && question == null && !summary) return
    Column(
        Modifier.background(StitchColors.PresentSurface, RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (step != null) {
            val hint = if (seatedParts == step.parts.size) "本步完成，可以翻页" else "对齐后松手"
            Text("${step.label}　$seatedParts/${step.parts.size} · $hint", color = StitchColors.PresentText, fontSize = 17.sp)
        } else if (question != null) {
            val choice = answers[pageId]
            Text(question.prompt, color = StitchColors.PresentText, fontSize = 17.sp)
            if (choice != question.answer) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    question.options.forEachIndexed { index, label ->
                        StitchToyTextButton(label, StitchSymbols.ArrowForward, label, true, { answers[pageId] = index })
                    }
                }
            }
            if (choice != null) {
                Text(if (choice == question.answer) "已记录 · ${question.finding}" else question.clue,
                    color = StitchColors.PresentText, fontSize = 16.sp)
            }
        } else {
            val count = fossilQuestions.count { (id, q) -> answers[id] == q.answer }
            Text("调查手记 · 已找到 $count/${fossilQuestions.size} 条证据", color = StitchColors.PresentText, fontSize = 17.sp)
        }
    }
    Spacer(Modifier.height(6.dp))
}
