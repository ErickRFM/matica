-- Mática · esquema privado para PostgreSQL administrado en Supabase.
-- V1 - aplicar una sola vez en un proyecto exclusivo de Mática.
-- No exponer este esquema en la Data API; el backend Ktor es el único escritor.
-- Requiere Supabase Auth (schema auth.users), disponible en proyectos Supabase.
BEGIN;

CREATE SCHEMA IF NOT EXISTS matica;
REVOKE ALL ON SCHEMA matica FROM PUBLIC, anon, authenticated;

CREATE TABLE IF NOT EXISTS matica.schema_version (
    version integer PRIMARY KEY CHECK (version > 0),
    applied_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS matica.learner_profiles (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name varchar(120) NOT NULL DEFAULT 'Estudiante',
    locale varchar(10) NOT NULL DEFAULT 'es',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS matica.topics (
    id varchar(100) PRIMARY KEY,
    title varchar(180) NOT NULL,
    description text,
    sort_order integer NOT NULL DEFAULT 0,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_matica_topics_order ON matica.topics(sort_order, id);

CREATE TABLE IF NOT EXISTS matica.skills (
    id varchar(120) PRIMARY KEY,
    topic_id varchar(100) NOT NULL REFERENCES matica.topics(id) ON DELETE RESTRICT,
    title varchar(180) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    mastery_threshold numeric(5,4) NOT NULL DEFAULT 0.8000
        CHECK(mastery_threshold BETWEEN 0 AND 1)
);
CREATE INDEX IF NOT EXISTS idx_matica_skills_topic ON matica.skills(topic_id,sort_order);

CREATE TABLE IF NOT EXISTS matica.skill_prerequisites (
    skill_id varchar(120) NOT NULL REFERENCES matica.skills(id) ON DELETE CASCADE,
    prerequisite_id varchar(120) NOT NULL REFERENCES matica.skills(id) ON DELETE CASCADE,
    PRIMARY KEY(skill_id, prerequisite_id),
    CHECK(skill_id <> prerequisite_id)
);

CREATE TABLE IF NOT EXISTS matica.exercises (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id varchar(120) NOT NULL REFERENCES matica.skills(id),
    template_key varchar(120) NOT NULL,
    seed bigint NOT NULL,
    difficulty smallint NOT NULL CHECK(difficulty BETWEEN 1 AND 10),
    payload jsonb NOT NULL DEFAULT '{}'::jsonb
        CHECK(jsonb_typeof(payload)='object'),
    contract_version varchar(50) NOT NULL DEFAULT 'matica.math.v1',
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(skill_id, template_key, seed)
);
CREATE INDEX IF NOT EXISTS idx_matica_exercises_skill ON matica.exercises(skill_id);

CREATE TABLE IF NOT EXISTS matica.attempts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES matica.learner_profiles(user_id) ON DELETE CASCADE,
    exercise_id uuid NOT NULL REFERENCES matica.exercises(id),
    operation_id uuid NOT NULL,
    submitted_answer jsonb NOT NULL DEFAULT '{}'::jsonb
        CHECK(jsonb_typeof(submitted_answer)='object'),
    evaluation jsonb NOT NULL DEFAULT '{}'::jsonb
        CHECK(jsonb_typeof(evaluation)='object'),
    is_correct boolean NOT NULL,
    elapsed_ms integer CHECK(elapsed_ms >= 0),
    submitted_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(user_id,operation_id)
);
CREATE INDEX IF NOT EXISTS idx_matica_attempts_user_time
  ON matica.attempts(user_id,submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_matica_attempts_exercise
  ON matica.attempts(exercise_id);

CREATE TABLE IF NOT EXISTS matica.skill_progress (
    user_id uuid NOT NULL REFERENCES matica.learner_profiles(user_id) ON DELETE CASCADE,
    skill_id varchar(120) NOT NULL REFERENCES matica.skills(id),
    mastery numeric(5,4) NOT NULL DEFAULT 0 CHECK(mastery BETWEEN 0 AND 1),
    attempts_count integer NOT NULL DEFAULT 0 CHECK(attempts_count >= 0),
    correct_count integer NOT NULL DEFAULT 0 CHECK(correct_count BETWEEN 0 AND attempts_count),
    next_review_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id,skill_id)
);
CREATE INDEX IF NOT EXISTS idx_matica_progress_review
  ON matica.skill_progress(user_id,next_review_at)
  WHERE next_review_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS matica.xp_ledger (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES matica.learner_profiles(user_id) ON DELETE CASCADE,
    attempt_id uuid UNIQUE REFERENCES matica.attempts(id) ON DELETE SET NULL,
    points integer NOT NULL CHECK(points BETWEEN -500 AND 500),
    reason varchar(60) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_matica_xp_user ON matica.xp_ledger(user_id,created_at DESC);

CREATE TABLE IF NOT EXISTS matica.streaks (
    user_id uuid PRIMARY KEY REFERENCES matica.learner_profiles(user_id) ON DELETE CASCADE,
    current_days integer NOT NULL DEFAULT 0 CHECK(current_days >= 0),
    longest_days integer NOT NULL DEFAULT 0 CHECK(longest_days >= current_days),
    last_activity_date date,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS matica.sync_operations (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES matica.learner_profiles(user_id) ON DELETE CASCADE,
    kind varchar(60) NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb CHECK(jsonb_typeof(payload)='object'),
    state varchar(12) NOT NULL DEFAULT 'pending'
      CHECK(state IN ('pending','processed','failed')),
    retries integer NOT NULL DEFAULT 0 CHECK(retries >= 0),
    processed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(user_id,id)
);
CREATE INDEX IF NOT EXISTS idx_matica_sync_pending
  ON matica.sync_operations(user_id,created_at)
  WHERE state='pending';

-- Defense in depth even though schema matica is not Data API-exposed.
ALTER TABLE matica.schema_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE matica.learner_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE matica.topics ENABLE ROW LEVEL SECURITY;
ALTER TABLE matica.skills ENABLE ROW LEVEL SECURITY;
ALTER TABLE matica.skill_prerequisites ENABLE ROW LEVEL SECURITY;
ALTER TABLE matica.exercises ENABLE ROW LEVEL SECURITY;
ALTER TABLE matica.attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE matica.skill_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE matica.xp_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE matica.streaks ENABLE ROW LEVEL SECURITY;
ALTER TABLE matica.sync_operations ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON ALL TABLES IN SCHEMA matica FROM PUBLIC, anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA matica FROM PUBLIC, anon, authenticated;

INSERT INTO matica.schema_version(version) VALUES (1)
ON CONFLICT(version) DO NOTHING;

COMMIT;
