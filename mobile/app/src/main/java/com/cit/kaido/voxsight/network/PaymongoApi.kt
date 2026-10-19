package com.cit.kaido.voxsight.network

import android.content.Context
import android.util.Base64
import com.cit.kaido.voxsight.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class CreateCheckoutSessionRequest(
    val username: String? = null,
    val email: String? = null,
    val planName: String = "VoxSight Premium (Monthly)",
    val amountCentavos: Int = 8500,
    val successUrl: String = PaymongoClient.DEFAULT_SUCCESS_URL,
    val cancelUrl: String = PaymongoClient.DEFAULT_CANCEL_URL
)

data class CreateCheckoutSessionResponse(
    val success: Boolean = false,
    val sessionId: String? = null,
    val checkoutUrl: String? = null,
    val successUrl: String? = null,
    val cancelUrl: String? = null,
    val error: String? = null
)

data class CheckoutSessionStatusResponse(
    val success: Boolean = false,
    val sessionId: String? = null,
    val paid: Boolean = false,
    val paymentIntentStatus: String? = null,
    val error: String? = null
)

interface PaymongoApi {
    @POST("api/payments/create-checkout-session")
    suspend fun createCheckoutSession(
        @Body request: CreateCheckoutSessionRequest
    ): Response<CreateCheckoutSessionResponse>

    @GET("api/payments/checkout-session/{sessionId}")
    suspend fun getCheckoutSessionStatus(
        @Path("sessionId") sessionId: String
    ): Response<CheckoutSessionStatusResponse>
}

object PaymongoClient {
    const val DEFAULT_SUCCESS_URL = "https://voxsight.app/payment/success"
    const val DEFAULT_CANCEL_URL = "https://voxsight.app/payment/cancel"
    private const val PAYMONGO_API_URL = "https://api.paymongo.com/v1/checkout_sessions"
    private const val PREF_FALLBACK_KEY = "paymongo_secret_key_override"

    private val directHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun getConfiguredSecretKey(context: Context): String {
        val prefs = context.getSharedPreferences("voxsight_prefs", Context.MODE_PRIVATE)
        val overrideKey = prefs.getString(PREF_FALLBACK_KEY, null)?.trim().orEmpty()
        if (overrideKey.isNotEmpty()) return overrideKey
        return BuildConfig.PAYMONGO_SECRET_KEY.trim()
    }

    fun saveSecretKeyOverride(context: Context, secretKey: String) {
        val prefs = context.getSharedPreferences("voxsight_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_FALLBACK_KEY, secretKey.trim()).apply()
    }

    /**
     * Creates a PayMongo Checkout Session via the Spring Boot backend first.
     * If the backend endpoint is not yet deployed or does not have PAYMONGO_SECRET_KEY configured,
     * and a fallback secret key is available locally, it falls back to calling PayMongo API directly.
     */
    suspend fun createCheckoutSession(
        context: Context,
        request: CreateCheckoutSessionRequest
    ): CreateCheckoutSessionResponse = withContext(Dispatchers.IO) {
        var backendError: String? = null
        try {
            val response = ApiClient.paymongoApi.createCheckoutSession(request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && body.success && !body.checkoutUrl.isNullOrBlank()) {
                    return@withContext body
                }
                backendError = body?.error ?: "Invalid response from payment server."
            } else {
                val errStr = response.errorBody()?.string()
                backendError = if (!errStr.isNullOrBlank()) {
                    try {
                        gson.fromJson(errStr, CreateCheckoutSessionResponse::class.java)?.error ?: "Server HTTP ${response.code()}"
                    } catch (_: Exception) {
                        "Server HTTP ${response.code()}"
                    }
                } else {
                    "Server HTTP ${response.code()}"
                }
            }
        } catch (e: Exception) {
            backendError = e.message ?: "Unable to connect to backend payment server."
        }

        // Check if we have a local/fallback PayMongo secret key for direct checkout creation
        val fallbackKey = getConfiguredSecretKey(context)
        if (fallbackKey.isNotBlank()) {
            return@withContext createDirectPaymongoSession(fallbackKey, request)
        }

        CreateCheckoutSessionResponse(
            success = false,
            error = backendError ?: "PayMongo is not configured yet. Set PAYMONGO_SECRET_KEY on the backend or in local.properties."
        )
    }

