# VoxSight Supabase Backend Integration Guide

## Overview

This branch (`feat/supabase-integration`) implements direct cloud authentication and user management with **Supabase** while strictly preserving the 3-tier architecture:

```
[ Android Mobile App ]  --->  [ Spring Boot Backend ]  --->  [ Supabase REST API ]
  (Port 8080 or URL)            (Proxy / Auth Layer)          (Cloud Database)
```

- **Security Principle**: The mobile client **never** interacts directly with Supabase, preventing API keys or service credentials from leaking into client APKs.
- **Connection Mode**: Connects via Supabase's PostgREST API using `SUPABASE_SECRET_KEY` over HTTPS. **No direct PostgreSQL port 5432 or database password is required.**

---

## Setup for Development

1. In the `voxsight/` directory, copy `.env.example` to `.env`:
   ```bash
   cp voxsight/.env.example voxsight/.env
   ```
2. Populate `.env` with your Supabase credentials:
   ```env
   SUPABASE_URL=https://<your-project-id>.supabase.co
   SUPABASE_PUBLISHABLE_KEY=<your-publishable-key>
   SUPABASE_SECRET_KEY=<your-secret-key>
   SUPABASE_JWKS_URL=https://<your-project-id>.supabase.co/auth/v1/.well-known/jwks.json
   ```
3. Run the Spring Boot backend:
   ```bash
   cd voxsight
   mvn spring-boot:run
   ```
   The `.env` file is automatically loaded into system properties on startup by `VoxsightApplication.java`.

---

## Implemented Features

### 1. `SupabaseClientService.java`
Located at `voxsight/src/main/java/edu/cit/capstone/voxsight/service/SupabaseClientService.java`:
- `findByIdentifier(identifier)`: Look up by either email or username.
- `existsByEmail(email)` / `existsByUsername(username)`: Pre-registration validation.
- `register(username, email, password)`: Hashes passwords with SHA-256 and inserts into Supabase `User` table.
- `updatePassword(id, newPassword)`: Patches `password_hash` in Supabase.
- `updateProfile(id, username, email)`: Patches username and email in Supabase.
- `updateLastLogin(id)`: Updates `last_login` timestamp asynchronously upon login.

### 2. `UserService.java`
Located at `voxsight/src/main/java/edu/cit/capstone/voxsight/service/UserService.java`:
- Connects Spring Boot auth workflows with `SupabaseClientService`.
- Supports dual password verification (existing Supabase SHA-256 hashes + standard BCrypt).
- Synchronizes with local JPA entities so user settings (`defaultVoicePart`, `soundfontTone`, `pitchToleranceCents`, etc.) and numeric user IDs remain fully compatible with existing mobile code.
- Gracefully falls back to local database if Supabase credentials are not provided.

### 3. Mobile Client
Located at `mobile/app/src/main/java/com/cit/kaido/voxsight/network/`:
- `AuthService.kt` (`/api/auth/register`, `/api/auth/login`)
- `UserService.kt` (`/api/user/profile`, `/api/user/settings`, `/api/user/change-password`)
- Complete with Settings & Profile UI integration.

---

## Suggested Next Steps for Teammates
1. **Cloud Deployment Configuration**: Add `SUPABASE_URL` and `SUPABASE_SECRET_KEY` to Railway/production environment variables when ready for release.
2. **Cloud Settings Table**: Optionally create a `UserSettings` table in Supabase if you wish to persist soundfont and vocal part settings directly in the cloud database rather than the local backend cache.
3. **Score Metadata Cloud Sync**: Optionally link score upload metadata to Supabase user IDs.
