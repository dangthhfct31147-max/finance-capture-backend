package com.moment.api.routes

import com.moment.api.db.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateInviteRequest(
    val name: String = "Couple",
)

@Serializable
data class JoinCoupleRequest(
    val inviteCode: String,
)

fun Routing.coupleRoutes() {
    get("/api/couple") {
        val authHeader = call.request.header(HttpHeaders.Authorization)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiResponse(
                success = false, error = "Missing Authorization header"
            ))

        val token = authHeader.removePrefix("Bearer ").trim()
        val user = extractUserFromToken(token)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiResponse(
                success = false, error = "Invalid token"
            ))

        val profile = supabaseDb.getProfileById(user.id)
        if (profile?.coupleId == null) {
            return@get call.respond(HttpStatusCode.OK, ApiResponse(
                success = true, data = null
            ))
        }

        val couple = supabaseDb.getCoupleByLeader(user.id)
        call.respond(HttpStatusCode.OK, ApiResponse(
            success = true,
            data = couple
        ))
    }

    post("/api/couple/invite") {
        val authHeader = call.request.header(HttpHeaders.Authorization)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiResponse(
                success = false, error = "Missing Authorization header"
            ))

        val token = authHeader.removePrefix("Bearer ").trim()
        val user = extractUserFromToken(token)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiResponse(
                success = false, error = "Invalid token"
            ))

        val existing = supabaseDb.getCoupleByLeader(user.id)
        if (existing != null) {
            return@post call.respond(HttpStatusCode.OK, ApiResponse(
                success = true, data = existing
            ))
        }

        val request = call.receiveNullable<CreateInviteRequest>()
        val couple = supabaseDb.createCoupleInvite(user.id, request?.name ?: "Couple")
        call.respond(HttpStatusCode.OK, ApiResponse(
            success = true,
            data = couple
        ))
    }

    post("/api/couple/join") {
        val authHeader = call.request.header(HttpHeaders.Authorization)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiResponse(
                success = false, error = "Missing Authorization header"
            ))

        val token = authHeader.removePrefix("Bearer ").trim()
        val user = extractUserFromToken(token)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ApiResponse(
                success = false, error = "Invalid token"
            ))

        val request = call.receive<JoinCoupleRequest>()
        val couple = supabaseDb.joinCouple(user.id, request.inviteCode.uppercase().trim())
        if (couple == null) {
            return@post call.respond(HttpStatusCode.BadRequest, ApiResponse(
                success = false, error = "Invalid or expired invite code"
            ))
        }
        call.respond(HttpStatusCode.OK, ApiResponse(
            success = true,
            data = couple
        ))
    }

    delete("/api/couple") {
        val authHeader = call.request.header(HttpHeaders.Authorization)
            ?: return@delete call.respond(HttpStatusCode.Unauthorized, ApiResponse(
                success = false, error = "Missing Authorization header"
            ))

        val token = authHeader.removePrefix("Bearer ").trim()
        val user = extractUserFromToken(token)
            ?: return@delete call.respond(HttpStatusCode.Unauthorized, ApiResponse(
                success = false, error = "Invalid token"
            ))

        val success = supabaseDb.leaveCouple(user.id)
        call.respond(HttpStatusCode.OK, ApiResponse(
            success = success,
            error = if (!success) "Failed to leave couple" else null
        ))
    }
}
