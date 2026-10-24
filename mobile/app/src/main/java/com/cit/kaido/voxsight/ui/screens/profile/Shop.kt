package com.cit.kaido.voxsight.ui.screens.profile

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cit.kaido.voxsight.R
import com.cit.kaido.voxsight.network.CreateCheckoutSessionRequest
import com.cit.kaido.voxsight.network.PaymongoClient
import com.cit.kaido.voxsight.util.PremiumManager
import kotlinx.coroutines.launch

data class CreditPackOption(
    val credits: Int,
    val title: String,
    val priceLabel: String,
    val amountCentavos: Int,
    val coinTier: Int
)

private val defaultCreditPacks = listOf(
    CreditPackOption(
        credits = 10,
        title = "10 Upload Credits",
        priceLabel = "₱20.00",
        amountCentavos = 2000,
        coinTier = 1
    ),
    CreditPackOption(
        credits = 30,
        title = "30 Upload Credits",
        priceLabel = "₱35.00",
        amountCentavos = 3500,
        coinTier = 2
    ),
    CreditPackOption(
        credits = 50,
        title = "50 Upload Credits",
        priceLabel = "₱50.00",
        amountCentavos = 5000,
        coinTier = 3
    ),
    CreditPackOption(
        credits = 100,
        title = "100 Upload Credits",
        priceLabel = "₱95.00",
        amountCentavos = 9500,
        coinTier = 4
    )
)

