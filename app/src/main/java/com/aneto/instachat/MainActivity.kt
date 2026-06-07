package com.aneto.instachat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aneto.instachat.core.web.WebBridge
import com.aneto.instachat.ui.AppRoute
import com.aneto.instachat.ui.LocalNavAnimatedVisibilityScope
import com.aneto.instachat.ui.LocalSharedTransitionScope
import com.aneto.instachat.ui.RootViewModel
import com.aneto.instachat.ui.inbox.InboxScreen
import com.aneto.instachat.ui.login.LoginScreen
import com.aneto.instachat.ui.media.MediaViewerScreen
import com.aneto.instachat.ui.theme.InstachatTheme
import com.aneto.instachat.ui.theme.emphasizedAccelerateSpec
import com.aneto.instachat.ui.theme.emphasizedDecelerateSpec
import com.aneto.instachat.ui.theme.emphasizedSpec
import com.aneto.instachat.ui.thread.ThreadScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var webBridge: WebBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        webBridge.boot()
        webBridge.attachTo(this)
        setContent {
            InstachatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent(webBridge = webBridge)
                }
            }
        }
    }

    override fun onDestroy() {
        webBridge.detach()
        super.onDestroy()
    }
}

private const val PEEK_SCALE = 0.92f
private const val PEEK_OFFSET_DIVISOR = 12
private val PEEK_CORNER = 28.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppContent(webBridge: WebBridge) {
    val root: RootViewModel = hiltViewModel()
    val navController = rememberNavController()
    val state by root.state.collectAsStateWithLifecycle()
    val start = if (state.hasSession) AppRoute.Inbox.route else AppRoute.Login.route

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            AppNavHost(navController = navController, start = start, root = root, webBridge = webBridge)
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    start: String,
    root: RootViewModel,
    webBridge: WebBridge,
) {
    NavHost(navController = navController, startDestination = start) {
        composable(
            route = AppRoute.Login.route,
            enterTransition = { fadeIn(emphasizedDecelerateSpec()) },
            exitTransition = { fadeOut(emphasizedAccelerateSpec()) },
        ) {
            LoginScreen(
                onLoggedIn = {
                    root.markAuthenticated()
                    webBridge.boot()
                    navController.navigate(AppRoute.Inbox.route) {
                        popUpTo(AppRoute.Login.route) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = AppRoute.Inbox.route,
            enterTransition = { fadeIn(emphasizedDecelerateSpec()) },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { fadeOut(emphasizedAccelerateSpec()) },
        ) {
            InboxScreen(
                onOpenThread = { threadId ->
                    navController.navigate(AppRoute.Thread.build(threadId))
                },
                onSessionLost = {
                    navController.navigate(AppRoute.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = AppRoute.Thread.route,
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    emphasizedDecelerateSpec(),
                )
            },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = {
                scaleOut(emphasizedSpec(), targetScale = PEEK_SCALE) +
                    slideOutHorizontally(emphasizedSpec()) { it / PEEK_OFFSET_DIVISOR }
            },
        ) { entry ->
            val corner by transition.animateDp(
                transitionSpec = { emphasizedSpec() },
                label = "threadCorner",
            ) { state ->
                when (state) {
                    EnterExitState.Visible -> 0.dp
                    EnterExitState.PreEnter, EnterExitState.PostExit -> PEEK_CORNER
                }
            }
            val threadId = entry.arguments?.getString(AppRoute.Thread.ARG_THREAD_ID).orEmpty()
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(corner))) {
                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                    ThreadScreen(
                        threadId = threadId,
                        onBack = { navController.popBackStack() },
                        onOpenMedia = { entries, index, origin ->
                            navController.navigate(AppRoute.Media.build(entries, index, origin))
                        },
                        onSessionLost = {
                            navController.navigate(AppRoute.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
        composable(
            route = AppRoute.Media.route,
            enterTransition = {
                scaleIn(emphasizedDecelerateSpec(), initialScale = 0.92f) +
                    fadeIn(emphasizedDecelerateSpec())
            },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = {
                scaleOut(emphasizedAccelerateSpec(), targetScale = 0.92f) +
                    fadeOut(emphasizedAccelerateSpec())
            },
        ) { entry ->
            val items = entry.arguments?.getString(AppRoute.Media.ARG_ITEMS).orEmpty()
            val index = entry.arguments?.getString(AppRoute.Media.ARG_INDEX)?.toIntOrNull() ?: 0
            val author = entry.arguments?.getString(AppRoute.Media.ARG_AUTHOR)?.takeIf { it.isNotBlank() }
            val avatar = entry.arguments?.getString(AppRoute.Media.ARG_AVATAR)?.takeIf { it.isNotBlank() }
            val caption = entry.arguments?.getString(AppRoute.Media.ARG_CAPTION)?.takeIf { it.isNotBlank() }
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                MediaViewerScreen(
                    entries = AppRoute.Media.parse(items),
                    initialIndex = index,
                    onClose = { navController.popBackStack() },
                    authorUsername = author,
                    authorAvatarUrl = avatar,
                    caption = caption,
                )
            }
        }
    }
}
