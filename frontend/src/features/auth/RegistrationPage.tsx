import { useState } from "react";
import { Link } from "react-router-dom";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Field } from "../../components/ui/Field";
import { ErrorMessage } from "../../components/ui/Feedback";
import { Placeholder } from "../../components/ui/Placeholder";
import { useCountdown } from "../../lib/useCountdown";
import { OtpField, PasswordFields, readPassword } from "./AuthFields";
import { LoginDialog } from "./LoginDialog";
import { useRegistration, type Registration } from "./hooks";

export function RegistrationPage() {
  const { mode } = useSession();
  if (mode === "demo")
    return (
      <main className="page">
        <Placeholder
          title="Đăng ký tài khoản"
          description="Đăng ký và xác thực OTP khả dụng khi kết nối API."
        />
      </main>
    );
  return <RegistrationForm />;
}
function RegistrationForm() {
  const { request, resend, verify } = useRegistration();
  const [registration, setRegistration] = useState<Registration>();
  const [email, setEmail] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [done, setDone] = useState(false);
  const [login, setLogin] = useState(false);
  const expiry = useCountdown(registration?.expiresAt);
  const cooldown = useCountdown(registration?.resendAvailableAt);
  const busy = request.isPending || resend.isPending || verify.isPending;
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const form = new FormData(event.currentTarget);
    try {
      if (registration) {
        await verify.mutateAsync({
          registrationId: registration.registrationId,
          otp: String(form.get("otp")),
        });
        verify.reset();
        setDone(true);
      } else {
        const address = String(form.get("email")).trim();
        const result = await request.mutateAsync({
          fullName: String(form.get("fullName")).trim(),
          email: address,
          phone: String(form.get("phone")).trim() || undefined,
          password: readPassword(form),
        });
        setEmail(address);
        setRegistration(result);
        request.reset();
      }
    } catch (error) {
      setError(error);
    }
  }
  return (
    <main className="page auth-page">
      <section className="surface">
        <h1>Đăng ký tài khoản</h1>
        {done ? (
          <>
            <p role="status">
              Tài khoản đã được xác thực. Bạn có thể đăng nhập để đặt vé.
            </p>
            <Button variant="primary" onClick={() => setLogin(true)}>
              Đăng nhập
            </Button>
          </>
        ) : (
          <form onSubmit={submit}>
            <fieldset disabled={busy}>
              {registration ? (
                <>
                  <p>
                    Nhập mã OTP gửi đến <strong>{email}</strong>.
                  </p>
                  <p className="muted">
                    {expiry.expired
                      ? "Mã đã hết hạn. Yêu cầu mã mới để tiếp tục."
                      : `Mã hết hạn sau ${expiry.label}.`}
                  </p>
                  <OtpField />
                  <Button
                    disabled={!cooldown.expired || busy}
                    onClick={async () => {
                      setError(null);
                      try {
                        setRegistration(
                          await resend.mutateAsync(registration.registrationId),
                        );
                        resend.reset();
                      } catch (error) {
                        setError(error);
                      }
                    }}
                  >
                    {cooldown.expired
                      ? "Gửi lại mã OTP"
                      : `Gửi lại sau ${cooldown.label}`}
                  </Button>
                </>
              ) : (
                <>
                  <Field label="Họ và tên">
                    <input
                      name="fullName"
                      maxLength={255}
                      autoComplete="name"
                      required
                    />
                  </Field>
                  <Field label="Email">
                    <input
                      name="email"
                      type="email"
                      maxLength={255}
                      autoComplete="email"
                      required
                    />
                  </Field>
                  <Field label="Số điện thoại (không bắt buộc)">
                    <input
                      name="phone"
                      type="tel"
                      pattern="\+?[0-9]{9,15}"
                      autoComplete="tel"
                    />
                  </Field>
                  <PasswordFields />
                </>
              )}
            </fieldset>
            <ErrorMessage error={error} />
            <Button
              type="submit"
              variant="primary"
              pending={busy}
              disabled={!!registration && expiry.expired}
            >
              {registration ? "Xác thực tài khoản" : "Gửi mã xác thực"}
            </Button>
          </form>
        )}
        <Link to="/" className="text-link">
          Về danh sách phim
        </Link>
      </section>
      {login && <LoginDialog close={() => setLogin(false)} />}
    </main>
  );
}
