# Android account authentication state

The settings account card retains the stored identity and cached plan when a
same-account request fails. Its existing plan pill shows `Sign in again` only when
credentials are absent or `AppPreferences` records a confirmed authentication
rejection.

The rejection state is separate from `last_error`, so clearing a refresh message
does not restore an invalid session. Only these remote outcomes set it:

- The refresh-token exchange returns HTTP 4xx with the structured OAuth error
  `invalid_grant`.
- An authenticated backend request still returns HTTP 401 after its token-refresh
  retry, including reset-credit requests that share the usage transport.

An initial HTTP 401 can recover through refresh. Timeouts, other transport failures,
HTTP 5xx responses, and error descriptions containing `invalid_grant` do not
establish rejection.
`invalid_grant` from an authorization-code exchange does not invalidate the stored
session.

`UsageApi.saveTokens` owns credential replacement and clearing the rejection state.
It shares `NETWORK_LOCK` with authenticated refreshes, so a rejection from the old
session cannot be recorded after a new session is saved. The state clears only after
the normal credential client saves successfully, or a successful usage request is
validated and cached. Credentials and cached usage are retained on rejection.

OAuth cancellation and credential commitment share a service-local boundary. Cancellation or
service destruction before commitment prevents a late exchange response from replacing the
session. Once the normal token client saves successfully, cancellation cannot reverse that
outcome, and any later settings or usage failure is reported as usage pending rather than a failed
login. Waiting for an in-flight backend request does not hold the cancellation boundary.

The visible settings account card observes authentication-state and cached-plan changes. It stops
observing while paused and reloads when resumed; unrelated preference changes do not reload the
credential client.

After a successful credential replacement, a confirmed account change clears the
old usage, reset-credit inventory, refresh errors, and usage history before any
request uses the new session. The existing cache cleanup also stops the old live
monitor and clears its derived reminder state without changing user settings.
Two nonempty account IDs take precedence: matching IDs keep the cache even if an
email changes. When the IDs cannot be compared, two nonempty emails confirm a
change only when they differ ignoring case. Missing identity information does not
establish a change. Same-account refresh and reauthentication keep the cache;
a failed credential save leaves the original identity and cache intact.

`UsageAuthenticationTest` replaces only network boundaries and uses the in-memory
token-store fixture. `SettingsAccountCardTest` checks the existing pill and explicit
reauthentication intent. These fixtures never enter a real credential store or make
an API request.