@Composable
fun ShopScreen(
    onBackClicked: () -> Unit = {},
    onHelpClicked: () -> Unit = {},
    onCreditPackSelected: (CreditPackOption) -> Unit = {},
    onPremiumClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val screenBg = Color(0xFFFAF9FD)
    val sectionLabelColor = Color(0xFF565865)
    val cardBorderColor = Color(0xFFEAE7F2)
    val deepPurple = Color(0xFF38036B)
    val pricePillBg = Color(0xFFF3EFFA)
    val pricePillBorder = Color(0xFFDAD3E8)
    val textDark = Color(0xFF1C1B1F)

    var currentCredits by remember { mutableIntStateOf(PremiumManager.getUploadCredits(context)) }
    var selectedPackForCheckout by remember { mutableStateOf<CreditPackOption?>(null) }
    var isLoadingCheckout by remember { mutableStateOf(false) }
    var activeCheckoutUrl by remember { mutableStateOf<String?>(null) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var activeSuccessUrl by remember { mutableStateOf(PaymongoClient.DEFAULT_SUCCESS_URL) }
    var activeCancelUrl by remember { mutableStateOf(PaymongoClient.DEFAULT_CANCEL_URL) }
    var purchasedPackSuccess by remember { mutableStateOf<CreditPackOption?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showKeySetupDialog by remember { mutableStateOf(false) }
    var secretKeyInput by remember { mutableStateOf("") }
    var showHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        PremiumManager.syncFromSupabase(context)
        currentCredits = PremiumManager.getUploadCredits(context)
    }

    fun startPackCheckout(pack: CreditPackOption) {
        if (isLoadingCheckout) return
        selectedPackForCheckout = pack
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
                    planName = "VoxSight - ${pack.title}",
                    amountCentavos = pack.amountCentavos
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

    fun handleCreditPurchaseConfirmed(pack: CreditPackOption) {
        activeCheckoutUrl = null
        coroutineScope.launch {
            val updated = PremiumManager.addUploadCredits(context, pack.credits)
            currentCredits = updated
            purchasedPackSuccess = pack
        }
    }

    // Render full-screen PayMongo In-App WebView when checkout is active
    if (activeCheckoutUrl != null && selectedPackForCheckout != null) {
        val pack = selectedPackForCheckout!!
        PaymongoInAppWebView(
            checkoutUrl = activeCheckoutUrl!!,
            sessionId = activeSessionId,
            successUrl = activeSuccessUrl,
            cancelUrl = activeCancelUrl,
            onPaymentSuccess = { handleCreditPurchaseConfirmed(pack) },
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
                            handleCreditPurchaseConfirmed(pack)
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
            .background(screenBg)
    ) {
        // ── Top Bar ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onBackClicked,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textDark
                    )
                }

                Text(
                    text = "Credit Shop",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )

                IconButton(
                    onClick = {
                        showHelpDialog = true
                        onHelpClicked()
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = "Help",
                        tint = Color(0xFF49454F)
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = Color(0xFFECEAF2)
            )
        }

        // ── Scrollable Body ─────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            // Section 1 Header + Current Balance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PURCHASE UPLOAD CREDITS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = sectionLabelColor,
                    letterSpacing = 0.8.sp
                )

                Text(
                    text = "Balance: $currentCredits",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = deepPurple
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                defaultCreditPacks.forEach { pack ->
                    val isThisPackLoading = isLoadingCheckout && selectedPackForCheckout?.credits == pack.credits
                    CreditPackRowCard(
                        pack = pack,
                        isLoading = isThisPackLoading,
                        cardBorderColor = cardBorderColor,
                        pricePillBg = pricePillBg,
                        pricePillBorder = pricePillBorder,
                        deepPurple = deepPurple,
                        textDark = textDark,
                        onClick = {
                            onCreditPackSelected(pack)
                            startPackCheckout(pack)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 2: VoxSight Premium
            Text(
                text = "VOXSIGHT PREMIUM",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = sectionLabelColor,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            // VoxSight Premium Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(alpha = 0.06f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.dp, cardBorderColor, RoundedCornerShape(20.dp))
                    .clickable { onPremiumClicked() }
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Solid Golden Crown Icon
                SolidGoldenCrownIcon(
                    modifier = Modifier.size(38.dp)
                )

                Spacer(modifier = Modifier.width(18.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "VoxSight Premium (Monthly)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "- 50 Upload Credits given per month",
                        fontSize = 14.sp,
                        color = Color(0xFF2B2B30)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "- Back up your sheets on the cloud",
                        fontSize = 14.sp,
                        color = Color(0xFF2B2B30)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open VoxSight Premium",
                    tint = textDark,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Purchase Success Dialog
    if (purchasedPackSuccess != null) {
        val pack = purchasedPackSuccess!!
        AlertDialog(
            onDismissRequest = { purchasedPackSuccess = null },
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
                    text = "Credits Added!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Your PayMongo payment of ${pack.priceLabel} was successful! +${pack.credits} Upload Credits have been added to your account.\n\nCurrent Balance: $currentCredits Upload Credits",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { purchasedPackSuccess = null },
                    colors = ButtonDefaults.buttonColors(containerColor = deepPurple)
                ) {
                    Text("Done", color = Color.White)
                }
            }
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("About Upload Credits", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Each sheet music scan or image/PDF conversion uses 1 Upload Credit.\n\n• New accounts start with 3 free Upload Credits.\n• Purchased Upload Credits never expire.\n• VoxSight Premium includes +50 Upload Credits plus cloud backup for your sheets."
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it", color = deepPurple, fontWeight = FontWeight.Bold)
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
                    Text("OK", color = deepPurple, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Fallback Key Setup Dialog if backend has not configured PAYMONGO_SECRET_KEY yet
    if (showKeySetupDialog) {
        AlertDialog(
            onDismissRequest = { showKeySetupDialog = false },
            title = {
                Text("Configure PayMongo Key", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "The server hasn't configured PAYMONGO_SECRET_KEY yet. Paste your PayMongo Secret Key (e.g. sk_test_...) below to test checkout:",
                        fontSize = 13.sp,
                        color = sectionLabelColor
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
                            selectedPackForCheckout?.let { startPackCheckout(it) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = deepPurple)
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

@Composable
private fun CreditPackRowCard(
    pack: CreditPackOption,
    isLoading: Boolean,
    cardBorderColor: Color,
    pricePillBg: Color,
    pricePillBorder: Color,
    deepPurple: Color,
    textDark: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, cardBorderColor, RoundedCornerShape(18.dp))
            .clickable(enabled = !isLoading) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CreditCoinIllustration(
            tier = pack.coinTier,
            modifier = Modifier.size(54.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = pack.title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = textDark,
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .width(92.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(pricePillBg)
                .border(1.dp, pricePillBorder, RoundedCornerShape(11.dp))
                .clickable(enabled = !isLoading) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = deepPurple,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = pack.priceLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = deepPurple
                )
            }
        }
    }
}

private var cachedUploadCreditBitmap: ImageBitmap? = null

/**
 * Safely decodes and renders `R.drawable.upload_credits` without triggering density-upscaling OOMs.
 */
@Composable
fun UploadCreditCoinImage(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    if (LocalInspectionMode.current) {
        Image(
            painter = painterResource(id = R.drawable.upload_credits),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val imageBitmap = remember {
        cachedUploadCreditBitmap ?: run {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inSampleSize = 4
            }
            BitmapFactory.decodeResource(context.resources, R.drawable.upload_credits, options)
                ?.asImageBitmap()
                ?.also { cachedUploadCreditBitmap = it }
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
    }
}

/**
 * Renders the 4 tiers of upload credit coins using `R.drawable.upload_credits`.
 */
@Composable
private fun CreditCoinIllustration(
    tier: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (tier) {
            1 -> {
                // Single large centered upload_credits coin
                UploadCreditCoinImage(
                    contentDescription = "10 Upload Credits",
                    modifier = Modifier
                        .size(46.dp)
                        .align(Alignment.Center)
                )
            }
            2 -> {
                // Two overlapping upload_credits coins
                UploadCreditCoinImage(
                    modifier = Modifier
                        .size(35.dp)
                        .offset(x = 2.dp, y = 7.dp)
                )
                UploadCreditCoinImage(
                    contentDescription = "30 Upload Credits",
                    modifier = Modifier
                        .size(37.dp)
                        .offset(x = 15.dp, y = 12.dp)
                )
            }
            3 -> {
                // Three diagonal cascading upload_credits coins
                UploadCreditCoinImage(
                    modifier = Modifier
                        .size(30.dp)
                        .offset(x = 2.dp, y = 20.dp)
                )
                UploadCreditCoinImage(
                    modifier = Modifier
                        .size(31.dp)
                        .offset(x = 11.dp, y = 12.dp)
                )
                UploadCreditCoinImage(
                    contentDescription = "50 Upload Credits",
                    modifier = Modifier
                        .size(32.dp)
                        .offset(x = 20.dp, y = 4.dp)
                )
            }
            else -> {
                // Four clustered upload_credits coins (100 credits pile)
                UploadCreditCoinImage(
                    modifier = Modifier
                        .size(29.dp)
                        .offset(x = 2.dp, y = 15.dp)
                )
                UploadCreditCoinImage(
                    modifier = Modifier
                        .size(29.dp)
                        .offset(x = 12.dp, y = 22.dp)
                )
                UploadCreditCoinImage(
                    modifier = Modifier
                        .size(30.dp)
                        .offset(x = 22.dp, y = 17.dp)
                )
                UploadCreditCoinImage(
                    contentDescription = "100 Upload Credits",
                    modifier = Modifier
                        .size(32.dp)
                        .offset(x = 11.dp, y = 3.dp)
                )
            }
        }
    }
}

/**
 * Draws the solid golden-amber 3-point crown icon shown in the VoxSight Premium card.
 */
@Composable
private fun SolidGoldenCrownIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val crownPath = Path().apply {
            moveTo(w * 0.14f, h * 0.78f)
            lineTo(w * 0.06f, h * 0.30f)
            lineTo(w * 0.33f, h * 0.50f)
            lineTo(w * 0.50f, h * 0.20f)
            lineTo(w * 0.67f, h * 0.50f)
            lineTo(w * 0.94f, h * 0.30f)
            lineTo(w * 0.86f, h * 0.78f)
            close()
        }

        drawPath(
            path = crownPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFBBF24),
                    Color(0xFFF59E0B)
                )
            )
        )
        drawPath(
            path = crownPath,
            color = Color(0xFFF59E0B),
            style = Stroke(width = w * 0.04f, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ShopScreenPreview() {
    ShopScreen()
}
