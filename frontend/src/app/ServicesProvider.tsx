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
import { createHttpClient, shouldRetryQuery } from "../lib/http";
import { createSessionStore } from "../features/auth/session";
import { createDemoShowtimes } from "../features/showtimes/api/demo-showtimes";
import { createHttpShowtimes } from "../features/showtimes/api/http-showtimes";
import { createMovieApi } from "../features/movies/api";
import { createBookingApi } from "../features/booking/api";

function createServices() {
  const session = createSessionStore();
  const client = createHttpClient({
    baseUrl: import.meta.env.VITE_API_BASE_URL || "/api",
    accessToken: () => session.getSnapshot()?.token,
    onUnauthorized: (token) => {
      if (session.getSnapshot()?.token === token) session.clear();
    },
  });
  const mode = import.meta.env.VITE_DATA_MODE || "api";
  if (mode !== "demo" && mode !== "api")
    throw new Error("VITE_DATA_MODE must be demo or api");
  return {
    session,
    client,
    movies: createMovieApi(client),
    booking: createBookingApi(client),
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
    let userId = services.session.getSnapshot()?.user.userId;
    return services.session.subscribe(() => {
      const nextId = services.session.getSnapshot()?.user.userId;
      if (nextId !== userId) {
        void queryClient.cancelQueries({ queryKey: ["private"] });
        queryClient.removeQueries({ queryKey: ["private"] });
      }
      userId = nextId;
    });
  }, [services, queryClient]);
  useEffect(() => {
    if (!current) return;
    const timer = setTimeout(
      services.session.clear,
      Math.max(0, Math.min(current.expiresAt - Date.now(), 2147483647)),
    );
    return () => clearTimeout(timer);
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
  const { session, client, showtimes } = useServices();
  const queryClient = useQueryClient();
  const current = useSyncExternalStore(session.subscribe, session.getSnapshot);
  return {
    current,
    canWrite:
      showtimes.mode === "demo" || !!current?.user.roles.includes("ADMIN"),
    isAdmin: !!current?.user.roles.includes("ADMIN"),
    mode: showtimes.mode,
    login: async (email: string, password: string, signal?: AbortSignal) => {
      await session.login(client, email, password, signal);
      if (session.getSnapshot()) void queryClient.invalidateQueries();
    },
    logout: session.clear,
  };
}
