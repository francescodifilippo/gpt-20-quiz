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
}
