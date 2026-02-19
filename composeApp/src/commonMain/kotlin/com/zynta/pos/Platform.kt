package com.zynta.pos

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform