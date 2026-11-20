package com.example.focustrace.data.repo

sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

interface AuthRepository {
    fun isSignedIn(): Boolean
    fun currentUserId(): String?
    suspend fun signUpWithEmail(email: String, password: String): AuthResult
    suspend fun signInWithEmail(email: String, password: String): AuthResult
    fun signOut()
}
