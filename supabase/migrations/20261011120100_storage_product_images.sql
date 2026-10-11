-- =====================================================================================
-- Storage bucket for product photographs.
-- Public bucket: anyone can VIEW a photo by its URL (customers need to see them).
-- Only administrators can upload, replace or delete photos. Max 5 MB, images only.
-- =====================================================================================

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('product-images', 'product-images', true, 5242880,
        array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
    set public = excluded.public,
        file_size_limit = excluded.file_size_limit,
        allowed_mime_types = excluded.allowed_mime_types;

create policy "product images: admins list"
    on storage.objects for select to authenticated
    using (bucket_id = 'product-images' and (select private.is_admin()));

create policy "product images: admins upload"
    on storage.objects for insert to authenticated
    with check (bucket_id = 'product-images' and (select private.is_admin()));

create policy "product images: admins replace"
    on storage.objects for update to authenticated
    using (bucket_id = 'product-images' and (select private.is_admin()))
    with check (bucket_id = 'product-images' and (select private.is_admin()));

create policy "product images: admins delete"
    on storage.objects for delete to authenticated
    using (bucket_id = 'product-images' and (select private.is_admin()));
