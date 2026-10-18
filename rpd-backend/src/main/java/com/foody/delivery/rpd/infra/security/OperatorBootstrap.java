package com.foody.delivery.rpd.infra.security;

import com.foody.delivery.rpd.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OperatorBootstrap implements ApplicationRunner {

    private final UserService userService;

    @Value("${app.operator.email:}")
    private String email;

    @Value("${app.operator.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        userService.createOperatorIfAbsent("Operador", email, password);
    }
}
