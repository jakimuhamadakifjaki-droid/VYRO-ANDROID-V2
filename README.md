# VYRO — Android Native + Supabase

Starter project for a short-video social app. Built with Kotlin, Jetpack Compose, AndroidX Media3, and Supabase REST/Auth/Storage endpoints.

## Included
- Android native app: email/password sign-up and login, vertical feed, video playback, upload from gallery, likes, comments, follow, search, profile basics.
- Supabase SQL schema with RLS, storage policies, counters, and admin role.
- `admin/index.html`: lightweight admin dashboard using the signed-in admin's access token (never a service-role key).
- Termux notes.

## Important status
This is a starter implementation, not a production TikTok clone. Before public launch, add rate limiting, moderation queues, upload size limits, resumable uploads, video transcoding/HLS, CDN, abuse reporting workflow, automated tests, and privacy/terms pages.

## 1. Create Supabase project
1. Create a project at https://supabase.com.
2. Open SQL Editor and run `supabase/schema.sql`.
3. In Project Settings / API, copy Project URL and the **publishable** key (`sb_publishable_...`) or legacy anon key. Never use `service_role` / secret key in Android or the admin page.
4. In Storage, the SQL creates a public `videos` bucket for MVP playback. Consider private storage + signed URLs for restricted content.
5. In Authentication settings, configure email confirmation and site/redirect URLs as desired.

## 2. Configure Android
Open `app/src/main/java/com/vyro/app/SupabaseConfig.kt`.
Set `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`. The publishable/anon key is designed for client use only when RLS is enabled. Do not put a secret/service-role key here.

## 3. Build APK
Recommended: Android Studio on a computer or a cloud Android build environment. Building a full Android Gradle project directly on a phone via Termux can be difficult because Android SDK/Gradle compatibility varies.
- Open this folder in Android Studio.
- Allow Gradle sync.
- Build > Build APK(s).
- Install the generated APK on your own device.

Termux can be used to edit files and manage Git; it is not guaranteed to build Android APKs without a correctly installed Android SDK and compatible build tools.

## 4. Promote an admin
After a user registers and their profile row exists, run in Supabase SQL Editor (replace email):
```sql
update public.profiles
set role = 'admin'
where id = (
  select id from auth.users where email = 'admin@example.com'
);
```
Only do this from the trusted Supabase SQL Editor. Never expose a service-role key to the app.

## 5. Database and security
- RLS is enabled on all user-facing tables.
- Users can edit their own profile, create/delete their own videos, and manage their own likes/comments/follows/bookmarks.
- Admin-only moderation uses `profiles.role = 'admin'`.
- Storage object policies restrict upload/delete to a path starting with the authenticated user's UUID.
- Public video playback is public in this MVP. Don't upload private/sensitive videos to a public bucket.

## 6. Current implementation notes
- Video uploads use a straightforward HTTP upload and are suitable only for small/medium test videos. Large uploads need resumable uploads.
- A video row is created after successful Storage upload.
- Feed and search use Supabase REST.
- Like/comment/follow actions are included in the app code; user-facing errors are shown as toast/status text.
- Admin page expects an admin to sign in through the Android app first only if a token is manually entered; use Supabase Auth in a proper admin portal for production. The included page lets you paste the user's access token, which should be treated as a password and not shared.
