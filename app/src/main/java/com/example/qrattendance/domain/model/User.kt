package com.example.qrattendance.domain.model

/** Roles are assigned by the backend (Cloud Function `registerUser`), never by the client. */
enum class UserRole(val value: String, val displayName: String) {
    TEACHER("teacher", "Teacher"),
    STUDENT("student", "Student");

    companion object {
        fun fromValue(value: String?): UserRole? = entries.firstOrNull { it.value == value }
    }
}

data class User(
    val uid: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val rollNumber: String?,
    val createdAtMillis: Long?,
) {
    val firstName: String
        get() = name.trim().substringBefore(' ').ifBlank { name }
}

data class RegistrationRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: UserRole,
    val rollNumber: String?,
)
