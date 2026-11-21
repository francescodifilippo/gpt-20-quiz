package com.francescodifilippo.gpt20quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ink = Color(0xFFF7F8FF)
private val subtle = Color(0xFFA4ACC8)
private val accent = Color(0xFF5EE5DA)
private val border = Color(0xFF4C4C70)

@Composable
fun ReasoningEffortSelector(
    selected: ReasoningEffort,
    italian: Boolean,
    onSelect: (ReasoningEffort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (italian) "LIVELLO DI RAGIONAMENTO" else "REASONING EFFORT",
            color = subtle, fontSize = 11.sp, letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold
        )
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            border = BorderStroke(1.dp, accent.copy(alpha = .62f)),
            shape = RoundedCornerShape(17.dp),
            color = Color(0xFF20233D)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected.label(italian), color = ink,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Text("▾", color = accent, fontSize = 20.sp)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ReasoningEffortPolicy.userChoices.forEach { effort ->
                DropdownMenuItem(
                    text = { Text(effort.label(italian)) },
                    onClick = {
                        onSelect(effort)
                        expanded = false
                    }
                )
            }
        }
        Text(
            if (italian)
                "Indipendente dal modello. Se il modello rifiuta il livello scelto, verrà segnalato senza modificarlo."
            else
                "Independent of the model. If the model rejects this level, you will be informed without an automatic change.",
            color = subtle, fontSize = 11.sp, lineHeight = 15.sp
        )
    }
}
