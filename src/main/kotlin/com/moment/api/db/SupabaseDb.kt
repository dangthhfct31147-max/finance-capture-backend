package com.moment.api.db

import io.postgresql.ds.PGSimpleDataSource
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

@Serializable
data class Profile(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val coupleId: String? = null,
    val partnerId: String? = null,
    val isCoupleLeader: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class Couple(
    val id: String,
    val inviteCode: String,
    val leaderId: String,
    val partnerId: String? = null,
    val name: String,
    val createdAt: String,
)

@Serializable
data class UpsertProfileRequest(
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
)

class SupabaseDb(
    private val connectionString: String,
    private val serviceKey: String,
) {
    private val dataSource: DataSource by lazy {
        val ds = PGSimpleDataSource()
        ds.setUrl(connectionString)
        ds.user = "postgres"
        ds.password = serviceKey
        ds
    }

    fun getConnection(): Connection = dataSource.connection

    private val json = Json { ignoreUnknownKeys = true }

data class ClerkUserData(
    val id: String,
    val email: String,
    val displayName: String?,
    val imageUrl: String?,
    val username: String?,
)

    fun getProfileById(userId: String): Profile? {
        return getConnection().use { conn ->
            conn.prepareStatement(
                """
                SELECT id, username, display_name, avatar_url, couple_id, partner_id,
                       is_couple_leader, created_at, updated_at
                FROM public.profiles WHERE id = ?
                """
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toProfile() else null
                }
            }
        }
    }

    fun upsertProfile(userId: String, clerkUser: ClerkUserData): Profile {
        val username = clerkUser.username
            ?: clerkUser.email.substringBefore("@").lowercase()
            .replace(Regex("[^a-z0-9]"), "") + "_" + userId.take(6)

        val displayName = clerkUser.displayName ?: clerkUser.username ?: clerkUser.email.substringBefore("@")

        return getConnection().use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO public.profiles (id, username, display_name, avatar_url)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    username = EXCLUDED.username,
                    display_name = EXCLUDED.display_name,
                    avatar_url = EXCLUDED.avatar_url,
                    updated_at = NOW()
                RETURNING id, username, display_name, avatar_url, couple_id, partner_id,
                          is_couple_leader, created_at, updated_at
                """
            ).use { stmt ->
                stmt.setString(1, userId)
                stmt.setString(2, username)
                stmt.setString(3, displayName)
                stmt.setString(4, clerkUser.imageUrl)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.toProfile()
                }
            }
        }
    }

    fun updateProfile(userId: String, request: UpsertProfileRequest): Profile? {
        val updates = mutableListOf<String>()
        val params = mutableListOf<Any>()
        var paramIndex = 1

        request.displayName?.let {
            updates.add("display_name = ?")
            params.add(it)
        }
        request.username?.let {
            updates.add("username = ?")
            params.add(it)
        }
        request.avatarUrl?.let {
            updates.add("avatar_url = ?")
            params.add(it)
        }

        if (updates.isEmpty()) return getProfileById(userId)

        updates.add("updated_at = NOW()")
        params.add(userId)

        return getConnection().use { conn ->
            val sql = """
                UPDATE public.profiles SET ${updates.joinToString(", ")}
                WHERE id = ?
                RETURNING id, username, display_name, avatar_url, couple_id, partner_id,
                          is_couple_leader, created_at, updated_at
            """
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    when (param) {
                        is String -> stmt.setString(index + 1, param)
                        else -> stmt.setObject(index + 1, param)
                    }
                }
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toProfile() else null
                }
            }
        }
    }

    fun getCoupleByLeader(leaderId: String): Couple? {
        return getConnection().use { conn ->
            conn.prepareStatement(
                """
                SELECT id, invite_code, leader_id, partner_id, name, created_at
                FROM public.couples WHERE leader_id = ?
                """
            ).use { stmt ->
                stmt.setString(1, leaderId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toCouple() else null
                }
            }
        }
    }

    fun createCoupleInvite(leaderId: String, name: String = "Couple"): Couple {
        return getConnection().use { conn ->
            val coupleId = java.util.UUID.randomUUID().toString()
            val inviteCode = generateInviteCode()

            conn.prepareStatement(
                """
                INSERT INTO public.couples (id, leader_id, invite_code, name)
                VALUES (?, ?, ?, ?)
                RETURNING id, invite_code, leader_id, partner_id, name, created_at
                """
            ).use { stmt ->
                stmt.setString(1, coupleId)
                stmt.setString(2, leaderId)
                stmt.setString(3, inviteCode)
                stmt.setString(4, name)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.toCouple()
                }
            }
        }
    }

    fun joinCouple(partnerId: String, inviteCode: String): Couple? {
        return getConnection().use { conn ->
            conn.autoCommit = false
            try {
                val couple = conn.prepareStatement(
                    "SELECT * FROM public.couples WHERE invite_code = ? AND partner_id IS NULL FOR UPDATE"
                ).use { stmt ->
                    stmt.setString(1, inviteCode)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.toCouple() else null
                    }
                } ?: return null

                if (couple.leaderId == partnerId) return null

                conn.prepareStatement(
                    """
                    UPDATE public.couples SET partner_id = ? WHERE id = ?
                    RETURNING id, invite_code, leader_id, partner_id, name, created_at
                    """
                ).use { stmt ->
                    stmt.setString(1, partnerId)
                    stmt.setString(2, couple.id)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        val updatedCouple = rs.toCouple()

                        conn.prepareStatement(
                            "UPDATE public.profiles SET couple_id = ?, partner_id = ?, updated_at = NOW() WHERE id = ?"
                        ).use { upd ->
                            upd.setString(1, couple.id)
                            upd.setString(2, couple.leaderId)
                            upd.setString(3, partnerId)
                            upd.executeUpdate()
                        }

                        conn.prepareStatement(
                            "UPDATE public.profiles SET couple_id = ?, partner_id = ?, updated_at = NOW() WHERE id = ?"
                        ).use { upd ->
                            upd.setString(1, couple.id)
                            upd.setString(2, partnerId)
                            upd.setString(3, couple.leaderId)
                            upd.executeUpdate()
                        }

                        conn.commit()
                        updatedCouple
                    }
                }
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    fun leaveCouple(userId: String): Boolean {
        return getConnection().use { conn ->
            conn.autoCommit = false
            try {
                val profile = getProfileById(userId) ?: return false
                val coupleId = profile.coupleId ?: return false

                if (profile.partnerId != null) {
                    conn.prepareStatement(
                        "UPDATE public.profiles SET couple_id = NULL, partner_id = NULL, updated_at = NOW() WHERE id = ?"
                    ).use { stmt ->
                        stmt.setString(1, profile.partnerId)
                        stmt.executeUpdate()
                    }
                }

                conn.prepareStatement(
                    "UPDATE public.profiles SET couple_id = NULL, partner_id = NULL, updated_at = NOW() WHERE id = ?"
                ).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.executeUpdate()
                }

                conn.prepareStatement(
                    "DELETE FROM public.couples WHERE id = ? AND leader_id = ?"
                ).use { stmt ->
                    stmt.setString(1, coupleId)
                    stmt.setString(2, userId)
                    stmt.executeUpdate()
                }

                conn.commit()
                true
            } catch (e: Exception) {
                conn.rollback()
                false
            }
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("")
    }

    private fun ResultSet.toProfile(): Profile {
        return Profile(
            id = getString("id"),
            username = getString("username"),
            displayName = getString("display_name"),
            avatarUrl = getString("avatar_url"),
            coupleId = getString("couple_id"),
            partnerId = getString("partner_id"),
            isCoupleLeader = getBoolean("is_couple_leader"),
            createdAt = getTimestamp("created_at")?.toInstant()?.toString() ?: "",
            updatedAt = getTimestamp("updated_at")?.toInstant()?.toString() ?: "",
        )
    }

    private fun ResultSet.toCouple(): Couple {
        return Couple(
            id = getString("id"),
            inviteCode = getString("invite_code"),
            leaderId = getString("leader_id"),
            partnerId = getString("partner_id"),
            name = getString("name"),
            createdAt = getTimestamp("created_at")?.toInstant()?.toString() ?: "",
        )
    }
}
