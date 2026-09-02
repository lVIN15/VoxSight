package com.cit.kaido.voxsight.network

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    /**
     * Static, immutable Production Cloud Server URL.
     */
    const val BASE_URL: String = "https://voxsight.onrender.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // Handles Render cloud cold-starts (~45s)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val omrService: OmrService by lazy {
        retrofit.create(OmrService::class.java)
    }

    /** Legacy compatibility hook (no-op since BASE_URL is static) */
    fun init(context: Context) {
        // Static server configuration - no local initialization needed
    }

    /** Returns the static cloud server URL */
    fun getBaseUrl(context: Context? = null): String = BASE_URL
}


