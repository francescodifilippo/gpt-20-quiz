package com.francescodifilippo.gpt20quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GameRulesTest {
    @Test fun countdownStartsAtTwentyAndStopsAtZero() {
        var remaining = GameRules.MAX_TURNS
        repeat(GameRules.MAX_TURNS) { remaining = GameRules.consumeTurn(remaining) }
        assertEquals(0, remaining)
        assertThrows(IllegalArgumentException::class.java) { GameRules.consumeTurn(0) }
    }

    @Test fun scoringIsDifferentByRole() {
        assertEquals(290, GameRules.award(PlayerRole.GUESSER, true, 19))
        assertEquals(110, GameRules.award(PlayerRole.RESPONDER, true, 19))
        assertEquals(300, GameRules.award(PlayerRole.RESPONDER, true, 0))
    }

    @Test fun losingCannotAwardPoints() {
        for (role in PlayerRole.entries) {
            assertEquals(0, GameRules.award(role, false, 0))
        }
    }

    @Test fun outOfRangeTurnsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            GameRules.award(PlayerRole.GUESSER, true, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameRules.consumeTurn(21)
        }
    }

    @Test fun wrongEarlyGuessSpendsTurnAndContinues() {
        val result = GameRules.resolveGuess(12, correct = false)
        assertEquals(GuessOutcome.INCORRECT_CONTINUE, result.outcome)
        assertEquals(11, result.remainingAfter)
    }

    @Test fun rightEarlyGuessEndsMatchWithoutUsingAllTurns() {
        val result = GameRules.resolveGuess(12, correct = true)
        assertEquals(GuessOutcome.CORRECT, result.outcome)
        assertEquals(11, result.remainingAfter)
    }

    @Test fun wrongTwentiethGuessEndsMatchAndAwardsResponder() {
        val result = GameRules.resolveGuess(1, correct = false)
        assertEquals(GuessOutcome.INCORRECT_EXHAUSTED, result.outcome)
        assertEquals(0, result.remainingAfter)
        assertEquals(300, GameRules.award(PlayerRole.RESPONDER, true, result.remainingAfter))
    }

    @Test fun repeatedWrongGuessesCannotExceedTheTwentyTurnBudget() {
        var remaining = 20
        repeat(19) {
            val result = GameRules.resolveGuess(remaining, correct = false)
            assertEquals(GuessOutcome.INCORRECT_CONTINUE, result.outcome)
            remaining = result.remainingAfter
        }
        assertEquals(1, remaining)
        val final = GameRules.resolveGuess(remaining, correct = false)
        assertEquals(GuessOutcome.INCORRECT_EXHAUSTED, final.outcome)
        assertEquals(0, final.remainingAfter)
        assertThrows(IllegalArgumentException::class.java) {
            GameRules.resolveGuess(0, correct = false)
        }
    }

    @Test fun finalCorrectGuessWinsEvenOnTwentiethAttempt() {
        val result = GameRules.resolveGuess(1, correct = true)
        assertEquals(GuessOutcome.CORRECT, result.outcome)
        assertEquals(0, result.remainingAfter)
    }
}
