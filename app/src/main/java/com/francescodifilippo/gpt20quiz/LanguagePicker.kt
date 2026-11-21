package com.francescodifilippo.gpt20quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ink = Color(0xFFF7F8FF)
private val soft = Color(0xFFA4ACC8)
private val purple = Color(0xFF9D85FF)
private val aqua = Color(0xFF5EE5DA)
private val stroke = Color(0xFF4C4C70)
private val dark = Color(0xFF080B19)

@Composable
fun LanguagePicker(
    italianUi: Boolean,
    selectedCode: String,
    deviceLanguageTag: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text(
            if (italianUi) "LA LINGUA\nDEL MISTERO" else "THE LANGUAGE\nOF THE SECRET",
            color = ink, fontWeight = FontWeight.Black, fontSize = 30.sp,
            lineHeight = 36.sp
        )
        Text(
            if (italianUi)
                "Scegli la lingua del gioco. Puoi cambiarla prima di ogni nuova partita."
            else
                "Choose the game language. You can change it before each new game.",
            color = soft, fontSize = 14.sp, lineHeight = 20.sp
        )
        Surface(
            color = purple.copy(alpha = .10f),
            border = BorderStroke(1.dp, purple.copy(alpha = .48f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(if (italianUi) "LINGUA SELEZIONATA" else "SELECTED LANGUAGE",
                    color = aqua, fontSize = 10.sp, letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold)
                val current = GameLanguages.option(selectedCode)
                Text(current.label(italianUi), color = ink,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(GameLanguages.resolve(current.code, deviceLanguageTag).effectiveLanguageTag,
                    color = soft, fontSize = 12.sp)
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            singleLine = true,
            placeholder = {
                Text(if (italianUi) "Cerca una lingua..." else "Search languages...", color = soft)
            },
            label = { Text(if (italianUi) "Cerca" else "Search") }
        )
        Text(if (italianUi) "LATINO · ESPERANTO · AUTOMATICA · 24 LINGUE UE"
             else "LATIN · ESPERANTO · AUTOMATIC · 24 EU LANGUAGES",
            color = soft, fontWeight = FontWeight.Bold, letterSpacing = .7.sp, fontSize = 10.sp)
        val matching = GameLanguages.options.filter { it.matches(search) }
        if (matching.isEmpty()) {
            Text(if (italianUi) "Nessun risultato" else "No results",
                color = soft, fontSize = 14.sp)
        }
        matching.forEach { option ->
            LanguageChoiceRow(
                option = option,
                italianUi = italianUi,
                selected = option.code == selectedCode,
                deviceTag = deviceLanguageTag,
                onClick = { onSelect(option.code) }
            )
        }
        Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onBack),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = .07f),
            border = BorderStroke(1.dp, stroke)) {
            Text(if (italianUi) "TORNA ALLA PARTITA" else "BACK TO GAME SETUP",
                Modifier.padding(18.dp), color = ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LanguageChoiceRow(
    option: LanguageOption,
    italianUi: Boolean,
    selected: Boolean,
    deviceTag: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(19.dp)
    val highlight = if (selected) aqua else stroke
    Row(modifier = Modifier.fillMaxWidth()
        .background(if (selected) aqua.copy(alpha = .12f) else ink.copy(alpha = .055f), shape)
        .border(if (selected) 1.5.dp else 1.dp, highlight, shape)
        .clickable(onClick = onClick)
        .padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(12.dp),
            color = if (selected) aqua.copy(alpha = .2f) else purple.copy(alpha = .15f)) {
            Text(option.code.uppercase(),
                Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                color = if (selected) aqua else purple,
                fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text(option.label(italianUi), color = ink,
                fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val detail = when {
                option.code == GameLanguages.AUTO -> {
                    val active = GameLanguages.resolve(option.code, deviceTag)
                    if (italianUi) "Rilevata: " + active.effectiveLanguageTag
                    else "Detected: " + active.effectiveLanguageTag
                }
                option.nativeName != option.label(italianUi) -> option.nativeName
                else -> option.code
            }
            Text(detail, color = soft, fontSize = 12.sp)
        }
        Surface(modifier = Modifier.size(23.dp),
            shape = CircleShape,
            color = if (selected) aqua else Color.Transparent,
            border = BorderStroke(1.5.dp, if (selected) aqua else soft)) {
            if (selected) Text("✓", color = dark, fontSize = 15.sp,
                fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun GameLanguageTile(
    italianUi: Boolean,
    selectionCode: String,
    deviceTag: String,
    onClick: () -> Unit
) {
    val option = GameLanguages.option(selectionCode)
    val active = GameLanguages.resolve(selectionCode, deviceTag)
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = purple.copy(alpha = .12f),
        border = BorderStroke(1.dp, purple.copy(alpha = .6f))) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Aa", color = aqua, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (italianUi) "LINGUA DELLA PARTITA" else "GAME LANGUAGE",
                    fontSize = 10.sp, color = soft, letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.Bold)
                Text(option.label(italianUi) + " · " + active.effectiveLanguageTag,
                    color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (italianUi) "Tocca per scegliere una delle 26 lingue"
                     else "Tap to choose one of 26 languages",
                    fontSize = 11.sp, color = soft)
            }
            Text("›", color = aqua, fontSize = 28.sp)
        }
    }
}

@Composable
fun GameLanguageIndicator(selection: GameLanguageSelection, italianUi: Boolean) {
    Surface(color = aqua.copy(alpha = .09f),
        border = BorderStroke(1.dp, aqua.copy(alpha = .45f)),
        shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(if (italianUi) "LINGUA DELLA PARTITA" else "GAME LANGUAGE",
                    color = soft, fontSize = 10.sp, letterSpacing = 1.sp)
                Text(GameLanguages.option(selection.preferenceCode).label(italianUi),
                    color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(selection.effectiveLanguageTag, color = aqua,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
