package com.foody.delivery.rpd.domain.user;

import com.foody.delivery.rpd.infra.exception.UserAlreadyRegisteredException;
import com.foody.delivery.rpd.infra.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(final String name, final String email, final String password) {
        final String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyRegisteredException("Email já cadastrado");
        }

        final String hash = passwordEncoder.encode(password);
        return userRepository.save(new User(name, normalizedEmail, hash));
    }

    public User findByEmail(final String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Usuário com email " + email + " não encontrado"));
    }

    @Transactional
    public void createOperatorIfAbsent(final String name, final String email, final String password) {
        final String normalized = email.trim().toLowerCase();
        if (!userRepository.existsByEmail(normalized)) {
            userRepository.save(new User(name, normalized, passwordEncoder.encode(password), Role.OPERATOR));
        }
    }
}
