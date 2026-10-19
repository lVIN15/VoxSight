package com.cit.kaido.voxsight.ui.screens.profile

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cit.kaido.voxsight.network.CreateCheckoutSessionRequest
import com.cit.kaido.voxsight.network.PaymongoClient
import com.cit.kaido.voxsight.util.PremiumManager
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.Crown
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.launch

@Composable
fun PremiumUpgradeScreen(
    onCancelClicked: () -> Unit,
    onUpgradeSuccess: () -> Unit = onCancelClicked
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val topPurple = Color(0xFF38036B)
    val yellowGold = Color(0xFFFACC15)
    val bgLight = Color(0xFFF4F4FA)
    val textGray = Color(0xFF6B7280)
    val textPurple = Color(0xFF6B21A8)
    val iconBgPurple = Color(0xFFF3E8FF)

    var isPremium by remember { mutableStateOf(PremiumManager.isUserPremium(context)) }
    var isLoadingCheckout by remember { mutableStateOf(false) }
    var activeCheckoutUrl by remember { mutableStateOf<String?>(null) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var activeSuccessUrl by remember { mutableStateOf(PaymongoClient.DEFAULT_SUCCESS_URL) }
    var activeCancelUrl by remember { mutableStateOf(PaymongoClient.DEFAULT_CANCEL_URL) }

    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showKeySetupDialog by remember { mutableStateOf(false) }
    var secretKeyInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isPremium = PremiumManager.syncFromSupabase(context)
    }

    fun startPaymongoCheckout() {
        if (isLoadingCheckout) return
        isLoadingCheckout = true
        errorMessage = null

        val prefs = context.getSharedPreferences("voxsight_prefs", android.content.Context.MODE_PRIVATE)
        val username = prefs.getString("logged_in_username", null)
        val email = prefs.getString("logged_in_email", null)

        coroutineScope.launch {
            val response = PaymongoClient.createCheckoutSession(
                context = context,
                request = CreateCheckoutSessionRequest(
                    username = username,
                    email = email,
                    planName = "VoxSight Premium (Monthly)",
                    amountCentavos = 8500
                )
            )
            isLoadingCheckout = false

            if (response.success && !response.checkoutUrl.isNullOrBlank()) {
                activeSessionId = response.sessionId
                activeSuccessUrl = response.successUrl ?: PaymongoClient.DEFAULT_SUCCESS_URL
                activeCancelUrl = response.cancelUrl ?: PaymongoClient.DEFAULT_CANCEL_URL
                activeCheckoutUrl = response.checkoutUrl
            } else {
                val err = response.error.orEmpty()
                if (err.contains("PAYMONGO_SECRET_KEY", ignoreCase = true) ||
                    err.contains("HTTP 404", ignoreCase = true) ||
                    err.contains("HTTP 503", ignoreCase = true)
                ) {
                    showKeySetupDialog = true
                } else {
                    errorMessage = err.ifBlank { "Unable to initialize PayMongo checkout." }
                }
            }
        }
    }

    fun handlePaymentConfirmed(sessionId: String?) {
        activeCheckoutUrl = null
        coroutineScope.launch {
            PremiumManager.markUserPremium(context, sessionId)
            isPremium = true
            showSuccessDialog = true
        }
    }

    // If the in-app PayMongo WebView is active, render it full screen
    if (activeCheckoutUrl != null) {
        PaymongoInAppWebView(
            checkoutUrl = activeCheckoutUrl!!,
            sessionId = activeSessionId,
            successUrl = activeSuccessUrl,
            cancelUrl = activeCancelUrl,
            onPaymentSuccess = { handlePaymentConfirmed(activeSessionId) },
            onPaymentCancelled = {
                activeCheckoutUrl = null
                Toast.makeText(context, "Payment cancelled", Toast.LENGTH_SHORT).show()
            },
            onVerifyAndClose = {
                val currentSession = activeSessionId
                if (!currentSession.isNullOrBlank()) {
                    coroutineScope.launch {
                        val status = PaymongoClient.verifyCheckoutSession(context, currentSession)
                        if (status.paid) {
                            handlePaymentConfirmed(currentSession)
                        } else {
                            activeCheckoutUrl = null
                        }
                    }
                } else {
                    activeCheckoutUrl = null
                }
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgLight)
    ) {
        // Top Section (Purple)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    color = topPurple,
                    shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
                )
        ) {
            // Background decorative notes
            Icon(
                imageVector = Lucide.Music,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.05f),
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.TopStart)
                    .offset(x = 20.dp, y = 40.dp)
            )
            Icon(
                imageVector = Lucide.Music,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.05f),
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-20).dp, y = (-20).dp)
            )
            Icon(
                imageVector = Lucide.Music,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.05f),
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (-60).dp, y = 80.dp)
            )

            // Content
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Crown icon in circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.Crown,
                        contentDescription = "Premium Crown",
                        tint = yellowGold,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isPremium) "ACTIVE PLAN" else "UPGRADE TO",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "PREMIUM",
                    color = yellowGold,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Subtitle Text
        Text(
            text = "With VoxSight Premium, you can elevate your practice sessions to the next level:",
            color = textGray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Features Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                FeatureRow(
                    icon = Lucide.Cloud,
                    iconBgColor = iconBgPurple,
                    iconColor = textPurple,
                    text = buildAnnotatedString {
                        append("Upload an ")
                        withStyle(style = SpanStyle(color = textPurple, fontWeight = FontWeight.Medium)) {
                            append("increased amount")
                        }
                        append(" of music sheets for you to practice.")
                    }
                )

                FeatureRow(
                    icon = Lucide.Cloud,
                    iconBgColor = iconBgPurple,
                    iconColor = textPurple,
                    text = buildAnnotatedString {
                        append("Upgraded cloud storage capacity for your converted sheets.")
                    }
                )

                FeatureRow(
                    icon = Lucide.Download,
                    iconBgColor = iconBgPurple,
                    iconColor = textPurple,
                    text = buildAnnotatedString {
                        append("Ability for you to ")
                        withStyle(style = SpanStyle(color = textPurple, fontWeight = FontWeight.Medium)) {
                            append("download")
                        }
                        append(" your converted sheets outside of this app.")
                    }
                )

                FeatureRow(
                    icon = Lucide.Plus,
                    iconBgColor = iconBgPurple,
                    iconColor = textPurple,
                    text = buildAnnotatedString {
                        append("And many more exclusive features...")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // PayMongo Supported Methods caption
        Text(
            text = "Secured by PayMongo • GCash, Maya, Cards & QR Ph",
            color = textGray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // Price / Upgrade Button
        Button(
            onClick = {
                if (!isPremium) {
                    startPaymongoCheckout()
                }
            },
            enabled = !isLoadingCheckout && !isPremium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPremium) Color(0xFF16A34A) else topPurple,
                disabledContainerColor = if (isPremium) Color(0xFF16A34A) else topPurple.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoadingCheckout) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Connecting to PayMongo...",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = if (isPremium) "✓ Active Premium Member" else "₱ 85.00 / month",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Cancel / Back Button
        TextButton(
            onClick = onCancelClicked,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = if (isPremium) "Back to Profile" else "Cancel",
                color = if (isPremium) textPurple else Color(0xFFF44336),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Payment Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onUpgradeSuccess()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Welcome to Premium!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Your PayMongo payment of ₱85.00 was successful. Your account has been upgraded to VoxSight Premium (+50 Upload Credits)!",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onUpgradeSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = topPurple)
                ) {
                    Text("Awesome!", color = Color.White)
                }
            }
        )
    }

    // Error Dialog
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Payment Error", fontWeight = FontWeight.Bold) },
            text = { Text(errorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK", color = topPurple, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Developer / Key Setup Dialog if backend has not configured PAYMONGO_SECRET_KEY yet
    if (showKeySetupDialog) {
        AlertDialog(
            onDismissRequest = { showKeySetupDialog = false },
            title = {
                Text("Configure PayMongo Key", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "The server hasn't configured PAYMONGO_SECRET_KEY yet (or hasn't been redeployed). Paste your PayMongo Secret Key (e.g. sk_test_...) below to test checkout immediately:",
                        fontSize = 13.sp,
                        color = textGray
                    )
                    OutlinedTextField(
                        value = secretKeyInput,
                        onValueChange = { secretKeyInput = it },
                        label = { Text("PayMongo Secret Key (sk_test_...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (secretKeyInput.isNotBlank()) {
                            PaymongoClient.saveSecretKeyOverride(context, secretKeyInput)
                            showKeySetupDialog = false
                            startPaymongoCheckout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = topPurple)
                ) {
                    Text("Save & Continue", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeySetupDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PaymongoInAppWebView(
    checkoutUrl: String,
    sessionId: String?,
    successUrl: String,
    cancelUrl: String,
    onPaymentSuccess: () -> Unit,
    onPaymentCancelled: () -> Unit,
    onVerifyAndClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val topPurple = Color(0xFF38036B)

    var isPageLoading by remember { mutableStateOf(true) }
    var isVerifyingStatus by remember { mutableStateOf(false) }
    var hasHandledCompletion by remember { mutableStateOf(false) }

    BackHandler {
        onVerifyAndClose()
    }

    fun checkUrlForRedirect(url: String?): Boolean {
        if (url.isNullOrBlank() || hasHandledCompletion) return false
        val normalized = url.lowercase()
        val isSuccessRedirect = normalized.startsWith(successUrl.lowercase()) ||
            normalized.contains("/payment/success") ||
            normalized.contains("/api/payments/success")
        val isCancelRedirect = normalized.startsWith(cancelUrl.lowercase()) ||
            normalized.contains("/payment/cancel") ||
            normalized.contains("/api/payments/cancel")

        return when {
            isSuccessRedirect -> {
                hasHandledCompletion = true
                onPaymentSuccess()
                true
            }
            isCancelRedirect -> {
                hasHandledCompletion = true
                onPaymentCancelled()
                true
            }
            else -> false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(topPurple)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Secure",
                    tint = Color(0xFFFACC15),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PayMongo Checkout",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!sessionId.isNullOrBlank()) {
                    TextButton(
                        enabled = !isVerifyingStatus,
                        onClick = {
                            isVerifyingStatus = true
                            coroutineScope.launch {
                                val status = PaymongoClient.verifyCheckoutSession(context, sessionId)
                                isVerifyingStatus = false
                                if (status.paid) {
                                    if (!hasHandledCompletion) {
                                        hasHandledCompletion = true
                                        onPaymentSuccess()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Payment not completed yet. Complete payment in the form below.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    ) {
                        Text(
                            text = if (isVerifyingStatus) "Checking..." else "Verify",
                            color = Color(0xFFFACC15),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }

                IconButton(
                    onClick = onVerifyAndClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Checkout",
                        tint = Color.White
                    )
                }
            }
        }

        if (isPageLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFACC15),
                trackColor = topPurple.copy(alpha = 0.2f)
            )
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isPageLoading = true
                            checkUrlForRedirect(url)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isPageLoading = false
                            checkUrlForRedirect(url)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUrl = request?.url?.toString() ?: return false
                            if (checkUrlForRedirect(targetUrl)) {
                                return true
                            }

                            // Handle external e-wallet deep links (e.g., gcash://, paymaya://, intent://)
                            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                                return try {
                                    val intent = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME)
                                    ctx.startActivity(intent)
                                    true
                                } catch (_: Exception) {
                                    try {
                                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                        ctx.startActivity(fallbackIntent)
                                        true
                                    } catch (_: Exception) {
                                        true
                                    }
                                }
                            }
                            return false
                        }
                    }

                    loadUrl(checkoutUrl)
                }
            }
        )
    }
}

@Composable
fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    iconColor: Color,
    text: androidx.compose.ui.text.AnnotatedString
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = Color(0xFF4B5563),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}