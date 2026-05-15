package com.moment.api.routes

import com.moment.api.db.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val userId: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
)

@Serializable
data class VerifyTokenRequest(
    val token: String,
)

fun Routing.authRoutes() {
    post("/auth/verify") {
        val body = call.receive<VerifyTokenRequest>()
        val jwt = verifyClerkToken(body.token)
        if (jwt == null) {
            call.respond(HttpStatusCode.Unauthorized, ApiResponse(
                success = false,
                error = "Invalid token"
            ))
            return@post
        }
        val user = extractUserFromToken(body.token)
        if (user == null) {
            call.respond(HttpStatusCode.Unauthorized, ApiResponse(
                success = false,
                error = "Invalid token"
            ))
            return@post
        }
        call.respond(HttpStatusCode.OK, ApiResponse(
            success = true,
            data = AuthResponse(
                userId = user.id,
                email = user.email,
                displayName = user.displayName,
                avatarUrl = user.imageUrl,
            )
        ))
    }
}
