

-- name: get-collection
select col.collectionname as collection_name,
from collections col
where
  col.collectionid = :collection_id;

--name: sql-get-category-description
select * from categories where id = :category_id;