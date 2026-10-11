package net.redngreen.easypos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The matrix exactly as the user-access-control spec fixes it. */
class PermissionsTest {

    @Test
    fun `cashier can sell, void own lines, view own cash`() {
        val p = Role.CASHIER.permissions()
        assertEquals(setOf(Permission.SELL, Permission.VOID_OWN_LINES, Permission.VIEW_OWN_CASH), p)
    }

    @Test
    fun `manager adds catalog, stock, cash io, reports`() {
        val p = Role.MANAGER.permissions()
        assertTrue(p.containsAll(Role.CASHIER.permissions()))
        assertTrue(p.containsAll(setOf(
            Permission.EDIT_CATALOG,
            Permission.VIEW_STOCK,
            Permission.ADJUST_STOCK,
            Permission.CASH_IN_OUT,
            Permission.VIEW_ALL_CASH,
            Permission.VIEW_REPORTS,
        )))
        assertFalse(Permission.MANAGE_USERS in p)
        assertFalse(Permission.MANAGE_SETTINGS in p)
        assertFalse(Permission.MANAGE_LICENSE in p)
    }

    @Test
    fun `admin is a superset of manager plus users, settings, license`() {
        val p = Role.ADMIN.permissions()
        assertTrue(p.containsAll(Role.MANAGER.permissions()))
        assertTrue(Permission.MANAGE_USERS in p)
        assertTrue(Permission.VIEW_ACTIVITY_LOG in p)
        assertTrue(Permission.MANAGE_SETTINGS in p)
        assertTrue(Permission.MANAGE_LICENSE in p)
    }

    @Test
    fun `fromName parses known roles and rejects others`() {
        assertEquals(Role.ADMIN, Role.fromName("ADMIN"))
        assertEquals(Role.MANAGER, Role.fromName("MANAGER"))
        assertEquals(Role.CASHIER, Role.fromName("CASHIER"))
        assertNull(Role.fromName("admin"))
        assertNull(Role.fromName("SUPERUSER"))
    }

    @Test
    fun `can reflects membership`() {
        assertTrue(Role.CASHIER.can(Permission.SELL))
        assertFalse(Role.CASHIER.can(Permission.EDIT_CATALOG))
        assertTrue(Role.ADMIN.can(Permission.EDIT_CATALOG))
    }
}
