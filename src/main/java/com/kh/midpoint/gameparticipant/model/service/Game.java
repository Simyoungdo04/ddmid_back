package com.kh.midpoint.gameparticipant.model.service;

import com.kh.midpoint.gameparticipant.model.dao.GameParticipantMapper;
import com.kh.midpoint.gameparticipant.model.vo.GameParticipant;
import com.kh.midpoint.participant.model.dto.ParticipantDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

// 참여자들이 고른 식당이 서로 다를 때(만장일치가 아닐 때) 최종 식당을 정하는 부분을 담당한다.
// 지금은 무작위로 한 명을 뽑지만(예전 RestaurantTiebreaker와 같은 로직), 나중에 여기에
// 게임(사다리타기, 룰렛 등) 결과로 승자를 정하는 로직이 들어갈 자리라 클래스 이름을 Game으로
// 뒀다. 승자는 GAME_PARTICIPANT에 기록해야 ROOM_RESULT.GAME_PARTICIPANT_ID가 채워진다.
@Service
public class Game {

	private final GameParticipantMapper gameParticipantMapper;

	public Game(GameParticipantMapper gameParticipantMapper) {
		this.gameParticipantMapper = gameParticipantMapper;
	}

	// 승자(그리고 승자의 GAME_PARTICIPANT 행 ID)를 함께 돌려준다 - ROOM_RESULT가 그 ID를
	// 그대로 참조해야 하기 때문이다.
	public record GameResult(ParticipantDto winner, Long gameParticipantId) {
	}

	// 만장일치일 때: 실제로는 게임이 진행되지 않았지만, ROOM_RESULT.GAME_PARTICIPANT_ID가
	// NOT NULL이라 참조할 승자 레코드가 하나는 있어야 한다 - 그 사람만 기록한다.
	@Transactional
	public GameResult recordUnanimousWinner(Long roomId, ParticipantDto winner) {
		Long gameParticipantId = insert(roomId, winner.getParticipantId(), UUID.randomUUID().toString(), true);
		return new GameResult(winner, gameParticipantId);
	}

	// 만장일치가 아닐 때: 후보 전원을 같은 게임 세션으로 묶어서 기록하고, 그중 무작위로
	// 한 명을 승자로 표시한다.
	@Transactional
	public GameResult pickWinner(Long roomId, List<ParticipantDto> candidates) {
		ParticipantDto winner = candidates.get(new Random().nextInt(candidates.size()));
		String gameSessionId = UUID.randomUUID().toString();

		Long winnerGameParticipantId = null;
		for (ParticipantDto candidate : candidates) {
			Long gameParticipantId = insert(roomId, candidate.getParticipantId(), gameSessionId, candidate == winner);
			if (candidate == winner) {
				winnerGameParticipantId = gameParticipantId;
			}
		}
		return new GameResult(winner, winnerGameParticipantId);
	}

	private Long insert(Long roomId, Long participantId, String gameSessionId, boolean winner) {
		Long gameParticipantId = gameParticipantMapper.nextGameParticipantId();
		GameParticipant gameParticipant = GameParticipant.builder()
				.gameParticipantId(gameParticipantId)
				.roomId(roomId)
				.participantId(participantId)
				.gameSessionId(gameSessionId)
				.isWinner(winner ? "Y" : "N")
				.joinedAt(LocalDateTime.now())
				.build();
		gameParticipantMapper.insert(gameParticipant);
		return gameParticipantId;
	}
}
