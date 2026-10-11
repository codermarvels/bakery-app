-- Starter categories. The administrator can rename, reorder, hide or add categories later.
insert into public.categories (slug, name, tagline, sort_order) values
    ('cakes',     'Cakes',     'Celebration & tea-time cakes', 10),
    ('pastries',  'Pastries',  'Flaky, creamy, delicate',      20),
    ('breads',    'Breads',    'Baked fresh every morning',    30),
    ('cookies',   'Cookies',   'Crisp, buttery bites',         40),
    ('snacks',    'Snacks',    'Puffs, rolls & savouries',     50),
    ('beverages', 'Beverages', 'Tea, coffee & coolers',        60)
on conflict (slug) do nothing;
