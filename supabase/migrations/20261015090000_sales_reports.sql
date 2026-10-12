-- =====================================================================================
-- VSJ BAKERY — Stage 6: sales reports (read-only)
--
-- sales_report(from, to) returns one JSON document with everything the Reports screen
-- and the PDF show. Dates are bakery calendar days in Indian Standard Time and both ends
-- are included. Only staff (reports or admin) may call it; it only READS data.
--
-- Definitions (so the numbers always reconcile):
--   * Gross sales  = money received: captured payments (online Razorpay + counter cash /
--                    UPI / card), by the day the payment was confirmed.
--   * Refunds      = money given back: processed refunds, by the day processed
--                    (includes undone counter sales and automatic online refunds).
--   * Net sales    = gross sales − refunds.
--   * Paid orders  = orders whose payment was confirmed in the period.
--   * Items sold   = items in paid orders that were not refunded / undone.
--   * Wastage and unrecorded differences are valued at today's selling price.
-- =====================================================================================

create function public.sales_report(p_from date, p_to date)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_tz     constant text := 'Asia/Kolkata';
    v_result jsonb;
begin
    if not private.is_staff() then
        raise exception 'Only the reports or administrator account can view reports' using errcode = '42501';
    end if;
    if p_from is null or p_to is null or p_from > p_to then
        raise exception 'Choose a valid date range (from date on or before to date)' using errcode = 'P0001';
    end if;
    if p_to - p_from > 366 then
        raise exception 'Choose a range of one year or less' using errcode = 'P0001';
    end if;

    with
    pay as (
        select p.id, p.order_id, p.provider, p.amount_paise,
               (p.verified_at at time zone v_tz)::date as day,
               o.channel
          from public.payments p
          join public.orders o on o.id = p.order_id
         where p.status = 'captured'
           and (p.verified_at at time zone v_tz)::date between p_from and p_to
    ),
    ref as (
        select r.id, r.amount_paise, (r.processed_at at time zone v_tz)::date as day
          from public.refunds r
         where r.status = 'processed'
           and (r.processed_at at time zone v_tz)::date between p_from and p_to
    ),
    sold as (
        select oi.product_name, sum(oi.quantity)::bigint as quantity, sum(oi.line_total_paise)::bigint as amount
          from public.order_items oi
          join public.orders o on o.id = oi.order_id
         where o.status in ('paid', 'ready_for_pickup', 'collected')
           and o.paid_at is not null
           and (o.paid_at at time zone v_tz)::date between p_from and p_to
         group by oi.product_name
    ),
    moves as (
        select sm.movement_type, sm.quantity_delta, sm.stock_count_id, p.name, p.price_paise
          from public.stock_movements sm
          join public.products p on p.id = sm.product_id
         where (sm.created_at at time zone v_tz)::date between p_from and p_to
    ),
    days as (
        select d::date as day from generate_series(p_from, p_to, interval '1 day') d
    )
    select jsonb_build_object(
        'from', p_from,
        'to', p_to,
        'generated_at', now(),
        'paid_orders', (select count(distinct order_id) from pay),
        'app_orders', (select count(distinct order_id) from pay where channel = 'app'),
        'counter_sales', (select count(distinct order_id) from pay where channel = 'counter'),
        'gross_paise', (select coalesce(sum(amount_paise), 0) from pay),
        'app_gross_paise', (select coalesce(sum(amount_paise), 0) from pay where channel = 'app'),
        'counter_gross_paise', (select coalesce(sum(amount_paise), 0) from pay where channel = 'counter'),
        'refund_count', (select count(*) from ref),
        'refunds_paise', (select coalesce(sum(amount_paise), 0) from ref),
        'net_paise', (select coalesce(sum(amount_paise), 0) from pay) - (select coalesce(sum(amount_paise), 0) from ref),
        'by_method', (
            select coalesce(jsonb_agg(jsonb_build_object('method', provider, 'count', n, 'amount_paise', amt)
                                      order by provider), '[]'::jsonb)
              from (select provider, count(*) as n, sum(amount_paise) as amt from pay group by provider) m
        ),
        'items', (
            select coalesce(jsonb_agg(jsonb_build_object('name', product_name, 'quantity', quantity, 'amount_paise', amount)
                                      order by quantity desc, product_name), '[]'::jsonb)
              from sold
        ),
        'production', (
            select coalesce(jsonb_agg(jsonb_build_object('name', name, 'quantity', qty) order by name), '[]'::jsonb)
              from (select name, sum(quantity_delta) as qty from moves
                     where movement_type = 'production' group by name) x
        ),
        'wastage', (
            select coalesce(jsonb_agg(jsonb_build_object('name', name, 'quantity', qty, 'value_paise', val) order by name), '[]'::jsonb)
              from (select name, -sum(quantity_delta) as qty, -sum(quantity_delta * price_paise) as val
                      from moves where movement_type = 'wastage' group by name) x
        ),
        'count_differences', (
            select coalesce(jsonb_agg(jsonb_build_object('name', name, 'quantity', qty, 'value_paise', val) order by name), '[]'::jsonb)
              from (select name, sum(quantity_delta) as qty, sum(quantity_delta * price_paise) as val
                      from moves where stock_count_id is not null group by name) x
        ),
        'daily', (
            select jsonb_agg(jsonb_build_object(
                       'day', d.day,
                       'orders', (select count(distinct order_id) from pay where pay.day = d.day),
                       'gross_paise', (select coalesce(sum(amount_paise), 0) from pay where pay.day = d.day),
                       'refunds_paise', (select coalesce(sum(amount_paise), 0) from ref where ref.day = d.day)
                   ) order by d.day)
              from days d
        )
    ) into v_result;

    return v_result;
end;
$$;

revoke all on function public.sales_report(date, date) from public, anon;
grant execute on function public.sales_report(date, date) to authenticated;
