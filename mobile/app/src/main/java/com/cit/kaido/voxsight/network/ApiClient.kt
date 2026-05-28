package com.cit.kaido.voxsight.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // 192.168.1.4 is the laptop's IP address on the Wi-Fi network
    private const val BASE_URL = "http://10.91.14.27:8080/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(900, TimeUnit.SECONDS) // Audiveris takes time
        .readTimeout(900, TimeUnit.SECONDS)
        .writeTimeout(900, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val omrService: OmrService by lazy {
        retrofit.create(OmrService::class.java)
    }
}
