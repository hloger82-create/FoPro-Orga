-- FoPro-Orga: initiales Supabase-Schema (Postgres + Row Level Security)
-- Region: Frankfurt (eu-central-1). eBird-API-Keys werden NICHT serverseitig gespeichert,
-- sondern lokal auf dem Gerät (Opt-in pro Nutzer).

create extension if not exists "uuid-ossp";

-- ---------- Profile ----------
create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text unique not null,
    display_name text not null,
    bio text,
    avatar_url text,
    homepage text,
    socials jsonb not null default '{}'::jsonb,
    skill_tags text[] not null default '{}',
    member_since date,
    show_homepage_publicly boolean not null default false,
    show_socials_publicly boolean not null default false,
    ebird_enabled boolean not null default false,
    created_at timestamptz not null default now()
);

-- ---------- Profile ----------
-- Profil-Zeile automatisch beim Registrieren anlegen (auth.users -> profiles)
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer as $$
begin
    insert into public.profiles (id, username, display_name)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'username', split_part(new.email, '@', 1)),
        coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1))
    );
    return new;
end; $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------- Projekte & Rollen ----------
create table if not exists public.projects (
    id uuid primary key default uuid_generate_v4(),
    name text not null,
    description text,
    creator_id uuid not null references public.profiles(id),
    region_polygon jsonb not null default '[]'::jsonb,
    ebird_enabled boolean not null default false,
    created_at timestamptz not null default now()
);

