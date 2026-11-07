-- Gestión de aula, tareas y telemetría de aprendizaje (B5, B6, B7, B8).

create table if not exists assignments (
    id             uuid primary key,
    group_id       uuid not null references study_groups(id) on delete cascade,
    skill_id       text not null,
    target_mastery text not null default 'proficient',
    due_date       timestamptz,
    created_by     uuid not null references users(id) on delete cascade,
    created_at     timestamptz not null default now()
);

create index if not exists assignments_group
    on assignments (group_id);

create table if not exists assignment_progress (
    assignment_id uuid not null references assignments(id) on delete cascade,
    student_id    uuid not null references users(id) on delete cascade,
    status        text not null default 'assigned' check (status in ('assigned', 'in_progress', 'completed')),
    completed_at  timestamptz,
    primary key (assignment_id, student_id)
);

create index if not exists assignment_progress_student
    on assignment_progress (student_id);

create table if not exists telemetry_events (
    id          uuid primary key,
    user_id     uuid references users(id) on delete cascade,
    event_type  text not null,
    skill_id    text,
    exercise_id text,
    dwell_ms    bigint,
    metadata    jsonb default '{}'::jsonb,
    recorded_at timestamptz not null default now()
);

create index if not exists telemetry_events_user_recorded
    on telemetry_events (user_id, recorded_at desc);
