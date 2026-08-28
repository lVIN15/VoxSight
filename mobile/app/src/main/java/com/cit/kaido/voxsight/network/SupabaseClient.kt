package com.cit.kaido.voxsight.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.android.Android

object Supabase {
    
    // TODO: Replace with your actual Supabase URL and Anon Key
    private const val SUPABASE_URL = "https://zffkuhdslokigfhawwrd.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpmZmt1aGRzbG9raWdmaGF3d3JkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODcwNDEwNDUsImV4cCI6MjEwMjYxNzA0NX0.1WU3-hZZLQEFCuDp2gHZrxq8kXP6m7ESn6eoy2e4WDs"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            httpEngine = Android.create()
            install(Auth)
            install(Postgrest)
        }
    }
}
