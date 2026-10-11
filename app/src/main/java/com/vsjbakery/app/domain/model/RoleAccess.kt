package com.vsjbakery.app.domain.model

/**
 * Screen-level role rules. These only decide what the app SHOWS; the database enforces
 * the real permissions, so bypassing these checks would not grant any extra access.
 */
object RoleAccess {

    /**
     * Error to show when someone signs in through a staff entry (Administrator / Reports)
     * with an account that does not have that role. The customer entry accepts any account.
     */
    fun entryError(entry: AppRole, actual: AppRole): String? = when {
        entry == AppRole.CUSTOMER -> null
        entry == actual -> null
        else -> "This account does not have ${entry.shortTitle} access."
    }
}
