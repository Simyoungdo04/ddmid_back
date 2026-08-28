package com.kh.midpoint.participant.model.service;

import com.kh.midpoint.common.exception.DuplicateException;
import com.kh.midpoint.common.exception.NotFoundException;
import com.kh.midpoint.participant.model.dao.ParticipantMapper;
import com.kh.midpoint.participant.model.dto.ParticipantDto;
import com.kh.midpoint.participant.model.vo.Participant;
import com.kh.midpoint.room.model.dto.RoomDto;
import com.kh.midpoint.room.model.service.RoomService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// 참여자(Participant) 책임만 담당한다 - 방 입장/퇴장의 실제 처리를 한다. 방 자체의 단계
// 전환이나 화면 응답 조합은 RoomService(방 책임)가 한다.
@Service
public class ParticipantService {

	private final ParticipantMapper participantMapper;
	private final RoomService roomService;

	public ParticipantService(ParticipantMapper participantMapper, RoomService roomService) {
		this.participantMapper = participantMapper;
		this.roomService = roomService;
	}

	@Transactional
	public Long join(String roomUuid, String nickname, Double lat, Double lng) {
		RoomDto room = roomService.requireRoom(roomUuid);

		// 방 하나에 참여자가 동시에(더블클릭 등으로) 들어오는 경우를 대비한 사전 체크 -
		// 최종 방어선은 DB의 (ROOM_ID, NICKNAME) 유니크 제약이다(아래 catch 참고).
		participantMapper.findActiveByRoomIdAndNickname(room.getRoomId(), nickname).ifPresent(p -> {
			throw new DuplicateException("이미 사용 중인 닉네임입니다: " + nickname);
		});

		List<ParticipantDto> active = participantMapper.findActiveByRoomId(room.getRoomId());
		Long participantId = participantMapper.nextParticipantId();
		Participant participant = Participant.builder()
				.participantId(participantId)
				.roomId(room.getRoomId())
				.nickname(nickname)
				.isHost(active.isEmpty() ? "Y" : "N")
				.isReady("N")
				.prefLat(lat)
				.prefLng(lng)
				.joinedAt(LocalDateTime.now())
				.build();

		try {
			participantMapper.insert(participant);
		} catch (DuplicateKeyException e) {
			// 동시에 같은 닉네임으로 들어온 경우 - 위 사전 체크를 통과했더라도 DB의
			// (ROOM_ID, NICKNAME) 유니크 제약이 최종 방어선이 되어 여기서 걸린다.
			throw new DuplicateException("이미 사용 중인 닉네임입니다: " + nickname);
		}
		return participantId;
	}

	// 방장이 나가면 남은 사람 중 가장 먼저 들어온 사람에게 방장을 넘긴다 - 방장이 없으면
	// 모드 선택/중간지점 찾기 버튼을 아무도 못 누르게 되기 때문이다.
	@Transactional
	public void leave(String roomUuid, Long participantId) {
		RoomDto room = roomService.requireRoom(roomUuid);
		ParticipantDto participant = participantMapper.findByRoomIdAndParticipantId(room.getRoomId(), participantId)
				.orElseThrow(() -> new NotFoundException("참여자를 찾을 수 없습니다: " + participantId));

		participantMapper.markLeft(room.getRoomId(), participantId, LocalDateTime.now());

		if (participant.isHost()) {
			List<ParticipantDto> remaining = participantMapper.findActiveByRoomId(room.getRoomId());
			if (!remaining.isEmpty()) {
				participantMapper.updateIsHost(room.getRoomId(), remaining.get(0).getParticipantId(), "Y");
			}
		}
	}

	// 중간지점을 못 찾았을 때(NotFoundException 등) 참여자가 방을 나갔다 새로
	// 들어오지 않고도 자기 위치만 다시 찍을 수 있게 한다.
	@Transactional
	public void updateLocation(String roomUuid, Long participantId, Double lat, Double lng) {
		RoomDto room = roomService.requireRoom(roomUuid);
		participantMapper.findByRoomIdAndParticipantId(room.getRoomId(), participantId)
				.orElseThrow(() -> new NotFoundException("참여자를 찾을 수 없습니다: " + participantId));
		participantMapper.updateLocation(room.getRoomId(), participantId, lat, lng);
	}
}
