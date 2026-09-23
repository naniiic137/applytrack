-- Optimistic locking: JPA bumps this column on every update of an application, and clients send back
-- the version they last read, so an edit based on stale data is rejected (409) instead of overwriting.
alter table job_applications add column version bigint default 0 not null;
