package com.ddmid.back.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

// 참여자들이 고른 식당이 서로 다를 때(만장일치가 아닐 때) 최종 식당을 정하는 부분만 따로
// 뺐다. 지금은 무작위로 한 명을 뽑지만, 나중에 여기에 게임(사다리타기, 룰렛 등) 결과로
// 승자를 정하는 로직이 들어갈 자리다.
@Service
public class RestaurantTiebreaker {

	// 반환값: 승자로 뽑힌 참여자(Participant) 한 명 — RoomService.resolve()가 이 사람의
	// chosenRestaurant를 room.resolvedRestaurant로 그대로 쓴다. 나중에 무작위 대신 게임으로
	// 바꾸더라도 반환 타입은 Participant로 유지해야 RoomService 쪽 수정 없이 갈아끼울 수 있다.
	public Participant pickWinner(List<Participant> chosen) {
		return chosen.get(new Random().nextInt(chosen.size()));
	}
}
