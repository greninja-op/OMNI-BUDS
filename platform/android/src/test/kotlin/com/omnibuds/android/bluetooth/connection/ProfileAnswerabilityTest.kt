package com.omnibuds.android.bluetooth.connection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The answerability ladder, which is the rule [SystemConnectedDeviceHandle.answerability] uses to combine
 * more than one channel's report about the same profile.
 *
 * The ladder exists because the enum's own declaration order ranks a refusal above a bind that has not
 * answered yet, and the two map to different support values one level up: only a refusal reaches
 * `NOT_ANSWERABLE`, which is what makes a union count as declined. A test for this is not ceremony -
 * `evidenceRank` had none, and the value it returned was the opposite of the one its own documentation
 * and ADR-P3-008 required (ADR-P3-019).
 */
class ProfileAnswerabilityTest {

    @Test
    fun aBindStillInFlightOutvotesAChannelThatWasRefused() {
        val answers = listOf(ProfileAnswerability.REFUSED, ProfileAnswerability.AWAITING_CALLBACK)

        val strongest = answers.maxByOrNull { answer -> answer.evidenceRank() }

        assertEquals(ProfileAnswerability.AWAITING_CALLBACK, strongest)
    }

    @Test
    fun theOrderingHoldsWhicheverOrderTheChannelsReportedIn() {
        val forward = listOf(ProfileAnswerability.REFUSED, ProfileAnswerability.AWAITING_CALLBACK)
        val reversed = forward.reversed()

        assertEquals(
            ProfileAnswerability.AWAITING_CALLBACK,
            forward.maxByOrNull { answer -> answer.evidenceRank() },
        )
        assertEquals(
            ProfileAnswerability.AWAITING_CALLBACK,
            reversed.maxByOrNull { answer -> answer.evidenceRank() },
        )
    }

    @Test
    fun aRefusalStillOutvotesAChannelThatNeverAsked() {
        val answers = listOf(ProfileAnswerability.NOT_REQUESTED, ProfileAnswerability.REFUSED)

        assertEquals(
            ProfileAnswerability.REFUSED,
            answers.maxByOrNull { answer -> answer.evidenceRank() },
        )
    }

    @Test
    fun aBoundServiceOutvotesEverySilence() {
        for (weaker in listOf(
            ProfileAnswerability.NOT_REQUESTED,
            ProfileAnswerability.AWAITING_CALLBACK,
            ProfileAnswerability.REFUSED,
        )) {
            assertEquals(
                ProfileAnswerability.ANSWERABLE,
                listOf(weaker, ProfileAnswerability.ANSWERABLE)
                    .maxByOrNull { answer -> answer.evidenceRank() },
            )
        }
    }

    @Test
    fun theLadderIsNotTheDeclarationOrderThatWouldRankARefusalAboveAPendingBind() {
        val rankedByDeclaration = ProfileAnswerability.entries.maxByOrNull { answer -> answer.ordinal }

        assertEquals(ProfileAnswerability.REFUSED, rankedByDeclaration)
        assertNotEquals(
            ProfileAnswerability.REFUSED,
            ProfileAnswerability.entries.maxByOrNull { answer -> answer.evidenceRank() },
        )
    }

    @Test
    fun everyCaseHasItsOwnRank() {
        val ranks = ProfileAnswerability.entries.map { answer -> answer.evidenceRank() }

        assertEquals(ranks.size, ranks.distinct().size)
    }
}
