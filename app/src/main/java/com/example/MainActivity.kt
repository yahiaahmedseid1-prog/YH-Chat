package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Request microphone recording permissions
    try {
      requestPermissions(
        arrayOf(
          android.Manifest.permission.RECORD_AUDIO,
          android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
          android.Manifest.permission.CAMERA
        ),
        101
      )
    } catch (e: Exception) {
      e.printStackTrace()
    }

    setContent {
      Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        TelegramWebView(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        )
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TelegramWebView(modifier: Modifier = Modifier) {
  AndroidView(
    modifier = modifier,
    factory = { context ->
      WebView(context).apply {
        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          databaseEnabled = true
          loadWithOverviewMode = true
          useWideViewPort = true
          mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
          allowContentAccess = true
          allowFileAccess = true
        }
        webViewClient = WebViewClient()
        webChromeClient = object : WebChromeClient() {
          override fun onPermissionRequest(request: PermissionRequest?) {
            try {
              request?.grant(request.resources)
            } catch (e: Exception) {
              e.printStackTrace()
            }
          }
        }
        loadUrl("file:///android_asset/index.html")
      }
    }
  )
}
