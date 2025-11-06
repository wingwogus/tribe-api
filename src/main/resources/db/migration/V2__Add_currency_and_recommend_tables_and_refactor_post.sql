ALTER TABLE community_post
    DROP FOREIGN KEY FK9kule8mvm0ic6k3cqaqxyuif;

ALTER TABLE community_post
    DROP FOREIGN KEY FKk1pt4gt75lo5nr7o50v8f5xvd;

CREATE TABLE currency
(
    date          date           NOT NULL,
    exchange_rate DECIMAL(10, 4) NOT NULL,
    cur_unit      VARCHAR(10)    NOT NULL,
    cur_name      VARCHAR(255)   NOT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (date, cur_unit)
);

CREATE TABLE recommended_place
(
    place_id             BIGINT                NULL,
    recommended_place_id BIGINT AUTO_INCREMENT NOT NULL,
    trip_review_id       BIGINT                NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (recommended_place_id)
);

ALTER TABLE community_post
    ADD created_at datetime(6) NULL;

ALTER TABLE community_post
    ADD last_modified_at datetime(6) NULL;

ALTER TABLE community_post
    ADD representative_image_url VARCHAR(255) NULL;

ALTER TABLE community_post
    ADD trip_id BIGINT NOT NULL;

ALTER TABLE expense
    ADD currency VARCHAR(255) NULL;

ALTER TABLE community_post
    ADD CONSTRAINT FK5fl7jc5o06opoco833oms179p FOREIGN KEY (author_id) REFERENCES member (member_id) ON DELETE NO ACTION;

ALTER TABLE community_post
    ADD CONSTRAINT FKjjqsmtlvw70sqov55tb5tn93a FOREIGN KEY (trip_id) REFERENCES trip (trip_id) ON DELETE NO ACTION;

CREATE INDEX FKjjqsmtlvw70sqov55tb5tn93a ON community_post (trip_id);

ALTER TABLE recommended_place
    ADD CONSTRAINT FKjy629ou4bx9a0r4bjvcvu1t8b FOREIGN KEY (trip_review_id) REFERENCES trip_review (trip_review_id) ON DELETE NO ACTION;

ALTER TABLE recommended_place
    ADD CONSTRAINT FKpi50me0nw0wbubwnv4bp38mkn FOREIGN KEY (place_id) REFERENCES place (place_id) ON DELETE NO ACTION;

ALTER TABLE community_post
    DROP COLUMN item_id;

ALTER TABLE community_post
    DROP COLUMN status;

ALTER TABLE community_post
    MODIFY title VARCHAR(255) NOT NULL;

-- 9. [누락되었던] trip_member 'role' enum 변경
ALTER TABLE trip_member
MODIFY COLUMN role enum('GUEST','MEMBER','OWNER') NOT NULL;