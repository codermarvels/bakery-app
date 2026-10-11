// Unit tests for the payment helpers. Run: deno test supabase/functions
import { assert, assertEquals, assertFalse, assertRejects } from "jsr:@std/assert@1";
import {
  isValidCheckoutSignature,
  isValidWebhookSignature,
  type PaymentGateway,
  type RazorpayPayment,
  type Rpc,
  settlePayment,
  settleRazorpayOrder,
  timingSafeEqual,
} from "./razorpay.ts";

// Reference values computed independently with Python's hmac module.
const CHECKOUT_SIG = "15656b40fea6f2159b578efa459e969de9f5e223fb8a08393e274ac578d9d005";
const WEBHOOK_BODY = '{"event":"payment.captured"}';
const WEBHOOK_SIG = "4673dd707ef4c41b987cb7fefe1583142dc702388c93145b7814b9ad3d3c183e";

Deno.test("valid checkout signature is accepted", async () => {
  assert(await isValidCheckoutSignature("test_secret", "order_ABC", "pay_XYZ", CHECKOUT_SIG));
});

Deno.test("tampered checkout signature, ids or secret are rejected", async () => {
  assertFalse(await isValidCheckoutSignature("test_secret", "order_ABC", "pay_OTHER", CHECKOUT_SIG));
  assertFalse(await isValidCheckoutSignature("wrong_secret", "order_ABC", "pay_XYZ", CHECKOUT_SIG));
  assertFalse(await isValidCheckoutSignature("test_secret", "order_ABC", "pay_XYZ", CHECKOUT_SIG.replace("1", "2")));
  assertFalse(await isValidCheckoutSignature("test_secret", "order_ABC", "pay_XYZ", ""));
  assertFalse(await isValidCheckoutSignature("", "order_ABC", "pay_XYZ", CHECKOUT_SIG));
});

Deno.test("webhook signature is checked against the raw body", async () => {
  assert(await isValidWebhookSignature("whsec", WEBHOOK_BODY, WEBHOOK_SIG));
  assertFalse(await isValidWebhookSignature("whsec", WEBHOOK_BODY + " ", WEBHOOK_SIG));
  assertFalse(await isValidWebhookSignature("whsec", WEBHOOK_BODY, null));
});

Deno.test("timingSafeEqual", () => {
  assert(timingSafeEqual("abc", "abc"));
  assertFalse(timingSafeEqual("abc", "abd"));
  assertFalse(timingSafeEqual("abc", "abcd"));
});

// ------------------------------------------------------------------ settlePayment with fakes

class FakeGateway implements PaymentGateway {
  captured = 0;
  refunds: Array<{ paymentId: string; amount: number }> = [];
  failRefund = false;
  constructor(public payment: RazorpayPayment) {}
  createOrder() {
    return Promise.resolve({ id: "order_new" });
  }
  attempts: RazorpayPayment[] = [];
  fetchPayment() {
    return Promise.resolve({ ...this.payment });
  }
  listOrderPayments() {
    return Promise.resolve(this.attempts);
  }
  capturePayment() {
    this.captured++;
    this.payment = { ...this.payment, status: "captured" };
    return Promise.resolve({ ...this.payment });
  }
  refundPayment(paymentId: string, amount: number) {
    if (this.failRefund) return Promise.reject(new Error("gateway down"));
    this.refunds.push({ paymentId, amount });
    return Promise.resolve({ id: "rfnd_1" });
  }
}

class FakeDb implements Rpc {
  calls: Array<{ fn: string; args: Record<string, unknown> }> = [];
  constructor(private readonly confirmResult: Record<string, unknown>) {}
  rpc(fn: string, args: Record<string, unknown>) {
    this.calls.push({ fn, args });
    const data = fn === "confirm_payment" ? this.confirmResult : null;
    return Promise.resolve({ data, error: null });
  }
}

