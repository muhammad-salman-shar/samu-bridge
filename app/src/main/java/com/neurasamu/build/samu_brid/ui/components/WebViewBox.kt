package com.neurasamu.build.samu_brid.ui.components

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.neurasamu.build.samu_brid.webview.WebViewManager

@Composable
fun WebViewBox(
    initialUrl: String,
    modifier: Modifier = Modifier,
    onReady: (WebView) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                WebViewManager.configure(this)
                if (initialUrl.isNotBlank()) loadUrl(initialUrl)
                post { onReady(this) }
            }
        }
    )
}
