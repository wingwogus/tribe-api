ALTER TABLE community_post_itinerary
ADD COLUMN memo TEXT NULL COMMENT '원본 여행의 메모' AFTER item_order;
