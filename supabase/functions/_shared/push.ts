// New-order alerts: sends a push notification (with sound) to the administrator's phone through
// Firebase Cloud Messaging (FCM HTTP v1) when the server confirms a payment.
//
// Secret used: FIREBASE_SERVICE_ACCOUNT, the full JSON key file of a Firebase service account
// (Firebase console → Project settings → Service accounts → Generate new private key). It stays
// in Supabase Edge Function secrets. Without it, alerts are simply skipped; payments never fail
// because of a notification problem.

export interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

/** What the notification shows. */
export interface OrderAlert {
  orderId: string;
  title: string;
  body: string;
}

// ------------------------------------------------------------------ Google sign-in (JWT → token)

function base64url(bytes: Uint8Array | string): string {
  const data = typeof bytes === "string" ? new TextEncoder().encode(bytes) : bytes;
  let binary = "";
  for (const b of data) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const body = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

/** A signed request for a Google access token that may send FCM messages. */
export async function signedAssertion(account: ServiceAccount, nowSeconds: number): Promise<string> {
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  }));
  const key = await importPrivateKey(account.private_key);
  const signature = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`)),
  );
  return `${header}.${claims}.${base64url(signature)}`;
}

async function accessToken(account: ServiceAccount, fetcher: typeof fetch): Promise<string> {
  const assertion = await signedAssertion(account, Math.floor(Date.now() / 1000));
  const response = await fetcher("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`Google token request failed (${response.status}): ${text.slice(0, 200)}`);
  return (JSON.parse(text) as { access_token: string }).access_token;
}

// ------------------------------------------------------------------ sending

/** FCM says this token will never work again (app uninstalled, data cleared, …). */
export function isDeadToken(status: number, body: string): boolean {
  return status === 404 || body.includes("UNREGISTERED") ||
    (status === 400 && body.includes("registration token is not a valid"));
}

/**
 * Sends one data message per phone. The app builds the notification itself (on its
 * "New orders" channel with sound), so it looks the same whether the app is open or closed.
 * Returns the tokens that are no longer valid so they can be removed.
 */
export async function sendToDevices(
  account: ServiceAccount,
  tokens: string[],
  alert: OrderAlert,
  fetcher: typeof fetch = fetch,
): Promise<{ sent: number; deadTokens: string[] }> {
  if (tokens.length === 0) return { sent: 0, deadTokens: [] };
  const bearer = await accessToken(account, fetcher);
  const url = `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(account.project_id)}/messages:send`;
  let sent = 0;
  const deadTokens: string[] = [];
  for (const token of tokens) {
    const response = await fetcher(url, {
      method: "POST",
      headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          data: { type: "new_order", order_id: alert.orderId, title: alert.title, body: alert.body },
          android: { priority: "HIGH", ttl: "3600s" },
        },
      }),
    });
    const text = await response.text();
    if (response.ok) {
      sent++;
    } else if (isDeadToken(response.status, text)) {
      deadTokens.push(token);
    } else {
      console.error(`FCM send failed (${response.status}): ${text.slice(0, 200)}`);
    }
  }
  return { sent, deadTokens };
}

// ------------------------------------------------------------------ building the alert

export function formatRupees(paise: number): string {
  const rupees = Math.floor(paise / 100).toLocaleString("en-IN");
  return `₹${rupees}.${String(paise % 100).padStart(2, "0")}`;
}

export function buildAlert(
  order: { id: string; order_number: number; total_paise: number },
  items: Array<{ product_name: string; quantity: number }>,
  customerName: string | null,
): OrderAlert {
  const list = items.map((i) => `${i.quantity}× ${i.product_name}`).join(", ");
  const who = customerName?.trim() ? ` · ${customerName.trim()}` : "";
  return {
    orderId: order.id,
    title: `New paid order #${order.order_number} · ${formatRupees(order.total_paise)}`,
    body: (list || "Open the app to see the items") + who,
  };
}

// ------------------------------------------------------------------ the whole flow

/** The few database calls needed (a service-role Supabase client fits). */
// deno-lint-ignore no-explicit-any
type Db = { from(table: string): any };

/**
 * Tells every administrator phone about a newly paid order. Never throws: a missing
 * secret or an FCM outage must not affect the payment, which is already confirmed.
 */
export async function notifyNewPaidOrder(db: Db, orderId: string): Promise<void> {
  try {
    const raw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
    if (!raw) return; // alerts not set up yet
    const account = JSON.parse(raw) as ServiceAccount;

    const { data: order } = await db.from("orders")
      .select("id, order_number, total_paise, customer_id").eq("id", orderId).maybeSingle();
    if (!order) return;
    const { data: items } = await db.from("order_items").select("product_name, quantity").eq("order_id", orderId);
    const { data: customer } = order.customer_id
      ? await db.from("profiles").select("full_name").eq("id", order.customer_id).maybeSingle()
      : { data: null };

    // Only phones of accounts that are still administrators.
    const { data: admins } = await db.from("profiles").select("id").eq("role", "admin");
    const adminIds = (admins ?? []).map((a: { id: string }) => a.id);
    if (adminIds.length === 0) return;
    const { data: devices } = await db.from("push_devices").select("token").in("user_id", adminIds);
    const tokens = (devices ?? []).map((d: { token: string }) => d.token);

    const result = await sendToDevices(account, tokens, buildAlert(order, items ?? [], customer?.full_name ?? null));
    if (result.deadTokens.length > 0) {
      await db.from("push_devices").delete().in("token", result.deadTokens);
    }
  } catch (e) {
    console.error("New-order alert could not be sent (the payment is fine)", e);
  }
}

// Supabase's runtime can finish background work after the response is sent.
declare const EdgeRuntime: { waitUntil(promise: Promise<unknown>): void } | undefined;

/** Sends the alert without making the customer wait for it. */
export function notifyInBackground(db: Db, result: { result: string; order_id?: string }): void {
  if (result.result !== "paid" || !result.order_id) return;
  const work = notifyNewPaidOrder(db, result.order_id);
  if (typeof EdgeRuntime !== "undefined") EdgeRuntime.waitUntil(work);
}
