-- Esquema inicial de Mática.
-- Las identidades las emite este backend (Argon2id + JWT), no un proveedor externo: por eso no
-- hay `auth.users` ni políticas RLS. La base sólo es alcanzable desde el servicio; el APK nunca
-- recibe la cadena de conexión.

create table if not exists users (
    id              uuid primary key,
    email           text not null,
    email_normalized text not null unique,
    password_hash   text not null,
    display_name    text,
    created_at      timestamptz not null default now()
);

-- Un intento es la unidad de sincronización. El id lo genera el teléfono: reintentar el mismo
-- envío no duplica evidencia.
create table if not exists attempts (
    id           uuid primary key,
    user_id      uuid not null references users(id) on delete cascade,
    skill_id     text not null,
    exercise_id  text not null,
    before_text  text not null,
    after_text   text not null,
    verdict      text not null check (verdict in ('valid', 'invalid', 'indeterminate', 'syntax')),
    used_hint    boolean not null default false,
    occurred_at  timestamptz not null,
    created_at   timestamptz not null default now()
);

create index if not exists attempts_user_occurred
    on attempts (user_id, occurred_at desc);

create table if not exists study_groups (
    id          uuid primary key,
    name        text not null,
    owner_id    uuid not null references users(id) on delete cascade,
    invite_code text not null unique,
    created_at  timestamptz not null default now()
);

create table if not exists group_members (
    group_id  uuid not null references study_groups(id) on delete cascade,
    user_id   uuid not null references users(id) on delete cascade,
    role      text not null check (role in ('owner', 'student')),
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

create index if not exists group_members_user
    on group_members (user_id);

create table if not exists device_tokens (
    user_id    uuid not null references users(id) on delete cascade,
    token      text not null,
    platform   text not null default 'android',
    updated_at timestamptz not null default now(),
    primary key (user_id, token)
);
