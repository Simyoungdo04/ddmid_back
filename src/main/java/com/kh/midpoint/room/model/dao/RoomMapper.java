package com.kh.midpoint.room.model.dao;

import com.kh.midpoint.room.model.dto.RoomDto;
import com.kh.midpoint.room.model.vo.Room;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface RoomMapper {

	// ROOM_ID 시퀀스 다음 값 - 불변 VO를 빌더로 만들기 전에 미리 받아온다.
	Long nextRoomId();

	void insert(Room room);

	// roomUuid는 URL에 쓰이는 공개 식별자(예: /room/CE99EF) - 내부 시퀀스 PK 대신 이걸로 조회한다.
	Optional<RoomDto> findByUuid(@Param("roomUuid") String roomUuid);

	void updateMode(@Param("roomId") Long roomId, @Param("mode") String mode, @Param("stage") String stage);

	void updateMidpoint(
			@Param("roomId") Long roomId, @Param("midpointLat") Double midpointLat,
			@Param("midpointLng") Double midpointLng, @Param("midpointSource") String midpointSource,
			@Param("stage") String stage
	);

	void updateStage(@Param("roomId") Long roomId, @Param("stage") String stage);

	// EXPIRES_AT이 지난 방들의 ROOM_ID - RoomCleanupScheduler가 주기적으로 이걸 조회해서
	// RoomDeletionService.deleteRoom()에 하나씩 넘긴다.
	List<Long> findExpiredRoomIds(@Param("now") LocalDateTime now);

	void deleteById(@Param("roomId") Long roomId);
}
