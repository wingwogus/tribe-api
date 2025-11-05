-- 1. community_post의 기존 외래 키 삭제
ALTER TABLE community_post
DROP FOREIGN KEY FK9kule8mvm0ic6k3cqaqxyuif;

ALTER TABLE community_post
DROP FOREIGN KEY FKk1pt4gt75lo5nr7o50v8f5xvd;

-- 2. recommended_place 테이블 생성
CREATE TABLE recommended_place
(
    place_id              BIGINT NULL,
    recommended_place_id BIGINT AUTO_INCREMENT NOT NULL,
    trip_review_id        BIGINT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (recommended_place_id)
);

-- 3. community_post 컬럼 추가
ALTER TABLE community_post
    ADD created_at datetime(6) NULL;

ALTER TABLE community_post
    ADD last_modified_at datetime(6) NULL;

ALTER TABLE community_post
    ADD representative_image_url VARCHAR(255) NULL;

ALTER TABLE community_post
    ADD trip_id BIGINT NOT NULL;

-- 4. community_post 새 외래 키 및 인덱스 추가
ALTER TABLE community_post
    ADD CONSTRAINT FK5fl7jc5o06opoco833oms179p FOREIGN KEY (author_id) REFERENCES member (member_id) ON DELETE NO ACTION;

ALTER TABLE community_post
    ADD CONSTRAINT FKjjqsmtlvw70sqov55tb5tn9a FOREIGN KEY (trip_id) REFERENCES trip (trip_id) ON DELETE NO ACTION;

CREATE INDEX FKjjqsmtlvw70sqov55tb5tn9a ON community_post (trip_id);

-- 5. recommended_place 새 외래 키 추가 (인덱스는 자동 생성됨)
ALTER TABLE recommended_place
    ADD CONSTRAINT FKjy629ou4bx9a0r4bjvcvu1t8b FOREIGN KEY (trip_review_id) REFERENCES trip_review (trip_review_id) ON DELETE NO ACTION;

ALTER TABLE recommended_place
    ADD CONSTRAINT FKpi50me0nw0wbubwnv4bp38mkn FOREIGN KEY (place_id) REFERENCES place (place_id) ON DELETE NO ACTION;

-- 6. community_post 기존 컬럼 삭제
ALTER TABLE community_post
DROP COLUMN item_id;

ALTER TABLE community_post
DROP COLUMN status;

-- 7. community_post 컬럼 속성 변경
ALTER TABLE community_post
    MODIFY title VARCHAR(255) NOT NULL;

-- 8. [누락되었던] trip_member 'role' enum 변경
ALTER TABLE trip_member MODIFY COLUMN role enum('GUEST','MEMBER','OWNER') NOT NULL;