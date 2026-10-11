package net.redngreen.easypos.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.redngreen.easypos.core.data.repo.TenantConfigRepository
import net.redngreen.easypos.core.data.sync.SyncStatus
import net.redngreen.easypos.core.data.sync.SyncStatusRepository

/** Shell chrome state: shop identity + honest sync status (task 12.4). */
@HiltViewModel
class ShellViewModel
@Inject
constructor(
    syncStatusRepository: SyncStatusRepository,
    tenantConfig: TenantConfigRepository,
) : ViewModel() {

    /** Live sync state: synced / pending / syncing / offline / error. */
    val syncStatus: StateFlow<SyncStatus> =
        syncStatusRepository.status
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                SyncStatus(net.redngreen.easypos.core.data.sync.SyncPhase.SYNCED, 0, false),
            )

    /** Shop display name for the sidebar header; blank until profile is set. */
    val shopName: StateFlow<String> =
        tenantConfig.observe()
            .map { it?.name?.takeIf { name -> name.isNotBlank() } ?: "" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}
