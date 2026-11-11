-- Retain only digests of rotated tokens for the lifetime of their session.
-- A replay revokes that session; deleting/expiring a session removes its history.
CREATE TABLE refresh_token_history (
    token_hash varchar(64) PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES auth_session(id) ON DELETE CASCADE,
    rotated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refresh_history_session ON refresh_token_history(session_id);
