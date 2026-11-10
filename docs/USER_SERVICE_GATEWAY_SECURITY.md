# User Service gateway identity boundary

API Gateway validates the access token, removes incoming `X-User-*` and `X-Gateway-Key` headers, and forwards `X-User-Id` and `X-User-Roles` for authenticated requests. Only the `user-service` route receives the configured `X-Gateway-Key`. User Service requires exactly one matching key before accepting any `/api/auth/**` or `/api/users/**` request, including the `/api/v1` aliases. User endpoints additionally validate the identity headers. Auth endpoints require the gateway key but do not require user identity headers.

Set the same `GATEWAY_SHARED_SECRET` on Gateway and User Service. Generate it with `openssl rand -base64 32`, independently of `JWT_SECRET`. Both services reject missing, malformed or short gateway keys at startup. Missing, incorrect or duplicate request keys receive HTTP 401 before reaching a controller, even when identity headers look valid. Rotate the key on both services together. The JWT signing key is also mandatory; there is no built-in signing secret.

Keep the network boundary as an additional layer of protection. Do not publish port 8081 or expose User Service through another ingress. Use TLS between services outside a trusted local network, because the shared key is a bearer credential.

In `compose.yml`, User Service and its database join only `user-private`; API Gateway joins that network and the default network. User Service has no `ports` mapping. Gateway uses `USER_SERVICE_URI=http://user-service:8081` and is the only application container on the private network. The private bridge still allows outbound SMTP traffic. Eureka registration is disabled for User Service in this Compose deployment so that its private address is not advertised to other services.

Outside Compose, User Service binds to loopback by default. Set `SERVER_ADDRESS` only to a private interface that the Gateway can reach, and enforce a firewall rule allowing only the Gateway source to connect.

For Kubernetes or another deployment platform, apply an equivalent network policy or firewall rule: allow inbound traffic to User Service port 8081 only from API Gateway, keep the database private, and expose only the Gateway endpoint. Verify the policy from outside the cluster and from an unrelated workload before accepting forwarded identity headers. Running User Service directly on a public interface without that restriction is unsafe, even if its header validation tests pass.

The filter is a single servlet `@Component` registered by Spring Boot. User Service does not define a Spring Security filter chain or manually register the same filter, so it runs once per request. Health and documentation paths remain outside the filter; identity validation applies only to user endpoints.
