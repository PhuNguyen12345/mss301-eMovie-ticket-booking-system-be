# Movie API

Local base URL: `http://localhost:8082/api/movies`.
Start Movie Service with its MySQL database configured and migrations applied.
Endpoints return the shared `ApiResponse` envelope.

| Method | Path | Behavior | Success status |
| --- | --- | --- | --- |
| GET | `/api/movies` | Filtered, paginated movies | 200 |
| GET | `/api/movies/{id}` | Movie details including genres and credits | 200 |
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

## Full movie details

`GET /api/movies/{id}` returns `MovieDetailResponseDTO` under `data`.
It preserves all base movie fields (id, title, description, durationMinutes,
releaseDate, language, ageRating, posterUrl, trailerUrl, status, createdAt,
updatedAt) and adds:

```json
{
  "genres": [
    {"id": 1, "name": "Action", "description": "Action films"}
  ],
  "credits": [
    {"id": 20, "personId": 2, "fullName": "Person A", "creditType": "ACTOR",
     "characterName": "Character A", "displayOrder": 1}
  ]
}
```

The JSON above shows only the two additional fields, not the full envelope.
Unassigned genres/credits are empty arrays, never null. A person with both roles
appears in two credits. Genres are ordered by name then ID; credits by type,
displayOrder then ID. EXPIRED movies remain accessible. Missing movies return
404; invalid IDs return 400.

The service reads the movie and uses the existing two JOIN FETCH queries inside
its read-only transaction. No lazy entities are exposed to the controller.
List, create, update and status responses continue using `MovieResponseDTO`.
Genre/credit assignment is documented in [MOVIE_RELATION_API.md](MOVIE_RELATION_API.md).

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

Run from the repository root. Detail tests exercise the real service with mocked
repositories; controller tests mock the service. Neither requires a database.
Verify actual SQL/query counts and lazy loading against local MySQL separately.

```bash
bash mvnw -o -pl services/movie-service -am test \
  -Dtest=MovieControllerTests,MovieDetailControllerTests -Dsurefire.failIfNoSpecifiedTests=false
```
