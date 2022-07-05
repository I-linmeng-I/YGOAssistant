package Linmeng

import kotlinx.serialization.Serializable

@Serializable
data class PlayerInfo(
    val token: String,
    val user: Player
)