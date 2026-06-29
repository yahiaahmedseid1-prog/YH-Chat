package com.example

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.core.app.NotificationCompat
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {

  companion object {
    const val ACTION_ANSWER_CALL = "com.example.ACTION_ANSWER_CALL"
    const val ACTION_DECLINE_CALL = "com.example.ACTION_DECLINE_CALL"
    const val ACTION_OPEN_CHAT = "com.example.ACTION_OPEN_CHAT"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CHAT_ID = "extra_chat_id"
    private const val NOTIFICATION_CALL_ID = 9999
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    createNotificationChannels()
    
    // Request permissions dynamically (including notification permissions for Android 13+)
    try {
      val permissions = mutableListOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
        android.Manifest.permission.CAMERA
      )
      if (Build.VERSION.SDK_INT >= 33) {
        permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
      }
      requestPermissions(permissions.toTypedArray(), 101)
    } catch (e: Exception) {
      e.printStackTrace()
    }

    handleIntent(intent)

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

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    intent?.let {
      when (it.action) {
        ACTION_ANSWER_CALL -> {
          val callId = it.getStringExtra(EXTRA_CALL_ID) ?: ""
          WebNotificationBridge.executeJS("window.handleNotificationAction('answer', '$callId')")
          cancelCallNotification()
        }
        ACTION_DECLINE_CALL -> {
          val callId = it.getStringExtra(EXTRA_CALL_ID) ?: ""
          WebNotificationBridge.executeJS("window.handleNotificationAction('decline', '$callId')")
          cancelCallNotification()
        }
        ACTION_OPEN_CHAT -> {
          val chatId = it.getStringExtra(EXTRA_CHAT_ID) ?: ""
          WebNotificationBridge.executeJS("window.handleNotificationAction('open_chat', '$chatId')")
        }
        else -> {}
      }
    }
  }

  private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      
      // Call channel
      val callChannel = NotificationChannel(
        "call_channel",
        "المكالمات الواردة",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "إشعارات المكالمات الواردة والتنبيه بالرنين"
        enableLights(true)
        lightColor = Color.RED
        enableVibration(true)
        setSound(
          Settings.System.DEFAULT_RINGTONE_URI,
          AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build()
        )
      }
      notificationManager.createNotificationChannel(callChannel)
      
      // Message channel
      val messageChannel = NotificationChannel(
        "message_channel",
        "الرسائل الجديدة",
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = "إشعارات الرسائل والدردشة"
        enableLights(true)
        lightColor = Color.BLUE
        enableVibration(true)
      }
      notificationManager.createNotificationChannel(messageChannel)
    }
  }

  fun showCallNotification(callerName: String, callId: String) {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    val answerIntent = Intent(this, MainActivity::class.java).apply {
      action = ACTION_ANSWER_CALL
      putExtra(EXTRA_CALL_ID, callId)
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val answerPendingIntent = PendingIntent.getActivity(
      this,
      1001,
      answerIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    val declineIntent = Intent(this, MainActivity::class.java).apply {
      action = ACTION_DECLINE_CALL
      putExtra(EXTRA_CALL_ID, callId)
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val declinePendingIntent = PendingIntent.getActivity(
      this,
      1002,
      declineIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val contentIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
      this,
      1003,
      contentIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val builder = NotificationCompat.Builder(this, "call_channel")
      .setSmallIcon(android.R.drawable.sym_call_incoming)
      .setContentTitle("مكالمة صوتية واردة")
      .setContentText(callerName)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setOngoing(true)
      .setAutoCancel(true)
      .setContentIntent(contentPendingIntent)
      .setFullScreenIntent(contentPendingIntent, true)
      .addAction(android.R.drawable.sym_call_incoming, "رد", answerPendingIntent)
      .addAction(android.R.drawable.sym_call_missed, "رفض", declinePendingIntent)
        
    notificationManager.notify(NOTIFICATION_CALL_ID, builder.build())
  }

  fun cancelCallNotification() {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(NOTIFICATION_CALL_ID)
  }

  fun showMessageNotification(title: String, body: String, chatId: String) {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    val intent = Intent(this, MainActivity::class.java).apply {
      action = ACTION_OPEN_CHAT
      putExtra(EXTRA_CHAT_ID, chatId)
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      chatId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    val builder = NotificationCompat.Builder(this, "message_channel")
      .setSmallIcon(android.R.drawable.stat_notify_chat)
      .setContentTitle(title)
      .setContentText(body)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)
        
    notificationManager.notify(chatId.hashCode(), builder.build())
  }
}

class WebAppInterface(private val activity: MainActivity) {
  @JavascriptInterface
  fun showCallNotification(callerName: String, callId: String) {
    activity.showCallNotification(callerName, callId)
  }
  
  @JavascriptInterface
  fun cancelCallNotification() {
    activity.cancelCallNotification()
  }
  
  @JavascriptInterface
  fun showMessageNotification(title: String, body: String, chatId: String) {
    activity.showMessageNotification(title, body, chatId)
  }
}

object WebNotificationBridge {
  var webViewRef: WeakReference<WebView>? = null
  
  fun executeJS(code: String) {
    webViewRef?.get()?.post {
      webViewRef?.get()?.evaluateJavascript(code, null)
    }
  }
}

fun getMainActivity(context: Context): MainActivity? {
  var ctx = context
  while (ctx is ContextWrapper) {
    if (ctx is MainActivity) {
      return ctx
    }
    ctx = ctx.baseContext
  }
  return null
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
          mediaPlaybackRequiresUserGesture = false
        }
        
        getMainActivity(context)?.let { activity ->
          addJavascriptInterface(WebAppInterface(activity), "AndroidBridge")
        }
        
        WebNotificationBridge.webViewRef = WeakReference(this)

        webViewClient = object : WebViewClient() {
          override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
          ): WebResourceResponse? {
            val url = request?.url ?: return null
            if (url.host == "appassets.androidplatform.net" && url.path?.startsWith("/assets/") == true) {
              val assetPath = url.path?.substringAfter("/assets/") ?: return null
              try {
                val inputStream = context.assets.open(assetPath)
                val mimeType = when {
                  assetPath.endsWith(".html") -> "text/html"
                  assetPath.endsWith(".css") -> "text/css"
                  assetPath.endsWith(".js") -> "application/javascript"
                  assetPath.endsWith(".png") -> "image/png"
                  assetPath.endsWith(".jpg") || assetPath.endsWith(".jpeg") -> "image/jpeg"
                  assetPath.endsWith(".svg") -> "image/svg+xml"
                  assetPath.endsWith(".json") -> "application/json"
                  else -> "application/octet-stream"
                }
                return WebResourceResponse(mimeType, "UTF-8", inputStream)
              } catch (e: Exception) {
                e.printStackTrace()
              }
            }
            return super.shouldInterceptRequest(view, request)
          }
        }
        webChromeClient = object : WebChromeClient() {
          override fun onPermissionRequest(request: PermissionRequest?) {
            try {
              val resources = request?.resources ?: emptyArray()
              request?.grant(resources)
            } catch (e: Exception) {
              e.printStackTrace()
            }
          }

          override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
            consoleMessage?.let {
              android.util.Log.d("WebViewConsole", "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
            }
            return true
          }
        }
        loadUrl("https://appassets.androidplatform.net/assets/index.html")
      }
    }
  )
}
