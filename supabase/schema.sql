-- VYRO Supabase schema. Run once in Supabase SQL Editor.
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique not null check (char_length(username) between 3 and 24),
  display_name text not null default '',
  bio text not null default '',
  avatar_url text,
  role text not null default 'user' check (role in ('user','admin')),
  created_at timestamptz not null default now()
);

create table if not exists public.videos (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  caption text not null default '' check (char_length(caption) <= 2200),
  video_url text not null,
  storage_path text not null,
  thumbnail_url text,
  status text not null default 'published' check (status in ('published','hidden','removed','review')),
  view_count bigint not null default 0 check (view_count >= 0),
  like_count bigint not null default 0 check (like_count >= 0),
  comment_count bigint not null default 0 check (comment_count >= 0),
  created_at timestamptz not null default now()
);

create table if not exists public.likes (
  id uuid primary key default gen_random_uuid(),
  video_id uuid not null references public.videos(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique(video_id, user_id)
);

create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(),
  video_id uuid not null references public.videos(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 1000),
  created_at timestamptz not null default now()
);

create table if not exists public.follows (
  follower_id uuid not null references public.profiles(id) on delete cascade,
  following_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (follower_id, following_id),
  check (follower_id <> following_id)
);

create table if not exists public.bookmarks (
  user_id uuid not null references public.profiles(id) on delete cascade,
  video_id uuid not null references public.videos(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, video_id)
);

create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  video_id uuid references public.videos(id) on delete cascade,
  reported_user_id uuid references public.profiles(id) on delete cascade,
  reason text not null check (char_length(reason) between 3 and 500),
  status text not null default 'open' check (status in ('open','reviewing','resolved','dismissed')),
  created_at timestamptz not null default now()
);

create index if not exists videos_created_at_idx on public.videos(created_at desc);
create index if not exists videos_status_created_idx on public.videos(status, created_at desc);
create index if not exists videos_user_idx on public.videos(user_id);
create index if not exists comments_video_created_idx on public.comments(video_id, created_at desc);
create index if not exists likes_video_idx on public.likes(video_id);
create index if not exists follows_following_idx on public.follows(following_id);

-- Auto-create profile after Auth registration. Username gets a unique suffix if email prefix is taken.
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public
as $$
declare
  base_name text;
  candidate text;
begin
  base_name := regexp_replace(lower(split_part(coalesce(new.email, 'user'), '@', 1)), '[^a-z0-9_]', '', 'g');
  if length(base_name) < 3 then base_name := 'user'; end if;
  candidate := left(base_name, 16) || '_' || substr(replace(new.id::text, '-', ''), 1, 6);
  insert into public.profiles(id, username, display_name)
  values (new.id, candidate, coalesce(split_part(new.email, '@', 1), 'Pengguna'))
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_vyro on auth.users;
create trigger on_auth_user_created_vyro
after insert on auth.users
for each row execute procedure public.handle_new_user();

create or replace function public.is_vyro_admin()
returns boolean language sql stable security definer set search_path = public
as $$
  select exists (select 1 from public.profiles where id = auth.uid() and role = 'admin');
$$;

alter table public.profiles enable row level security;
alter table public.videos enable row level security;
alter table public.likes enable row level security;
alter table public.comments enable row level security;
alter table public.follows enable row level security;
alter table public.bookmarks enable row level security;
alter table public.reports enable row level security;

-- Profiles: public read; only owner edits own profile. Role cannot be changed by normal users.
drop policy if exists "profiles readable" on public.profiles;
create policy "profiles readable" on public.profiles for select using (true);
drop policy if exists "owner update own profile" on public.profiles;
create policy "owner update own profile" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id and role = 'user');
drop policy if exists "owner insert own profile" on public.profiles;
create policy "owner insert own profile" on public.profiles for insert with check (auth.uid() = id and role = 'user');

