#!/usr/bin/env python3
"""Replace local test data with a realistic demo catalogue.

  python3 tools/seed/seed_demo.py reset          # back up, then show what would be deleted
  python3 tools/seed/seed_demo.py reset --yes    # back up and delete the test data
  python3 tools/seed/seed_demo.py seed           # create movies, cinemas, showtimes, combos and vouchers

Run from the repository root while `docker-compose up` is running. Only the Python standard library is used.
See tools/seed/README.md.
"""
import argparse
import datetime as dt
import json
import os
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
BACKUPS = ROOT / ".seed-backups"
GATEWAY = os.environ.get("SEED_GATEWAY_URL", "http://127.0.0.1:8080").rstrip("/")
DATABASES = ["movie-db", "cinema-db", "showtime-db", "booking-db", "user-db"]
UA = "emovie-seed/1.0 (school demo project)"

# Tables emptied by `reset`, children first. Flyway history and user accounts are never touched.
RESET = {
    "booking-db": ["booking_check_ins", "booking_vouchers", "booking_combos", "booking_seats", "refunds",
                   "payment_transactions", "user_vouchers", "vouchers", "bookings", "combos"],
    "showtime-db": ["showtime_seat_prices", "showtime"],
    "movie-db": ["movie_credit", "movie_genre", "movie", "person", "genre"],
}

CINEMAS = [
    # code, name, location. DEMO_* already exist: cinema-service recreates them on startup, so they are renamed.
    ("DEMO_HN", "eMovie Hà Nội – Hoàn Kiếm", "Hà Nội"),
    ("DEMO_HCM", "eMovie Sài Gòn – Quận 1", "Hồ Chí Minh"),
    ("EM_DN", "eMovie Đà Nẵng – Hải Châu", "Đà Nẵng"),
]
SCREENS = [("S1", "Phòng chiếu 1"), ("S2", "Phòng chiếu 2")]
ROWS, SEATS_PER_ROW, VIP_ROWS = "ABCDEFGH", 12, set("FGH")

COMBOS = [
    ("Bắp rang bơ (L)", "1 bắp rang bơ cỡ lớn", 59000),
    ("Nước ngọt (L)", "1 ly nước ngọt cỡ lớn", 39000),
    ("Combo Solo", "1 bắp rang bơ + 1 nước ngọt", 89000),
    ("Combo Đôi", "1 bắp rang bơ cỡ lớn + 2 nước ngọt", 129000),
    ("Combo Gia Đình", "2 bắp rang bơ + 4 nước ngọt", 219000),
    ("Nachos phô mai", "1 phần nachos kèm sốt phô mai", 69000),
]

SHOW_DAYS = 7
FIRST_SHOW, LAST_START = dt.time(9, 0), dt.time(22, 30)
CLEANING = dt.timedelta(minutes=15)
VIP_SURCHARGE = 30000


def price_for(start):
    base = 75000 if start.hour < 17 else 95000
    return base + (10000 if start.weekday() >= 5 else 0)


# ---------- database access through docker-compose ----------

def compose(*args, stdin=None, capture=True):
    result = subprocess.run(["docker-compose", *args], cwd=ROOT, input=stdin, capture_output=capture, text=True)
    if result.returncode != 0:
        raise SystemExit(f"docker-compose {' '.join(args[:3])} failed: {result.stderr.strip()[:300]}")
    return result.stdout


def sql(service, statements):
    return compose("exec", "-T", service, "sh", "-c",
                   'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -u "$MYSQL_USER" "$MYSQL_DATABASE"',
                   stdin=statements)


def backup():
    folder = BACKUPS / dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    folder.mkdir(parents=True)
    for service in DATABASES:
        dump = compose("exec", "-T", service, "sh", "-c",
                       'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --no-tablespaces --single-transaction '
                       '--default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"')
        (folder / f"{service}.sql").write_text(dump, encoding="utf-8")
        print(f"  backed up {service}: {len(dump) // 1024} KiB")
    return folder


def reset(confirmed):
    folder = backup()
    print(f"Backup: {folder.relative_to(ROOT)}")
    for service, tables in RESET.items():
        counts = sql(service, "".join(f"SELECT '{t}', COUNT(*) FROM `{t}`;\n" for t in tables))
        print(f"  {service}: " + ", ".join(line.replace("\t", "=") for line in counts.split("\n") if line))
    if not confirmed:
        print("Nothing deleted. Run again with --yes to delete the rows above.")
        return
    for service, tables in RESET.items():
        statements = "SET FOREIGN_KEY_CHECKS = 0;\n" + "".join(
            f"DELETE FROM `{t}`;\nALTER TABLE `{t}` AUTO_INCREMENT = 1;\n" for t in tables) + "SET FOREIGN_KEY_CHECKS = 1;\n"
        sql(service, statements)
    # The sample seats have a VIP row C; the demo layout keeps VIP seats at the back (rows F-H).
    sql("cinema-db", """
        UPDATE seats s JOIN screens sc ON sc.id = s.screen_id JOIN cinemas c ON c.id = sc.cinema_id
        SET s.seat_type_id = (SELECT id FROM seat_types WHERE code = 'STANDARD')
        WHERE c.code IN ('DEMO_HN', 'DEMO_HCM') AND s.row_code NOT IN ('F', 'G', 'H');
    """)
    print("Test data deleted. User accounts, cinemas and seats were kept.")