    /**
     * Verifies whether a PayMongo Checkout Session has been paid.
     */
    suspend fun verifyCheckoutSession(
        context: Context,
        sessionId: String
    ): CheckoutSessionStatusResponse = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.paymongoApi.getCheckoutSessionStatus(sessionId)
            if (response.isSuccessful && response.body()?.success == true) {
                return@withContext response.body()!!
            }
        } catch (_: Exception) {
            // Fall through to direct check if available
        }

        val fallbackKey = getConfiguredSecretKey(context)
        if (fallbackKey.isNotBlank()) {
            return@withContext verifyDirectPaymongoSession(fallbackKey, sessionId)
        }

        CheckoutSessionStatusResponse(
            success = false,
            sessionId = sessionId,
            paid = false,
            error = "Could not verify payment session status."
        )
    }

    private fun buildBasicAuth(secretKey: String): String {
        val raw = "${secretKey.trim()}:"
        val encoded = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $encoded"
    }

    private fun createDirectPaymongoSession(
        secretKey: String,
        req: CreateCheckoutSessionRequest
    ): CreateCheckoutSessionResponse {
        val methodCandidates = listOf(
            listOf("gcash", "paymaya", "card", "grab_pay", "qrph"),
            listOf("gcash", "paymaya", "card"),
            listOf("gcash", "card"),
            listOf("card")
        )

        var lastError = "Failed to create PayMongo checkout session."

        for (methods in methodCandidates) {
            try {
                val payloadJson = buildDirectPayload(req, methods)
                val httpReq = Request.Builder()
                    .url(PAYMONGO_API_URL)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", buildBasicAuth(secretKey))
                    .post(payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                directHttpClient.newCall(httpReq).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val root = gson.fromJson(bodyStr, JsonObject::class.java)
                        val data = root.getAsJsonObject("data")
                        val attributes = data.getAsJsonObject("attributes")
                        val sessionId = data.get("id")?.asString.orEmpty()
                        val checkoutUrl = attributes.get("checkout_url")?.asString.orEmpty()

                        return CreateCheckoutSessionResponse(
                            success = true,
                            sessionId = sessionId,
                            checkoutUrl = checkoutUrl,
                            successUrl = req.successUrl,
                            cancelUrl = req.cancelUrl
                        )
                    } else {
                        lastError = extractPaymongoError(bodyStr, resp.code)
                        if (!bodyStr.contains("payment_method_types")) {
                            return CreateCheckoutSessionResponse(success = false, error = lastError)
                        }
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Network error contacting PayMongo."
                break
            }
        }

        return CreateCheckoutSessionResponse(success = false, error = lastError)
    }

    private fun verifyDirectPaymongoSession(
        secretKey: String,
        sessionId: String
    ): CheckoutSessionStatusResponse {
        return try {
            val httpReq = Request.Builder()
                .url("$PAYMONGO_API_URL/$sessionId")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", buildBasicAuth(secretKey))
                .get()
                .build()

            directHttpClient.newCall(httpReq).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return CheckoutSessionStatusResponse(
                        success = false,
                        sessionId = sessionId,
                        paid = false,
                        error = extractPaymongoError(bodyStr, resp.code)
                    )
                }

                val root = gson.fromJson(bodyStr, JsonObject::class.java)
                val attributes = root.getAsJsonObject("data")?.getAsJsonObject("attributes")
                val paymentIntentStatus = attributes
                    ?.getAsJsonObject("payment_intent")
                    ?.getAsJsonObject("attributes")
                    ?.get("status")?.asString.orEmpty()

                var isPaid = paymentIntentStatus.equals("succeeded", ignoreCase = true)
                val payments = attributes?.getAsJsonArray("payments")
                if (!isPaid && payments != null) {
                    for (elem in payments) {
                        val pStatus = elem.asJsonObject
                            ?.getAsJsonObject("attributes")
                            ?.get("status")?.asString.orEmpty()
                        if (pStatus.equals("paid", ignoreCase = true) || pStatus.equals("succeeded", ignoreCase = true)) {
                            isPaid = true
                            break
                        }
                    }
                }

                CheckoutSessionStatusResponse(
                    success = true,
                    sessionId = sessionId,
                    paid = isPaid,
                    paymentIntentStatus = paymentIntentStatus
                )
            }
        } catch (e: Exception) {
            CheckoutSessionStatusResponse(
                success = false,
                sessionId = sessionId,
                paid = false,
                error = e.message
            )
        }
    }

    private fun buildDirectPayload(req: CreateCheckoutSessionRequest, methods: List<String>): String {
        val attributes = JsonObject().apply {
            addProperty("send_email_receipt", true)
            addProperty("show_description", true)
            addProperty("show_line_items", true)
            addProperty("description", "VoxSight Premium Subscription - Elevate your choral practice sessions")
            addProperty("success_url", req.successUrl)
            addProperty("cancel_url", req.cancelUrl)

            val hasName = !req.username.isNullOrBlank()
            val hasEmail = !req.email.isNullOrBlank() && req.email.contains("@")
            if (hasName || hasEmail) {
                val billing = JsonObject()
                if (hasName) billing.addProperty("name", req.username!!.trim())
                if (hasEmail) billing.addProperty("email", req.email!!.trim())
                add("billing", billing)
            }

            val lineItems = JsonArray()
            val item = JsonObject().apply {
                addProperty("currency", "PHP")
                addProperty("amount", req.amountCentavos)
                addProperty("name", req.planName)
                addProperty("quantity", 1)
                addProperty("description", "Increased sheet music uploads, upgraded cloud storage, and sheet downloads.")
            }
            lineItems.add(item)
            add("line_items", lineItems)

            val methodsArr = JsonArray()
            methods.forEach { methodsArr.add(it) }
            add("payment_method_types", methodsArr)
        }

        val data = JsonObject().apply {
            add("attributes", attributes)
        }
        val root = JsonObject().apply {
            add("data", data)
        }
        return gson.toJson(root)
    }

    private fun extractPaymongoError(body: String, statusCode: Int): String {
        if (body.isBlank()) return "PayMongo HTTP $statusCode"
        return try {
            val root = gson.fromJson(body, JsonObject::class.java)
            val errors = root.getAsJsonArray("errors")
            if (errors != null && errors.size() > 0) {
                errors[0].asJsonObject.get("detail")?.asString ?: "PayMongo HTTP $statusCode"
            } else {
                "PayMongo HTTP $statusCode"
            }
        } catch (_: Exception) {
            "PayMongo HTTP $statusCode"
        }
    }
}

