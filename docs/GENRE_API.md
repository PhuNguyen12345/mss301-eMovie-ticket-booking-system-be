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

The usage check before deletion does not alone prevent concurrent link creation.
When implementing movie-genre assignment, coordinate locking between assignment
and deletion if this rule must hold under concurrent requests. The current schema
retains ON DELETE CASCADE; it is not changed by this feature.

## Test

These tests exercise the controller and real service with mocked repositories;
they do not verify MySQL queries, collation or transactions against a live DB.

```bash
bash mvnw -pl services/movie-service -am test \
  -Dtest=GenreControllerTests -Dsurefire.failIfNoSpecifiedTests=false
```
