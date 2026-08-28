package com.kh.midpoint.common.config;

import org.springframework.context.annotation.Configuration;

// 지금은 프론트가 1초 폴링으로 방 상태를 갱신하고 있어서(체감 지연 때문에 2초 -> 1초로
// 줄임) 아직 실제 웹소켓 핸들러는 없다. 나중에 폴링을 웹소켓으로 바꿀 때 여기에
// WebSocketConfigurer 구현을 채운다 - 자리만 미리 만들어둔 빈 설정 클래스다.
@Configuration
public class WebSocketConfig {
}
