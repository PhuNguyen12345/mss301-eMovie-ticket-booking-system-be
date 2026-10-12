# Movie API

Local base URL: `http://localhost:8082/api/movies`.
Start Movie Service with its MySQL database configured and migrations applied.
Endpoints return the shared `ApiResponse` envelope.

| Method | Path | Behavior | Success status |
| --- | --- | --- | --- |
| GET | `/api/movies` | Filtered, paginated movies | 200 |
| GET | `/api/movies/{id}` | Movie details | 200 |
| POST | `/api/movies` | Create movie; includes Location header | 201 |
| PUT | `/api/movies/{id}` | Replace editable movie fields | 200 |
| PATCH | `/api/movies/{id}/status` | Change status | 200 |
| POST | `/api/movies/{id}/expire` | Set EXPIRED | 200 |

## List parameters

- `keyword`: optional title search. Blank values disable this filter.
- `status`: optional `WILL_PRESENT`, `PRESENTING`, or `EXPIRED`.
- `genreId`: optional positive genre ID.
- `page`: zero-based page, default 0.
- `size`: 1–100, default 10.

Order: releaseDate descending, then id descending.
Pagination appears under `data`, with the movie list under `data.data`.

```bash
curl 'http://localhost:8082/api/movies?status=PRESENTING&page=0&size=10'
```

## Create / update body

POST and PUT use the same request format. Title, durationMinutes, releaseDate,
and status are required. PUT replaces editable fields; omitted optional fields
become null. ID and timestamps are server-managed. Genres and credits are not
written by these endpoints yet.

```json
{
  "title": "Phim A",
  "description": "Mo ta phim",
  "durationMinutes": 120,
  "releaseDate": "2026-10-12",
  "language": "en",
  "ageRating": "T13",
  "posterUrl": null,
  "trailerUrl": null,
  "status": "PRESENTING"
}
```

```bash
curl -X PATCH 'http://localhost:8082/api/movies/1/status' \
  -H 'Content-Type: application/json' \
  -d '{"status":"EXPIRED"}'
```

EXPIRED movies remain accessible; expiry does not delete a movie.
Invalid input returns 400; a missing movie returns 404, both with ApiResponse.

## Controller verification

Run from the repository root; these tests use a mocked service and need no database:

```bash
bash mvnw -o -pl services/movie-service -am test \
  -Dtest=MovieControllerTests -Dsurefire.failIfNoSpecifiedTests=false
```
