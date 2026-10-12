# Sales reports guide

**Who can open it:** the reports account (Account → Reports → sign in), and the administrator
(Account → **Sales reports**). Customers can't, and the database refuses them even if they try.
The page is **read-only**: nothing on it can change orders, payments or stock.

## Choosing the period

Tap a chip at the top:

| Chip | Period |
|------|--------|
| Today | Today so far |
| Yesterday | The whole of yesterday |
| This month | From the 1st of this month to today |
| Last month | The whole previous month |
| Custom | Pick a start and end date (up to one year). Tap one date only for a single day. |

Days run from midnight to midnight **Indian time**. Tap ⟳ to refresh, or ⓘ for a short explanation.

## What the numbers mean

| Line | Meaning |
|------|---------|
| **Gross sales** | All money received: app payments **verified by the server with Razorpay**, plus counter sales (cash, UPI, card). Counted on the day the payment was confirmed. |
| **Refunds** | Money given back: online refunds and undone counter sales, on the day processed. |
| **Net sales** | Gross sales − refunds. |
| **Paid orders** | Orders whose payment was confirmed in the period (split into app and counter). |
| **Money received by payment method** | Gross sales split into Online (Razorpay), Cash, UPI and Card. Use this to match the cash drawer and the bank/Razorpay settlement. |
| **Items sold** | Quantity and amount per item from paid orders that were not refunded or undone. |
| **Daily breakdown** | Orders, gross, refunds and net for each day in the period. |
| **Produced** | Stock added through "Record production". |
| **Wastage** | Stock written off as wastage, valued at today's selling price. |
| **Unrecorded differences** | Differences found by the end-of-day count, valued at today's price. A negative number means items are missing compared with what the app expected. |

An order that is still "awaiting payment" never appears. A payment only counts once the
server has verified it, never because the phone said "success".

**Why gross can differ from items sold:** a refund on a later day lowers that later day's net
sales, while the refunded order drops out of "Items sold" for its own day. Over a full month
the two agree.

## Export and share

Tap **Export PDF**. The app creates an A4 PDF (with the period and the time it was made) and opens
Android's share menu, so you can send it by WhatsApp or Gmail, or save it to Drive or Files.
Only the latest exported PDF is kept in the app's temporary storage.
