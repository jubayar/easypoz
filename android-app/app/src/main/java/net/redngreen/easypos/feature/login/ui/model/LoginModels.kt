package net.redngreen.easypos.feature.login.ui.model

import net.redngreen.easypos.core.database.SettingsKeys
import net.redngreen.easypos.feature.login.domain.UserAccount

data class LoginState(
    val activeUser: UserAccount? = null,
    val unlockableUsers: List<UserAccount> = emptyList(),
    val hasAnyUser: Boolean? = null,
    val hasUnlockableUser: Boolean? = null,
    val idleLockMinutes: Int = SettingsKeys.DEFAULT_IDLE_LOCK_MINUTES,
)

sealed interface LoginAction {
    data class Unlock(val userId: String, val pin: String) : LoginAction

    data class CreateFirstAdmin(val name: String, val pin: String) : LoginAction

    data class LockNow(val detail: String) : LoginAction
}

sealed interface LoginEvent {
    data object UnlockFailed : LoginEvent

    data object AdminCreateFailed : LoginEvent
}
