package net.redngreen.easypos.core.model

/**
 * Role permission matrix (user-access-control spec). Enforcement happens at
 * ViewModel/action level via SessionManager.requirePermission; screens only
 * hide affordances — the matrix here is the single source of truth.
 */

enum class Role {
    ADMIN,
    MANAGER,
    CASHIER,
    ;

    fun permissions(): Set<Permission> = when (this) {
        // Cashier: sell, void own pre-payment lines, view own cash entries.
        CASHIER -> setOf(
            Permission.SELL,
            Permission.VOID_OWN_LINES,
            Permission.VIEW_OWN_CASH,
        )
        // Manager: cashier powers + catalog editing, stock views/edits,
        // cash in/out, reports.
        MANAGER -> CASHIER.permissions() + setOf(
            Permission.EDIT_CATALOG,
            Permission.VIEW_STOCK,
            Permission.ADJUST_STOCK,
            Permission.CASH_IN_OUT,
            Permission.VIEW_ALL_CASH,
            Permission.VIEW_REPORTS,
        )
        // Admin: everything + user management, settings, license/account.
        ADMIN -> MANAGER.permissions() + setOf(
            Permission.MANAGE_USERS,
            Permission.VIEW_ACTIVITY_LOG,
            Permission.MANAGE_SETTINGS,
            Permission.MANAGE_LICENSE,
        )
    }

    fun can(permission: Permission): Boolean = permission in permissions()

    companion object {
        fun fromName(name: String): Role? = entries.firstOrNull { it.name == name }
    }
}

enum class Permission {
    /** Complete sales on the sell screen. */
    SELL,

    /** Void own cart lines before payment. */
    VOID_OWN_LINES,

    /** See cash entries the user created. */
    VIEW_OWN_CASH,

    /** Create/edit/deactivate products and categories. */
    EDIT_CATALOG,

    /** Open stock overview, history, valuation. */
    VIEW_STOCK,

    /** Manual stock adjustments. */
    ADJUST_STOCK,

    /** Record cash in/out. */
    CASH_IN_OUT,

    /** See every cash entry. */
    VIEW_ALL_CASH,

    /** Day summaries and reports. */
    VIEW_REPORTS,

    /** User CRUD, PIN resets, deactivation. */
    MANAGE_USERS,

    /** Denial/activity log viewer. */
    VIEW_ACTIVITY_LOG,

    /** Shop profile, branding, printer, toggles. */
    MANAGE_SETTINGS,

    /** Account & license screens/actions. */
    MANAGE_LICENSE,
}
