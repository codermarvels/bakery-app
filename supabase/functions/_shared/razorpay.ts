// Razorpay helpers shared by the `payments` and `razorpay-webhook` Edge Functions.
// Secrets (key secret, webhook secret) are read from Supabase Edge Function secrets and
// never leave the server.

const encoder = new TextEncoder();

export async function hmacSha256Hex(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(message));
  return Array.from(new Uint8Array(signature))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/** Compares two strings without leaking where they differ through timing. */
export function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

/** Checkout signature: HMAC-SHA256(order_id + "|" + payment_id, key_secret). */
export async function isValidCheckoutSignature(
  keySecret: string,
  razorpayOrderId: string,
  razorpayPaymentId: string,
  signature: string,
): Promise<boolean> {
  if (!keySecret || !razorpayOrderId || !razorpayPaymentId || !signature) return false;
  const expected = await hmacSha256Hex(keySecret, `${razorpayOrderId}|${razorpayPaymentId}`);
  return timingSafeEqual(expected, signature);
}

/** Webhook signature: HMAC-SHA256(raw request body, webhook_secret). */
export async function isValidWebhookSignature(
  webhookSecret: string,
  rawBody: string,
  signature: string | null,
): Promise<boolean> {
  if (!webhookSecret || !signature) return false;
  const expected = await hmacSha256Hex(webhookSecret, rawBody);
  return timingSafeEqual(expected, signature);
}

// ------------------------------------------------------------------ Razorpay REST API

export interface RazorpayPayment {
  id: string;
  order_id: string | null;
  amount: number;
  currency: string;
  status: "created" | "authorized" | "captured" | "refunded" | "failed" | string;
}

export interface PaymentGateway {
  createOrder(amountPaise: number, receipt: string, notes: Record<string, string>): Promise<{ id: string }>;
  fetchPayment(paymentId: string): Promise<RazorpayPayment>;
  /** All payment attempts made against one Razorpay order. */
  listOrderPayments(razorpayOrderId: string): Promise<RazorpayPayment[]>;
  capturePayment(paymentId: string, amountPaise: number): Promise<RazorpayPayment>;
  refundPayment(paymentId: string, amountPaise: number, reason: string): Promise<{ id: string }>;
}

export class RazorpayApi implements PaymentGateway {
  private readonly auth: string;

  constructor(keyId: string, keySecret: string, private readonly baseUrl = "https://api.razorpay.com/v1") {
    this.auth = "Basic " + btoa(`${keyId}:${keySecret}`);
  }

  private async call<T>(method: string, path: string, body?: unknown): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method,
      headers: { Authorization: this.auth, "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    if (!response.ok) {
      // Razorpay error bodies never contain our secrets; keep the description for logs.
      throw new Error(`Razorpay ${method} ${path} failed (${response.status}): ${text.slice(0, 300)}`);
    }
    return JSON.parse(text) as T;
  }

  createOrder(amountPaise: number, receipt: string, notes: Record<string, string>) {
    return this.call<{ id: string }>("POST", "/orders", {
      amount: amountPaise,
      currency: "INR",
      receipt,
      notes,
    });
  }

  fetchPayment(paymentId: string) {
    return this.call<RazorpayPayment>("GET", `/payments/${encodeURIComponent(paymentId)}`);
  }

  async listOrderPayments(razorpayOrderId: string) {
    const page = await this.call<{ items?: RazorpayPayment[] }>(
      "GET",
      `/orders/${encodeURIComponent(razorpayOrderId)}/payments`,
    );
    return page.items ?? [];
  }

  capturePayment(paymentId: string, amountPaise: number) {
    return this.call<RazorpayPayment>("POST", `/payments/${encodeURIComponent(paymentId)}/capture`, {
      amount: amountPaise,
      currency: "INR",
    });
  }

  refundPayment(paymentId: string, amountPaise: number, reason: string) {
    return this.call<{ id: string }>("POST", `/payments/${encodeURIComponent(paymentId)}/refund`, {
      amount: amountPaise,
      notes: { reason: reason.slice(0, 250) },
    });
  }
}

// ------------------------------------------------------------------ settling a payment

/** Minimal database access needed here (a Supabase client's rpc fits this shape). */
export interface Rpc {
  rpc(fn: string, args: Record<string, unknown>): PromiseLike<{ data: unknown; error: { message: string } | null }>;
}

export interface SettleResult {
  result: "paid" | "already_confirmed" | "refund_required" | "refund_recorded" | "not_captured";
  order_id?: string;
  order_number?: number;
  order_status?: string;
  refund_id?: string;
  refund_status?: "processed" | "failed";
  reason?: string;
}

/**
 * Asks Razorpay for the payment's real state, captures it if it is only authorised, and
 * then records it in the database. Only a payment Razorpay reports as `captured`, for the
 * Razorpay order we created, is ever confirmed. If the bakery cannot fulfil the order,
 * the money is sent back straight away.
 */
export async function settlePayment(
  gateway: PaymentGateway,
  db: Rpc,
  razorpayOrderId: string,
  razorpayPaymentId: string,
): Promise<SettleResult> {
  let payment = await gateway.fetchPayment(razorpayPaymentId);
  if (payment.order_id !== razorpayOrderId) {
    throw new Error("Payment does not belong to this order");
  }
  if (payment.currency !== "INR") {
    throw new Error(`Unexpected currency ${payment.currency}`);
  }
  if (payment.status === "authorized") {
    payment = await gateway.capturePayment(payment.id, payment.amount);
  }
  if (payment.status !== "captured") {
    return { result: "not_captured" };
  }

  const { data, error } = await db.rpc("confirm_payment", {
    p_provider_order_id: razorpayOrderId,
    p_provider_payment_id: payment.id,
    p_amount_paise: payment.amount,
  });
  if (error) throw new Error(`confirm_payment failed: ${error.message}`);
  const result = data as SettleResult & { refund_amount_paise?: number };

  if (result.result === "refund_required" && result.refund_id) {
    try {
      const refund = await gateway.refundPayment(
        payment.id,
        result.refund_amount_paise ?? payment.amount,
        result.reason ?? "Order could not be fulfilled",
      );
      await db.rpc("complete_refund", {
        p_refund_id: result.refund_id,
        p_provider_refund_id: refund.id,
        p_succeeded: true,
      });
      result.refund_status = "processed";
    } catch (e) {
      console.error("Refund failed; recorded as failed so the bakery can retry it", e);
      await db.rpc("complete_refund", {
        p_refund_id: result.refund_id,
        p_provider_refund_id: null,
        p_succeeded: false,
      });
      result.refund_status = "failed";
    }
  }
  return result;
}

/**
 * Asks Razorpay whether ANY attempt on this Razorpay order has been paid, and settles it.
 * This is the safety net for when the phone did not report success (app closed, network
 * dropped, or the checkout screen reported an error) and the webhook has not arrived.
 */
export async function settleRazorpayOrder(
  gateway: PaymentGateway,
  db: Rpc,
  razorpayOrderId: string,
): Promise<SettleResult> {
  const attempts = await gateway.listOrderPayments(razorpayOrderId);
  const paid = attempts.find((p) => p.status === "captured") ??
    attempts.find((p) => p.status === "authorized");
  if (!paid) return { result: "not_captured" };
  return settlePayment(gateway, db, razorpayOrderId, paid.id);
}

export function requireEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing Edge Function secret: ${name}`);
  return value;
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
