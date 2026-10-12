// Edge Function: payments
//
//   POST { "action": "create", "order_id": "<uuid>" }
//     → creates (or reuses) a Razorpay order for the order's total as stored on the server
//       and returns what the app needs to open Razorpay Checkout.
//
//   POST { "action": "sync", "order_id": "<uuid>" }
//     → asks Razorpay whether any payment attempt for this order was already paid and, if
//       so, confirms it. Used before paying again and when the phone reported an error.
//
//   POST { "action": "verify", "razorpay_order_id", "razorpay_payment_id", "razorpay_signature" }
//     → checks the signature, asks Razorpay whether the money was captured, and only then
//       marks the order paid (and reduces stock) in the database.
//
//   POST { "action": "refund", "order_id": "<uuid>", "reason": "...", "restock": true }
//     → ADMINISTRATOR ONLY: refunds a paid app order in full through Razorpay and records it.
//       Calling it again for a failed refund retries it (never refunding twice).
//
// The caller must be signed in (their Supabase access token is checked here); customers can only
// act on their own orders, and only the administrator can refund.
// Secrets used: RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET (+ SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY,
// which Supabase provides automatically).

import { createClient } from "jsr:@supabase/supabase-js@2";
import {
  isValidCheckoutSignature,
  json,
  RazorpayApi,
  type RefundStart,
  requireEnv,
  sendRefund,
  settlePayment,
  settleRazorpayOrder,
} from "../_shared/razorpay.ts";

/** Server-side database client (service role; bypasses Row Level Security, so check ownership). */
function createAdminClient() {
  return createClient(requireEnv("SUPABASE_URL"), requireEnv("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

type AdminClient = ReturnType<typeof createAdminClient>;

/** True when the order exists and belongs to this customer. */
async function ownsOrder(admin: AdminClient, orderId: string, customerId: string): Promise<boolean> {
  if (!orderId) return false;
  const { data, error } = await admin
    .from("orders")
    .select("id")
    .eq("id", orderId)
    .eq("customer_id", customerId)
    .maybeSingle();
  if (error) console.error("ownsOrder: could not read order", error);
  return !!data;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, error: "Method not allowed" }, 405);

  try {
    const keyId = requireEnv("RAZORPAY_KEY_ID");
    const keySecret = requireEnv("RAZORPAY_KEY_SECRET");
    const admin = createAdminClient();

    // Who is calling? The token is validated by Supabase Auth, not trusted blindly.
    const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
    const { data: userData, error: userError } = await admin.auth.getUser(token);
    const user = userData?.user;
    if (userError || !user) return json({ ok: false, error: "Please sign in again." }, 401);

    const body = await req.json().catch(() => ({}));
    const gateway = new RazorpayApi(keyId, keySecret);

    if (body.action === "create") {
      const orderId = String(body.order_id ?? "");
      const { data: prep, error } = await admin.rpc("prepare_payment", {
        p_order_id: orderId,
        p_customer_id: user.id,
      });
      if (error) {
        console.error("create: prepare_payment refused", error);
        return json({ ok: false, error: error.message });
      }

      let razorpayOrderId: string | null = prep.reusable_provider_order_id;
      if (!razorpayOrderId) {
        const created = await gateway.createOrder(prep.amount_paise, `order-${prep.order_number}`, {
          order_id: prep.order_id,
          order_number: String(prep.order_number),
        });
        const { error: recordError } = await admin.rpc("record_payment_attempt", {
          p_order_id: prep.order_id,
          p_provider: "razorpay",
          p_provider_order_id: created.id,
          p_amount_paise: prep.amount_paise,
        });
        if (recordError) return json({ ok: false, error: recordError.message });
        razorpayOrderId = created.id;
      }

      return json({
        ok: true,
        key_id: keyId, // the Key ID is public; the Key Secret never leaves the server
        razorpay_order_id: razorpayOrderId,
        amount_paise: prep.amount_paise,
        currency: "INR",
        order_number: prep.order_number,
        customer_email: user.email ?? null,
      });
    }

    if (body.action === "sync") {
      const orderId = String(body.order_id ?? "");
      // Only this customer's own order (checked first, as its own simple query).
      if (!(await ownsOrder(admin, orderId, user.id))) {
        return json({ ok: false, error: "Order not found for this account." });
      }
      // Its payment attempts that are not confirmed yet.
      const { data: attempts, error } = await admin
        .from("payments")
        .select("provider_order_id")
        .eq("order_id", orderId)
        .eq("provider", "razorpay")
        .neq("status", "captured");
      if (error) {
        console.error("sync: could not read payment attempts", error);
        return json({ ok: false, error: "Could not check the payment." });
      }

      for (const attempt of attempts ?? []) {
        if (!attempt.provider_order_id) continue;
        const result = await settleRazorpayOrder(gateway, admin, attempt.provider_order_id);
        if (result.result !== "not_captured") return json({ ok: true, ...result });
      }
      return json({ ok: true, result: "not_captured" });
    }

    if (body.action === "verify") {
      const razorpayOrderId = String(body.razorpay_order_id ?? "");
      const razorpayPaymentId = String(body.razorpay_payment_id ?? "");
      const signature = String(body.razorpay_signature ?? "");

      if (!(await isValidCheckoutSignature(keySecret, razorpayOrderId, razorpayPaymentId, signature))) {
        return json({ ok: false, error: "Payment could not be verified." });
      }

      // The payment must be for one of this customer's orders.
      const { data: attempt, error: attemptError } = await admin
        .from("payments")
        .select("order_id")
        .eq("provider_order_id", razorpayOrderId)
        .maybeSingle();
      if (attemptError) console.error("verify: could not read payment attempt", attemptError);
      if (!attempt || !(await ownsOrder(admin, attempt.order_id, user.id))) {
        return json({ ok: false, error: "Payment not found for this account." });
      }

      const result = await settlePayment(gateway, admin, razorpayOrderId, razorpayPaymentId);
      return json({ ok: true, ...result });
    }

    if (body.action === "refund") {
      // Role from the database (via the verified user id), never from the request.
      const { data: profile, error: profileError } = await admin
        .from("profiles")
        .select("role")
        .eq("id", user.id)
        .maybeSingle();
      if (profileError) console.error("refund: could not read profile", profileError);
      if (profile?.role !== "admin") {
        return json({ ok: false, error: "Only the administrator can refund orders." }, 403);
      }

      const reason = String(body.reason ?? "").trim();
      const { data: start, error } = await admin.rpc("start_app_refund", {
        p_order_id: String(body.order_id ?? ""),
        p_admin_id: user.id,
        p_reason: reason,
        p_restock: body.restock === true,
      });
      if (error) return json({ ok: false, error: error.message });

      const refund = start as RefundStart;
      const sent = await sendRefund(gateway, admin, refund, reason || "Refund");
      if (!sent.ok) {
        return json({
          ok: false,
          error: "Razorpay could not send the refund right now. The order is marked for refund; " +
            "tap Refund again later to retry.",
        });
      }
      return json({ ok: true, result: "refunded", order_number: refund.order_number, amount_paise: refund.amount_paise });
    }

    return json({ ok: false, error: "Unknown action" }, 400);
  } catch (e) {
    console.error("payments function error", e);
    return json({ ok: false, error: "Payment service error. Please try again." }, 500);
  }
});
