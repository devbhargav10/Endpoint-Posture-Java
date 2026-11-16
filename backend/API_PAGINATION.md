# Backend pagination contract

Job list responses remain compatible with the existing `limit` endpoints:

- `GET /api/v1/jobs?limit=...` defaults to 200 and caps at 1000.
- `GET /api/v1/jobs/endpoint/{id}?limit=...` defaults to 100 and caps at 1000.

Clients that need pagination metadata can use the additive endpoints:

- `GET /api/v1/jobs/page?page=0&size=25`
- `GET /api/v1/jobs/endpoint/{id}/page?page=0&size=25`

Pages are zero-based. Size defaults to 25 and is bounded to 1..200. Responses use
`{ "items": [...], "total": n, "page": p, "size": s }`. Results order by
`createdAt DESC, id DESC` so rows with the same timestamp stay in a stable order.
The frontend can adopt these endpoints independently; no existing request needs
to change for this batch.
