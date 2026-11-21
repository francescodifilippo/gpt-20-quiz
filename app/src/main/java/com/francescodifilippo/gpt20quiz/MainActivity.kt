package com.francescodifilippo.gpt20quiz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val bg = Color(0xFF080B19)
private val white = Color(0xFFF7F8FF)
private val muted = Color(0xFFA4ACC8)
private val violet = Color(0xFF9D85FF)
private val cyan = Color(0xFF5EE5DA)
private val line = Color(0xFF4C4C70)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RealQuizApp() }
    }
}

@Composable
private fun QuizApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("demo_score", 0) }
    val deviceLanguageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    // The UI language (IT/EN) and the selected game language are independent.
    var language by rememberSaveable {
        mutableStateOf(if (deviceLanguageTag.substringBefore("-") == "it") "it" else "en")
    }
    val it = language == "it"
    // "auto" is the DEFAULT, regardless of which language happens to be first in the list.
    var selectedGameLanguage by rememberSaveable {
        mutableStateOf(GameLanguages.option(prefs.getString("game_language_code", GameLanguages.AUTO)).code)
    }
    var inLanguagePicker by rememberSaveable { mutableStateOf(false) }
    var activeGameLanguageCode by rememberSaveable { mutableStateOf(GameLanguages.AUTO) }
    var activeGameLanguageTag by rememberSaveable {
        mutableStateOf(GameLanguages.resolve(GameLanguages.AUTO, deviceLanguageTag).effectiveLanguageTag)
    }
    var roleId by rememberSaveable { mutableStateOf(PlayerRole.GUESSER.name) }
    val role = PlayerRole.valueOf(roleId)
    var phaseId by rememberSaveable { mutableStateOf(GamePhase.HOME.name) }
    val phase = GamePhase.valueOf(phaseId)
    var remaining by rememberSaveable { mutableIntStateOf(20) }
    var turn by rememberSaveable { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    var reply by rememberSaveable { mutableStateOf("") }
    var lastWon by rememberSaveable { mutableStateOf(false) }
    var lastAward by rememberSaveable { mutableIntStateOf(0) }
    var guessScore by rememberSaveable { mutableIntStateOf(prefs.getInt("guesser", 0)) }
    var answerScore by rememberSaveable { mutableIntStateOf(prefs.getInt("responder", 0)) }
    var guessWins by rememberSaveable { mutableIntStateOf(prefs.getInt("guesserWins", 0)) }
    var answerWins by rememberSaveable { mutableIntStateOf(prefs.getInt("responderWins", 0)) }

    fun begin(chosen: PlayerRole) {
        roleId = chosen.name
        // Freeze the effective game language at the beginning of the match.
        val selection = GameLanguages.resolve(selectedGameLanguage, deviceLanguageTag)
        activeGameLanguageCode = selection.preferenceCode
        activeGameLanguageTag = selection.effectiveLanguageTag
        inLanguagePicker = false
        phaseId = GamePhase.PLAYING.name
        remaining = 20
        turn = 0
        input = ""
        reply = ""
        lastAward = 0
    }
    fun finish(won: Boolean) {
        if (phaseId != GamePhase.PLAYING.name) return
        remaining = GameRules.consumeTurn(remaining)
        lastAward = GameRules.award(role, won, remaining)
        lastWon = won
        if (won) {
            if (role == PlayerRole.GUESSER) {
                guessScore += lastAward
                guessWins++
                prefs.edit().putInt("guesser", guessScore).putInt("guesserWins", guessWins).apply()
            } else {
                answerScore += lastAward
                answerWins++
                prefs.edit().putInt("responder", answerScore).putInt("responderWins", answerWins).apply()
            }
        }
        phaseId = GamePhase.RESULT.name
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = violet, secondary = cyan, background = bg, surface = bg)) {
        Box(Modifier.fillMaxSize().background(bg)) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(brush = Brush.radialGradient(
                    listOf(violet.copy(alpha = .19f), Color.Transparent),
                    center = Offset(size.width, 10f), radius = size.width),
                    center = Offset(size.width, 10f), radius = size.width)
                drawCircle(brush = Brush.radialGradient(
                    listOf(cyan.copy(alpha = .10f), Color.Transparent),
                    center = Offset(0f, size.height * .7f), radius = size.width),
                    center = Offset(0f, size.height * .7f), radius = size.width)
            }
            Column(
                Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Spacer(Modifier.height(3.dp))
                AppHeader(it, onLanguage = { language = if (it) "en" else "it" },
                    onHome = {
                        phaseId = GamePhase.HOME.name
                        inLanguagePicker = false
                    })
                when (phase) {
                    GamePhase.HOME -> {
                        if (inLanguagePicker) {
                            LanguagePicker(
                                italianUi = it,
                                selectedCode = selectedGameLanguage,
                                deviceLanguageTag = deviceLanguageTag,
                                onSelect = { code ->
                                    selectedGameLanguage = GameLanguages.option(code).code
                                    prefs.edit().putString("game_language_code", selectedGameLanguage).apply()
                                },
                                onBack = { inLanguagePicker = false }
                            )
                        } else {
                            Hero(it)
                            GameLanguageTile(
                                italianUi = it,
                                selectionCode = selectedGameLanguage,
                                deviceTag = deviceLanguageTag,
                                onClick = { inLanguagePicker = true }
                            )
                            Text(if (it) "SCEGLI DA CHE PARTE STARE" else "CHOOSE YOUR SIDE",
                                color = muted, fontSize = 12.sp, letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Bold)
                            RoleTile(PlayerRole.GUESSER, role == PlayerRole.GUESSER, it,
                                guessScore, guessWins) { roleId = PlayerRole.GUESSER.name }
                            RoleTile(PlayerRole.RESPONDER, role == PlayerRole.RESPONDER, it,
                                answerScore, answerWins) { roleId = PlayerRole.RESPONDER.name }
                            MainButton(if (it) "INIZIA PARTITA" else "START GAME") { begin(role) }
                            DemoNotice(it)
                        }
                    }
                    GamePhase.PLAYING -> {
                        Countdown(remaining, role, it)
                        // Snapshot consumed by the future SIWC prompt adapter.
                        val state = GameSessionState(
                            role = role,
                            language = GameLanguages.resolve(activeGameLanguageCode, activeGameLanguageTag),
                            questionNumber = GameRules.MAX_TURNS - remaining + 1,
                            questionsRemaining = remaining
                        )
                        GameLanguageIndicator(state.language, it)
                        if (role == PlayerRole.RESPONDER) {
                            val final = remaining == 1
                            QuestionBox(
                                if (final) { if (it) "TENTATIVO FINALE · DEMO" else "FINAL GUESS · DEMO" }
                                else { if (it) "DOMANDA DELL'AI · DEMO" else "AI QUESTION · DEMO" },
                                if (final) { if (it) "Credo sia un gatto. Ho indovinato?" else "I think it's a cat. Am I right?" }
                                else sampleQuestion(turn, it)
                            )
                            if (final) {
                                MainButton(if (it) "SÌ, HA INDOVINATO" else "YES, AI GUESSED IT") { finish(false) }
                                PlainButton(if (it) "NO, HO VINTO IO" else "NO, I WIN") { finish(true) }
                            } else {
                                Text(if (it) "LA TUA RISPOSTA" else "YOUR ANSWER",
                                    color = muted, fontSize = 12.sp, letterSpacing = 1.2.sp)
                                val answers = if (it)
                                    listOf("Sì", "No", "Probabilmente", "Probabilmente no", "Non so")
                                else
                                    listOf("Yes", "No", "Probably", "Probably not", "I don't know")
                                answers.forEachIndexed { i, a ->
                                    AnswerTile(a, i == 0) {
                                        remaining = GameRules.consumeTurn(remaining)
                                        turn++
                                    }
                                }
                                PlainButton(if (it) "ABBANDONA" else "GIVE UP") { finish(false) }
                            }
                        } else {
                            val final = remaining == 1
                            QuestionBox(
                                if (final) { if (it) "ULTIMA POSSIBILITÀ" else "FINAL ATTEMPT" }
                                else { if (it) "TOCCA A TE" else "YOUR TURN" },
                                if (final) { if (it) "Qual è la tua ipotesi finale?" else "What is your final guess?" }
                                else { if (it) "Quale domanda vuoi porre?" else "What would you like to ask?" }
                            )
                            if (reply.isNotEmpty()) {
                                Surface(color = cyan.copy(alpha = .12f),
                                    border = BorderStroke(1.dp, cyan.copy(alpha = .5f)),
                                    shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                        Text(if (it) "RISPOSTA SIMULATA" else "SIMULATED REPLY",
                                            color = cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(6.dp))
                                        Text(reply, color = white, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = input, onValueChange = { input = it.take(220) },
                                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
                                shape = RoundedCornerShape(18.dp),
                                textStyle = TextStyle(color = white, fontSize = 18.sp),
                                placeholder = {
                                    Text(if (final) {
                                        if (it) "Scrivi la tua risposta..." else "Type your guess..."
                                    } else {
                                        if (it) "Scrivi una domanda sì/no..." else "Type a yes/no question..."
                                    }, color = muted)
                                }
                            )
                            if (final) {
                                MainButton(if (it) "HO INDOVINATO!" else "I GOT IT!",
                                    enabled = input.isNotBlank()) { finish(true) }
                                PlainButton(if (it) "NON HO INDOVINATO" else "I DIDN'T GET IT") { finish(false) }
                            } else {
                                MainButton(if (it) "INVIA DOMANDA" else "ASK QUESTION",
                                    enabled = input.isNotBlank()) {
                                    reply = if (turn % 3 == 0) {
                                        if (it) "Sì" else "Yes"
                                    } else {
                                        if (it) "No" else "No"
                                    }
                                    remaining = GameRules.consumeTurn(remaining)
                                    turn++
                                    input = ""
                                }
                                PlainButton(if (it) "HO GIÀ INDOVINATO" else "I ALREADY KNOW",
                                    enabled = input.isNotBlank()) { finish(true) }
                                PlainButton(if (it) "MI ARRENDO" else "GIVE UP") { finish(false) }
                            }
                        }
                        DemoNotice(it)
                    }
                    GamePhase.RESULT -> {
                        GameLanguageIndicator(
                            GameLanguages.resolve(activeGameLanguageCode, activeGameLanguageTag), it
                        )
                        ResultPanel(it, role, lastWon, lastAward,
                            if (role == PlayerRole.GUESSER) guessScore else answerScore)
                        MainButton(if (it) "NUOVA PARTITA" else "NEW GAME") { begin(role) }
                        PlainButton(if (it) "CAMBIA RUOLO" else "CHANGE ROLE") {
                            phaseId = GamePhase.HOME.name
                        }
                        DemoNotice(it)
                    }
                }
                Spacer(Modifier.height(25.dp))
            }
        }
    }
}

