package com.moment.api.routes

import com.moment.api.auth.AuthConfig
import com.moment.api.auth.ClerkJwtConfig
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
        val config = ClerkJwtConfig(
            publishableKey = System.getenv("CLERK_PUBLISHABLE_KEY")
                ?: AuthConfig.PUBLISHABLE_KEY,
            secretKey = System.getenv("CLERK_SECRET_KEY")
                ?: AuthConfig.SECRET_KEY,
        )
        val jwt = config.verifyToken(body.token)
        if (jwt == null) {
            call.respond(HttpStatusCode.Unauthorized, ApiResponse(
                success = false,
                error = "Invalid token"
            ))
            return@post
        }
        val user = config.extractUser(jwt)
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
