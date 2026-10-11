package net.redngreen.easypos.core.data

/** Well-known settings keys (Settings table; shop-settings spec). */
object SettingsKeys {
    /** Idle auto-lock in minutes; default 5 (user-access-control spec). */
    const val IDLE_LOCK_MINUTES = "idle_lock_minutes"

    const val DEFAULT_IDLE_LOCK_MINUTES = 5

    /** Base64 merchant QR image (e.g. DuitNow sticker) shown at tender — offline-safe. */
    const val DUITNOW_QR_IMAGE = "duitnow_qr_image"

    /** Paired Bluetooth ESC/POS printer MAC address; blank = no printer configured. */
    const val PRINTER_MAC = "printer_mac"

    /** Paper width in mm (58 or 80) — selects the receipt column count. */
    const val PRINTER_WIDTH_MM = "printer_width_mm"

    const val PRINTER_WIDTH_DEFAULT_MM = 80

    /** Kick the cash drawer (via printer) on cash sales (receipts spec). */
    const val DRAWER_KICK = "drawer_kick"

    /** Receipt branding: optional header line and footer message. */
    const val RECEIPT_HEADER = "receipt_header"

    const val RECEIPT_FOOTER = "receipt_footer"

    /** First-run wizard resume point ("profile"/"printer"/"products"/"done"); absent = not applicable. */
    const val ONBOARDING_STEP = "onboarding_step"

    const val ONBOARDING_PROFILE = "profile"
    const val ONBOARDING_PRINTER = "printer"
    const val ONBOARDING_PRODUCTS = "products"
    const val ONBOARDING_DONE = "done"

    /** Column counts per paper width (receipts spec). */
    fun columnsForWidthMm(widthMm: Int): Int = if (widthMm >= 80) 42 else 32
}
