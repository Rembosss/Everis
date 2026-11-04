package org.everis.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import org.everis.app.theme.EverisTheme
import java.time.LocalDate
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.ViewTreeObserver

class MainActivity : AppCompatActivity() {
    private lateinit var diary: DiaryViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configuration = resources.configuration
        val hasSupportedLanguage = (0 until configuration.locales.size()).any { index ->
            appLanguages.any { it.code == configuration.locales[index].language }
        }
        if (!hasSupportedLanguage && AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))
        }
        enableEdgeToEdge()
        diary = ViewModelProvider(this)[DiaryViewModel::class.java]
        openNotification(intent)
        val loadDeadline = SystemClock.uptimeMillis() + 1500
        window.decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!diary.state.loaded && SystemClock.uptimeMillis() < loadDeadline) return false
                window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
        splashScreen.setOnExitAnimationListener { splash ->
            if (!ValueAnimator.areAnimatorsEnabled() || diary.state.performanceMode) splash.remove()
            else ObjectAnimator.ofFloat(splash, "alpha", 1f, 0f).apply {
                duration = 240
                interpolator = android.view.animation.DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { splash.remove() }
                    override fun onAnimationCancel(animation: Animator) { splash.remove() }
                })
                start()
            }
        }
        setContent {
            val dark = when (diary.state.themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            LaunchedEffect(dark, diary.state.loaded, diary.state.blackTheme) {
                if (diary.state.loaded && !dark && diary.state.blackTheme) diary.setBlackTheme(false)
            }
            EverisTheme(palette = diary.state.palette, themeMode = diary.state.themeMode, blackTheme = diary.state.blackTheme,
                performanceMode = diary.state.performanceMode) {
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect { window.decorView.setBackgroundColor(background) }
                HapticsProvider(diary.state.hapticsEnabled) { DiaryApp(diary) }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotification(intent)
    }
    override fun onStart() {
        super.onStart()
        diary.checkAppUpdate()
    }
    private fun openNotification(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "everis" && uri.host == "day") runCatching { LocalDate.parse(uri.lastPathSegment) }
            .getOrNull()?.takeIf { it.year in 1900..2200 }?.let(diary::requestDay)
    }
}
