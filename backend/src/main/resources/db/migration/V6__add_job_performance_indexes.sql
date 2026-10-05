-- V6__add_job_performance_indexes.sql
-- Composite performance indexes for background inactivity and release processing jobs

CREATE INDEX idx_wills_inactivity_scan ON wills (state, last_verified_activity_at);
CREATE INDEX idx_wills_warning_scan ON wills (state, warning_sent_at);
CREATE INDEX idx_wills_final_warning_scan ON wills (state, final_warning_sent_at);
CREATE INDEX idx_wills_release_scan ON wills (state, release_after, cancelled_at);
