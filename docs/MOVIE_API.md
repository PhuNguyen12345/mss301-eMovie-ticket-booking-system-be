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
| PUT | `/api/movies/{id}/poster` | Upload/replace poster using multipart file | 200 |
| PUT | `/api/movies/{id}/poster/from-url` | Import/replace poster using an HTTPS URL | 200 |

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

## Poster from URL

```bash
curl -X PUT 'http://localhost:8082/api/movies/10/poster/from-url' \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/poster.jpg"}'
```

Use a publicly accessible image URL, not a web page containing an image.
The request accepts a required `url` string, trimmed and limited to 4096 characters.
Only HTTPS with a domain name and the default/443 port is accepted. Credentials,
fragments, IP literals and local hostnames are rejected. Query strings are allowed.
This is syntax validation, not a DNS/redirect allowlist. Cloudinary retrieves the
source; Movie Service does not fetch the external URL itself.

Cloudinary imports the image using a fresh public ID. Both poster sources share
metadata validation: JPEG/PNG, <= 5 MB, <= 25 million pixels. For remote imports,
size/dimensions are checked after Cloudinary has uploaded the asset. Rejected
assets are deleted before any database update. This does not limit how many bytes
Cloudinary downloads; configure provider-side restrictions separately if required.
Spring multipart size limits apply only to uploaded files.

On success, `data` contains `movieId` and `posterUrl`. The URL and public ID stored
in the database belong to Cloudinary, not to the original source.

- Invalid URL/unsupported image/oversized image: 400.
- Missing movie: 404; no upload is attempted.
- Provider or invalid metadata failure: 502, without exposing provider messages.
- Invalid JSON or missing body: 400.

Upload happens outside the database transaction. The existing poster writer
commits the replacement before the old image is deleted. On normal replacement
failure the new asset is cleaned up; on transaction-system/heuristic exceptions
it is retained for reconciliation because the commit outcome may be unknown.
Cleanup failures are logged and do not mask the original failure or invalidate a
successful replacement. Cleanup currently has no persistent retry queue. An upload
whose provider response is lost may also require asset reconciliation.

No schema migration or new credentials are needed. Existing Cloudinary environment
variables are reused. Expiring a movie continues to keep its poster.

## Controller verification

Run from the repository root. Detail tests exercise the real service with mocked
repositories; controller tests mock the service. Neither requires a database.
Verify actual SQL/query counts and lazy loading against local MySQL separately.

```bash
bash mvnw -o -pl services/movie-service -am test \
  -Dtest=MovieControllerTests,MovieDetailControllerTests -Dsurefire.failIfNoSpecifiedTests=false
```

Poster validation and lifecycle tests mock Cloudinary and database collaborators;
they do not upload real assets or verify live MySQL commits:

```bash
bash mvnw -pl services/movie-service -am test \
  -Dtest=CloudinaryServiceTests,MoviePosterUrlControllerTests \
  -Dsurefire.failIfNoSpecifiedTests=false
```
