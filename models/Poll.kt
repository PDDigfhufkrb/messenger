package com.hemax.models

data class Poll(
    val id: Long,
    val question: String,
    val options: List<String>,
    val totalVoterCount: Int,
    val isClosed: Boolean,
    val isAnonymous: Boolean,
    val allowsMultipleAnswers: Boolean,
    val isQuiz: Boolean,
    val correctOptionId: Int? = null,
    val explanation: String? = null
)

data class PollAnswer(
    val pollId: Long,
    val optionIds: List<Int>,
    val userId: Long
)
