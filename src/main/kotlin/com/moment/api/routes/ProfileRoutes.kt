package com.moment.api.routes

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.algorithms.Algorithm
import com.moment.api.auth.AuthConfig
import com.moment.api.db.ApiResponse
import com.moment.api.db.ClerkUserData
import com.moment.api.db.UpsertProfileRequest
import com.moment.api.db.SupabaseDb
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.security.interfaces.RSAPublicKey

val supabaseDb: SupabaseDb by lazy {
    val host = System.getenv("DB_HOST") ?: "db.npipmuxxflbfaoeglyzz.supabase.co"
    val port = System.getenv("DB_PORT") ?: "5432"
    val db = System.getenv("DB_NAME") ?: "postgres"
    val user = System.getenv("DB_USER") ?: "postgres"
    val password = System.getenv("DB_PASSWORD")
        ?: throw IllegalStateException("DB_PASSWORD not set")
    SupabaseDb(
        connectionString = "jdbc:postgresql://$host:$port/$db?user=$user&password=$password",
        serviceKey = System.getenv("SUPABASE_SERVICE_KEY") ?: ""
    )
}

suspend fun verifyClerkToken(token: String): DecodedJWT? {
    return try {
        val publishableKey = System.getenv("CLERK_PUBLISHABLE_KEY") ?: AuthConfig.PUBLISHABLE_KEY
        val key = publishableKey.removePrefix("pk_test_").removePrefix("pk_live_")
        val domain = key.split("_").drop(2).joinToString(".")
        val jwkProvider = UrlJwkProvider("https://$domain/.well-known/jwks.json")
        val jwt = JWT.decode(token)
        val jwk = jwkProvider.get(jwt.keyId)
        val algo = Algorithm.RSA256(jwk.publicKey as RSAPublicKey)
        JWT.require(algo)
            .withIssuer("https://$domain/")
            .withAudience(publishableKey)
            .build()
            .verify(token)
    } catch (e: Exception) {
        null
    }
}

suspend fun extractUserFromToken(token: String): ClerkUserData? {
    val jwt = verifyClerkToken(token) ?: return null
    return ClerkUserData(
        id = jwt.subject ?: return null,
        email = jwt.getClaim("email").asString() ?: "",
        displayName = jwt.getClaim("name").asString(),
        imageUrl = jwt.getClaim("picture").asString(),
        username = jwt.getClaim("username").asString(),
    )
}

fun Routing.profileRoutes() {
    get("/api/profile") {
        val authHeader = call.request.header(HttpHeaders.Authorization)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(
                success = false, error = "Missing Authorization header"
            ))

        val token = authHeader.removePrefix("Bearer ").trim()
        val user = extractUserFromToken(token)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(
                success = false, error = "Invalid token"
            ))

        val profile = supabaseDb.getProfileById(user.id)
        if (profile == null) {
            val newProfile = supabaseDb.upsertProfile(user.id, user)
            return@get call.respond(HttpStatusCode.OK, ApiResponse(
                success = true, data = newProfile
            ))
        }
        return@get call.respond(HttpStatusCode.OK, ApiResponse(
            success = true, data = profile
        ))
    }

    put("/api/profile") {
        val authHeader = call.request.header(HttpHeaders.Authorization)
            ?: return@put call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(
                success = false, error = "Missing Authorization header"
            ))

        val token = authHeader.removePrefix("Bearer ").trim()
        val user = extractUserFromToken(token)
            ?: return@put call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(
                success = false, error = "Invalid token"
            ))

        val request = call.receive<UpsertProfileRequest>()
        val profile = supabaseDb.updateProfile(user.id, request)
            ?: supabaseDb.upsertProfile(user.id, user)
        return@put call.respond(HttpStatusCode.OK, ApiResponse(
            success = true, data = profile
        ))
    }
}
