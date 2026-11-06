import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.clock.setFixedTime(new Date("2026-11-05T08:00:00+07:00"));
  await page.goto("/");
});

test("filters select the correct movie and show an empty result", async ({
  page,
}) => {
  await expect(
    page.getByRole("heading", { name: "Lịch chiếu", exact: true }),
  ).toBeVisible();
  await page.getByLabel("Phim", { exact: true }).selectOption("2");
  await expect(page.locator(".show-row")).toHaveCount(1);
  await expect(page.locator(".panel-header p")).toHaveText("Dune: Part Two");
  await page.getByLabel("Mã phòng", { exact: true }).fill("1");
  await expect(
    page.getByText("Không có suất chiếu", { exact: true }),
  ).toBeVisible();
});

test("prices persist across reload and can be reset to base price", async ({
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

test("overlap rejection, creation and cancellation use the same data hooks", async ({
  page,
}) => {
  await page
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Phim", { exact: true }).selectOption("1");
  await dialog
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  await expect(dialog.getByRole("alert")).toContainText("đã có suất chiếu");
  await dialog.getByLabel("Giờ bắt đầu").fill("14:00");
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
  await page.getByLabel("Trạng thái").selectOption("CANCELLED");
  await expect(page.locator(".show-row")).toHaveCount(1);
});

test("dialogs support Escape and a new cinema can receive a show", async ({
  page,
}) => {
  await page.getByLabel("Mã rạp", { exact: true }).fill("2");
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
    .getByLabel("Phim", { exact: true })
    .selectOption("1");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Tạo suất chiếu", exact: true })
    .click();
  await expect(page.locator(".show-row")).toHaveCount(1);
});

test("mobile is dark, uses the Vietnamese font, and supports editing without overflow", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator(".show-row")).toHaveCount(5);
  await page.evaluate(() => document.fonts.ready);
  const style = await page.evaluate(() => ({
    width: document.documentElement.scrollWidth,
    viewport: innerWidth,
    font: getComputedStyle(document.body).fontFamily,
    scheme: getComputedStyle(document.documentElement).colorScheme,
  }));
  expect(style.width).toBeLessThanOrEqual(style.viewport);
  expect(style.font).toContain("Noto Sans Variable");
  expect(style.scheme).toBe("dark");
  await page.getByLabel("Giá Standard", { exact: true }).fill("99000");
  await page.getByRole("button", { name: "Lưu bảng giá", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Đã cập nhật");
});

test("changing the selected day does not move the visible week", async ({
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

test("unsaved prices are protected when switching showtimes", async ({
  page,
}) => {
  await page.getByLabel("Giá VIP", { exact: true }).fill("155000");
  page.once("dialog", (dialog) => dialog.dismiss());
  await page.locator(".show-row").nth(1).click();
  await expect(page.getByLabel("Giá VIP", { exact: true })).toHaveValue(
    "155000",
  );
  page.once("dialog", (dialog) => dialog.accept());
  await page.locator(".show-row").nth(1).click();
  await expect(page.locator(".panel-header p")).toHaveText("Dune: Part Two");
  await expect(page.getByLabel("Giá VIP", { exact: true })).toHaveValue(
    "125000",
  );
});
