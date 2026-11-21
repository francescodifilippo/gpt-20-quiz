package com.francescodifilippo.gpt20quiz

/** Explains why an AI match cannot start without silently falling back to mock play. */
enum class StartBlocker {
    SIGN_IN, AUTHORIZE_PLAN, WAIT_FOR_MODELS, SELECT_MODEL, READY
}

object GameStartReadiness {
    fun evaluate(
        isConnected: Boolean,
        hasPlanPermission: Boolean,
        loading: Boolean,
        selectedModel: String
    ): StartBlocker = when {
        !isConnected -> StartBlocker.SIGN_IN
        !hasPlanPermission -> StartBlocker.AUTHORIZE_PLAN
        loading -> StartBlocker.WAIT_FOR_MODELS
        selectedModel.isBlank() -> StartBlocker.SELECT_MODEL
        else -> StartBlocker.READY
    }

    fun explanation(blocker: StartBlocker, italian: Boolean): String = when (blocker) {
        StartBlocker.SIGN_IN ->
            if (italian) "Per iniziare, accedi con ChatGPT."
            else "Sign in with ChatGPT to start."
        StartBlocker.AUTHORIZE_PLAN ->
            if (italian) "Autorizza l'utilizzo del tuo piano ChatGPT per giocare."
            else "Authorize ChatGPT plan usage to play."
        StartBlocker.WAIT_FOR_MODELS ->
            if (italian) "Attendi il completamento della richiesta in corso."
            else "Please wait for the current request to complete."
        StartBlocker.SELECT_MODEL ->
            if (italian) "Carica i modelli e selezionane uno per iniziare."
            else "Load available models and select one to start."
        StartBlocker.READY ->
            if (italian) "Pronto per giocare."
            else "Ready to play."
    }
}
