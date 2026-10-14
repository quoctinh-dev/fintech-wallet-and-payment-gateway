package com.fintech.wallet.modules.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.wallet.modules.account.dto.request.CreateAccountRequest;
import com.fintech.wallet.modules.account.enums.AccountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Tạo tài khoản thành công - Trả về status 201 CREATED")
    void createAccount_Success() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setUserId(UUID.randomUUID());
        request.setAccountType(AccountType.USER_WALLET);
        request.setCurrency("VND");

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currency").value("VND"))
                .andExpect(jsonPath("$.data.balance").value(0));
    }

    @Test
    @DisplayName("Báo lỗi Validate khi thiếu thông tin bắt buộc - Trả về 400 BAD REQUEST")
    void createAccount_ValidationError() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setCurrency("INVALID_CURRENCY");

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}