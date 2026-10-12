import { assert, assertEquals } from "jsr:@std/assert@1";
import { buildAlert, formatRupees, isDeadToken, sendToDevices, type ServiceAccount, signedAssertion } from "./push.ts";

async function testAccount(): Promise<{ account: ServiceAccount; publicKey: CryptoKey }> {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", pair.privateKey));
  let binary = "";
  for (const b of der) binary += String.fromCharCode(b);
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(binary).match(/.{1,64}/g)!.join("\n")}\n-----END PRIVATE KEY-----\n`;
  return {
    account: { project_id: "vsj-test", client_email: "push@vsj-test.iam.gserviceaccount.com", private_key: pem },
    publicKey: pair.publicKey,
  };
}

function fromBase64url(s: string): Uint8Array<ArrayBuffer> {
  const padded = s.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (s.length % 4)) % 4);
  return Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
}

Deno.test("the Google sign-in request is a valid RS256 token for FCM", async () => {
  const { account, publicKey } = await testAccount();
  const jwt = await signedAssertion(account, 1_700_000_000);
  const [header, claims, signature] = jwt.split(".");
  assertEquals(JSON.parse(new TextDecoder().decode(fromBase64url(header))), { alg: "RS256", typ: "JWT" });
  const body = JSON.parse(new TextDecoder().decode(fromBase64url(claims)));
  assertEquals(body.iss, account.client_email);
  assertEquals(body.scope, "https://www.googleapis.com/auth/firebase.messaging");
  assertEquals(body.exp - body.iat, 3600);
  assert(
    await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      publicKey,
      fromBase64url(signature),
      new TextEncoder().encode(`${header}.${claims}`),
    ),
  );
});

Deno.test("the alert says what was ordered, for how much and by whom", () => {
  const alert = buildAlert(
    { id: "o1", order_number: 42, total_paise: 125050 },
    [{ product_name: "Black Forest", quantity: 1 }, { product_name: "Veg Puff", quantity: 4 }],
    "Asha",
  );
  assertEquals(alert.title, "New paid order #42 · ₹1,250.50");
  assertEquals(alert.body, "1× Black Forest, 4× Veg Puff · Asha");
  assertEquals(formatRupees(5), "₹0.05");
});

Deno.test("one message per phone; dead phones are reported for removal", async () => {
  const { account } = await testAccount();
  const sentTo: string[] = [];
  const fakeFetch = (async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    if (url.startsWith("https://oauth2.googleapis.com/token")) {
      return new Response(JSON.stringify({ access_token: "ya29.test" }));
    }
    assertEquals(url, "https://fcm.googleapis.com/v1/projects/vsj-test/messages:send");
    assertEquals((init?.headers as Record<string, string>).Authorization, "Bearer ya29.test");
    const message = JSON.parse(String(init?.body)).message;
    assertEquals(message.android.priority, "HIGH");
    assertEquals(message.data.type, "new_order");
    sentTo.push(message.token);
    if (message.token === "old-phone") {
      return new Response('{"error":{"status":"NOT_FOUND","details":[{"errorCode":"UNREGISTERED"}]}}', { status: 404 });
    }
    return new Response('{"name":"projects/vsj-test/messages/1"}');
  }) as typeof fetch;

  const result = await sendToDevices(account, ["shop-phone", "old-phone"], { orderId: "o1", title: "t", body: "b" }, fakeFetch);
  assertEquals(sentTo, ["shop-phone", "old-phone"]);
  assertEquals(result, { sent: 1, deadTokens: ["old-phone"] });
});

Deno.test("no phones means no Google calls at all", async () => {
  const { account } = await testAccount();
  const result = await sendToDevices(account, [], { orderId: "o1", title: "t", body: "b" }, (() => {
    throw new Error("should not be called");
  }) as typeof fetch);
  assertEquals(result.sent, 0);
});

Deno.test("only permanently invalid tokens are treated as dead", () => {
  assert(isDeadToken(404, ""));
  assert(isDeadToken(400, '{"errorCode":"UNREGISTERED"}'));
  assert(!isDeadToken(500, "internal"));
  assert(!isDeadToken(429, "quota"));
});
