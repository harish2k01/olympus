package dev.olympus.desk

import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme



@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val Figtree = FontFamily(
    Font(R.font.figtree, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.figtree, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.figtree, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.figtree, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)
class MainActivity:ComponentActivity(){
    private var notificationNonce by androidx.compose.runtime.mutableLongStateOf(0)
    private var notificationTarget by androidx.compose.runtime.mutableStateOf("")
    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        window.attributes=window.attributes.apply {layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView){view,insets->
            if(insets.isVisible(WindowInsetsCompat.Type.statusBars()))view.postDelayed({if(!isFinishing&&!isDestroyed)immersive()},2500)
            insets
        }
        notificationTarget=intent.getStringExtra("eventId").orEmpty()
        pairFromDebugIntent(intent)
        if(getSharedPreferences("home-v3",0).getBoolean("backgroundAlerts",false))startForegroundService(Intent(this,AlertWatchService::class.java))
        lifecycleScope.launch {
            val settings = getSharedPreferences("home-v3", MODE_PRIVATE)
            val backgroundAlerts = MutableStateFlow(settings.getBoolean("backgroundAlerts", false))
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                if (key == "backgroundAlerts") backgroundAlerts.value = prefs.getBoolean(key, false)
            }
            settings.registerOnSharedPreferenceChangeListener(listener)
            try {
                val cached = withContext(Dispatchers.IO) { LiveRepository.cachedFrame(this@MainActivity) }
                if (cached != null && LiveRepository.lastSuccess == 0L) runCatching { LiveRepository.restore(cached) }
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        while (isActive) {
                            val previousState = LiveRepository.state
                            LiveRepository.checkFreshness()
                            if (LiveRepository.state != previousState) {
                                OlympusStatusWidget.publish(this@MainActivity)
                            }
                            delay(5000)
                        }
                    }
                    backgroundAlerts.collectLatest { watchedByService ->
                        if (watchedByService) return@collectLatest
                        val pair = Pairing.read(this@MainActivity) ?: return@collectLatest
                        var retry = 1000L
                        while (isActive) {
                            LiveRepository.reconnecting()
                            try {
                                LiveRepository.stream(pair).collect { frame ->
                                    LiveRepository.applyFrame(frame)
                                    OlympusStatusWidget.publish(this@MainActivity)
                                    retry = 1000L
                                    withContext(Dispatchers.IO) { LiveRepository.cacheFrame(this@MainActivity, frame) }
                                }
                                LiveRepository.unavailable()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                LiveRepository.unavailable()
                            }
                            OlympusStatusWidget.publish(this@MainActivity)
                            LiveRepository.awaitRetry(retry)
                            retry = (retry * 2).coerceAtMost(30_000)
                        }
                    }
                }
            } finally {
                settings.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }
        setContent {
            val settings = remember { getSharedPreferences("home-v3", MODE_PRIVATE) }
            var appearance by remember { mutableStateOf(settings.getString("appearance", "Dark")) }
            DisposableEffect(settings) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                    if (key == "appearance") appearance = prefs.getString(key, "Dark")
                }
                settings.registerOnSharedPreferenceChangeListener(listener)
                onDispose { settings.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            val light = appearance == "Light" || (appearance == "System" && !isSystemInDarkTheme())
            val palette = if (light) LightDesk else DarkDesk
            val base = if (light) lightColorScheme() else darkColorScheme()
            MaterialTheme(
                colorScheme = base.copy(primary = palette.blue, background = palette.background, surface = palette.surface,
                    onSurface = palette.text, onBackground = palette.text, onPrimary = palette.onPrimary,
                    primaryContainer = palette.selected, onPrimaryContainer = palette.onSelected, secondaryContainer = palette.selected,
                    onSecondaryContainer = palette.onSelected, surfaceVariant = palette.raised, onSurfaceVariant = palette.muted, error = palette.critical),
                typography=Typography(
                    bodyLarge=TextStyle(fontFamily=Figtree,fontSize=14.sp),
                    bodyMedium=TextStyle(fontFamily=Figtree,fontSize=14.sp),
                    bodySmall=TextStyle(fontFamily=Figtree,fontSize=12.sp),
                    titleLarge=TextStyle(fontFamily=Figtree,fontSize=22.sp,fontWeight=FontWeight.Bold),
                    labelLarge=TextStyle(fontFamily=Figtree,fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold),
                    labelMedium=TextStyle(fontFamily=Figtree,fontSize=12.sp,lineHeight=14.sp,fontWeight=FontWeight.SemiBold)
                )
            ) { ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = Figtree)) { CompositionLocalProvider(LocalDeskPalette provides palette) { HomeApp(notificationTarget,notificationNonce) } } }
        }
    }
    private fun immersive(){WindowCompat.getInsetsController(window,window.decorView).apply{systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;hide(WindowInsetsCompat.Type.systemBars())}}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)immersive()}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);notificationNonce++;notificationTarget=intent.getStringExtra("eventId").orEmpty();pairFromDebugIntent(intent)}
    private fun pairFromDebugIntent(intent:Intent){if(BuildConfig.DEBUG){val token=intent.getStringExtra("pairToken");val url=intent.getStringExtra("backendUrl");if(token!=null&&url!=null)Pairing.save(this,url,token)}}
}

