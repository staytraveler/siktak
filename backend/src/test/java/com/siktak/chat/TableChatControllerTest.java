package com.siktak.chat;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.siktak.chat.api.TableChatController;
import com.siktak.chat.application.ChatService;
import com.siktak.chat.domain.ChatMessage;
import com.siktak.common.config.SecurityConfig;
import com.siktak.common.error.BusinessException;
import com.siktak.common.error.ErrorCode;
import com.siktak.common.error.GlobalExceptionHandler;
import com.siktak.common.security.JwtConfig;
import com.siktak.common.security.JwtTokenService;
import com.siktak.common.security.JwtUserAuthenticationConverter;
import com.siktak.user.domain.User;
import com.siktak.user.domain.UserRole;
import com.siktak.user.infra.UserRepository;

/** 9.2 POST /api/v1/tables/{tableId}/messages — 200 / 403 / 400. */
@WebMvcTest(TableChatController.class)
@Import({SecurityConfig.class, JwtConfig.class, JwtUserAuthenticationConverter.class, GlobalExceptionHandler.class})
class TableChatControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenService jwtTokenService;

    @MockitoBean private UserRepository userRepository;
    @MockitoBean private ChatService chatService;
    @MockitoBean private Clock clock;

    private String auth() {
        User user = User.create("user01", UserRole.PARTICIPANT, "김참가", null);
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        return "Bearer " + jwtTokenService.issue(user).accessToken();
    }

    private static String json(String content) {
        return "{\"content\":\"" + content + "\"}";
    }

    @Test
    void 구성원_전송은_200이고_내_메시지로_내려간다() throws Exception {
        String auth = auth();
        ChatMessage saved = ChatMessage.fromUser(70L, 1L, "안녕");
        ReflectionTestUtils.setField(saved, "id", 5L);
        given(chatService.postUserMessage(7L, 1L, "안녕")).willReturn(saved);
        given(chatService.senderInitials(List.of(saved))).willReturn(Map.of(1L, "김"));

        mockMvc.perform(post("/api/v1/tables/7/messages").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(json("안녕")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("안녕"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.senderLabel").value("김"));
    }

    @Test
    void 비구성원이면_403() throws Exception {
        String auth = auth();
        given(chatService.postUserMessage(8L, 1L, "hi")).willThrow(new BusinessException(ErrorCode.FORBIDDEN));
        mockMvc.perform(post("/api/v1/tables/8/messages").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(json("hi")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 빈_값이면_400() throws Exception {
        String auth = auth();
        given(chatService.postUserMessage(7L, 1L, "")).willThrow(new BusinessException(ErrorCode.INVALID_REQUEST));
        mockMvc.perform(post("/api/v1/tables/7/messages").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(json("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(post("/api/v1/tables/7/messages").contentType(MediaType.APPLICATION_JSON).content(json("hi")))
                .andExpect(status().isUnauthorized());
    }
}
