import { useState } from "react";
import { useSession } from "../../app/ServicesProvider";
import { useProfile, useChangePassword } from "./hooks";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import { Dialog } from "../../components/ui/Dialog";
import { Loading, ErrorMessage } from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import { useCountdown } from "../../lib/useCountdown";
export function AccountPage() {
  const profile = useProfile();
  const session = useSession();
  const [password, setPassword] = useState(false);
  return (
    <main className="page cinema-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">TÀI KHOẢN</p>
          <h1>Thông tin của bạn</h1>
        </div>
        <Button onClick={() => setPassword(true)}>Đổi mật khẩu</Button>
      </div>
      {profile.isPending ? (
        <Loading />
      ) : profile.isError ? (
        <ErrorMessage
          error={profile.error}
          retry={() => {
            void profile.refetch();
          }}
        />
      ) : (
        <section className="surface profile-card">
          <h2>{profile.data.fullName}</h2>
          <dl className="summary-lines">
            {[
              ["Email", profile.data.email],
              ["Số điện thoại", profile.data.phone],
              ["Ngày sinh", profile.data.dateOfBirth],
              [
                "Địa chỉ",
                [
                  profile.data.address,
                  profile.data.district,
                  profile.data.cityProvince,
                ]
                  .filter(Boolean)
                  .join(", "),
              ],
            ].map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value || "Chưa cập nhật"}</dd>
              </div>
            ))}
          </dl>
          <Placeholder compact title="Chỉnh sửa thông tin" />
        </section>
      )}
      <p className="muted account-note">
        Đăng xuất sẽ kết thúc phiên trên trình duyệt này.
      </p>
      {password && (
        <PasswordDialog
          close={() => setPassword(false)}
          logout={session.logout}
        />
      )}
    </main>
  );
}
function PasswordDialog({
  close,
  logout,
}: {
  close: () => void;
  logout: () => void;
}) {
  const { request, confirm } = useChangePassword();
  const [error, setError] = useState<unknown>(null);
  const timer = useCountdown(request.data?.expiresAt);
  const pending = request.isPending || confirm.isPending;
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const form = new FormData(event.currentTarget);
    try {
      if (!request.data)
        await request.mutateAsync(String(form.get("currentPassword")));
      else {
        const password = String(form.get("newPassword"));
        if (password !== form.get("repeat"))
          throw new Error("Mật khẩu nhập lại không khớp.");
        await confirm.mutateAsync({
          otp: String(form.get("otp")),
          newPassword: password,
        });
      }
    } catch (error) {
      setError(error);
    }
  }
  return (
    <Dialog title="Đổi mật khẩu" onClose={close} busy={pending}>
      {confirm.isSuccess ? (
        <>
          <p role="status">Đã đổi mật khẩu. Đăng nhập lại để tiếp tục.</p>
          <Button
            onClick={() => {
              close();
              logout();
            }}
          >
            Đăng nhập lại
          </Button>
        </>
      ) : (
        <form onSubmit={submit}>
          <fieldset disabled={pending}>
            {!request.data ? (
              <Field label="Mật khẩu hiện tại">
                <input
                  name="currentPassword"
                  type="password"
                  autoComplete="current-password"
                  required
                />
              </Field>
            ) : (
              <>
                <p className="notice">
                  Nhập mã OTP gửi đến email của bạn. Mã hết hạn sau{" "}
                  {timer.label}.
                </p>
                <Field label="Mã OTP">
                  <input
                    name="otp"
                    inputMode="numeric"
                    pattern="[0-9]{6}"
                    maxLength={6}
                    autoComplete="one-time-code"
                    required
                  />
                </Field>
                <Field label="Mật khẩu mới">
                  <input
                    name="newPassword"
                    type="password"
                    minLength={8}
                    maxLength={72}
                    autoComplete="new-password"
                    required
                  />
                </Field>
                <Field label="Nhập lại mật khẩu mới">
                  <input
                    name="repeat"
                    type="password"
                    minLength={8}
                    maxLength={72}
                    autoComplete="new-password"
                    required
                  />
                </Field>
                {timer.expired && (
                  <Button
                    onClick={() => {
                      request.reset();
                      setError(null);
                    }}
                  >
                    Yêu cầu mã mới
                  </Button>
                )}
              </>
            )}
          </fieldset>
          <ErrorMessage error={error} />
          <div className="dialog-actions">
            <Button disabled={pending} onClick={close}>
              Đóng
            </Button>
            <Button
              variant="primary"
              type="submit"
              pending={pending}
              disabled={!!request.data && timer.expired}
            >
              {request.data ? "Xác nhận đổi mật khẩu" : "Gửi mã OTP"}
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  );
}
