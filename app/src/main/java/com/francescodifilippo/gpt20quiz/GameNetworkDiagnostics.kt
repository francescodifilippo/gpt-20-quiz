package com.francescodifilippo.gpt20quiz

import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * User-facing messages for interrupted streaming inference.
 * Never display raw exception text; it may contain network configuration.
 */
object GameNetworkDiagnostics {
    fun isInterrupted(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.take(8).any {
            it is SocketException || it is SocketTimeoutException ||
                it is EOFException || it is UnknownHostException
        }

    fun message(error: Throwable, italian: Boolean): String? {
        if (!isInterrupted(error)) return null
        return if (italian) {
            "Connessione al modello interrotta. La risposta non è arrivata " +
                "completa: nessuna domanda è stata consumata. " +
                "Premi «Riprova la domanda» per ripetere soltanto la richiesta " +
                "del turno corrente. Con effort max può richiedere più tempo."
        } else {
            "Connection to the model was interrupted. The response was " +
                "not completed and no turn was consumed. Tap «Retry AI question» " +
                "to repeat only the current request. Max effort may take longer."
        }
    }
}
