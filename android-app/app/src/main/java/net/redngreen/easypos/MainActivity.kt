package net.redngreen.easypos

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import android.graphics.Color as AndroidColor
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.redngreen.easypos.designsystem.EasyPOSTheme
import net.redngreen.easypos.feature.login.ui.LockRoot
import net.redngreen.easypos.feature.login.ui.LoginViewModel
import net.redngreen.easypos.feature.login.ui.model.LoginAction
import net.redngreen.easypos.ui.main.ShellViewModel
import net.redngreen.easypos.ui.onboarding.OnboardingViewModel
import net.redngreen.easypos.ui.onboarding.OnboardingWizard
import org.koin.androidx.compose.koinViewModel

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

  @javax.inject.Inject lateinit var syncScheduler: net.redngreen.easypos.core.sync.SyncScheduler

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Forced-light theme: request light system-bar styling explicitly so a
    // dark-OS tablet doesn't render light-on-light status/nav icons.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
    )
    setContent {
      EasyPOSTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          AppRoot()
        }
      }
    }
  }

  // Foreground trigger (task 12.3): a sync pass whenever the app comes forward.
  override fun onStart() {
    super.onStart()
    syncScheduler.requestNow()
  }
}

/**
 * App root: navigation lives underneath, the lock overlay covers it when
 * there is no active user (initial run, manual lock, quick-switch, idle
 * auto-lock). Composing the overlay instead of navigating keeps every
 * ViewModel — open carts included — alive through lock/unlock.
 */
@Composable
private fun AppRoot(
  loginViewModel: LoginViewModel = koinViewModel(),
  shellViewModel: ShellViewModel = hiltViewModel(),
  onboardingViewModel: OnboardingViewModel = hiltViewModel(),
) {
  val loginState by loginViewModel.state.collectAsStateWithLifecycle()
  val activeUser = loginState.activeUser
  val hasAnyUser = loginState.hasAnyUser
  val idleLockMinutes = loginState.idleLockMinutes
  val shopName by shellViewModel.shopName.collectAsStateWithLifecycle()
  val onboardingDone by onboardingViewModel.done.collectAsStateWithLifecycle()

  val lastInteraction = remember { mutableLongStateOf(System.currentTimeMillis()) }

  Box(
    Modifier.fillMaxSize().pointerInput(Unit) {
      awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        lastInteraction.longValue = System.currentTimeMillis()
      }
    }
  ) {
    MainNavigation()

    when {
      activeUser == null -> LockRoot(shopName = shopName)
      // First-run wizard resumes until finished/skipped (task 11.2), covering the sell screen.
      onboardingDone == false -> OnboardingWizard(viewModel = onboardingViewModel)
    }
  }

  // Idle auto-lock (task 5.4): default 5 minutes, configurable via settings.
  LaunchedEffect(activeUser?.id, idleLockMinutes) {
    val timeoutMs = idleLockMinutes.minutes.inWholeMilliseconds
    while (isActive) {
      delay(IDLE_CHECK_INTERVAL_MS)
      if (activeUser != null &&
        System.currentTimeMillis() - lastInteraction.longValue >= timeoutMs
      ) {
        loginViewModel.onAction(LoginAction.LockNow("idle"))
      }
    }
  }
}

private const val IDLE_CHECK_INTERVAL_MS = 15_000L
