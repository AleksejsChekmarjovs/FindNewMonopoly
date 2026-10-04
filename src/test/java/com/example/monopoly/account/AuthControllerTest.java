package com.example.monopoly.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** HTTP-API входа: регистрация → me → выход. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    TestRestTemplate http;

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> me(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void registerMeLogout() {
        ResponseEntity<Map> registered = http.postForEntity("/api/auth/register",
                Map.of("username", "Dora", "password", "secret1"), Map.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) registered.getBody().get("token");
        assertThat(registered.getBody()).containsEntry("username", "Dora");

        assertThat(me(token).getBody()).containsEntry("username", "Dora");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        http.postForEntity("/api/auth/logout", new HttpEntity<>(headers), Void.class);
        assertThat(me(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void errorsComeBackAsMessages() {
        ResponseEntity<Map> bad = http.postForEntity("/api/auth/login",
                Map.of("username", "nobody", "password", "secret1"), Map.class);

        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody()).containsEntry("message", "Неверное имя или пароль");
    }
}
