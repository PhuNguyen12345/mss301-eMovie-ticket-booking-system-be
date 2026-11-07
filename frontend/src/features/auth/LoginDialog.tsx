import { useEffect, useRef, useState } from "react";
import { useSession } from "../../app/ServicesProvider";
import { Button } from "../../components/ui/Button";
import { Dialog } from "../../components/ui/Dialog";
import { ErrorMessage } from "../../components/ui/Feedback";
import { Field } from "../../components/ui/Field";
import { Link } from "react-router-dom";

export function LoginDialog({ close }: { close: () => void }) {
  const session = useSession();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setPending(true);
    setError(null);
    request.current = new AbortController();
    try {
      await session.login(
        String(data.get("email")),
        String(data.get("password")),
        request.current.signal,
      );
      close();
    } catch (error) {
      if (!request.current.signal.aborted) setError(error);
    } finally {
      setPending(false);
    }
  }
  return (
    <Dialog title="Đăng nhập" onClose={close} busy={pending}>
      <form onSubmit={submit}>
        <fieldset disabled={pending}>
          <Field label="Email">
            <input name="email" type="email" autoComplete="username" required />
          </Field>
          <Field label="Mật khẩu">
            <input
              name="password"
              type="password"
              autoComplete="current-password"
              maxLength={72}
              required
            />
          </Field>
        </fieldset>
        <ErrorMessage error={error} />
        {!pending && (
          <div className="auth-links">
            <Link to="/register" onClick={close}>
              Đăng ký tài khoản
            </Link>
            <Link to="/forgot-password" onClick={close}>
              Quên mật khẩu?
            </Link>
          </div>
        )}
        <div className="dialog-actions">
          <Button onClick={close} disabled={pending}>
            Đóng
          </Button>
          <Button variant="primary" type="submit" pending={pending}>
            Đăng nhập
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
