package com.cit.kaido.voxsight.network

import android.content.Context
import com.cit.kaido.voxsight.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val PREFS_NAME = "voxsight_prefs"
    private const val KEY_BASE_URL = "backend_base_url"
    private const val KEY_DEBUG_MANUAL_URL = "debug_manual_base_url"

    const val PRODUCTION_BASE_URL: String = "https://voxsight-production.up.railway.app/"
    const val EMULATOR_BASE_URL: String = "http://10.0.2.2:8080/"
    val DEFAULT_BASE_URL: String = BuildConfig.BASE_URL

    private var activeUrl = DEFAULT_BASE_URL
    private var cachedRetrofit: Retrofit? = null
    private var cachedService: OmrService? = null
    private var cachedVersionApi: AppVersionApi? = null
    private var cachedPaymongoApi: PaymongoApi? = null

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("X-App-Version-Code", BuildConfig.VERSION_CODE.toString())
                .header("X-App-Version", BuildConfig.VERSION_NAME)
            val response = chain.proceed(requestBuilder.build())
            if (response.code == 426) {
                com.cit.kaido.voxsight.ui.update.AppUpdateManager.triggerForceUpdate()
            }
            response
        }
        .connectTimeout(60, TimeUnit.SECONDS) // Railway connection handling
        .readTimeout(180, TimeUnit.SECONDS) // 3 min timeout for OMR conversion on cloud
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    /**
     * Initializes the client with a stored URL from SharedPreferences
     */
    private fun isRunningOnEmulator(): Boolean {
        return try {
            val fp = android.os.Build.FINGERPRINT ?: ""
            val model = android.os.Build.MODEL ?: ""
            val product = android.os.Build.PRODUCT ?: ""
            val brand = android.os.Build.BRAND ?: ""
            val device = android.os.Build.DEVICE ?: ""
            fp.startsWith("generic") || fp.startsWith("unknown") ||
                model.contains("google_sdk") || model.contains("Emulator") || model.contains("Android SDK built for") || model.contains("sdk_gphone") ||
                product.contains("sdk") || product.contains("emulator") ||
                (brand.startsWith("generic") && device.startsWith("generic"))
        } catch (_: Throwable) {
            false
        }
    }

    private fun isLocalUrl(url: String): Boolean {
        return url.contains("10.0.2.2") || url.contains("192.168.") || url.contains("localhost") || url.contains("127.0.0.1")
    }

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var storedUrl = prefs.getString(KEY_BASE_URL, null)

        if (!BuildConfig.DEBUG) {
            val candidate = storedUrl ?: PRODUCTION_BASE_URL
            storedUrl = if (isLocalUrl(candidate) || candidate.contains("onrender.com")) {
                PRODUCTION_BASE_URL
            } else {
                candidate
            }
        } else {
            val manualDebugUrl = prefs.getString(KEY_DEBUG_MANUAL_URL, null)?.takeIf { it.isNotBlank() }
            storedUrl = when {
                manualDebugUrl != null -> manualDebugUrl
                isLocalUrl(DEFAULT_BASE_URL) && isRunningOnEmulator() -> EMULATOR_BASE_URL
                else -> DEFAULT_BASE_URL
            }
        }
        updateBaseUrl(context, storedUrl)
    }

    fun setDebugServerOverride(context: Context, newUrl: String?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (newUrl.isNullOrBlank()) {
            prefs.edit().remove(KEY_DEBUG_MANUAL_URL).apply()
            init(context)
        } else {
            updateBaseUrl(context, newUrl)
            prefs.edit().putString(KEY_DEBUG_MANUAL_URL, activeUrl).apply()
        }
    }

    fun getBaseUrl(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun updateBaseUrl(context: Context, newUrl: String) {
        var formattedUrl = newUrl.trim()
        if (formattedUrl.isNotEmpty()) {
            if (!formattedUrl.endsWith("/")) {
                formattedUrl += "/"
            }
            if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
                formattedUrl = if (formattedUrl.contains("up.railway.app") || formattedUrl.contains("onrender.com") || formattedUrl.contains(".com") || formattedUrl.contains(".io")) {
                    "https://$formattedUrl"
                } else {
                    "http://$formattedUrl"
                }
            }
        } else {
            formattedUrl = DEFAULT_BASE_URL
        }
        
        // Save to preferences
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BASE_URL, formattedUrl).apply()

        synchronized(this) {
            if (activeUrl != formattedUrl || cachedRetrofit == null) {
                activeUrl = formattedUrl
                cachedRetrofit = Retrofit.Builder()
                    .baseUrl(activeUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                cachedService = cachedRetrofit!!.create(OmrService::class.java)
                cachedVersionApi = cachedRetrofit!!.create(AppVersionApi::class.java)
                cachedPaymongoApi = cachedRetrofit!!.create(PaymongoApi::class.java)
            }
        }
    }

    val omrService: OmrService
        get() {
            synchronized(this) {
                if (cachedService == null) {
                    cachedRetrofit = Retrofit.Builder()
                        .baseUrl(activeUrl)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                    cachedService = cachedRetrofit!!.create(OmrService::class.java)
                    cachedVersionApi = cachedRetrofit!!.create(AppVersionApi::class.java)
                    cachedPaymongoApi = cachedRetrofit!!.create(PaymongoApi::class.java)
                }
                return cachedService!!
            }
        }

    val versionApi: AppVersionApi
        get() {
            synchronized(this) {
                if (cachedVersionApi == null) {
                    cachedRetrofit = Retrofit.Builder()
                        .baseUrl(activeUrl)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                    cachedService = cachedRetrofit!!.create(OmrService::class.java)
                    cachedVersionApi = cachedRetrofit!!.create(AppVersionApi::class.java)
                    cachedPaymongoApi = cachedRetrofit!!.create(PaymongoApi::class.java)
                }
                return cachedVersionApi!!
            }
        }

    val paymongoApi: PaymongoApi
        get() {
            synchronized(this) {
                if (cachedPaymongoApi == null) {
                    cachedRetrofit = Retrofit.Builder()
                        .baseUrl(activeUrl)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                    cachedService = cachedRetrofit!!.create(OmrService::class.java)
                    cachedVersionApi = cachedRetrofit!!.create(AppVersionApi::class.java)
                    cachedPaymongoApi = cachedRetrofit!!.create(PaymongoApi::class.java)
                }
                return cachedPaymongoApi!!
            }
        }
}

