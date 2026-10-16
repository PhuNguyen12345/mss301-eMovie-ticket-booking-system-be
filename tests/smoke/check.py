import base64
import concurrent.futures
import datetime
import hashlib
import hmac
import json
import os
import time
import urllib.error
import urllib.request

BASE = os.environ['GATEWAY_URL']
SECRET = base64.b64decode(os.environ['JWT_SECRET'])
CHECKS = 0


def b64(value):
    return base64.urlsafe_b64encode(value).rstrip(b'=')


def token(roles=None, changes=None, remove=(), key=SECRET, algorithm='HS256'):
    claims = {'sub': '42', 'roles': roles if roles is not None else ['ADMIN'],
              'iss': os.environ['JWT_ISSUER'], 'aud': os.environ['JWT_AUDIENCE'],
              'iat': int(time.time()), 'exp': int(time.time()) + 900}
    claims.update(changes or {})
    for name in remove:
        claims.pop(name, None)
    message = b'.'.join([b64(json.dumps({'alg': algorithm, 'typ': 'JWT'}).encode()),
                         b64(json.dumps(claims).encode())])
    return (message + b'.' + b64(hmac.new(key, message, hashlib.sha256).digest())).decode()


def call(method, path, status=200, auth=None, body=None, headers=None):
    global CHECKS
    request_headers = dict(headers or {})
    if auth is not None:
        request_headers['Authorization'] = 'Bearer ' + auth
    payload = None
    if body is not None:
        payload = json.dumps(body).encode()
        request_headers['Content-Type'] = 'application/json'
    request = urllib.request.Request(BASE + path, data=payload, headers=request_headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=20)
    except urllib.error.HTTPError as error:
        response = error
    raw = response.read()
    assert response.status == status, (method, path, response.status, status, raw.decode())
    data = json.loads(raw) if raw and response.headers.get('Content-Type', '').startswith('application/json') else None
    if data is not None:
        assert data['status'] == status, data
    CHECKS += 1
    return data, response.headers


def wait_for_catalog():
    for attempt in range(45):
        try:
            call('GET', '/api/movies')
            call('GET', '/api/showtimes')
            return
        except (AssertionError, urllib.error.URLError):
            time.sleep(2)
    raise AssertionError('Services did not become discoverable through Eureka')


wait_for_catalog()
admin = token()
customer = token(['CUSTOMER'])
call('POST', '/api/showtimes', 401, body={})
call('POST', '/api/showtimes', 403, auth=customer, body={})
call('GET', '/api/bookings', 401)
call('GET', '/api/users/123', 403, auth=customer)
call('GET', '/api/users/123', auth=admin)
call('GET', '/actuator/env', 403, auth=admin)
call('GET', '/internal/secrets', 403, auth=admin)

for invalid in [
    'not-a-token', token(key=b'a-different-key-with-at-least-32-bytes'),
    token(changes={'exp': int(time.time()) - 120}),
    token(changes={'nbf': int(time.time()) + 600}),
    token(changes={'iss': 'attacker'}), token(changes={'aud': 'another-app'}),
    token(remove=('exp',)), token(remove=('aud',)), token(remove=('sub',)),
    token(remove=('roles',)), token(changes={'roles': 'ADMIN'}),
    token(changes={'roles': ['ADMIN\r\nX-Injected: true']}),
    token(changes={'sub': '42\r\nX-Injected: true'}), token(algorithm='none'),
]:
    call('GET', '/api/bookings', 401, auth=invalid)
call('GET', '/api/movies', 401, auth=token(changes={'exp': int(time.time()) - 120}))

spoofed = {'X-User-Id': 'evil', 'X-User-Roles': 'ADMIN', 'X-Role': 'ADMIN', 'X-User-Email': 'evil@example.com'}
data, _ = call('GET', '/api/bookings?mine=true', auth=customer, headers=spoofed)
forwarded = {key.lower(): value for key, value in data['data']['headers'].items()}
assert forwarded['x-user-id'] == '42'
assert forwarded['x-user-roles'] == 'CUSTOMER'
assert 'authorization' not in forwarded and 'x-role' not in forwarded and 'x-user-email' not in forwarded
assert data['data']['path'] == '/api/bookings?mine=true'
data, _ = call('POST', '/api/auth/login', body={}, headers=spoofed)
forwarded = {key.lower(): value for key, value in data['data']['headers'].items()}
assert not any(key.startswith('x-user-') or key == 'x-role' for key in forwarded)
call('POST', '/api/showtimes', 401, body={}, headers=spoofed)
call('GET', '/api/users/me', auth=customer)
data, _ = call('GET', '/api/seats', auth=token(['ADMIN', 'CUSTOMER']), headers=spoofed)
forwarded = {key.lower(): value for key, value in data['data']['headers'].items()}
assert forwarded['x-user-id'] == '42' and forwarded['x-user-roles'] == 'ADMIN,CUSTOMER'
call('GET', '/api/payments/disconnect', 502, auth=customer)
_, headers = call('OPTIONS', '/api/showtimes', headers={
    'Origin': 'http://localhost:5173', 'Access-Control-Request-Method': 'POST',
    'Access-Control-Request-Headers': 'authorization,content-type'})
