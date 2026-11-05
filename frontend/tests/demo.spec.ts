import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-11-05T08:00:00+07:00"));
  await page.goto("/");
});

test("filtering and selecting a show updates its price editor", async ({
  page,
}) => {
  await expect(
    page.getByRole("heading", { name: "Lịch chiếu.", level: 1 }),
  ).toBeVisible();
  await page.getByLabel("Tìm phim").fill("Dune");
  await expect(page.locator(".show-row")).toHaveCount(1);
  await expect(page.locator(".price-panel h2").first()).toHaveText(
    "Dune: Part Two",
  );
  await page.getByLabel("Lọc phòng").selectOption("1");
  await expect(page.getByText("Khung giờ còn rộng mở")).toBeVisible();
  await expect(page.locator(".no-selection")).toBeVisible();
});

test("prices save, survive reload and can be reset to base-price fallback", async ({
  page,
}) => {
  await page.getByLabel("Giá VIP", { exact: true }).fill("155000");
  await page.getByRole("button", { name: "Lưu bảng giá", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Đã cập nhật");
  await page.reload();
  await expect(page.getByLabel("Giá VIP", { exact: true })).toHaveValue(
    "155000",
  );
  await page
    .getByRole("button", { name: "Dùng giá cơ bản cho tất cả" })
    .click();
  await page.getByRole("button", { name: "Lưu bảng giá", exact: true }).click();
  await expect(page.getByLabel("Giá VIP", { exact: true })).toHaveValue("");
  await expect(page.getByRole("status")).toContainText("áp dụng giá cơ bản");
});

test("overlapping creation is rejected and a new slot can be cancelled", async ({
  page,
}) => {
  await page
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  await expect(dialog.getByRole("alert")).toContainText("đã có suất chiếu");
  await dialog.locator("[name=time]").fill("14:00");
  await dialog
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  await expect(dialog).not.toBeVisible();
  await expect(page.locator(".show-row")).toHaveCount(6);
  await expect(page.locator(".panel-show-info")).toContainText("14:00");
  await page
    .getByRole("button", { name: "Hủy suất chiếu", exact: true })
    .click();
  await page.getByRole("button", { name: "Xác nhận hủy", exact: true }).click();
  await expect(page.locator(".show-row")).toHaveCount(5);
  await page.getByLabel("Lọc trạng thái").selectOption("CANCELLED");
  await expect(page.locator(".show-row")).toHaveCount(1);
  await expect(page.locator(".panel-footer")).toContainText(
    "Suất chiếu đã hủy",
  );
});

test("dialogs support Escape and creation works in another cinema", async ({
  page,
}) => {
  await page.getByLabel("RẠP ĐANG QUẢN LÝ").selectOption("2");
  await expect(page.locator(".show-row")).toHaveCount(0);
  await page
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  await expect(page.locator(".show-row")).toHaveCount(1);
});

test("mobile layout fits viewport and price editing remains usable", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator(".show-row")).toHaveCount(5);
  const overflows = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth,
  );
  expect(overflows).toBe(false);
  await page.getByLabel("Giá Standard", { exact: true }).fill("99000");
  await page.getByRole("button", { name: "Lưu bảng giá", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Đã cập nhật");
});

test("week navigation retains the visible week when selecting another day", async ({
  page,
}) => {
  await page.getByRole("button", { name: "Tuần sau", exact: true }).click();
  const days = page.locator(".day");
  const labels = await days.allTextContents();
  await days.nth(2).click();
  await expect(days.nth(2)).toHaveAttribute("aria-pressed", "true");
  expect(await days.allTextContents()).toEqual(labels);
  await page.getByRole("button", { name: "Tuần trước", exact: true }).click();
  await expect(page.locator(".show-row")).toHaveCount(5);
});
