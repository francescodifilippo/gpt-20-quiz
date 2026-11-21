package com.francescodifilippo.gpt20quiz

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val night = Color(0xFF080B19)
private val bright = Color(0xFFF7F8FF)
private val dim = Color(0xFFA4ACC8)
private val luminous = Color(0xFF9D85FF)
private val turquoise = Color(0xFF5EE5DA)
private val outline = Color(0xFF4C4C70)

@Composable
fun RealQuizApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("live_score", 0) }
    val deviceLocale = LocalConfiguration.current.locales[0].toLanguageTag()
    val auth = remember { SiwcAuthManager(context) }
    val ai = remember { LiveGameService(auth) }
    val scope = rememberCoroutineScope()
    var uiLanguage by remember { mutableStateOf(if (deviceLocale.startsWith("it")) "it" else "en") }
    val italian = uiLanguage == "it"
    var selectedGameLanguage by remember {
        mutableStateOf(GameLanguages.option(
            context.getSharedPreferences("demo_score", 0)
                .getString("game_language_code", GameLanguages.AUTO)
        ).code)
    }
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    var role by remember { mutableStateOf(PlayerRole.RESPONDER) }
    var session by remember { mutableStateOf(auth.sessionOrNull()) }
    var modelList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedModel by remember { mutableStateOf("") }
    var modelLoadState by remember { mutableStateOf("idle") } // idle/loading/ready/empty/error
    var modelLoadError by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    // Never persist an OAuth callback URL: it contains a one-use authorization code.
    var manualCallbackUrl by remember { mutableStateOf("") }
    var currentPage by remember { mutableStateOf(GamePhase.HOME) }
    var activeLanguage by remember { mutableStateOf(GameLanguages.resolve("auto", deviceLocale)) }
    var remaining by remember { mutableIntStateOf(20) }
    var currentQuestion by remember { mutableStateOf("") }
    var questionKind by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var lastAnswer by remember { mutableStateOf("") }
    var gameWon by remember { mutableStateOf(false) }
    var awarded by remember { mutableIntStateOf(0) }
    var guessScore by remember { mutableIntStateOf(prefs.getInt("guesser", 0)) }
    var answerScore by remember { mutableIntStateOf(prefs.getInt("responder", 0)) }
    var guessWins by remember { mutableIntStateOf(prefs.getInt("guesserWins", 0)) }
    var answerWins by remember { mutableIntStateOf(prefs.getInt("responderWins", 0)) }
    val history: SnapshotStateList<GameExchange> = remember { mutableStateListOf() }
    val pageScrollState = rememberScrollState()

    BackHandler(enabled = showLanguages && currentPage == GamePhase.HOME) {
        showLanguages = false
    }
    LaunchedEffect(showLanguages, currentPage) {
        // In particular, returning from the last language no longer leaves
        // the user stranded at the bottom of the long scrolling list.
        pageScrollState.scrollTo(0)
    }

    fun turnState(): GameSessionState {
        require(remaining > 0)
        return GameSessionState(
            role = role, language = activeLanguage,
            questionNumber = GameRules.MAX_TURNS - remaining + 1,
            questionsRemaining = remaining
        )
    }

    fun finish(won: Boolean) {
        if (currentPage != GamePhase.PLAYING || remaining <= 0) return
        remaining = GameRules.consumeTurn(remaining)
        gameWon = won
        awarded = GameRules.award(role, won, remaining)
        if (won) {
            if (role == PlayerRole.GUESSER) {
                guessScore += awarded
                guessWins++
                prefs.edit().putInt("guesser", guessScore)
                    .putInt("guesserWins", guessWins).apply()
            } else {
                answerScore += awarded
                answerWins++
                prefs.edit().putInt("responder", answerScore)
                    .putInt("responderWins", answerWins).apply()
            }
        }
        currentPage = GamePhase.RESULT
    }

    fun loadModels() {
        // The catalog is account-scoped; never fabricate models or use a
        // hard-coded fallback model when the server reports none.
        if (busy || modelLoadState == "loading") return
        scope.launch {
            busy = true
            modelLoadState = "loading"
            modelLoadError = ""
            try {
                val discovered = ai.availableModels()
                modelList = discovered
                if (discovered.isEmpty()) {
                    selectedModel = ""
                    modelLoadState = "empty"
                    modelLoadError = if (italian)
                        "L'account non ha restituito modelli visibili. Puoi riprovare o controllare il permesso del piano."
                    else "This account returned no visible models. Retry or check plan authorization."
                } else {
                    if (selectedModel !in discovered.map { it.first }) {
                        selectedModel = discovered.first().first
                    }
                    modelLoadState = "ready"
                }
                session = auth.sessionOrNull()
            } catch (e: Exception) {
                modelList = emptyList()
                selectedModel = ""
                modelLoadState = "error"
                modelLoadError = safeUserFacingError(e, italian)
            } finally {
                busy = false
            }
        }
    }

    // Restore an already connected account without another browser login.
    // On fresh sign-in the onSignIn callback triggers model discovery explicitly.
    LaunchedEffect(session?.clientId, session?.canUsePlan) {
        if (session?.canUsePlan == true && modelLoadState == "idle" && !busy) {
            loadModels()
        }
    }

    fun nextAiTurn() {
        scope.launch {
            busy = true
            message = ""
            try {
                val result = ai.nextAiQuestion(selectedModel, turnState(), history.toList())
                questionKind = result.kind
                currentQuestion = result.text
            } catch (e: Exception) {
                message = e.message ?: "AI request failed"
                questionKind = ""
                currentQuestion = ""
            } finally { busy = false }
        }
    }

    fun startGame() {
        if (session?.canUsePlan != true || busy || selectedModel.isBlank()) return
        activeLanguage = GameLanguages.resolve(selectedGameLanguage, deviceLocale)
        remaining = GameRules.MAX_TURNS
        history.clear()
        secret = ""
        currentQuestion = ""
        questionKind = ""
        input = ""
        lastAnswer = ""
        message = ""
        currentPage = GamePhase.PLAYING
        if (role == PlayerRole.RESPONDER) {
            nextAiTurn()
        } else {
            scope.launch {
                busy = true
                try {
                    secret = ai.selectSecret(selectedModel, turnState())
                } catch (e: Exception) {
                    message = e.message ?: "Could not start game"
                    currentPage = GamePhase.HOME
                } finally { busy = false }
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = luminous, secondary = turquoise, background = night,
        surface = night, onBackground = bright, onSurface = bright
    )) {
        Column(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF1D1A3D), night, night))
            ).systemBarsPadding().verticalScroll(pageScrollState)
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.clickable {
                    if (!busy) { currentPage = GamePhase.HOME; showLanguages = false }
                }) {
                    Text("GPT 20 QUIZ", fontWeight = FontWeight.Black, color = bright, fontSize = 21.sp)
                    Text(if (italian) "GIOCO CON CHATGPT" else "PLAY WITH CHATGPT",
                        color = turquoise, fontSize = 10.sp, letterSpacing = 1.3.sp)
                }
                OutlinedButton(
                    onClick = { uiLanguage = if (italian) "en" else "it" },
                    shape = RoundedCornerShape(20.dp)
                ) { Text(if (italian) "IT" else "EN") }
            }
            if (showLanguages && currentPage == GamePhase.HOME) {
                OutlinedButton(
                    onClick = { showLanguages = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (italian) "← TORNA ALLA PARTITA" else "← BACK TO GAME SETUP")
                }
                LanguagePicker(
                    italianUi = italian,
                    selectedCode = selectedGameLanguage,
                    deviceLanguageTag = deviceLocale,
                    onSelect = {
                        selectedGameLanguage = GameLanguages.option(it).code
                        context.getSharedPreferences("demo_score", 0).edit()
                            .putString("game_language_code", selectedGameLanguage).apply()
                        showLanguages = false
                    },
                    onBack = { showLanguages = false }
                )
            } else {
                when (currentPage) {
                    GamePhase.HOME -> {
                        Hero(italian)
                        if (message.isNotBlank()) {
                            CompactStatus(message, italian)
                        }
                        // Sign in is the only route to AI gameplay.
                        AccountCard(session, italian, busy,
                            onSignIn = {
                                if (!busy) scope.launch {
                                    busy = true
                                    message = if (italian)
                                        "Accedi nel browser. Poi torna nell'app."
                                    else "Sign in in the browser, then return to the app."
                                    try {
                                        val connected = auth.signIn()
                                        session = connected
                                        if (!connected.canUsePlan) {
                                            message = if (italian)
                                                "Accesso riuscito, ma il piano ChatGPT non è autorizzato."
                                            else "Signed in, but ChatGPT plan usage was not approved."
                                        } else {
                                            message = if (italian)
                                                "Piano ChatGPT connesso." else "ChatGPT plan connected."
                                        }
                                        modelList = emptyList()
                                        selectedModel = ""
                                        modelLoadState = "idle"
                                        modelLoadError = ""
                                    } catch (e: Exception) {
                                        message = safeUserFacingError(e, italian)
                                    } finally { busy = false }
                                    if (session?.canUsePlan == true) {
                                        // Fetch the account catalog automatically.
                                        // The app need not ask the player to press
                                        // an unexplained extra button.
                                        loadModels()
                                    }
                                }
                            },
                            onDisconnect = {
                                if (!busy) scope.launch {
                                    busy = true
                                    val revoked = try { auth.disconnect() } catch (_: Exception) { false }
                                    session = null
                                    selectedModel = ""
                                    modelList = emptyList()
                                    modelLoadState = "idle"
                                    modelLoadError = ""
                                    message = if (revoked) {
                                        if (italian) "Account scollegato." else "Account disconnected."
                                    } else {
                                        if (italian) "Scollegato localmente. Verifica l'accesso nelle impostazioni ChatGPT."
                                        else "Local sign-out completed. Check ChatGPT settings to revoke remote access."
                                    }
                                    busy = false
                                }
                            }
                        )
                        if (busy && session == null) {
                            Surface(
                                shape = RoundedCornerShape(19.dp),
                                color = Color(0xFF202842),
                                border = BorderStroke(1.dp, turquoise.copy(alpha = .55f))
                            ) {
                                Column(Modifier.fillMaxWidth().padding(17.dp),
                                    verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                    Text(
                                        if (italian) "CHROME NON RAGGIUNGE 127.0.0.1?"
                                        else "CHROME CANNOT REACH 127.0.0.1?",
                                        color = turquoise, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (italian)
                                            "Se il browser va in timeout dopo il consenso, copia l'indirizzo COMPLETO dalla barra di Chrome, torna qui e incollalo. Il codice rimane sul telefono."
                                        else
                                            "If the browser times out after consent, copy the FULL URL from Chrome's address bar, return here and paste it. The code remains on your phone.",
                                        color = bright, fontSize = 12.sp, lineHeight = 18.sp
                                    )
                                    OutlinedTextField(
                                        value = manualCallbackUrl,
                                        onValueChange = { manualCallbackUrl = it.take(8192) },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 3,
                                        label = {
                                            Text(
                                                if (italian) "URL di ritorno (privato)"
                                                else "Callback URL (private)"
                                            )
                                        },
                                        visualTransformation =
                                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                                    )
                                    MainButton(
                                        if (italian) "COMPLETA ACCESSO CON URL"
                                        else "COMPLETE SIGN-IN WITH URL",
                                        enabled = manualCallbackUrl.isNotBlank()
                                    ) {
                                        val pasted = manualCallbackUrl
                                        manualCallbackUrl = ""
                                        scope.launch {
                                            try {
                                                auth.submitManualCallback(pasted)
                                                message = if (italian)
                                                    "Callback ricevuto. Verifico l'accesso..."
                                                else "Callback received. Verifying sign-in..."
                                            } catch (e: Exception) {
                                                message = e.message ?: "Invalid callback"
                                            }
                                        }
                                    }
                                    Text(
                                        if (italian)
                                            "Per sicurezza incolla l'URL soltanto qui, mai in chat o nei log."
                                        else "For your security, paste this URL only here, never in chat or logs.",
                                        color = dim, fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        if (session?.canUsePlan == true) {
                            ModelDiscoveryPanel(
                                italian = italian,
                                loadState = modelLoadState,
                                errorMessage = modelLoadError,
                                modelCount = modelList.size,
                                busy = busy,
                                onReload = { loadModels() }
                            )
                            if (modelList.isNotEmpty()) {
                                ModelSelector(modelList, selectedModel, italian) {
                                    selectedModel = it
                                }
                            }
                        }
                        GameLanguageTile(italian, selectedGameLanguage, deviceLocale) {
                            if (!busy) showLanguages = true
                        }
                        Text(if (italian) "SCEGLI IL TUO RUOLO" else "CHOOSE YOUR ROLE",
                            color = dim, fontSize = 12.sp, letterSpacing = 1.5.sp)
                        RoleTile(PlayerRole.GUESSER, role == PlayerRole.GUESSER, italian,
                            guessScore, guessWins) { if (!busy) role = PlayerRole.GUESSER }
                        RoleTile(PlayerRole.RESPONDER, role == PlayerRole.RESPONDER, italian,
                            answerScore, answerWins) { if (!busy) role = PlayerRole.RESPONDER }
                        val ready = GameStartReadiness.evaluate(
                            isConnected = session != null,
                            hasPlanPermission = session?.canUsePlan == true,
                            loading = busy,
                            selectedModel = selectedModel
                        )
                        MainButton(
                            if (italian) "INIZIA NUOVA PARTITA" else "START NEW GAME",
                            enabled = ready == StartBlocker.READY
                        ) { startGame() }
                        if (ready != StartBlocker.READY) {
                            Text(
                                GameStartReadiness.explanation(ready, italian),
                                color = dim, fontSize = 13.sp, lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    GamePhase.PLAYING -> {
                        Countdown(remaining, role, italian)
                        GameLanguageIndicator(activeLanguage, italian)
                        Text("MODEL · " + selectedModel,
                            color = turquoise, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (role == PlayerRole.RESPONDER) {
                            if (questionKind.isBlank() && !busy) {
                                MainButton(
                                    if (italian) "RIPROVA LA DOMANDA" else "RETRY AI QUESTION"
                                ) { nextAiTurn() }
                            }
                            if (currentQuestion.isNotBlank()) {
                                QuestionBox(
                                    if (questionKind == "guess") {
                                        if (italian) "IPOTESI DELL'AI" else "AI GUESS"
                                    } else {
                                        if (italian) "DOMANDA DELL'AI" else "AI QUESTION"
                                    }, currentQuestion
                                )
                                if (questionKind == "guess") {
                                    MainButton(if (italian) "SÌ, HA INDOVINATO" else "YES, AI GOT IT",
                                        enabled = !busy) { finish(false) }
                                    PlainButton(if (italian) "NO, NON HA INDOVINATO" else "NO, AI WAS WRONG",
                                        enabled = !busy) { finish(true) }
                                } else if (!busy) {
                                    Text(if (italian) "RISPONDI" else "YOUR ANSWER",
                                        color = dim, fontSize = 12.sp, letterSpacing = 1.3.sp)
                                    val labels = if (italian)
                                        listOf("Sì","No","Probabilmente","Probabilmente no","Non so")
                                    else listOf("Yes","No","Probably","Probably not","I don't know")
                                    val canonical = listOf("yes","no","probably","probably_not","unknown")
                                    labels.forEachIndexed { i, label ->
                                        AnswerTile(label, i == 0) {
                                            if (!busy && currentQuestion.isNotBlank()) {
                                                history.add(GameExchange(currentQuestion, canonical[i]))
                                                remaining = GameRules.consumeTurn(remaining)
                                                questionKind = ""
                                                currentQuestion = ""
                                                // The last turn must be a guess, so remaining is
                                                // never zero here after an exploratory question.
                                                if (remaining > 0) nextAiTurn()
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (secret.isNotBlank()) {
                                QuestionBox(
                                    if (remaining == 1) {
                                        if (italian) "ULTIMO TENTATIVO" else "FINAL ATTEMPT"
                                    } else {
                                        if (italian) "TU SEI L'INDOVINO" else "YOU ARE THE GUESSER"
                                    },
                                    if (remaining == 1) {
                                        if (italian) "Qual è il segreto?" else "What is the secret?"
                                    } else {
                                        if (italian) "Fai una domanda oppure prova a indovinare."
                                        else "Ask a question or make your guess."
                                    }
                                )
                                if (lastAnswer.isNotBlank()) {
                                    Surface(color = turquoise.copy(alpha = .13f),
                                        shape = RoundedCornerShape(15.dp)) {
                                        Text(lastAnswer,
                                            Modifier.fillMaxWidth().padding(16.dp),
                                            color = turquoise, fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                                OutlinedTextField(
                                    value = input, onValueChange = { input = it.take(250) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = {
                                        Text(if (italian) "La tua domanda o ipotesi"
                                        else "Your question or guess")
                                    },
                                    minLines = 2, maxLines = 3,
                                    shape = RoundedCornerShape(17.dp)
                                )
                                if (remaining > 1) {
                                    MainButton(
                                        if (italian) "CHIEDI ALL'AI" else "ASK THE AI",
                                        enabled = input.isNotBlank() && !busy
                                    ) {
                                        val question = input.trim()
                                        scope.launch {
                                            busy = true; message = ""
                                            try {
                                                val code = ai.answerHumanQuestion(
                                                    selectedModel, turnState(), secret,
                                                    history.toList(), question
                                                )
                                                history.add(GameExchange(question, code))
                                                remaining = GameRules.consumeTurn(remaining)
                                                lastAnswer = when (code) {
                                                    "yes" -> if (italian) "Sì" else "Yes"
                                                    "no" -> "No"
                                                    "probably" -> if (italian) "Probabilmente" else "Probably"
                                                    "probably_not" -> if (italian) "Probabilmente no" else "Probably not"
                                                    else -> if (italian) "Non so" else "Unknown"
                                                }
                                                input = ""
                                            } catch (e: Exception) {
                                                message = e.message ?: "AI request failed"
                                            } finally { busy = false }
                                        }
                                    }
                                }
                                MainButton(
                                    if (italian) "È LA MIA RISPOSTA FINALE" else "THIS IS MY FINAL GUESS",
                                    enabled = input.isNotBlank() && !busy
                                ) {
                                    val guess = input.trim()
                                    scope.launch {
                                        busy = true; message = ""
                                        try {
                                            val correct = ai.verifyHumanGuess(
                                                selectedModel, turnState(), secret, guess
                                            )
                                            finish(correct)
                                        } catch (e: Exception) {
                                            message = e.message ?: "Could not verify guess"
                                        } finally { busy = false }
                                    }
                                }
                            }
                        }
                        PlainButton(if (italian) "ABBANDONA PARTITA" else "GIVE UP",
                            enabled = !busy) { finish(false) }
                    }
                    GamePhase.RESULT -> {
                        GameLanguageIndicator(activeLanguage, italian)
                        ResultPanel(
                            italian, role, gameWon, awarded,
                            if (role == PlayerRole.GUESSER) guessScore else answerScore
                        )
                        if (role == PlayerRole.GUESSER && secret.isNotBlank()) {
                            Text((if (italian) "IL SEGRETO ERA: " else "THE SECRET WAS: ") + secret,
                                color = turquoise, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth())
                        }
                        MainButton(if (italian) "NUOVA PARTITA" else "NEW GAME") { startGame() }
                        PlainButton(if (italian) "CAMBIA RUOLO" else "CHANGE ROLE") {
                            currentPage = GamePhase.HOME
                        }
                    }
                }
            }
            if (busy) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(23.dp), color = turquoise,
                        strokeWidth = 2.dp)
                    Text(if (italian) "Connessione a ChatGPT..." else "Connecting to ChatGPT...",
                        color = dim, fontSize = 13.sp)
                }
            }
            // Status appears near the header on Home, never below all 27
            // language choices. Gameplay errors still appear near the turn UI.
            if (message.isNotBlank() &&
                currentPage != GamePhase.HOME && !showLanguages) {
                CompactStatus(message, italian)
            }
            if (session?.canUsePlan == true) {
                Text(if (italian) "Usi il tuo piano ChatGPT · Gestisci utilizzo"
                     else "Using your ChatGPT plan · Manage usage",
                    modifier = Modifier.fillMaxWidth().clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://chatgpt.com/settings/usage")))
                    }, color = turquoise, textAlign = TextAlign.Center, fontSize = 12.sp)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun AccountCard(
    session: SiwcSession?,
    italian: Boolean,
    busy: Boolean,
    onSignIn: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color(0xFF1F2843), Color(0xFF232045))),
                RoundedCornerShape(23.dp))
            .border(1.dp, turquoise.copy(alpha = .45f), RoundedCornerShape(23.dp))
            .padding(19.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (italian) "ACCOUNT CHATGPT" else "CHATGPT ACCOUNT",
            color = turquoise, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp,
            fontSize = 11.sp)
        Text(
            when {
                session == null -> if (italian)
                    "Collega il tuo account per giocare con un modello reale."
                    else "Connect your account to play against a real model."
                session.canUsePlan -> if (italian)
                    "Connesso · " + (session.email.ifBlank { "ChatGPT" })
                    else "Connected · " + (session.email.ifBlank { "ChatGPT" })
                else -> if (italian) "Connesso. Permesso di utilizzo del piano assente."
                    else "Connected. ChatGPT plan use was not granted."
            }, color = bright, fontSize = 14.sp, lineHeight = 20.sp
        )
        if (session?.canUsePlan != true) {
            MainButton("Continue with ChatGPT", enabled = !busy, onClick = onSignIn)
        } else {
            PlainButton(if (italian) "SCOLLEGA ACCOUNT" else "DISCONNECT",
                enabled = !busy, onClick = onDisconnect)
        }
    }
}

@Composable
private fun ModelSelector(
    models: List<Pair<String, String>>,
    selected: String,
    italian: Boolean,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (italian) "MODELLO AI" else "AI MODEL",
            color = dim, fontSize = 11.sp, letterSpacing = 1.2.sp)
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            border = BorderStroke(1.dp, outline),
            shape = RoundedCornerShape(17.dp),
            color = Color(0xFF20233D)
        ) {
            val name = models.firstOrNull { it.first == selected }?.second ?: selected
            Text(name + "   ▾", Modifier.padding(17.dp), color = bright,
                fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { (slug, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = {
                    onSelect(slug)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun CompactStatus(text: String, italian: Boolean) {
    val safe = safeVisibleStatus(text, italian)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF24273E),
        border = BorderStroke(1.dp, outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            safe,
            Modifier.padding(14.dp),
            color = bright,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ModelDiscoveryPanel(
    italian: Boolean,
    loadState: String,
    errorMessage: String,
    modelCount: Int,
    busy: Boolean,
    onReload: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF202641),
        border = BorderStroke(1.dp, turquoise.copy(alpha = .45f))
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (italian) "MODELLI DISPONIBILI" else "AVAILABLE MODELS",
                color = turquoise,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            when (loadState) {
                "loading" -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        Modifier.size(21.dp), color = turquoise, strokeWidth = 2.dp
                    )
                    Text(
                        if (italian) "Sto interrogando il catalogo SIWC..."
                        else "Checking your SIWC model catalog...",
                        color = bright, fontSize = 13.sp
                    )
                }
                "ready" -> Text(
                    if (italian) "$modelCount modelli trovati. Seleziona il modello qui sotto."
                    else "$modelCount models found. Choose one below.",
                    color = bright, fontSize = 13.sp
                )
                "empty", "error" -> Text(
                    errorMessage,
                    color = Color(0xFFFFC5B9), fontSize = 13.sp, lineHeight = 18.sp
                )
                else -> Text(
                    if (italian) "Carica il catalogo disponibile per il tuo piano ChatGPT."
                    else "Load the models available for your ChatGPT plan.",
                    color = dim, fontSize = 13.sp
                )
            }
            if (loadState != "loading") {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onReload,
                    enabled = !busy,
                    border = BorderStroke(1.dp, turquoise.copy(alpha = .55f)),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text(
                        if (italian) {
                            if (loadState == "idle") "CARICA MODELLI" else "AGGIORNA MODELLI"
                        } else {
                            if (loadState == "idle") "LOAD MODELS" else "REFRESH MODELS"
                        },
                        color = turquoise
                    )
                }
            }
        }
    }
}

private fun safeUserFacingError(error: Exception, italian: Boolean): String {
    val message = error.message?.trim().orEmpty()
    val benign = message.length in 1..160 &&
        !message.contains('{') && !message.contains('}') &&
        !message.contains("Bearer", ignoreCase = true) &&
        !message.contains("instructions", ignoreCase = true) &&
        !message.contains("access_token", ignoreCase = true) &&
        !message.contains("code=", ignoreCase = true) &&
        !message.contains('\n') &&
        !message.contains("http://") && !message.contains("https://")
    return if (benign) message else {
        if (italian) "Operazione non riuscita. Riprova o verifica la connessione."
        else "Operation failed. Please retry or check your connection."
    }
}

private fun safeVisibleStatus(text: String, italian: Boolean): String {
    // Never show raw JSON, complete model prompts, OAuth codes or large
    // backend messages in the app's user-facing status area.
    return safeUserFacingError(IllegalStateException(text), italian)
}
