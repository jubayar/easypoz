package net.redngreen.easypos.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local audit trail (user-access-control spec): denials, logins, lock events,
 * and attribution for mutating actions. UserName is denormalized so entries
 * stay readable even if the user is later renamed or deactivated.
 */
@Entity(tableName = "activity_log", indices = [Index("userId"), Index("at")])
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userId: String? = null,
    val userName: String? = null,
    /** LOGIN | LOGIN_FAILED | LOCK | ACTION | DENIAL */
    val type: String,
    /** Dotted action id, e.g. "user.create", "settings.open". */
    val action: String,
    val detail: String? = null,
    val at: Long,
) {
    companion object {
        const val TYPE_LOGIN = "LOGIN"
        const val TYPE_LOGIN_FAILED = "LOGIN_FAILED"
        const val TYPE_LOCK = "LOCK"
        const val TYPE_ACTION = "ACTION"
        const val TYPE_DENIAL = "DENIAL"
    }
}
