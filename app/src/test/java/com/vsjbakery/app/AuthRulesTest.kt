package com.vsjbakery.app

import com.vsjbakery.app.data.auth.AuthErrorMessages
import com.vsjbakery.app.domain.model.AppRole
import com.vsjbakery.app.domain.model.CredentialsValidator
import com.vsjbakery.app.domain.model.RoleAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRulesTest {

    @Test
    fun databaseRolesMapToAppRoles() {
        assertEquals(AppRole.ADMIN, AppRole.fromDatabase("admin"))
        assertEquals(AppRole.REPORTS, AppRole.fromDatabase("reports"))
        assertEquals(AppRole.CUSTOMER, AppRole.fromDatabase("customer"))
    }

    @Test
    fun unknownRolesFallBackToLeastPrivilege() {
        assertEquals(AppRole.CUSTOMER, AppRole.fromDatabase(null))
        assertEquals(AppRole.CUSTOMER, AppRole.fromDatabase("ADMIN"))
        assertEquals(AppRole.CUSTOMER, AppRole.fromDatabase("superuser"))
    }

    @Test
    fun staffEntriesRequireMatchingRole() {
        assertNull(RoleAccess.entryError(AppRole.ADMIN, AppRole.ADMIN))
        assertNull(RoleAccess.entryError(AppRole.REPORTS, AppRole.REPORTS))
        assertEquals(
            "This account does not have administrator access.",
            RoleAccess.entryError(AppRole.ADMIN, AppRole.CUSTOMER),
        )
        assertNotNull(RoleAccess.entryError(AppRole.ADMIN, AppRole.REPORTS))
        assertNotNull(RoleAccess.entryError(AppRole.REPORTS, AppRole.ADMIN))
    }

    @Test
    fun customerEntryAcceptsAnyAccount() {
        AppRole.entries.forEach { assertNull(RoleAccess.entryError(AppRole.CUSTOMER, it)) }
    }

    @Test
    fun emailValidation() {
        assertNotNull(CredentialsValidator.emailError(""))
        assertNotNull(CredentialsValidator.emailError("not-an-email"))
        assertNotNull(CredentialsValidator.emailError("a@b"))
        assertNull(CredentialsValidator.emailError("owner@vsjbakery.in"))
        assertNull(CredentialsValidator.emailError("  owner@vsjbakery.in  "))
    }

    @Test
    fun passwordValidation() {
        assertNotNull(CredentialsValidator.passwordError(""))
        assertNotNull(CredentialsValidator.passwordError("1234567"))
        assertNull(CredentialsValidator.passwordError("12345678"))
    }

    @Test
    fun nameValidation() {
        assertNotNull(CredentialsValidator.nameError("   "))
        assertNotNull(CredentialsValidator.nameError("x".repeat(101)))
        assertNull(CredentialsValidator.nameError("Asha"))
    }

    @Test
    fun authErrorsBecomePlainLanguage() {
        assertEquals("Incorrect email or password.", AuthErrorMessages.forCode("invalid_credentials", "raw"))
        assertEquals("server text", AuthErrorMessages.forCode("something_new", "server text"))
        assertEquals("Sign-in failed. Please try again.", AuthErrorMessages.forCode(null, " "))
    }
}
