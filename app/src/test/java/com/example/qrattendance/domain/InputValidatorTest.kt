package com.example.qrattendance.domain

import com.example.qrattendance.domain.model.UserRole
import com.example.qrattendance.domain.usecase.InputValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InputValidatorTest {

    @Test
    fun `email validation`() {
        assertNull(InputValidator.validateEmail("student@college.edu"))
        assertNull(InputValidator.validateEmail("  first.last+tag@uni.ac.in  "))
        assertEquals("Email is required", InputValidator.validateEmail("   "))
        assertNotNull(InputValidator.validateEmail("student@"))
        assertNotNull(InputValidator.validateEmail("student.college.edu"))
        assertNotNull(InputValidator.validateEmail("a@b"))
    }

    @Test
    fun `login password only needs to be present`() {
        assertNull(InputValidator.validateLoginPassword("x"))
        assertEquals("Password is required", InputValidator.validateLoginPassword(""))
    }

    @Test
    fun `new password rules`() {
        assertNull(InputValidator.validateNewPassword("secret123"))
        assertNotNull(InputValidator.validateNewPassword("short1"))
        assertNotNull(InputValidator.validateNewPassword("lettersonly"))
        assertNotNull(InputValidator.validateNewPassword("12345678"))
        assertNotNull(InputValidator.validateNewPassword(""))
    }

    @Test
    fun `passwords must match`() {
        assertNull(InputValidator.validateConfirmPassword("secret123", "secret123"))
        assertEquals("Passwords do not match", InputValidator.validateConfirmPassword("secret123", "secret124"))
        assertEquals("Please confirm your password", InputValidator.validateConfirmPassword("secret123", ""))
    }

    @Test
    fun `roll number is required for students only`() {
        assertNotNull(InputValidator.validateRollNumber("", UserRole.STUDENT))
        assertNull(InputValidator.validateRollNumber("", UserRole.TEACHER))
        assertNull(InputValidator.validateRollNumber("21CS-045/A", UserRole.STUDENT))
        assertNotNull(InputValidator.validateRollNumber("21 CS 045", UserRole.STUDENT))
        assertNotNull(InputValidator.validateRollNumber("x".repeat(31), UserRole.TEACHER))
    }

    @Test
    fun `name and subject validation`() {
        assertNull(InputValidator.validateName("Asha Verma"))
        assertNotNull(InputValidator.validateName("A"))
        assertNotNull(InputValidator.validateName(" "))
        assertNull(InputValidator.validateSubject("Data Structures"))
        assertNotNull(InputValidator.validateSubject(""))
        assertNotNull(InputValidator.validateSubject("x".repeat(61)))
        assertEquals("Data Structures", InputValidator.normalizeSubject("  Data    Structures "))
    }
}
