-- OPTIONAL test products so you can try the catalogue and cart before the admin screens
-- exist (Stage 5). Run in Supabase → SQL Editor. Safe to run more than once.
-- To remove them later:  update public.products set is_active = false where description like '[TEST]%';

with new_products (slug, name, description, price_paise, stock) as (
    values
        ('cakes',     'Black Forest Cake (500 g)', '[TEST] Chocolate sponge, cherries and fresh cream', 45000, 6),
        ('cakes',     'Plum Cake (250 g)',         '[TEST] Rich fruit cake',                            18000, 10),
        ('pastries',  'Pineapple Pastry',          '[TEST] Light sponge with pineapple cream',          6000, 15),
        ('breads',    'Milk Bread',                '[TEST] Soft sandwich loaf',                          4500, 12),
        ('cookies',   'Butter Cookies (200 g)',    '[TEST] Crisp, buttery and golden',                  12000, 0),
        ('snacks',    'Veg Puff',                  '[TEST] Flaky puff with spiced vegetables',           2500, 30),
        ('beverages', 'Filter Coffee',             '[TEST] Freshly brewed',                              3000, 40)
),
inserted as (
    insert into public.products (category_id, name, description, price_paise)
    select c.id, n.name, n.description, n.price_paise
      from new_products n
      join public.categories c on c.slug = n.slug
     where not exists (select 1 from public.products p where p.name = n.name)
    returning id, name
)
-- Opening stock goes through the stock ledger, exactly like real production entries.
insert into public.stock_movements (product_id, movement_type, quantity_delta, reason)
select i.id, 'production', n.stock, 'Test opening stock'
  from inserted i
  join new_products n on n.name = i.name
 where n.stock > 0;

-- Check:
select p.name, c.name as category, (p.price_paise / 100.0)::numeric(10, 2) as price_rupees, p.stock_quantity
  from public.products p join public.categories c on c.id = p.category_id
 order by c.sort_order, p.name;