-- Videos: public can read published; owners manage own; admins moderate.
drop policy if exists "published videos readable" on public.videos;
create policy "published videos readable" on public.videos for select using (status = 'published' or auth.uid() = user_id or public.is_vyro_admin());
drop policy if exists "authenticated create own videos" on public.videos;
create policy "authenticated create own videos" on public.videos for insert to authenticated with check (auth.uid() = user_id and status in ('published','review'));
drop policy if exists "owner or admin update videos" on public.videos;
create policy "owner or admin update videos" on public.videos for update to authenticated using (auth.uid() = user_id or public.is_vyro_admin()) with check (auth.uid() = user_id or public.is_vyro_admin());
drop policy if exists "owner or admin delete videos" on public.videos;
create policy "owner or admin delete videos" on public.videos for delete to authenticated using (auth.uid() = user_id or public.is_vyro_admin());

-- Likes
drop policy if exists "likes readable" on public.likes;
create policy "likes readable" on public.likes for select using (true);
drop policy if exists "users create own likes" on public.likes;
create policy "users create own likes" on public.likes for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists "users delete own likes" on public.likes;
create policy "users delete own likes" on public.likes for delete to authenticated using (auth.uid() = user_id or public.is_vyro_admin());

-- Comments
drop policy if exists "comments readable" on public.comments;
create policy "comments readable" on public.comments for select using (true);
drop policy if exists "users add own comments" on public.comments;
create policy "users add own comments" on public.comments for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists "owner or admin delete comments" on public.comments;
create policy "owner or admin delete comments" on public.comments for delete to authenticated using (auth.uid() = user_id or public.is_vyro_admin());

-- Follows
drop policy if exists "follows readable" on public.follows;
create policy "follows readable" on public.follows for select using (true);
drop policy if exists "users follow as themselves" on public.follows;
create policy "users follow as themselves" on public.follows for insert to authenticated with check (auth.uid() = follower_id and follower_id <> following_id);
drop policy if exists "users unfollow themselves" on public.follows;
create policy "users unfollow themselves" on public.follows for delete to authenticated using (auth.uid() = follower_id or public.is_vyro_admin());

-- Bookmarks
drop policy if exists "users read own bookmarks" on public.bookmarks;
create policy "users read own bookmarks" on public.bookmarks for select to authenticated using (auth.uid() = user_id);
drop policy if exists "users create own bookmarks" on public.bookmarks;
create policy "users create own bookmarks" on public.bookmarks for insert to authenticated with check (auth.uid() = user_id);
drop policy if exists "users delete own bookmarks" on public.bookmarks;
create policy "users delete own bookmarks" on public.bookmarks for delete to authenticated using (auth.uid() = user_id);

-- Reports: reporter creates own; reporter and admin can read; admin can update.
drop policy if exists "reporter or admin reads reports" on public.reports;
create policy "reporter or admin reads reports" on public.reports for select to authenticated using (auth.uid() = reporter_id or public.is_vyro_admin());
drop policy if exists "users create own reports" on public.reports;
create policy "users create own reports" on public.reports for insert to authenticated with check (auth.uid() = reporter_id);
drop policy if exists "admin updates reports" on public.reports;
create policy "admin updates reports" on public.reports for update to authenticated using (public.is_vyro_admin()) with check (public.is_vyro_admin());

-- Public bucket for MVP. Public read means anyone with URL can watch videos.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('videos', 'videos', true, 52428800, array['video/mp4','video/webm','video/quicktime'])
on conflict (id) do update set public = excluded.public, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "public read vyro videos" on storage.objects;
create policy "public read vyro videos" on storage.objects for select using (bucket_id = 'videos');
drop policy if exists "users upload own vyro videos" on storage.objects;
create policy "users upload own vyro videos" on storage.objects for insert to authenticated
with check (bucket_id = 'videos' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "users delete own vyro videos" on storage.objects;
create policy "users delete own vyro videos" on storage.objects for delete to authenticated
using (bucket_id = 'videos' and ((storage.foldername(name))[1] = auth.uid()::text or public.is_vyro_admin()));

-- NOTE: counters are kept simple for this MVP. Production should update counts via trusted database triggers/RPC,
-- not let clients write arbitrary count values.
grant usage on schema public to anon, authenticated;
grant select on public.profiles, public.videos, public.likes, public.comments, public.follows to anon, authenticated;
grant insert, update on public.profiles to authenticated;
grant insert, update, delete on public.videos to authenticated;
grant insert, delete on public.likes, public.follows to authenticated;
grant select, insert, delete on public.comments to authenticated;
grant select, insert, delete on public.bookmarks to authenticated;
grant select, insert, update on public.reports to authenticated;
