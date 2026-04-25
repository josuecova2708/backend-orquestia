package com.orquestia.diagramador;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DiagramadorSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/diagrama/{procesoId}")
    public void handleEvent(
            @DestinationVariable String procesoId,
            @Payload DiagramaEvent event,
            SimpMessageHeaderAccessor headerAccessor) {

        // Inject identity from JWT handshake stored in STOMP session
        String userId  = (String) headerAccessor.getSessionAttributes().get("userId");
        String userName = (String) headerAccessor.getSessionAttributes().get("userName");
        event.setUserId(userId != null ? userId : "anon");
        event.setUserName(userName != null ? userName : "Anónimo");

        // Broadcast to all subscribers of this proceso (including sender — frontend filters)
        messagingTemplate.convertAndSend("/topic/diagrama/" + procesoId, event);
    }
}
