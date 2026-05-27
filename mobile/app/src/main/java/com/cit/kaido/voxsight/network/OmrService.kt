package com.cit.kaido.voxsight.network

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

interface OmrService {
    /** Legacy endpoint: Audiveris only, returns URL to MusicXML file */
    @Multipart
    @POST("api/convert")
    suspend fun convertScore(@Part file: MultipartBody.Part): OmrResponse

    /**
     * Enhanced endpoint: Audiveris + SATB Analysis Pipeline.
     * Returns raw MusicXML + score_metadata + events[] (ORDER-FROZEN).
     * Architecture Contract v3.7.
     */
    @Multipart
    @POST("api/analyze")
    suspend fun analyzeScore(@Part file: MultipartBody.Part): OmrAnalysisResponse

    @GET
    suspend fun downloadXml(@Url url: String): ResponseBody
}

