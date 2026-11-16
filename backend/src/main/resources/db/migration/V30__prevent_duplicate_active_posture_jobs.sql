-- Preserve all existing rows while resolving any active duplicates. Keep the
-- oldest active row per endpoint/type; close extras as FAILED for auditability.
WITH ranked_active AS (
    SELECT id,
           row_number() OVER (PARTITION BY endpoint_id, job_type ORDER BY created_at, id) AS active_rank
      FROM posture_job
     WHERE status IN ('QUEUED', 'RUNNING')
)
UPDATE posture_job j
   SET status = 'FAILED',
       completed_at = COALESCE(j.completed_at, now()),
       error_message = COALESCE(j.error_message, 'Closed during active-job deduplication migration')
  FROM ranked_active r
 WHERE j.id = r.id
   AND r.active_rank > 1;

-- At most one queued or running job for a given endpoint and type, enforced
-- atomically for all application instances and direct SQL enqueue paths.
CREATE UNIQUE INDEX uq_posture_job_one_active_per_endpoint_type
    ON posture_job(endpoint_id, job_type)
    WHERE status IN ('QUEUED', 'RUNNING');
