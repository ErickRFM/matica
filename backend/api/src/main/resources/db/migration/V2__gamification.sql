-- Motor de gamificación y recompensas de Mática (B4-03).
-- Gestionado exclusivamente por el servidor para garantizar anti-tampering y determinismo.

create table if not exists user_streaks (
    user_id          uuid primary key references users(id) on delete cascade,
    current_streak   int not null default 0,
    longest_streak   int not null default 0,
    last_active_date date,
    freeze_credits   int not null default 2,
    updated_at       timestamptz not null default now()
);

create table if not exists user_experience (
    user_id    uuid primary key references users(id) on delete cascade,
    total_xp   int not null default 0,
    weekly_xp  int not null default 0,
    level      int not null default 1,
    updated_at timestamptz not null default now()
);

create table if not exists reward_ledger (
    id          uuid primary key,
    user_id     uuid not null references users(id) on delete cascade,
    attempt_id  uuid references attempts(id) on delete set null,
    xp_awarded  int not null,
    reason      text not null,
    awarded_at  timestamptz not null default now()
);

create index if not exists reward_ledger_user_awarded
    on reward_ledger (user_id, awarded_at desc);
