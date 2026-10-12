# VSJ BAKERY — Administrator guide

Open the app, go to **Account → Administrator**, sign in, then tap **Open admin dashboard**.
The dashboard has four tabs. Every tab has an **ⓘ** button that explains what to enter.

## A normal day

| When | Where | What to do |
|---|---|---|
| Morning, after baking | **Stock → + Made** | Enter how many of each item were made, e.g. 30 Veg Puffs. App customers can now order them. |
| All day | **Orders** | *To prepare* lists paid app orders. Pack the order and tap **Mark ready**. When the customer picks it up, tap **Collected**. |
| All day | **Counter** | For every walk-in customer, tap the items, choose **Cash / UPI / Card** and tap **Record sale**. |
| When something spoils | **Stock → Wastage** | Enter the quantity and a reason, e.g. "burnt". |
| Closing time | **Counter → Today at the counter** | Compare **Cash** with the cash drawer, and **UPI** / **Card** with the bank or UPI app and the card machine. |
| Closing time | **Stock → End-of-day count** | Count what is left on the shelves and type it in. The app shows any **unrecorded difference** and its value. |

## Counter tab
- It's for customers who buy at the shop **without** the app.
- Recording a sale reduces stock immediately, so app customers never order something that's already gone.
- **UPI** here means the customer paid the shop's own UPI/QR (`vsj87@oksbi`); **Card** means the card machine. The
  app records what you enter, and who entered it. These payments don't go through Razorpay.
- **Undo** a sale entered by mistake, with a reason. Stock goes back and the sale drops out of today's totals and
  the reports.

## Orders tab
- **To prepare**: paid app orders. **Ready**: waiting at the counter. **Collected**: done.
- **Unpaid**: the customer has not paid yet. **Do not prepare these.**
- **Refund** (paid app orders): gives the full amount back to the customer's UPI / card / bank through
  Razorpay and cancels the order. Enter a reason. Tick *Put the items back in stock* if the order was not
  prepared or handed over. Always refund here, not only in the Razorpay dashboard, so reports and stock
  stay correct. If it says the refund failed, tap **Retry refund** later. It never refunds twice.
- Orders from customers who deleted their account show as *Deleted account*.

## Stock tab
- **+ Made**: fresh production.
- **Wastage**: spoiled, burnt, dropped or expired items. A reason is required.
- **Adjust**: correct a mistake up or down, e.g. `-2`. A reason is required.
- **History**: every change to that product, with the reason, for checking.
- App and counter sales reduce stock automatically. **Don't** enter them here as well.

## Products tab
- **+ Add product**: name, category, price per item in rupees (e.g. `45` or `45.50`), optional description and photo.
- Tap a product to edit it. **Show in app** turns off hides it from customers but keeps its history.
- New products start with 0 stock. Add stock with **Stock → + Made**.
- **Manage categories**: rename, reorder (a smaller number shows first), hide or add categories.

## What the reports user can see
The reports account can **read** all of this (orders, counter sales, stock history, counts) for the Stage 6
reports, but it can't change anything. The database enforces this.
