package com.moment.api.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Routing.healthRoutes() {
    get("/health") {
        call.respondText("OK")
    }
}
