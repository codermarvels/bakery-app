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
// The caller must be a signed-in customer (their Supabase access token is checked here).
// Secrets used: RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET (+ SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY,
// which Supabase provides automatically).

import { createClient } from "jsr:@supabase/supabase-js@2";
import {
  isValidCheckoutSignature,
  json,
  RazorpayApi,
  requireEnv,
  settlePayment,
  settleRazorpayOrder,
} from "../_shared/razorpay.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false, error: "Method not allowed" }, 405);

  try {
    const keyId = requireEnv("RAZORPAY_KEY_ID");
    const keySecret = requireEnv("RAZORPAY_KEY_SECRET");
    const admin = createClient(requireEnv("SUPABASE_URL"), requireEnv("SUPABASE_SERVICE_ROLE_KEY"), {
      auth: { persistSession: false, autoRefreshToken: false },
    });

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
      if (error) return json({ ok: false, error: error.message });

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
      // Only this customer's own order, and only attempts not already confirmed.
      const { data: attempts, error } = await admin
        .from("payments")
        .select("provider_order_id, orders!inner(customer_id)")
        .eq("order_id", orderId)
        .eq("orders.customer_id", user.id)
        .eq("provider", "razorpay")
        .neq("status", "captured");
      if (error) return json({ ok: false, error: "Could not check the payment." });

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
      const { data: owned } = await admin
        .from("payments")
        .select("id, orders!inner(customer_id)")
        .eq("provider_order_id", razorpayOrderId)
        .eq("orders.customer_id", user.id)
        .maybeSingle();
      if (!owned) return json({ ok: false, error: "Payment not found for this account." });

      const result = await settlePayment(gateway, admin, razorpayOrderId, razorpayPaymentId);
      return json({ ok: true, ...result });
    }

    return json({ ok: false, error: "Unknown action" }, 400);
  } catch (e) {
    console.error("payments function error", e);
    return json({ ok: false, error: "Payment service error. Please try again." }, 500);
  }
});
