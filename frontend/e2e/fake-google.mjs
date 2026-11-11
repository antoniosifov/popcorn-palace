// Stands in for Google in the end-to-end tests (see compose.e2e.yml). It does
// what Google does in a sign-in, and is as strict as Google about what it is
// sent, so a mistake in what this application sends fails here as it would there:
//
//   GET  /auth     the page the browser is sent to; "signs in" at once and sends it back with a code
//   POST /token    exchanges the code for an ID token, if the secret, the address and the PKCE verifier are right
//   GET  /certs    the keys the ID tokens are signed with
//   POST /person   test control: who "signs in" next
//
// No dependencies: node's own http and crypto.
import { createHash, generateKeyPairSync, randomBytes, sign } from "node:crypto";
import { createServer } from "node:http";

const PORT = Number(process.env.PORT ?? 9955);
const CLIENT_ID = process.env.CLIENT_ID ?? "popcorn-e2e.apps.googleusercontent.com";
const CLIENT_SECRET = process.env.CLIENT_SECRET ?? "e2e-client-secret";
// Like the "authorised redirect URIs" of a real client: an address not on the list is refused.
const REDIRECT_URIS = (process.env.REDIRECT_URIS ?? "http://localhost:3000/api/auth/google/callback").split(",");

const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const KEY_ID = "fake-google-key";
const jwk = { ...publicKey.export({ format: "jwk" }), kid: KEY_ID, alg: "RS256", use: "sig" };

/** Who signs in next. Set by the tests, one at a time. */
let person = { sub: "100000000000000000001", email: "someone@example.com", emailVerified: true, name: "Some One" };
/** Codes handed out and not yet exchanged. */
const codes = new Map();

const b64 = (value) => Buffer.from(typeof value === "string" ? value : JSON.stringify(value)).toString("base64url");

function idToken(claims) {
  const unsigned = `${b64({ alg: "RS256", typ: "JWT", kid: KEY_ID })}.${b64(claims)}`;
  return `${unsigned}.${sign("RSA-SHA256", Buffer.from(unsigned), privateKey).toString("base64url")}`;
}

function send(response, status, body, headers = {}) {
  const text = typeof body === "string" ? body : JSON.stringify(body);
  response.writeHead(status, {
    "Content-Type": typeof body === "string" ? "text/plain" : "application/json",
    ...headers,
  });
  response.end(text);
}

async function bodyOf(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString("utf8");
}

createServer(async (request, response) => {
  const url = new URL(request.url, `http://${request.headers.host}`);

  if (request.method === "GET" && url.pathname === "/health") {
    return send(response, 200, { status: "UP" });
  }

  if (request.method === "GET" && url.pathname === "/certs") {
    return send(response, 200, { keys: [jwk] });
  }

  if (request.method === "POST" && url.pathname === "/person") {
    person = { ...person, ...JSON.parse(await bodyOf(request)) };
    return send(response, 200, person);
  }

  if (request.method === "GET" && url.pathname === "/auth") {
    const q = url.searchParams;
    const complaint =
      q.get("client_id") !== CLIENT_ID
        ? "unknown client_id"
        : !REDIRECT_URIS.includes(q.get("redirect_uri") ?? "")
          ? `redirect_uri_mismatch: ${q.get("redirect_uri")}`
          : q.get("response_type") !== "code"
            ? "response_type must be code"
            : !(q.get("scope") ?? "").split(" ").includes("openid")
              ? "scope must include openid"
              : !q.get("state") || !q.get("nonce")
                ? "state and nonce are required"
                : q.get("code_challenge_method") !== "S256" || !q.get("code_challenge")
                  ? "PKCE with S256 is required"
                  : null;
    if (complaint) {
      return send(response, 400, `Error 400: ${complaint}`);
    }
    const code = randomBytes(24).toString("base64url");
    codes.set(code, {
      redirectUri: q.get("redirect_uri"),
      challenge: q.get("code_challenge"),
      nonce: q.get("nonce"),
      person,
    });
    const back = new URL(q.get("redirect_uri"));
    back.searchParams.set("code", code);
    back.searchParams.set("state", q.get("state"));
    return send(response, 302, "", { Location: back.toString() });
  }

  if (request.method === "POST" && url.pathname === "/token") {
    const form = new URLSearchParams(await bodyOf(request));
    const issued = codes.get(form.get("code") ?? "");
    // A code works once, whatever comes of it.
    codes.delete(form.get("code") ?? "");
    const verifierFits =
      issued &&
      createHash("sha256")
        .update(form.get("code_verifier") ?? "")
        .digest("base64url") === issued.challenge;
    if (form.get("client_id") !== CLIENT_ID || form.get("client_secret") !== CLIENT_SECRET) {
      return send(response, 401, { error: "invalid_client" });
    }
    if (
      form.get("grant_type") !== "authorization_code" ||
      !issued ||
      !verifierFits ||
      form.get("redirect_uri") !== issued.redirectUri
    ) {
      return send(response, 400, { error: "invalid_grant" });
    }
    const now = Math.floor(Date.now() / 1000);
    return send(response, 200, {
      access_token: randomBytes(16).toString("hex"),
      token_type: "Bearer",
      expires_in: 3600,
      id_token: idToken({
        iss: "https://accounts.google.com",
        aud: CLIENT_ID,
        sub: issued.person.sub,
        email: issued.person.email,
        email_verified: issued.person.emailVerified,
        name: issued.person.name,
        nonce: issued.nonce,
        iat: now,
        exp: now + 3600,
      }),
    });
  }

  send(response, 404, "Not found");
}).listen(PORT, () => console.log(`fake Google listening on ${PORT}`));
