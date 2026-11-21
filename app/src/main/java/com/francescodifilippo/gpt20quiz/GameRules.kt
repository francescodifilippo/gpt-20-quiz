package com.francescodifilippo.gpt20quiz

/** The local engine owns the turn budget and the scoring policy. */
enum class PlayerRole { GUESSER, RESPONDER }
enum class GamePhase { HOME, PLAYING, RESULT }

object GameRules {
    const val MAX_TURNS = 20

    fun consumeTurn(remaining: Int): Int {
        require(remaining in 1..MAX_TURNS) { "No turns remaining" }
        return remaining - 1
    }

    /**
     * Both roles have separate scoreboards.
     * Guesser: wins sooner => more points.
     * Responder: resists a correct AI guess through 20 turns => more points.
     * Scores are awarded only for a win.
     */
    fun award(role: PlayerRole, won: Boolean, remainingAfterFinalTurn: Int): Int {
        require(remainingAfterFinalTurn in 0..MAX_TURNS)
        if (!won) return 0
        return when (role) {
            PlayerRole.GUESSER -> 100 + remainingAfterFinalTurn * 10
            PlayerRole.RESPONDER -> 100 + (MAX_TURNS - remainingAfterFinalTurn) * 10
        }
    }
}