@Composable
internal fun AppHeader(it: Boolean, onLanguage: () -> Unit, onHome: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clickable(onClick = onHome), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(39.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(violet, cyan))),
                contentAlignment = Alignment.Center) {
                Text("20", color = bg, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("GPT 20 QUIZ", color = white, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(if (it) "EDIZIONE SPERIMENTALE" else "EXPERIMENTAL EDITION",
                    color = muted, letterSpacing = 1.sp, fontSize = 9.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Surface(color = cyan.copy(alpha = .13f), border = BorderStroke(1.dp, cyan.copy(alpha = .4f)),
                shape = RoundedCornerShape(50)) {
                Text("DEMO", Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    color = cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Surface(modifier = Modifier.clickable(onClick = onLanguage),
                shape = RoundedCornerShape(50), color = white.copy(alpha = .1f)) {
                Text(if (it) "IT" else "EN",
                    Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    color = white, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun Hero(it: Boolean) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF302554), Color(0xFF153046))))
        .border(1.dp, line, RoundedCornerShape(30.dp))
        .padding(25.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text(if (it) "VENTI DOMANDE.\nUNA SOLA RISPOSTA." else "TWENTY QUESTIONS.\nONE SECRET.",
            color = white, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black)
        Text(if (it) "Metti alla prova la tua intuizione.\nSfida una mente artificiale."
             else "Challenge your intuition.\nPlay against an artificial mind.",
            color = muted, fontSize = 14.sp, lineHeight = 19.sp)
        Box(Modifier.size(112.dp).align(Alignment.End).clip(CircleShape)
            .background(Brush.radialGradient(listOf(cyan, violet.copy(alpha = .7f), Color.Transparent))),
            contentAlignment = Alignment.Center) {
            Text("20?", color = white, fontSize = 38.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun RoleTile(role: PlayerRole, selected: Boolean, it: Boolean,
    points: Int, wins: Int, onClick: () -> Unit) {
    val accent = if (role == PlayerRole.GUESSER) violet else cyan
    val title = if (role == PlayerRole.GUESSER) {
        if (it) "SONO L'INDOVINO" else "I'M THE GUESSER"
    } else {
        if (it) "CUSTODISCO IL SEGRETO" else "I KEEP THE SECRET"
    }
    val subtitle = if (role == PlayerRole.GUESSER) {
        if (it) "Pongo domande e cerco la soluzione." else "I ask questions and find the answer."
    } else {
        if (it) "Rispondo; l'AI prova a indovinare." else "I answer; AI tries to guess."
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(if (selected) accent.copy(alpha = .14f) else white.copy(alpha = .05f))
        .border(if (selected) 2.dp else 1.dp, if (selected) accent else line,
            RoundedCornerShape(22.dp))
        .clickable(onClick = onClick).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(47.dp).clip(RoundedCornerShape(15.dp))
            .background(accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Text(if (role == PlayerRole.GUESSER) "?" else "!",
                color = accent, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = white, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = muted, fontSize = 11.sp, lineHeight = 15.sp)
            Text(if (it) "$points punti · $wins vittorie" else "$points points · $wins wins",
                color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(22.dp).clip(CircleShape)
            .background(if (selected) accent else Color.Transparent)
            .border(2.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Text("✓", color = bg, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun Countdown(remaining: Int, role: PlayerRole, it: Boolean) {
    val current = 21 - remaining
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (role == PlayerRole.GUESSER) {
            if (it) "INDOVINO" else "GUESSER"
        } else { if (it) "RISPONDITORE" else "RESPONDER" },
            color = cyan, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(remaining.toString().padStart(2, '0'),
                    style = TextStyle(
                        brush = Brush.horizontalGradient(listOf(violet, cyan)),
                        fontSize = 94.sp, lineHeight = 102.sp, fontWeight = FontWeight.Black
                    )
                )
                Text(if (it) "DOMANDE RIMASTE" else "QUESTIONS LEFT",
                    color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            }
            Surface(color = white.copy(alpha = .08f), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.End) {
                    Text(if (it) "TURNO" else "TURN", color = muted, fontSize = 10.sp, letterSpacing = 1.sp)
                    Text(current.toString() + " / 20", color = white,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(20) { i ->
                Box(Modifier.weight(1f).height(7.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i < 20 - remaining) violet else white.copy(alpha = .14f)))
            }
        }
    }
}

@Composable
internal fun QuestionBox(kicker: String, question: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF1C203C), Color(0xFF172A3C))))
        .border(1.dp, line, RoundedCornerShape(28.dp))
        .padding(25.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(kicker, color = cyan, letterSpacing = 1.5.sp,
            fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(question, color = white, fontSize = 26.sp,
            fontWeight = FontWeight.Bold, lineHeight = 32.sp)
    }
}

@Composable
internal fun AnswerTile(answer: String, highlight: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = if (highlight) violet.copy(alpha = .2f) else white.copy(alpha = .07f),
        border = BorderStroke(1.dp, if (highlight) violet else line)) {
        Text(answer, Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            color = white, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun ResultPanel(it: Boolean, role: PlayerRole, won: Boolean, award: Int, total: Int) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF312550), Color(0xFF163448))))
        .border(1.dp, line, RoundedCornerShape(30.dp)).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (it) "PARTITA CONCLUSA" else "GAME OVER",
            color = muted, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(if (won) {
            if (it) "HAI VINTO!" else "YOU WIN!"
        } else {
            if (it) "RITENTA!" else "TRY AGAIN!"
        }, color = if (won) cyan else white, fontWeight = FontWeight.Black, fontSize = 34.sp)
        Text("+" + award.toString(), color = if (won) Color(0xFFFFD680) else muted,
            fontSize = 71.sp, fontWeight = FontWeight.Black)
        Text(if (it) "PUNTI OTTENUTI" else "POINTS EARNED",
            color = muted, fontSize = 11.sp, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(5.dp))
        Text(if (it) "TOTALE DEL RUOLO" else "ROLE TOTAL", color = muted, fontSize = 11.sp)
        Text(total.toString(), color = white, fontSize = 29.sp, fontWeight = FontWeight.Black)
        Text(if (role == PlayerRole.GUESSER) {
            if (it) "Classifica: indovino" else "Guesser scoreboard"
        } else { if (it) "Classifica: risponditore" else "Responder scoreboard" },
            color = violet, fontSize = 12.sp)
    }
}

@Composable
internal fun MainButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(19.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = violet, contentColor = bg,
            disabledContainerColor = line, disabledContentColor = muted
        )) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun PlainButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(55.dp),
        shape = RoundedCornerShape(19.dp), border = BorderStroke(1.dp, line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = white)) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DemoNotice(it: Boolean) {
    Text(if (it)
        "MODALITÀ DEMO · Nessuna connessione SIWC attiva. Domande e risposte simulate; vittorie autodichiarate."
    else
        "DEMO MODE · No SIWC connection. Scripted questions and answers; self-reported wins.",
        modifier = Modifier.fillMaxWidth(), color = muted,
        fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
}

private fun sampleQuestion(index: Int, italianUi: Boolean): String {
    val italian = listOf(
        "È qualcosa di vivente?", "Si trova in natura?", "È più grande di una persona?",
        "Può muoversi da solo?", "Si può toccare?", "È presente in molte case?",
        "È stato creato dall'uomo?", "È molto antico?", "Può produrre un suono?",
        "Si usa tutti i giorni?", "È qualcosa che si può mangiare?",
        "Ha una forma riconoscibile?", "È associato a un paese?",
        "È più piccolo di una palla?", "Ha bisogno di energia?",
        "È legato alla tecnologia?", "Lo si trova all'aperto?",
        "È qualcosa di raro?", "È noto anche ai bambini?"
    )
    val english = listOf(
        "Is it alive?", "Does it occur in nature?", "Is it larger than a person?",
        "Can it move by itself?", "Can you touch it?", "Is it found in many homes?",
        "Was it made by people?", "Is it very old?", "Can it make a sound?",
        "Do people use it every day?", "Can people eat it?",
        "Does it have a recognizable shape?", "Is it linked to a country?",
        "Is it smaller than a ball?", "Does it need energy?",
        "Is it related to technology?", "Can it be found outdoors?",
        "Is it rare?", "Would children know it?"
    )
    return (if (italianUi) italian else english).getOrElse(index) {
        if (italianUi) "È qualcosa di conosciuto?" else "Is it widely known?"
    }
}
