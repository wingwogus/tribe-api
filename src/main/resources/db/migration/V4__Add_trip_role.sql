-- TripRole 권한 추가
ALTER TABLE trip_member
MODIFY COLUMN role enum('GUEST','MEMBER','OWNER', 'ADMIN', 'KICKED', 'EXITED') NOT NULL;