# ---------- HTTP through the gateway ----------

class Api:
    def __init__(self, email, password):
        self.email, self.password, self.token = email, password, None
        self.login()

    def login(self):
        status, body = self._send("POST", "/api/auth/login", {"email": self.email, "password": self.password}, auth=False)
        if status != 200:
            raise SystemExit(f"Admin login failed ({status}). Check SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD.")
        self.token = body["data"]["accessToken"]

    def _send(self, method, path, body=None, auth=True, raw=None, content_type="application/json"):
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        request = urllib.request.Request(GATEWAY + path, data=data, method=method)
        if data is not None:
            request.add_header("Content-Type", content_type)
        if auth and self.token:
            request.add_header("Authorization", "Bearer " + self.token)
        for attempt in range(4):
            try:
                with urllib.request.urlopen(request, timeout=60) as response:
                    return response.status, json.loads(response.read() or b"{}")
            except urllib.error.HTTPError as error:
                payload = error.read()
                if error.code in (502, 503, 504) and attempt < 3:
                    time.sleep(3 * (attempt + 1))
                    continue
                try:
                    return error.code, json.loads(payload or b"{}")
                except ValueError:
                    return error.code, {}
            except (urllib.error.URLError, TimeoutError):
                if attempt == 3:
                    raise
                time.sleep(3 * (attempt + 1))
        return 0, {}

    def call(self, method, path, body=None, expect=(200, 201), **kwargs):
        status, data = self._send(method, path, body, **kwargs)
        if status == 401:
            self.login()
            status, data = self._send(method, path, body, **kwargs)
        if expect and status not in expect:
            raise RuntimeError(f"{method} {path} -> {status} {data.get('message')}")
        return status, data.get("data")

    def get(self, path):
        return self.call("GET", path)[1]

    def pages(self, path):
        page, items = 0, []
        while True:
            sep = "&" if "?" in path else "?"
            data = self.get(f"{path}{sep}page={page}&size=100")
            items += data["data"]
            if data.get("last", True):
                return items
            page += 1


class TemporaryAdmin:
    """An admin account that exists only while the seed runs, unless SEED_ADMIN_* are given."""

    def __enter__(self):
        given = os.environ.get("SEED_ADMIN_EMAIL"), os.environ.get("SEED_ADMIN_PASSWORD")
        if all(given):
            self.created = False
            return given
        self.created = True
        self.email = f"seed-admin-{uuid.uuid4().hex[:10]}@example.invalid"
        password = secrets.token_urlsafe(18) + "Aa1"
        try:
            hashed = subprocess.run(["htpasswd", "-bnBC", "10", "", password], capture_output=True, text=True,
                                    check=True).stdout.strip().lstrip(":").replace("$2y$", "$2a$", 1)
        except (FileNotFoundError, subprocess.CalledProcessError):
            raise SystemExit("htpasswd is missing. Install apache2-utils or set SEED_ADMIN_EMAIL and SEED_ADMIN_PASSWORD.")
        sql("user-db", f"""
            INSERT INTO users (full_name, email, password_hash, status, email_verified_at, created_at)
            VALUES ('Seed Admin', '{self.email}', '{hashed}', 'ACTIVE', NOW(6), NOW(6));
            INSERT INTO user_roles (user_id, role_id)
            SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'ADMIN' WHERE u.email = '{self.email}';
        """)
        return self.email, password

    def __exit__(self, *exc):
        if self.created:
            sql("user-db", f"""
                DELETE t FROM refresh_tokens t JOIN users u ON u.id = t.user_id WHERE u.email = '{self.email}';
                DELETE ur FROM user_roles ur JOIN users u ON u.id = ur.user_id WHERE u.email = '{self.email}';
                DELETE FROM users WHERE email = '{self.email}';
            """)
        return False


# ---------- seed steps ----------