create table if not exists public.project_members (
    project_id uuid not null references public.projects(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    role text not null check (role in ('admin','member')),
    primary key (project_id, user_id)
);
-- Projekt-Ersteller ist automatisch Admin
create or replace function public.handle_new_project()
returns trigger language plpgsql security definer as $$
begin
    insert into public.project_members (project_id, user_id, role)
    values (new.id, new.creator_id, 'admin');
    return new;
end; $$;
drop trigger if exists on_project_created on public.projects;
create trigger on_project_created after insert on public.projects
    for each row execute function public.handle_new_project();

-- Allgemeiner Chat-Kanal pro Projekt
create or replace function public.handle_new_project_channel()
returns trigger language plpgsql security definer as $$
begin
    insert into public.chat_channels (project_id, name, is_general)
    values (new.id, 'Allgemein', true);
    return new;
end; $$;
drop trigger if exists on_project_channel_created on public.projects;
create trigger on_project_channel_created after insert on public.projects
    for each row execute function public.handle_new_project_channel();

-- ---------- Areas, Foren, Pinnwand ----------
create table if not exists public.areas (
    id uuid primary key default uuid_generate_v4(),
    project_id uuid not null references public.projects(id) on delete cascade,
    name text not null,
    description text,
    created_at timestamptz not null default now()
);

create table if not exists public.area_moderators (
    area_id uuid not null references public.areas(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    primary key (area_id, user_id)
);

create table if not exists public.area_subscriptions (
    area_id uuid not null references public.areas(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    subscribed boolean not null default true,
    primary key (area_id, user_id)
);

create table if not exists public.forums (
    id uuid primary key default uuid_generate_v4(),
    area_id uuid not null references public.areas(id) on delete cascade,
    name text not null,
    created_by uuid not null references public.profiles(id),
    created_at timestamptz not null default now()
);

-- Posts decken Pinnwand (forum_id null) und Foren-Themen ab
create table if not exists public.posts (
    id uuid primary key default uuid_generate_v4(),
    area_id uuid not null references public.areas(id) on delete cascade,
    forum_id uuid references public.forums(id) on delete cascade,
    author_id uuid not null references public.profiles(id),
    title text not null,
    body text not null default '',
    image_url text,
    pinned boolean not null default false,
    created_at timestamptz not null default now()
);

-- Threads in Foren (Ersteller gibt Titel vor und verfasst ersten Beitrag)
create table if not exists public.threads (
    id uuid primary key default uuid_generate_v4(),
    forum_id uuid not null references public.forums(id) on delete cascade,
    title text not null,
    first_post text not null default '',
    created_by uuid not null references public.profiles(id),
    created_at timestamptz not null default now()
);

-- Kommentare zu Pinnwand-Posts und Threads
create table if not exists public.post_comments (
    id uuid primary key default uuid_generate_v4(),
    post_id uuid references public.posts(id) on delete cascade,
    thread_id uuid references public.threads(id) on delete cascade,
    author_id uuid not null references public.profiles(id),
    body text not null,
    created_at timestamptz not null default now(),
    check (post_id is not null or thread_id is not null)
);

-- ---------- Chat ----------
create table if not exists public.chat_channels (
    id uuid primary key default uuid_generate_v4(),
    project_id uuid not null references public.projects(id) on delete cascade,
    area_id uuid references public.areas(id) on delete cascade,
    name text not null,
    is_general boolean not null default false
);

create table if not exists public.messages (
    id uuid primary key default uuid_generate_v4(),
    channel_id uuid not null references public.chat_channels(id) on delete cascade,
    author_id uuid not null references public.profiles(id),
    body text not null default '',
    image_url text,
    created_at timestamptz not null default now()
);

create table if not exists public.channel_mutes (
    channel_id uuid not null references public.chat_channels(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    muted boolean not null default true,
    primary key (channel_id, user_id)
);

-- ---------- Kalender ----------
create table if not exists public.events (
    id uuid primary key default uuid_generate_v4(),
    project_id uuid not null references public.projects(id) on delete cascade,
    title text not null,
    description text,
    kind text not null check (kind in ('online','on_site')),
    location_name text,
    location_lat double precision,
    location_lng double precision,
    map_link text,
    start_time timestamptz not null,
    end_time timestamptz,
    max_participants int,
    created_by uuid not null references public.profiles(id),
    created_at timestamptz not null default now()
);

create table if not exists public.event_invites (
    event_id uuid not null references public.events(id) on delete cascade,
    user_id uuid references public.profiles(id) on delete cascade,
    area_id uuid references public.areas(id) on delete cascade,
    check (user_id is not null or area_id is not null),
    unique (event_id, user_id, area_id)
);

create table if not exists public.event_rsvps (
    event_id uuid not null references public.events(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    status text not null check (status in ('yes','no','maybe')),
    primary key (event_id, user_id)
);

create table if not exists public.event_comments (
    id uuid primary key default uuid_generate_v4(),
    event_id uuid not null references public.events(id) on delete cascade,
    author_id uuid not null references public.profiles(id),
    body text not null,
    created_at timestamptz not null default now()
);

-- ---------- Karte ----------
create table if not exists public.map_spots (
    id uuid primary key default uuid_generate_v4(),
    project_id uuid not null references public.projects(id) on delete cascade,
    author_id uuid not null references public.profiles(id),
    title text not null,
    description text,
    lat double precision not null,
    lng double precision not null,
    photo_url text,
    exif jsonb,
    visibility text not null default 'project' check (visibility in ('project','area_only','private')),
    created_at timestamptz not null default now()
);

-- ---------- Umfragen ----------
create table if not exists public.surveys (
    id uuid primary key default uuid_generate_v4(),
    scope text not null check (scope in ('project','area')),
    project_id uuid not null references public.projects(id) on delete cascade,
    area_id uuid references public.areas(id) on delete cascade,
    question text not null,
    options jsonb not null,
    multiple_choice boolean not null default false,
    created_by uuid not null references public.profiles(id),
    created_at timestamptz not null default now()
);

create table if not exists public.survey_votes (
    survey_id uuid not null references public.surveys(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    option_indexes int[] not null,
    primary key (survey_id, user_id)
);

-- ---------- Realtime ----------
alter publication supabase_realtime add table public.messages;
alter publication supabase_realtime add table public.posts;
alter publication supabase_realtime add table public.events;
alter publication supabase_realtime add table public.event_rsvps;
alter publication supabase_realtime add table public.map_spots;

-- ---------- Grants (RLS allein gewährt keinen Zugriff) ----------
grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, update, delete on public.projects to authenticated;
grant select, insert, update, delete on public.project_members to authenticated;
grant select, insert, update, delete on public.areas to authenticated;
grant select, insert, update, delete on public.area_moderators to authenticated;
grant select, insert, update, delete on public.area_subscriptions to authenticated;
grant select, insert, update, delete on public.forums to authenticated;
grant select, insert, update, delete on public.posts to authenticated;
grant select, insert, update, delete on public.threads to authenticated;
grant select, insert, update, delete on public.post_comments to authenticated;
grant select, insert, update, delete on public.chat_channels to authenticated;
grant select, insert, update, delete on public.messages to authenticated;
grant select, insert, update, delete on public.channel_mutes to authenticated;
grant select, insert, update, delete on public.events to authenticated;
grant select, insert, update, delete on public.event_invites to authenticated;
grant select, insert, update, delete on public.event_rsvps to authenticated;
grant select, insert, update, delete on public.event_comments to authenticated;
grant select, insert, update, delete on public.map_spots to authenticated;
grant select, insert, update, delete on public.surveys to authenticated;
grant select, insert, update, delete on public.survey_votes to authenticated;

-- ---------- Row Level Security ----------
alter table public.profiles enable row level security;
alter table public.projects enable row level security;
alter table public.project_members enable row level security;
alter table public.areas enable row level security;
alter table public.area_moderators enable row level security;
alter table public.area_subscriptions enable row level security;
alter table public.forums enable row level security;
alter table public.posts enable row level security;
alter table public.threads enable row level security;
alter table public.post_comments enable row level security;
alter table public.chat_channels enable row level security;
alter table public.messages enable row level security;
alter table public.channel_mutes enable row level security;
alter table public.events enable row level security;
alter table public.event_invites enable row level security;
alter table public.event_rsvps enable row level security;
alter table public.event_comments enable row level security;
alter table public.map_spots enable row level security;
alter table public.surveys enable row level security;
alter table public.survey_votes enable row level security;

-- Hilfsfunktionen
create or replace function public.is_project_member(p_project uuid)
returns boolean language sql stable security definer as $$
    select exists (select 1 from public.project_members m
                   where m.project_id = p_project and m.user_id = auth.uid());
$$;

create or replace function public.is_project_admin(p_project uuid)
returns boolean language sql stable security definer as $$
    select exists (select 1 from public.project_members m
                   where m.project_id = p_project and m.user_id = auth.uid() and m.role = 'admin');
$$;

create or replace function public.is_area_moderator(p_area uuid)
returns boolean language sql stable security definer as $$
    select exists (select 1 from public.area_moderators am
                   where am.area_id = p_area and am.user_id = auth.uid())
           or exists (select 1 from public.areas a
                      where a.id = p_area and public.is_project_admin(a.project_id));
$$;

-- Profile: eigenes Profil schreiben, alle Mitglieder lesen
create policy "profiles read" on public.profiles for select using (true);
create policy "profiles self upsert" on public.profiles for insert with check (auth.uid() = id);
create policy "profiles self update" on public.profiles for update using (auth.uid() = id);

-- Projekte: Mitglieder lesen, Admins verwalten
create policy "projects read" on public.projects for select using (public.is_project_member(id));
create policy "projects create" on public.projects for insert with check (auth.uid() = creator_id);
create policy "projects admin update" on public.projects for update using (public.is_project_admin(id));
create policy "projects admin delete" on public.projects for delete using (public.is_project_admin(id));

create policy "members read" on public.project_members for select using (public.is_project_member(project_id));
create policy "members admin manage" on public.project_members for all using (public.is_project_admin(project_id));

-- Areas: Mitglieder lesen, Admins verwalten
create policy "areas read" on public.areas for select using (public.is_project_member(project_id));
create policy "areas admin write" on public.areas for insert with check (public.is_project_admin(project_id));
create policy "areas admin update" on public.areas for update using (public.is_project_admin(project_id));
create policy "areas admin delete" on public.areas for delete using (public.is_project_admin(project_id));

create policy "area mods read" on public.area_moderators for select using (public.is_project_member((select project_id from public.areas where id = area_id)));
create policy "area mods manage" on public.area_moderators for all using (public.is_project_admin((select project_id from public.areas where id = area_id)));

create policy "area subs self" on public.area_subscriptions for all using (auth.uid() = user_id);

create policy "forums read" on public.forums for select using (public.is_project_member((select project_id from public.areas where id = area_id)));
create policy "forums member create" on public.forums for insert with check (
    public.is_project_member((select project_id from public.areas where id = area_id)) and auth.uid() = created_by);

-- Posts: Mitglieder der Area lesen; eigene Posts schreiben/löschen; Mods/Admins pinnen & fremde löschen
create policy "posts read" on public.posts for select using (public.is_project_member((select project_id from public.areas where id = area_id)));
create policy "posts self insert" on public.posts for insert with check (auth.uid() = author_id and public.is_project_member((select project_id from public.areas where id = area_id)));
create policy "posts self update" on public.posts for update using (auth.uid() = author_id);
create policy "posts self delete" on public.posts for delete using (auth.uid() = author_id);
create policy "posts mod delete" on public.posts for delete using (public.is_area_moderator(area_id));
create policy "posts mod pin" on public.posts for update using (public.is_area_moderator(area_id));

-- Threads: Area-Mitglieder lesen/erstellen; Kommentare ebenso
create policy "threads read" on public.threads for select using (public.is_project_member((select a.project_id from public.forums f join public.areas a on a.id = f.area_id where f.id = forum_id)));
create policy "threads member create" on public.threads for insert with check (
    auth.uid() = created_by and public.is_project_member((select a.project_id from public.forums f join public.areas a on a.id = f.area_id where f.id = forum_id)));
create policy "comments read" on public.post_comments for select using (
    (post_id is not null and public.is_project_member((select project_id from public.areas where id = (select area_id from public.posts where id = post_id))))
    or (thread_id is not null and public.is_project_member((select a.project_id from public.threads t join public.forums f on f.id = t.forum_id join public.areas a on a.id = f.area_id where t.id = thread_id)))
);
create policy "comments self insert" on public.post_comments for insert with check (auth.uid() = author_id);
create policy "comments self delete" on public.post_comments for delete using (auth.uid() = author_id);

create policy "channels read" on public.chat_channels for select using (public.is_project_member(project_id));
create policy "channels admin write" on public.chat_channels for all using (public.is_project_admin(project_id));

create policy "messages read" on public.messages for select using (
    public.is_project_member((select project_id from public.chat_channels where id = channel_id)));
create policy "messages self insert" on public.messages for insert with check (auth.uid() = author_id);
create policy "messages self delete" on public.messages for delete using (auth.uid() = author_id);

create policy "mutes self" on public.channel_mutes for all using (auth.uid() = user_id);

-- Events: Mitglieder lesen/erstellen; RSVPs & Kommentare je selbst; Admins verwalten
create policy "events read" on public.events for select using (public.is_project_member(project_id));
create policy "events member create" on public.events for insert with check (public.is_project_member(project_id) and auth.uid() = created_by);
create policy "events admin manage" on public.events for all using (public.is_project_admin(project_id));

create policy "invites read" on public.event_invites for select using (public.is_project_member((select project_id from public.events where id = event_id)));
create policy "invites admin manage" on public.event_invites for all using (public.is_project_admin((select project_id from public.events where id = event_id)));

create policy "rsvps read" on public.event_rsvps for select using (public.is_project_member((select project_id from public.events where id = event_id)));
create policy "rsvps self" on public.event_rsvps for all using (auth.uid() = user_id);

create policy "event comments read" on public.event_comments for select using (public.is_project_member((select project_id from public.events where id = event_id)));
create policy "event comments self insert" on public.event_comments for insert with check (auth.uid() = author_id);
create policy "event comments self delete" on public.event_comments for delete using (auth.uid() = author_id);

-- Spots: Projekt-Mitglieder sehen 'project'; 'area_only' nur Area-Mitglieder; 'private' nur Autor
create policy "spots read" on public.map_spots for select using (
    author_id = auth.uid()
    or (visibility = 'project' and public.is_project_member(project_id))
    or (visibility = 'area_only' and exists (
        select 1 from public.area_subscriptions s join public.areas a on a.id = s.area_id
        where s.user_id = auth.uid() and s.subscribed and a.project_id = map_spots.project_id))
);
create policy "spots self insert" on public.map_spots for insert with check (
    auth.uid() = author_id and public.is_project_member(project_id));
create policy "spots self delete" on public.map_spots for delete using (auth.uid() = author_id);
create policy "spots admin delete" on public.map_spots for delete using (public.is_project_admin(project_id));

create policy "surveys read" on public.surveys for select using (public.is_project_member(project_id));
create policy "surveys admin create" on public.surveys for insert with check (public.is_project_admin(project_id) and auth.uid() = created_by);
create policy "votes read" on public.survey_votes for select using (public.is_project_member((select project_id from public.surveys where id = survey_id)));
create policy "votes self" on public.survey_votes for all using (auth.uid() = user_id);
