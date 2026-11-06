# User Service gateway identity boundary

API Gateway validates the access token, removes incoming `X-User-*` headers, and forwards `X-User-Id` and `X-User-Roles` for authenticated requests. User Service validates the shape and presence of both headers on `/api/users/**` and `/api/v1/users/**` and uses them as the caller identity. Auth endpoints do not require identity headers. `JWT_SECRET`, issuer, audience, and token handling are unchanged.

These headers are not cryptographic proof of origin. A caller that can connect directly to User Service can supply a valid-looking ID and roles and impersonate another user. Do not publish port 8081 or expose User Service through another ingress. There is no application-level protection against a caller already on the private User Service network.

In `compose.yml`, User Service and its database join only `user-private`; API Gateway joins that network and the default network. User Service has no `ports` mapping. Gateway uses `USER_SERVICE_URI=http://user-service:8081` and is the only application container on the private network. The private bridge still allows outbound SMTP traffic. Eureka registration is disabled for User Service in this Compose deployment so that its private address is not advertised to other services.

Outside Compose, User Service binds to loopback by default. Set `SERVER_ADDRESS` only to a private interface that the Gateway can reach, and enforce a firewall rule allowing only the Gateway source to connect.

For Kubernetes or another deployment platform, apply an equivalent network policy or firewall rule: allow inbound traffic to User Service port 8081 only from API Gateway, keep the database private, and expose only the Gateway endpoint. Verify the policy from outside the cluster and from an unrelated workload before accepting forwarded identity headers. Running User Service directly on a public interface without that restriction is unsafe, even if its header validation tests pass.

The filter is a single servlet `@Component` registered by Spring Boot. User Service does not define a Spring Security filter chain or manually register the same filter, so it runs once per request. Its path check limits identity validation to user endpoints.
