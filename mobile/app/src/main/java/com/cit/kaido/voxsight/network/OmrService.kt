package com.cit.kaido.voxsight.network

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

interface OmrService {
    @Multipart
    @POST("api/convert")
    suspend fun convertScore(@Part file: MultipartBody.Part): OmrResponse

    @GET
    suspend fun downloadXml(@Url url: String): ResponseBody
}
