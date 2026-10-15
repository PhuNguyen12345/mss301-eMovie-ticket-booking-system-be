# Genre API

Base URL: `http://localhost:8082/api/genres`.
All responses use `ApiResponse`; list results use `PageResponseDTO` under `data`.

| Method | Path | Success |
| --- | --- | --- |
| GET | `/api/genres?keyword=action&page=0&size=10` | 200, paginated list |
| GET | `/api/genres/{id}` | 200, details |
| POST | `/api/genres` | 201, body and Location header |
| PUT | `/api/genres/{id}` | 200, updated genre |
| DELETE | `/api/genres/{id}` | 200, ApiResponse with null data |

POST and PUT accept JSON:

```json
{"name":"Action","description":"Phim hanh dong"}
```

- Name is required, trimmed and limited to 100 characters.
- Description is optional and limited to 500 characters.
- PUT replaces both editable fields; omitted description becomes null.
- Page starts at 0; size is 1–100. Defaults are page=0 and size=10.
- List order is name ascending, then id ascending.
- Blank keyword disables name filtering.
- Invalid input returns 400; missing genre returns 404.
- Duplicate names and database constraint conflicts return 409.
- Deleting a genre already linked to a movie returns 409.
- The DB unique constraint remains the final guard against concurrent duplicate creation.

Deletion locks the genre before checking usage. Movie-genre assignment locks the
requested genres in ascending ID order using the same repository method. Future
link writers must follow this protocol. The schema retains ON DELETE CASCADE.

## Test

These tests exercise the controller and real service with mocked repositories;
they do not verify MySQL queries, collation or transactions against a live DB.

```bash
bash mvnw -pl services/movie-service -am test \
  -Dtest=GenreControllerTests -Dsurefire.failIfNoSpecifiedTests=false
```
