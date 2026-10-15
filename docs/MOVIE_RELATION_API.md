# Movie genres and credits

Base URL: `http://localhost:8082`. All successful operations return HTTP 200
with the shared `ApiResponse` envelope and a list under `data`.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/movies/{id}/genres` | Read assigned genres, ordered by name then ID |
| PUT | `/api/movies/{id}/genres` | Replace all genre links |
| GET | `/api/movies/{id}/credits` | Read credits, ordered by type, displayOrder then ID |
| PUT | `/api/movies/{id}/credits` | Replace all credits |

## Genres

```bash
curl -X PUT 'http://localhost:8082/api/movies/10/genres' \
  -H 'Content-Type: application/json' \
  -d '{"genreIds":[1,3]}'
```

IDs must be positive, distinct and refer to existing genres.
Existing links in the requested list are retained; absent links are removed.
Deleting links does not delete genres.

## Credits

```bash
curl -X PUT 'http://localhost:8082/api/movies/10/credits' \
  -H 'Content-Type: application/json' \
  -d '{"credits":[{"personId":1,"creditType":"DIRECTOR","displayOrder":1},{"personId":2,"creditType":"ACTOR","characterName":"Character A","displayOrder":1}]}'
```

- personId must be positive and refer to an existing person.
- creditType is required: ACTOR or DIRECTOR.
- displayOrder is required and must be >= 1; ties are allowed.
- characterName is optional for actors, trimmed, and limited to 255 characters.
- Director characterName must be absent, null or blank.
- A person can have both an ACTOR and a DIRECTOR credit for the same movie.
- Duplicate `(personId, creditType)` pairs in a request return 400.
- Existing credit IDs are retained when that pair is retained. Name/order are updated.
- Credits absent from the request are removed; persons are not deleted.

## Replacement semantics and errors

`{"genreIds":[]}` or `{"credits":[]}` clears the corresponding list.
Missing/null lists and null list items return 400, never implicitly clear data.
Invalid fields return 400; missing movie/genre/person returns 404.
Validation and reference checks complete before existing entities are changed.
Each replacement is one transaction, so database errors roll back the operation.
GET for an existing movie with no links returns an empty list; a missing movie is 404.

Concurrent replacements lock the movie first. Requested genres/persons are locked
in ascending ID order. Genre/person deletion acquires the corresponding reference
lock before checking usage. Other future link writers must follow this protocol.
Database lock/constraint conflicts return 409; reload and retry the request.

No migration is added. The composite genre key already prevents duplicate links.
Credit-pair uniqueness is enforced by these APIs under the movie lock, not by a
new SQL constraint. Existing duplicate credit pairs return 409 on replacement;
inspect and resolve them before retrying. Direct DB writes bypass API validation.

These endpoints do not change the existing Movie list/detail response shape.

## Verification

```bash
bash mvnw -pl services/movie-service -am test \
  -Dtest=MovieRelationControllerTests,GenreControllerTests,PersonControllerTests,MovieControllerTests,MovieServiceTransactionTests \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Tests use Spring transaction interception and mocked repositories with simulated
state. They cover replacement, stable IDs, clearing, validation before changes,
rollback decisions and error envelopes. Verify actual query execution, DB rollback,
and concurrent locking separately against local MySQL before integration.
