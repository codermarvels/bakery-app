// Edge Function: razorpay-webhook
//
// Razorpay calls this URL directly from its servers when a payment is captured or fails.
// It makes sure an order is confirmed even if the customer's phone closes or loses its
// connection right after paying. Every request must carry a valid X-Razorpay-Signature
// made with the webhook secret; anything else is rejected.
//
// Secrets used: RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET
// (+ SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, provided automatically).

import { createClient } from "jsr:@supabase/supabase-js@2";
import { isValidWebhookSignature, json, RazorpayApi, requireEnv, settlePayment } from "../_shared/razorpay.ts";
import { notifyInBackground } from "../_shared/push.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ ok: false }, 405);

  const rawBody = await req.text();
  try {
    const webhookSecret = requireEnv("RAZORPAY_WEBHOOK_SECRET");
    if (!(await isValidWebhookSignature(webhookSecret, rawBody, req.headers.get("X-Razorpay-Signature")))) {
      return json({ ok: false, error: "invalid signature" }, 401);
    }

    const event = JSON.parse(rawBody);
    const payment = event?.payload?.payment?.entity;
    const razorpayOrderId: string | undefined = payment?.order_id ?? event?.payload?.order?.entity?.id;

    const admin = createClient(requireEnv("SUPABASE_URL"), requireEnv("SUPABASE_SERVICE_ROLE_KEY"), {
      auth: { persistSession: false, autoRefreshToken: false },
    });

    // Ignore payments that were not created by this app.
    if (!razorpayOrderId) return json({ ok: true, ignored: "no order" });
    const { data: known } = await admin
      .from("payments")
      .select("id")
      .eq("provider_order_id", razorpayOrderId)
      .maybeSingle();
    if (!known) return json({ ok: true, ignored: "unknown order" });

    switch (event.event) {
      case "payment.captured":
      case "payment.authorized":
      case "order.paid": {
        const gateway = new RazorpayApi(requireEnv("RAZORPAY_KEY_ID"), requireEnv("RAZORPAY_KEY_SECRET"));
        // Re-reads the payment from Razorpay rather than trusting the webhook body alone.
        const result = await settlePayment(gateway, admin, razorpayOrderId, payment.id);
        notifyInBackground(admin, result); // "New paid order" alert to the administrator's phone
        return json({ ok: true, result: result.result });
      }
      case "payment.failed": {
        await admin.rpc("mark_payment_failed", { p_provider_order_id: razorpayOrderId });
        return json({ ok: true });
      }
      default:
        return json({ ok: true, ignored: event.event });
    }
  } catch (e) {
    console.error("razorpay-webhook error", e);
    // A 500 makes Razorpay retry later, which is what we want for temporary failures.
    return json({ ok: false }, 500);
  }
});
