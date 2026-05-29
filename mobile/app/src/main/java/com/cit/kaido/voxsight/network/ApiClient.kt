package com.cit.kaido.voxsight.network

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val PREFS_NAME = "voxsight_prefs"
    private const val KEY_BASE_URL = "backend_base_url"
    private const val DEFAULT_BASE_URL = "http://192.168.1.3:8080/"

    private var activeUrl = DEFAULT_BASE_URL
    private var cachedRetrofit: Retrofit? = null
    private var cachedService: OmrService? = null

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(900, TimeUnit.SECONDS) // Audiveris takes time
        .readTimeout(900, TimeUnit.SECONDS)
        .writeTimeout(900, TimeUnit.SECONDS)
        .build()

    /**
     * Initializes the client with a stored URL from SharedPreferences
     */
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        updateBaseUrl(context, storedUrl)
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
                formattedUrl = "http://$formattedUrl"
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
                }
                return cachedService!!
            }
        }
}
