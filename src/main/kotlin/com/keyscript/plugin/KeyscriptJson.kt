package com.keyscript.plugin

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Shared Jackson ObjectMapper instance for the plugin.
 * Avoids creating multiple ObjectMapper instances across services.
 */
object KeyscriptJson {
    val mapper = jacksonObjectMapper()
}
