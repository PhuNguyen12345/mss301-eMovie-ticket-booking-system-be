# Person API

Base URL: `http://localhost:8082/api/persons`. Responses use `ApiResponse`.

| Method | Path | Success |
| --- | --- | --- |
| GET | `/api/persons?keyword=nolan&page=0&size=10` | 200, paginated people |
| GET | `/api/persons/{id}` | 200, details |
| POST | `/api/persons` | 201, body and Location header |
| PUT | `/api/persons/{id}` | 200, updated person |
| DELETE | `/api/persons/{id}` | 200, response with null data |

POST and PUT accept:

```json
{"fullName":"Christopher Nolan"}
```

- Full name is required, trimmed and limited to 255 characters.
- Different people may share a name. There is no name uniqueness check.
- ACTOR/DIRECTOR roles belong to movie credits, not to Person.
- GET list defaults to page 0 and size 10; size must be 1–100.
- Order: fullName ascending, then id ascending.
- List data is under `data.data`, with pagination under `data`.
- Invalid input returns 400; missing person returns 404.
- Deleting a person with existing movie credits returns 409.

Example:

```bash
curl -X POST 'http://localhost:8082/api/persons' \
  -H 'Content-Type: application/json' \
  -d '{"fullName":"Christopher Nolan"}'
```

Deletion locks the person before checking credit usage. Movie-credit assignment
locks requested persons in ascending ID order using the same repository method.
Future credit writers must follow this protocol. ON DELETE CASCADE is unchanged.

## Verification

Controller tests exercise the real service with mocked repositories; they do not
verify MySQL query execution, collation or database transaction behavior.

```bash
bash mvnw -pl services/movie-service -am test \
  -Dtest=PersonControllerTests -Dsurefire.failIfNoSpecifiedTests=false
```
