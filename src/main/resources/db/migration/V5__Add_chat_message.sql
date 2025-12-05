CREATE TABLE chat_message
(
    chat_message_id  BIGINT AUTO_INCREMENT NOT NULL,
    created_at       datetime NULL,
    last_modified_at datetime NULL,
    sender_id        BIGINT   NOT NULL,
    trip_id          BIGINT   NOT NULL,
    content          LONGTEXT NOT NULL,
    CONSTRAINT `PRIMARY` PRIMARY KEY (chat_message_id)
);

ALTER TABLE chat_message
    ADD CONSTRAINT FK4e7rl8st6ja235njwm73ol0k2 FOREIGN KEY (sender_id) REFERENCES trip_member (trip_member_id) ON DELETE NO ACTION;

CREATE INDEX FK4e7rl8st6ja235njwm73ol0k2 ON chat_message (sender_id);

ALTER TABLE chat_message
    ADD CONSTRAINT FKl53fpkbh31a77rwu1fpkxw4y5 FOREIGN KEY (trip_id) REFERENCES trip (trip_id) ON DELETE NO ACTION;

CREATE INDEX FKl53fpkbh31a77rwu1fpkxw4y5 ON chat_message (trip_id);