def seed_cinemas(api):
    types = {t["code"]: t["id"] for t in api.get("/api/seat-types")}
    for code, name in (("STANDARD", "Ghế thường"), ("VIP", "Ghế VIP")):
        if code not in types:
            types[code] = api.call("POST", "/api/seat-types", {"code": code, "name": name})[1]["id"]
    existing = {c["code"]: c for c in api.get("/api/cinemas")}
    screens, created_seats = [], 0
    for code, name, location in CINEMAS:
        body = {"code": code, "name": name, "location": location}
        cinema = (api.call("PUT", f"/api/cinemas/{existing[code]['id']}", body)[1] if code in existing
                  else api.call("POST", "/api/cinemas", body)[1])
        have = {s["code"]: s for s in api.get(f"/api/cinemas/{cinema['id']}/screens")}
        for screen_code, screen_name in SCREENS:
            screen_body = {"code": screen_code, "name": screen_name}
            screen = (api.call("PUT", f"/api/screens/{have[screen_code]['id']}", screen_body)[1] if screen_code in have
                      else api.call("POST", f"/api/cinemas/{cinema['id']}/screens", screen_body)[1])
            taken = {(s["rowCode"], s["seatNumber"]) for s in api.get(f"/api/screens/{screen['id']}/seats")}
            for row in ROWS:
                for number in range(1, SEATS_PER_ROW + 1):
                    if (row, number) in taken:
                        continue
                    seat_type = types["VIP" if row in VIP_ROWS else "STANDARD"]
                    api.call("POST", f"/api/screens/{screen['id']}/seats",
                             {"rowCode": row, "seatNumber": number, "seatTypeId": seat_type})
                    created_seats += 1
            screens.append((cinema, screen))
    print(f"  cinemas: {len(CINEMAS)}, screens: {len(screens)}, new seats: {created_seats}")
    return screens, types


def seed_movies(api, movies, upload_posters):
    genres = {g["name"]: g["id"] for g in api.pages("/api/genres")}
    people = {}
    for p in api.pages("/api/persons"):
        people.setdefault(p["fullName"], p["id"])
    titles = {m["title"]: m for m in api.pages("/api/movies")}
    created = []
    for movie in movies:
        for name in movie["genres"]:
            if name not in genres:
                genres[name] = api.call("POST", "/api/genres", {"name": name})[1]["id"]
        for name in movie["directors"] + [c["name"] for c in movie["cast"]]:
            if name not in people:
                people[name] = api.call("POST", "/api/persons", {"fullName": name})[1]["id"]
        if movie["title"] in titles:
            created.append((movie, titles[movie["title"]]["id"]))
            continue
        description = movie["description"]
        if movie.get("durationEstimated"):
            description += " (Thời lượng dự kiến.)"
        body = {"title": movie["title"], "description": f"Tên gốc: {movie['originalTitle']}. {description}",
                "durationMinutes": movie["durationMinutes"], "releaseDate": movie["releaseDate"],
                "language": movie["language"], "ageRating": movie["ageRating"], "posterUrl": movie["posterUrl"],
                "trailerUrl": movie.get("trailerUrl"), "status": movie["status"]}
        movie_id = api.call("POST", "/api/movies", body)[1]["id"]
        api.call("PUT", f"/api/movies/{movie_id}/genres", {"genreIds": [genres[g] for g in movie["genres"]]})
        credits = [{"personId": people[d], "creditType": "DIRECTOR", "displayOrder": i + 1}
                   for i, d in enumerate(movie["directors"])]
        credits += [{"personId": people[c["name"]], "creditType": "ACTOR", "characterName": c.get("character"),
                     "displayOrder": i + 1} for i, c in enumerate(movie["cast"])]
        api.call("PUT", f"/api/movies/{movie_id}/credits", {"credits": credits})
        if upload_posters:
            upload_poster(api, movie_id, movie["posterUrl"])
        created.append((movie, movie_id))
    print(f"  movies: {len(created)}, genres: {len(genres)}, people: {len(people)}")
    return created


def upload_poster(api, movie_id, url):
    """Copies the poster into the team's Cloudinary; on any failure the Wikimedia link stays."""
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=60) as r:
            image, mime = r.read(), r.headers.get_content_type()
        boundary = uuid.uuid4().hex
        name = url.rsplit("/", 1)[-1]
        payload = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
                   f"Content-Type: {mime}\r\n\r\n").encode() + image + f"\r\n--{boundary}--\r\n".encode()
        api.call("PUT", f"/api/movies/{movie_id}/poster", raw=payload,
                 content_type=f"multipart/form-data; boundary={boundary}")
    except Exception as error:  # noqa: BLE001 - a poster is optional
        print(f"    poster upload skipped for movie {movie_id}: {error}")


