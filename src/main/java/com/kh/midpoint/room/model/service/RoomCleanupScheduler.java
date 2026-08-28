package com.kh.midpoint.room.model.service;

import com.kh.midpoint.room.model.dao.RoomMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// 만든 지 EXPIRATION_HOURS(RoomService)가 지난 방을 주기적으로 찾아서 지운다. Redis 기반
// TTL로 바꾸기 전까지의 임시 구현 - 활동 여부와 무관하게 생성 시각 기준 고정 타이머다.
@Component
public class RoomCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(RoomCleanupScheduler.class);
	private static final long CHECK_INTERVAL_MS = 10 * 60 * 1000;

	private final RoomMapper roomMapper;
	private final RoomDeletionService roomDeletionService;

	public RoomCleanupScheduler(RoomMapper roomMapper, RoomDeletionService roomDeletionService) {
		this.roomMapper = roomMapper;
		this.roomDeletionService = roomDeletionService;
	}

	@Scheduled(fixedRate = CHECK_INTERVAL_MS)
	public void deleteExpiredRooms() {
		List<Long> expiredRoomIds = roomMapper.findExpiredRoomIds(LocalDateTime.now());
		for (Long roomId : expiredRoomIds) {
			try {
				roomDeletionService.deleteRoom(roomId);
				log.info("만료된 방 삭제: roomId={}", roomId);
			} catch (RuntimeException e) {
				// 방 하나 삭제가 실패해도 나머지 방은 계속 지운다 - 다음 주기에 다시 시도된다.
				log.warn("만료된 방 삭제 실패: roomId={}", roomId, e);
			}
		}
	}
}
