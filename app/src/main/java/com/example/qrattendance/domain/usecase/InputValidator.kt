package com.example.qrattendance.domain.usecase

import com.example.qrattendance.domain.model.UserRole

/**
 * Form validation shared by the login, registration and start-session screens.
 * Each function returns a user-facing error message, or null when the input is valid.
 * The same rules are enforced again on the server (functions/src/validation.js).
 */
object InputValidator {

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 128
    const val MIN_NAME_LENGTH = 2
    const val MAX_NAME_LENGTH = 60
    const val MAX_SUBJECT_LENGTH = 60

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val ROLL_NUMBER_REGEX = Regex("^[A-Za-z0-9/_-]{1,30}$")

    fun validateEmail(email: String): String? {
        val value = email.trim()
        return when {
            value.isEmpty() -> "Email is required"
            value.length > 254 || !EMAIL_REGEX.matches(value) -> "Enter a valid email address"
            else -> null
        }
    }

    fun validateLoginPassword(password: String): String? =
        if (password.isEmpty()) "Password is required" else null

    fun validateNewPassword(password: String): String? = when {
        password.isEmpty() -> "Password is required"
        password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters"
        password.length > MAX_PASSWORD_LENGTH -> "Password is too long"
        password.none { it.isLetter() } || password.none { it.isDigit() } ->
            "Password must contain at least one letter and one number"
        else -> null
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): String? = when {
        confirmPassword.isEmpty() -> "Please confirm your password"
        password != confirmPassword -> "Passwords do not match"
        else -> null
    }

    fun validateName(name: String): String? {
        val value = name.trim()
        return when {
            value.isEmpty() -> "Full name is required"
            value.length < MIN_NAME_LENGTH -> "Name is too short"
            value.length > MAX_NAME_LENGTH -> "Name must be at most $MAX_NAME_LENGTH characters"
            else -> null
        }
    }

    fun validateRollNumber(rollNumber: String, role: UserRole): String? {
        val value = rollNumber.trim()
        if (value.isEmpty()) {
            return if (role == UserRole.STUDENT) "Roll number is required for students" else null
        }
        return if (ROLL_NUMBER_REGEX.matches(value)) null
        else "Use letters, numbers, '-', '/' or '_' (max 30 characters)"
    }

    fun validateSubject(subject: String): String? {
        val value = subject.trim()
        return when {
            value.isEmpty() -> "Subject name is required"
            value.length < 2 -> "Subject name is too short"
            value.length > MAX_SUBJECT_LENGTH -> "Subject name must be at most $MAX_SUBJECT_LENGTH characters"
            else -> null
        }
    }

    /** Collapses repeated whitespace so "Data   Structures" and "Data Structures" are the same subject. */
    fun normalizeSubject(subject: String): String = subject.trim().replace(Regex("\\s+"), " ")
}