const capturedPayment: RazorpayPayment = {
  id: "pay_1",
  order_id: "order_1",
  amount: 100000,
  currency: "INR",
  status: "captured",
};

Deno.test("captured payment is confirmed with the amount reported by Razorpay", async () => {
  const gateway = new FakeGateway(capturedPayment);
  const db = new FakeDb({ result: "paid", order_number: 7 });
  const result = await settlePayment(gateway, db, "order_1", "pay_1");
  assertEquals(result.result, "paid");
  assertEquals(db.calls[0], {
    fn: "confirm_payment",
    args: { p_provider_order_id: "order_1", p_provider_payment_id: "pay_1", p_amount_paise: 100000 },
  });
});

Deno.test("authorised payment is captured before confirming", async () => {
  const gateway = new FakeGateway({ ...capturedPayment, status: "authorized" });
  const db = new FakeDb({ result: "paid" });
  await settlePayment(gateway, db, "order_1", "pay_1");
  assertEquals(gateway.captured, 1);
  assertEquals(db.calls[0].fn, "confirm_payment");
});

Deno.test("failed or pending payments never reach the database", async () => {
  for (const status of ["failed", "created", "refunded"]) {
    const db = new FakeDb({ result: "paid" });
    const result = await settlePayment(new FakeGateway({ ...capturedPayment, status }), db, "order_1", "pay_1");
    assertEquals(result.result, "not_captured");
    assertEquals(db.calls.length, 0);
  }
});

Deno.test("a payment from a different Razorpay order is rejected", async () => {
  const db = new FakeDb({ result: "paid" });
  await assertRejects(() =>
    settlePayment(new FakeGateway({ ...capturedPayment, order_id: "order_other" }), db, "order_1", "pay_1")
  );
  assertEquals(db.calls.length, 0);
});

Deno.test("when the order cannot be fulfilled, the money is refunded", async () => {
  const gateway = new FakeGateway(capturedPayment);
  const db = new FakeDb({ result: "refund_required", refund_id: "r1", refund_amount_paise: 100000, reason: "sold out" });
  const result = await settlePayment(gateway, db, "order_1", "pay_1");
  assertEquals(result.refund_status, "processed");
  assertEquals(gateway.refunds, [{ paymentId: "pay_1", amount: 100000 }]);
  assertEquals(db.calls[1], {
    fn: "complete_refund",
    args: { p_refund_id: "r1", p_provider_refund_id: "rfnd_1", p_succeeded: true },
  });
});

Deno.test("a refund that fails is recorded as failed for follow-up", async () => {
  const gateway = new FakeGateway(capturedPayment);
  gateway.failRefund = true;
  const db = new FakeDb({ result: "refund_required", refund_id: "r1", refund_amount_paise: 100000 });
  const result = await settlePayment(gateway, db, "order_1", "pay_1");
  assertEquals(result.refund_status, "failed");
  assertEquals(db.calls[1].args.p_succeeded, false);
});

Deno.test("order check finds a captured attempt among failed ones and settles it", async () => {
  const gateway = new FakeGateway(capturedPayment);
  gateway.attempts = [{ ...capturedPayment, id: "pay_0", status: "failed" }, capturedPayment];
  const db = new FakeDb({ result: "paid", order_number: 7 });
  const result = await settlePayment(gateway, db, "order_1", "pay_1");
  assertEquals(result.result, "paid");
  const viaOrder = await settleRazorpayOrder(gateway, new FakeDb({ result: "paid" }), "order_1");
  assertEquals(viaOrder.result, "paid");
});

Deno.test("order check with no successful attempt changes nothing", async () => {
  const gateway = new FakeGateway(capturedPayment);
  gateway.attempts = [{ ...capturedPayment, status: "failed" }];
  const db = new FakeDb({ result: "paid" });
  const result = await settleRazorpayOrder(gateway, db, "order_1");
  assertEquals(result.result, "not_captured");
  assertEquals(db.calls.length, 0);
});
