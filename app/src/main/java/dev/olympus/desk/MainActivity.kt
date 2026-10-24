package dev.olympus.desk

import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
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
import androidx.compose.ui.unit.sp
import dev.olympus.desk.data.*
import kotlinx.coroutines.*

private val Ink=Color(0xFF14171E)
private val Tile=Color(0xFF242832)
private val Cobalt=Color(0xFF506CF4)
private val Paper=Color(0xFFF5F3EF)
class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        window.attributes=window.attributes.apply {layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()
        pairFromDebugIntent(intent)
        lifecycleScope.launch {
          val cache=java.io.File(filesDir,"last-frame.json")
          val saved=withContext(Dispatchers.IO){try{if(cache.exists()&&cache.length()<3*1024*1024)cache.readText()else null}catch(_:Exception){null}}
          if(saved!=null)try{LiveRepository.restore(saved)}catch(_:Exception){}
          repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { while(isActive){LiveRepository.checkFreshness();delay(5000)} }
            var retry=1000L
            while(isActive){
                val pair=Pairing.read(this@MainActivity)
                if(pair==null){delay(3000);continue}
                try{LiveRepository.stream(pair).collect{LiveRepository.applyFrame(it);retry=1000L;withContext(Dispatchers.IO){try{val temp=java.io.File(filesDir,"last-frame.tmp");temp.writeText(it);temp.renameTo(cache)}catch(_:Exception){android.util.Log.w("Olympus","snapshot_cache_write_failed")}}};LiveRepository.unavailable()}
                catch(e:CancellationException){throw e}
                catch(_:Exception){LiveRepository.unavailable()}
                delay(retry);retry=(retry*2).coerceAtMost(30000)
            }
        }}
        setContent {
            MaterialTheme(
                colorScheme=darkColorScheme(primary=Cobalt,background=Ink,surface=Tile,onSurface=Paper,onBackground=Paper),
                typography=Typography(
                    bodyLarge=TextStyle(fontSize=13.sp),
                    bodyMedium=TextStyle(fontSize=12.sp),
                    bodySmall=TextStyle(fontSize=11.sp),
                    labelLarge=TextStyle(fontSize=12.sp,lineHeight=16.sp),
                    labelMedium=TextStyle(fontSize=11.sp,lineHeight=14.sp)
                )
            ) { HomeApp() }
        }
    }
    private fun immersive(){WindowCompat.getInsetsController(window,window.decorView).apply{systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;hide(WindowInsetsCompat.Type.systemBars())}}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(hasFocus)immersive()}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);pairFromDebugIntent(intent)}
    private fun pairFromDebugIntent(intent:Intent){if(BuildConfig.DEBUG){val token=intent.getStringExtra("pairToken");val url=intent.getStringExtra("backendUrl");if(token!=null&&url!=null)Pairing.save(this,url,token)}}
}