assert headers['Access-Control-Allow-Origin'] == 'http://localhost:5173'
call('OPTIONS', '/api/showtimes', 403, headers={
    'Origin': 'https://attacker.example', 'Access-Control-Request-Method': 'POST'})
print('PASS: JWT verification, authorization, header propagation, spoofing and CORS', flush=True)

day = datetime.date.today() + datetime.timedelta(days=30)
data, _ = call('POST', '/api/movies', 201, auth=admin, body={
    'title': 'Gateway Showtime Integration', 'durationMinutes': 120,
    'releaseDate': day.isoformat(), 'status': 'WILL_PRESENT'})
movie_id = data['data']['id']
call('GET', f'/api/movies/{movie_id}')
call('GET', '/api/genres')
call('GET', '/api/persons')


def show(screen=1, hour=18, date=day, **changes):
    body = {'movieId': movie_id, 'cinemaId': 1, 'screenId': screen,
            'startTime': f'{date.isoformat()}T{hour:02}:00:00', 'basePrice': 85000}
    body.update(changes)
    return body


data, headers = call('POST', '/api/showtimes', 201, auth=admin, body=show())
show_id = data['data']['id']
assert data['data']['endTime'] == f'{day.isoformat()}T20:00:00'
assert data['data']['status'] == 'SCHEDULED'
assert headers['Location'].endswith(f'/api/showtimes/{show_id}')
call('GET', f'/api/showtimes/{show_id}')
call('PUT', f'/api/showtimes/{show_id}', auth=admin, body=show(basePrice=90000))
for hour in (17, 18, 19):
    call('POST', '/api/showtimes', 409, auth=admin, body=show(hour=hour))
call('POST', '/api/showtimes', 201, auth=admin, body=show(hour=20))
call('POST', '/api/showtimes', 201, auth=admin, body=show(screen=4))
call('PUT', f'/api/showtimes/{show_id}', 409, auth=admin, body=show(hour=19))
data, _ = call('GET', f'/api/showtimes?movieId={movie_id}&cinemaId=1&screenId=1&date={day}&location=Ha%20Noi&status=SCHEDULED')
assert data['data']['totalElements'] == 2

for body in [show(screen=2), show(screen=3), show(screen=404), show(movieId=2147483647),
             show(basePrice=-1), show(basePrice=1.001), show(movieId=0), show(startTime=None),
             show(date=day - datetime.timedelta(days=365)), show(date=day - datetime.timedelta(days=1))]:
    call('POST', '/api/showtimes', 400, auth=admin, body=body)
call('POST', '/api/showtimes', 502, auth=admin, body=show(screen=503))
call('GET', '/api/showtimes/999999999', 404)
for path in ['/api/showtimes/no-id', '/api/showtimes?status=WRONG', '/api/showtimes?size=101',
             '/api/showtimes?page=-1', '/api/showtimes?date=invalid']:
    call('GET', path, 400)
call('POST', '/api/showtimes', 400, auth=admin, body={})
call('POST', f'/api/showtimes/{show_id}/cancel', auth=admin)
call('POST', f'/api/showtimes/{show_id}/cancel', auth=admin)
call('PUT', f'/api/showtimes/{show_id}', 409, auth=admin, body=show())
call('POST', '/api/showtimes', 201, auth=admin, body=show())
print('PASS: real Movie + Showtime + MySQL CRUD, validation, filters, overlap and cancellation', flush=True)


def race(_):
    request = urllib.request.Request(BASE + '/api/showtimes', data=json.dumps(show(screen=100)).encode(),
        headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + admin}, method='POST')
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
    outcomes = list(executor.map(race, range(8)))
assert outcomes.count(201) == 1 and outcomes.count(409) == 7, outcomes
data, _ = call('GET', '/api/showtimes?screenId=100')
assert data['data']['totalElements'] == 1
print(f'PASS: {CHECKS} HTTP checks + 8 simultaneous creates (1 created, 7 conflicts)', flush=True)
