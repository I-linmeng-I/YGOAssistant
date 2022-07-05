package Linmeng

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val active: Boolean,
    val admin: Boolean,
    val avatar: String,
    val created_at: String,
    val email: String,
    val id: Int,
    val ip_address: String? = null,
    val locale: String? = null,
    val name: String? = null,
    val password_hash: String? = null,
    val registration_ip_address: String? = null,
    val salt: String? = null,
    val updated_at: String? = null,
    val username: String
) {

    inline val memberSince: String
        get() {
            val newSequence = CharArray(created_at.length)
            created_at.forEachIndexed { index, c ->
                if (c.isLetter()) {
                    newSequence[index] = ' '
                } else {
                    newSequence[index] = c
                }
            }
            return newSequence.joinToString("")
        }
}