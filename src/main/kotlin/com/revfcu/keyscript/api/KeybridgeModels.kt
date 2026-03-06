package com.revfcu.keyscript.api

data class LogonRequest(
    val query: LogonQuery
)

data class LogonQuery(
    val logon: LogonParams
)

data class LogonParams(
    val userName: String,
    val deviceName: String,
    val password: String
)

data class LogonResponse(
    val result: LogonResult? = null,
    val error: LogonError? = null
)

data class LogonResult(
    val logon: LogonSession
)

data class LogonSession(
    val sessionId: String
)

data class LogonError(
    val message: String,
    val code: Int? = null
)
