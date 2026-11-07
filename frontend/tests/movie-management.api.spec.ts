import { expect, test, type Page } from "@playwright/test";
const png = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=",
  "base64",
);
const pageOf = (rows: unknown[], page = 0, size = 10) => ({
  data: rows.slice(page * size, (page + 1) * size),
  page,
  size,
  totalElements: rows.length,
  totalPages: Math.ceil(rows.length / size),
  first: page === 0,
  last: (page + 1) * size >= rows.length,
});
async function setup(page: Page) {
  const genres = Array.from({ length: 11 }, (_, i) => ({
    id: i + 1,
    name: `Thể loại ${i + 1}`,
    description: null as string | null,
  }));
  const state = {
    genres,
    persons: [{ id: 1, fullName: "Nguyễn An" }],
    linked: [genres[10]],
    credits: [] as any[],
    writes: [] as {
      path: string;
      method: string;
      body: any;
      headers: Record<string, string>;
    }[],
    genreStatus: 200,
    deleteStatus: 409,
    posterStatus: 503,
    posterUrl: null as string | null,
    refreshCalls: 0,
    role: "ADMIN",
  };
  await page.route("https://assets.example.test/**", (route) =>
    route.fulfill({ contentType: "image/png", body: png }),
  );
  await page.route(
    (url) => url.pathname.startsWith("/api/"),
    async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = url.pathname,
        method = req.method();
      const multipart = req
        .headers()
        ["content-type"]?.includes("multipart/form-data");
      const body = req.postData()
        ? multipart
          ? req.postData()
          : req.postDataJSON()
        : null;
      const send = (data: unknown, status = 200, message = "OK") =>
        route.fulfill({ status, json: { status, message, data } });
      if (method !== "GET")
        state.writes.push({ path, method, body, headers: req.headers() });
      if (path === "/api/auth/login" || path === "/api/auth/refresh") {
        if (path.endsWith("/refresh")) state.refreshCalls++;
        return send({
          accessToken: `token-${state.refreshCalls}`,
          tokenType: "Bearer",
          expiresIn: 100,
          refreshToken: `refresh-${state.refreshCalls}`,
          refreshExpiresIn: 10000,
          user: { userId: 42, fullName: "Admin", roles: [state.role] },
        });
      }
      const movie = {
        id: 1,
        title: "Phim kiểm thử",
        durationMinutes: 120,
        status: "PRESENTING",
        releaseDate: "2030-01-01",
        posterUrl: state.posterUrl,
        genres: state.linked,
        credits: state.credits,
      };
      if (path === "/api/movies") return send(pageOf([movie]));
      if (path === "/api/movies/1") return send(movie);
      if (path === "/api/movies/1/genres") {
        if (method === "PUT") {
          if (state.genreStatus !== 200)
            return send(null, state.genreStatus, "Thể loại đã thay đổi");
          state.linked = body.genreIds.map((id: number) =>
            state.genres.find((g) => g.id === id)!,
          );
        }
        return send(state.linked);
      }
      if (path === "/api/movies/1/credits") {
        if (method === "PUT")
          state.credits = body.credits.map((c: any, index: number) => ({
            ...c,
            id: index + 1,
            fullName: state.persons.find((p) => p.id === c.personId)!.fullName,
          }));
        return send(state.credits);
      }
      if (
        path === "/api/movies/1/poster" ||
        path === "/api/movies/1/poster/from-url"
      ) {
        if (state.posterStatus !== 200) return send(null, state.posterStatus);
        state.posterUrl = "https://assets.example.test/poster.png";
        return send({ movieId: 1, posterUrl: state.posterUrl });
      }
      if (path === "/api/genres") {
        if (method === "POST") {
          const created = { id: 12, ...body };
          state.genres.push(created);
          return send(created, 201);
        }
        return send(
          pageOf(
            state.genres.filter((g) =>
              g.name.includes(url.searchParams.get("keyword") ?? ""),
            ),
            Number(url.searchParams.get("page")),
            Number(url.searchParams.get("size") ?? 10),
          ),
        );
      }
      if (/^\/api\/genres\/\d+$/.test(path)) {
        const id = Number(path.split("/").at(-1));
        if (method === "DELETE") {
          if (state.deleteStatus !== 200)
            return send(null, 409, "Thể loại đang được sử dụng");
          state.genres = state.genres.filter((g) => g.id !== id);
          return send(null);
        }
        const index = state.genres.findIndex((g) => g.id === id);
        state.genres[index] = { id, ...body };
        return send(state.genres[index]);
      }
      if (path === "/api/persons") {
        if (method === "POST") {
          const created = { id: 2, ...body };
          state.persons.push(created);
          return send(created, 201);
        }
        return send(
          pageOf(
            state.persons.filter((p) =>
              p.fullName.includes(url.searchParams.get("keyword") ?? ""),
            ),
          ),
        );
      }
      if (path === "/api/showtimes") return send(pageOf([]));
      if (path === "/api/cinemas") return send([]);
      if (path === "/api/users/me")
        return send({
          userId: 42,
          fullName: "Admin",
          email: "admin@example.test",
          phone: null,
          dateOfBirth: null,
          gender: null,
          cityProvince: null,
          district: null,
          address: null,
          preferredCinemaId: null,
          avatarUrl: null,
        });
      return send(null, 404);
    },
  );
  return state;
}
async function login(page: Page, path: string) {
  await page.goto(path);
  await page
    .locator(".topbar")
    .getByRole("button", { name: "Đăng nhập", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Email").fill("admin@example.test");
  await dialog.getByLabel("Mật khẩu", { exact: true }).fill("Password123");
  await dialog.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(dialog).toBeHidden();
}
test("reference admin supports server pagination, create/edit and explicit delete conflicts", async ({
  page,
}) => {
  const state = await setup(page);
  await login(page, "/admin/genres");
  await page.getByRole("button", { name: "Trang sau" }).click();
  await expect(
    page.getByRole("heading", { name: "Thể loại 11", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Thêm thể loại", exact: true })
    .click();
  let dialog = page.getByRole("dialog");
  await dialog.getByLabel("Tên thể loại").fill("Khoa học");
  await dialog.getByLabel("Mô tả").fill("Phim khoa học");
  await dialog.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(dialog).toBeHidden();
  await page.getByRole("button", { name: "Sửa Khoa học", exact: true }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("Tên thể loại").fill("Khoa học viễn tưởng");
  await dialog.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(dialog).toBeHidden();
  await page
    .getByRole("button", { name: "Xóa Khoa học viễn tưởng", exact: true })
    .click();
  expect(state.writes.filter((w) => w.method === "DELETE")).toHaveLength(0);
  dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "Xác nhận xóa" }).click();
  await expect(dialog.getByRole("alert")).toContainText("đang được sử dụng");
  expect(state.writes.filter((w) => w.method === "DELETE")).toHaveLength(1);
  state.deleteStatus = 200;
  await dialog.getByRole("button", { name: "Xác nhận xóa" }).click();
  await expect(dialog).toBeHidden();
});
test("movie genres preserve selections outside the first catalogue page and retain failed edits", async ({
  page,
}) => {
  const state = await setup(page);
  await login(page, "/admin/movies/1");
  await expect(
    page.getByRole("button", { name: "Bỏ thể loại Thể loại 11", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Chọn thể loại", { exact: true }).selectOption("1");
  const section = page.locator("section").filter({
    has: page.getByRole("heading", { name: "Thể loại phim", exact: true }),
  });
  await section.getByRole("button", { name: "Thêm vào phim" }).click();
  state.genreStatus = 409;
  await page.getByRole("button", { name: "Lưu thể loại", exact: true }).click();
  await expect(section.getByRole("alert")).toContainText(
    "Thể loại đã thay đổi",
  );
  expect(state.writes.find((w) => w.path.endsWith("/1/genres"))?.body).toEqual({
    genreIds: [11, 1],
  });
  state.genreStatus = 200;
  await page.getByRole("button", { name: "Lưu thể loại", exact: true }).click();
  await expect(section.getByRole("status")).toContainText("Đã lưu thể loại");
});
test("credits support two roles and customer detail renders persisted names", async ({
  page,
}) => {
  const state = await setup(page);
  await login(page, "/admin/movies/1");
  const section = page.locator(".movie-credits-panel");
  await page.getByLabel("Vai trò thêm vào phim").selectOption("DIRECTOR");
  await page.getByLabel("Chọn diễn viên / đạo diễn").selectOption("1");
  await section.getByRole("button", { name: "Thêm vào phim" }).click();
  await page.getByLabel("Vai trò thêm vào phim").selectOption("ACTOR");
  await page.getByLabel("Chọn diễn viên / đạo diễn").selectOption("1");
  await section.getByRole("button", { name: "Thêm vào phim" }).click();
  await page.getByLabel("Vai diễn của Nguyễn An").fill("Nhân vật A");
  await page.getByRole("button", { name: "Lưu ê-kíp" }).click();
  await expect(section.getByRole("status")).toContainText("Đã lưu ê-kíp");
  expect(state.writes.find((w) => w.path.endsWith("/credits"))?.body).toEqual({
    credits: [
      {
        personId: 1,
        creditType: "DIRECTOR",
        characterName: null,
        displayOrder: 1,
      },
      {
        personId: 1,
        creditType: "ACTOR",
        characterName: "Nhân vật A",
        displayOrder: 2,
      },
    ],
  });
  await page.goto("/movies/1");
  await expect(page.locator(".movie-credits")).toContainText(
    "Nguyễn An (Nhân vật A)",
  );
});
test("poster uploads use multipart and do not show success when storage fails", async ({
  page,
}) => {
  const state = await setup(page);
  await login(page, "/admin/movies/1");
  await page
    .getByLabel("Ảnh poster", { exact: true })
    .setInputFiles({ name: "poster.png", mimeType: "image/png", buffer: png });
  await page.getByRole("button", { name: "Lưu poster" }).click();
  const section = page.locator("section").filter({
    has: page.getByRole("heading", { name: "Poster", exact: true }),
  });
  await expect(section.getByRole("alert")).toContainText(
    "Dịch vụ đang gặp sự cố",
  );
  await expect(page.getByAltText("Poster đã chọn, chưa lưu")).toBeVisible();
  const writes = state.writes.filter((w) => w.path === "/api/movies/1/poster");
  expect(writes).toHaveLength(1);
  expect(writes[0].headers["content-type"]).toContain(
    "multipart/form-data; boundary=",
  );
  expect(writes[0].body).toContain('name="file"; filename="poster.png"');
  state.posterStatus = 200;
  await page.getByRole("button", { name: "Lưu poster" }).click();
  await expect(section.getByRole("status")).toContainText("Đã cập nhật poster");
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
});
test("navigation protects unsaved movie metadata", async ({ page }) => {
  await setup(page);
  await login(page, "/admin/movies/1");
  await page.getByRole("button", { name: "Bỏ thể loại Thể loại 11" }).click();
  const dismissed = page
    .waitForEvent("dialog")
    .then((dialog) => dialog.dismiss());
  await page.getByRole("link", { name: "← Quản lý phim" }).click();
  await dismissed;
  await expect(page).toHaveURL(/admin\/movies\/1$/);
  const accepted = page
    .waitForEvent("dialog")
    .then((dialog) => dialog.accept());
  await page.getByRole("link", { name: "← Quản lý phim" }).click();
  await accepted;
  await expect(page).toHaveURL(/admin\/movies$/);
});
test("automatic refresh keeps the active session and rotated credentials out of browser storage", async ({
  page,
}) => {
  await page.clock.install({ time: new Date("2030-01-01T00:00:00Z") });
  const state = await setup(page);
  await login(page, "/admin/movies");
  await page.clock.runFor(81000);
  await expect.poll(() => state.refreshCalls).toBe(1);
  await page.getByRole("link", { name: "Tài khoản", exact: true }).click();
  await expect(
    page.getByText("admin@example.test", { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Đăng xuất" })).toBeVisible();
  expect(
    await page.evaluate(
      () => JSON.stringify(localStorage) + JSON.stringify(sessionStorage),
    ),
  ).not.toMatch(/token-|refresh-/);
});
