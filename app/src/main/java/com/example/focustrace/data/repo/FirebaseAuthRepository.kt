package com.example.focustrace.data.repo

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    override fun isSignedIn(): Boolean {
        return auth.currentUser != null
    }

    override fun currentUserId(): String? {
        return auth.currentUser?.uid
    }

    override fun currentUserEmail(): String? {
        return auth.currentUser?.email
    }

    override suspend fun signUpWithEmail(email: String, password: String): AuthResult {
        return suspendCancellableCoroutine { continuation ->
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (continuation.isActive) {
                        if (task.isSuccessful) {
                            continuation.resume(AuthResult.Success)
                        } else {
                            val exception = task.exception
                            continuation.resume(AuthResult.Error(mapError(exception)))
                        }
                    }
                }
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return suspendCancellableCoroutine { continuation ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (continuation.isActive) {
                        if (task.isSuccessful) {
                            continuation.resume(AuthResult.Success)
                        } else {
                            val exception = task.exception
                            continuation.resume(AuthResult.Error(mapError(exception)))
                        }
                    }
                }
        }
    }

    override fun signOut() {
        auth.signOut()
    }

    private fun mapError(exception: Throwable?): String {
        return when (exception) {
            is FirebaseAuthUserCollisionException -> "This email is already in use."
            is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "Email or password is incorrect."
            is FirebaseNetworkException -> "Check your internet connection."
            else -> "Something went wrong. Please try again."
        }
    }
}