def seed_showtimes(api, movies, screens, types):
    showing = [(m, i) for m, i in movies if m["status"] in ("PRESENTING", "WILL_PRESENT")]
    today = dt.date.today()
    created = skipped = 0
    for index, (cinema, screen) in enumerate(screens):
        rotation = index * 3
        for offset in range(1, SHOW_DAYS + 1):
            day = today + dt.timedelta(days=offset)
            playable = [(m, i) for m, i in showing if dt.date.fromisoformat(m["releaseDate"]) <= day]
            if not playable:
                continue
            start = dt.datetime.combine(day, FIRST_SHOW)
            while start.time() <= LAST_START and start.date() == day:
                movie, movie_id = playable[rotation % len(playable)]
                rotation += 1
                base = price_for(start)
                status, data = api.call("POST", "/api/showtimes", {
                    "movieId": movie_id, "cinemaId": cinema["id"], "screenId": screen["id"],
                    "startTime": start.isoformat(timespec="minutes"), "basePrice": base}, expect=None)
                if status == 201:
                    api.call("PUT", f"/api/showtimes/{data['id']}/seat-prices", [
                        {"seatTypeId": types["STANDARD"], "price": base},
                        {"seatTypeId": types["VIP"], "price": base + VIP_SURCHARGE}])
                    created += 1
                elif status == 409:
                    skipped += 1
                else:
                    raise RuntimeError(f"showtime at {start} -> {status} {(data or {})}")
                end = start + dt.timedelta(minutes=movie["durationMinutes"]) + CLEANING
                start = end + dt.timedelta(minutes=(-end.minute) % 5)
    print(f"  showtimes: {created} created, {skipped} already there")


def seed_combos(api):
    have = {c["name"]: c for c in api.get("/api/combos/all")}
    ids = {}
    for name, description, price in COMBOS:
        ids[name] = have[name]["id"] if name in have else api.call(
            "POST", "/api/combos", {"name": name, "description": description, "price": price})[1]["id"]
    print(f"  combos: {len(ids)}")
    return ids


def seed_vouchers(api, combos, types):
    start = dt.date.today()
    end = start + dt.timedelta(days=60)
    plans = [
        ("EMOVIE-GIAM20", 3, {"kind": "TICKET_DISCOUNT", "discountType": "PERCENT", "discountValue": 20},
         "Giảm 20% tiền vé"),
        ("EMOVIE-30K", 3, {"kind": "TICKET_DISCOUNT", "discountType": "FIXED", "discountValue": 30000,
                           "minOrderAmount": 150000}, "Giảm 30.000đ tiền vé cho đơn từ 150.000đ"),
        ("EMOVIE-VEFREE", 2, {"kind": "TICKET_EXCHANGE", "seatTypeId": types["STANDARD"]}, "Đổi 1 vé ghế thường"),
        ("EMOVIE-SOLO", 2, {"kind": "COMBO_EXCHANGE", "comboId": combos["Combo Solo"]}, "Đổi 1 Combo Solo"),
        ("EMOVIE-COMBO50", 2, {"kind": "COMBO_DISCOUNT", "discountType": "PERCENT", "discountValue": 50},
         "Giảm 50% tiền combo"),
    ]
    have = {v["code"] for v in api.pages("/api/vouchers")}
    lines = []
    for prefix, count, terms, label in plans:
        for n in range(1, count + 1):
            code = f"{prefix}-{n:02d}"
            if code not in have:
                api.call("POST", "/api/vouchers", dict(terms, code=code, startDate=start.isoformat(),
                                                       endDate=end.isoformat()))
            lines.append(f"{code}\t{label}\thạn {end.isoformat()}")
    BACKUPS.mkdir(exist_ok=True)
    out = BACKUPS / "vouchers.txt"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"  vouchers: {len(lines)} (codes in {out.relative_to(ROOT)})")


def seed(upload_posters):
    movies = json.loads((HERE / "movies.json").read_text(encoding="utf-8"))
    with TemporaryAdmin() as (email, password):
        api = Api(email, password)
        print("Seeding through", GATEWAY)
        screens, types = seed_cinemas(api)
        created = seed_movies(api, movies, upload_posters)
        combos = seed_combos(api)
        seed_vouchers(api, combos, types)
        seed_showtimes(api, created, screens, types)
    print("Done.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    r = sub.add_parser("reset", help="back up every database, then delete the test data")
    r.add_argument("--yes", action="store_true", help="really delete; without it only the counts are shown")
    s = sub.add_parser("seed", help="create the demo catalogue through the API gateway")
    s.add_argument("--upload-posters", action="store_true", help="copy posters into Cloudinary")
    args = parser.parse_args()
    if args.command == "reset":
        reset(args.yes)
    else:
        seed(args.upload_posters)


if __name__ == "__main__":
    sys.exit(main())
