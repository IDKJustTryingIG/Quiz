const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
export const apiUrl = (path) => `${BASE_URL}${path}`;
export class ApiRequestError extends Error {
  status;
  fieldErrors;
  constructor(status, message, fieldErrors) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}
/**
 * The access token lives here, in a plain variable, and nowhere else.
 *
 * It is deliberately not saved to localStorage. Anything saved there can be
 * read by any script running on the page, and the whole reason the refresh
 * token sits in a cookie the page cannot touch is to keep it away from that.
 * Saving the access token would give back what the cookie was protecting.
 *
 * The cost is that reloading the page loses it. That is fine: the cookie
 * survives, so the app asks the server for a new one on startup.
 */
let accessToken = null;

/** The refresh currently in progress, if any. Everything else waits on it. */
let refreshInFlight = null;
export const setAccessToken = (token) => {
  accessToken = token;
};
/**
 * Sends the request immediately, with whatever token is in hand.
 *
 * Only the refresh call itself should use this directly. Everything else goes
 * through send(), which waits for a refresh in progress first.
 */
const dispatch = (path, options = {}) => {
  const headers = {};
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  return fetch(apiUrl(path), {
    method: options.method ?? "GET",
    headers,
    // Without this the browser leaves the refresh cookie behind, and the
    // server has no way to tell who is calling.
    credentials: "include",
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
};

/**
 * Sends the request, but holds it back while a refresh is happening.
 *
 * Without this a call that starts mid-refresh would go out carrying the old
 * token, which the server has already stopped accepting — a wasted round trip
 * and a 401 we knew was coming. Waiting costs nothing and the call then picks
 * up the new token.
 *
 * No list of waiting requests is needed. Each one is paused on the same
 * promise, keeping its own path and options, and they all carry on when it
 * settles.
 */
const send = async (path, options = {}) => {
  if (refreshInFlight) await refreshInFlight;
  // Read after the wait, never before, or we would send the dead token anyway.
  return dispatch(path, options);
};
const parse = async (response) => {
  const text = response.status === 204 ? "" : await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      // Not JSON, so this came from something other than our API — usually an
      // error page. Fall back to a message built from the status code instead
      // of letting a parsing error reach the screen.
      data = null;
    }
  }
  if (!response.ok) {
    const body = data;
    throw new ApiRequestError(
      response.status,
      body?.message ?? `Request failed (${response.status})`,
      body?.fieldErrors,
    );
  }
  return data;
};
const performRefresh = async () => {
  try {
    const response = await dispatch("/api/auth/refresh", { method: "POST" });
    if (!response.ok) {
      accessToken = null;
      return false;
    }
    const tokens = await response.json();
    accessToken = tokens.access_token;
    return true;
  } catch {
    accessToken = null;
    return false;
  }
};
/**
 * Gets a new access token, and makes sure only one of these is ever in the air
 * at a time.
 *
 * If three calls expire together they all end up here at once. Letting all
 * three ask separately would be a problem: the server swaps the refresh token
 * for a new one each time it is used, so the second and third would turn up
 * holding a token that has already been spent. The server treats that as a
 * stolen token and signs the user out everywhere.
 *
 * So the first caller starts the request and everyone else waits on that same
 * one.
 */
export const refreshSession = () => {
  refreshInFlight ??= performRefresh().finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
};
export const apiRequest = async (path, options = {}) => {
  let response = await send(path, options);
  if (response.status === 401 && !options.noRetry) {
    const refreshed = await refreshSession();
    if (refreshed) response = await send(path, options);
  }
  return parse(response);
};
