import {
  createContext,
  useContext,
  useEffect,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import {
  QueryClient,
  QueryClientProvider,
  useQueryClient,
} from "@tanstack/react-query";
import {
  createHttpClient,
  shouldRetryQuery,
  type HttpClient,
} from "../lib/http";
import { createSessionStore } from "../features/auth/session";
import { createDemoShowtimes } from "../features/showtimes/api/demo-showtimes";
import { createHttpShowtimes } from "../features/showtimes/api/http-showtimes";
import { createMovieApi } from "../features/movies/api";
import { createBookingApi } from "../features/booking/api";
import { createCinemaApi } from "../features/cinema/api";
import { createComboApi } from "../features/combos/api";
import { createMovieManagementApi } from "../features/movie-management/api";

function createServices() {
  const mode = import.meta.env.VITE_DATA_MODE || "api";
  const session = createSessionStore({
    cookie: mode === "api",
    exclusive: (operation) =>
      navigator.locks
        ? navigator.locks.request("emovie-browser-session", operation)
        : operation(),
  });
  const client: HttpClient = createHttpClient({
    baseUrl: import.meta.env.VITE_API_BASE_URL || "/api",
    accessToken: () => session.accessToken(client),
    onUnauthorized: (token) => {
      if (session.getSnapshot()?.token === token) session.clear();
    },
  });
  if (mode !== "demo" && mode !== "api")
    throw new Error("VITE_DATA_MODE must be demo or api");
  return {
    session,
    sessionSource: crypto.randomUUID(),
    client,
    movies: createMovieApi(client),
    booking: createBookingApi(client),
    cinema: createCinemaApi(client),
    combos: createComboApi(client),
    movieManagement: createMovieManagementApi(client),
    showtimes:
      mode === "api" ? createHttpShowtimes(client) : createDemoShowtimes(),
  };
}
const ServicesContext = createContext<ReturnType<typeof createServices> | null>(
  null,
);
export function ServicesProvider({ children }: { children: ReactNode }) {
  const [services] = useState(createServices);
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30000,
            gcTime: 300000,
            retry: shouldRetryQuery,
            retryDelay: (attempt) => Math.min(500 * 2 ** attempt, 4000),
          },
          mutations: { retry: false, gcTime: 0 },
        },
      }),
  );
  const current = useSyncExternalStore(
    services.session.subscribe,
    services.session.getSnapshot,
  );
  useEffect(() => {
    void services.session.restore(services.client);
    if (typeof BroadcastChannel === "undefined") return;
    const channel = new BroadcastChannel("emovie-session-events");
    channel.onmessage = (event: MessageEvent<unknown>) => {
      if (event.data === services.sessionSource) return;
      services.session.clear();
      void services.session.restore(services.client, true);
    };
    return () => channel.close();
  }, [services]);
  useEffect(() => {
    const identity = () => {
      const session = services.session.getSnapshot();
      return session
        ? `${session.sessionId}:${[...session.user.roles].sort().join(",")}`
        : undefined;
    };
    let userId = identity();
    return services.session.subscribe(() => {
      const nextId = identity();
      if (nextId !== userId) {
        void queryClient.cancelQueries({ queryKey: ["private"] });
        queryClient.removeQueries({ queryKey: ["private"] });
      }
      userId = nextId;
    });
  }, [services, queryClient]);
  useEffect(() => {
    if (!current) return;
    const maintain = () => {
      void services.session.accessToken(services.client).catch(() => {
        /* Session store clears failed refreshes. */
      });
    };
    const onVisible = () => {
      if (document.visibilityState === "visible") maintain();
    };
    const timer = setTimeout(
      maintain,
      Math.max(0, Math.min(current.refreshAt - Date.now(), 2147483647)),
    );
    window.addEventListener("focus", maintain);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("focus", maintain);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [current, services]);
  return (
    <ServicesContext.Provider value={services}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </ServicesContext.Provider>
  );
}
export function useServices() {
  const services = useContext(ServicesContext);
  if (!services) throw new Error("ServicesProvider is required");
  return services;
}
export function useSession() {
  const { session, client, showtimes, sessionSource } = useServices();
  const queryClient = useQueryClient();
  const current = useSyncExternalStore(session.subscribe, session.getSnapshot);
  const lifecycle = useSyncExternalStore(
    session.subscribe,
    session.getLifecycle,
  );
  const broadcast = () => {
    if (typeof BroadcastChannel === "undefined") return;
    const channel = new BroadcastChannel("emovie-session-events");
    channel.postMessage(sessionSource);
    channel.close();
  };
  return {
    current,
    restoring: lifecycle.pending,
    restoreError: lifecycle.error,
    loggingOut: lifecycle.loggingOut,
    logoutError: lifecycle.logoutError,
    retryRestore: () => {
      void session.restore(client, true);
    },
    canWrite:
      showtimes.mode === "demo" || !!current?.user.roles.includes("ADMIN"),
    isAdmin: !!current?.user.roles.includes("ADMIN"),
    mode: showtimes.mode,
    login: async (email: string, password: string, signal?: AbortSignal) => {
      await session.login(client, email, password, signal);
      if (session.getSnapshot()) {
        void queryClient.invalidateQueries();
        broadcast();
      }
    },
    logout: async () => {
      try {
        await session.logout(client);
        broadcast();
      } catch {
        /* The shared session feedback displays logout failures. */
      }
    },
  };
}
