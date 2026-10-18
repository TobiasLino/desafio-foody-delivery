package com.foody.delivery.rpd.domain;

import com.foody.delivery.rpd.domain.user.User;
import com.foody.delivery.rpd.domain.user.UserRepository;
import com.foody.delivery.rpd.domain.user.UserService;
import com.foody.delivery.rpd.infra.exception.UserAlreadyRegisteredException;
import com.foody.delivery.rpd.infra.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldRegisterUserWithNormalizedEmailAndEncodedPassword() {
        when(userRepository.existsByEmail("teste@teste.com")).thenReturn(false);
        when(passwordEncoder.encode("teste123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        final User user = userService.register("Teste", "Teste@Teste.com", "teste123");

        assertEquals("teste@teste.com", user.getEmail());
        assertEquals("hash", user.getPassword());

        final ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        final User saved = captor.getValue();

        assertEquals("Teste", saved.getName());
        assertEquals("teste@teste.com", saved.getEmail());
        assertEquals("hash", saved.getPassword());
        assertNotEquals("teste123", saved.getPassword());
    }

    @Test
    void shouldThrowExceptionWhenEmailExists() {
        when(userRepository.existsByEmail("teste@teste.com")).thenReturn(true);

        assertThrows(UserAlreadyRegisteredException.class,
                () -> userService.register("Teste", "Teste@Teste.com", "teste123"));

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldThrowUserNotFoundExceptionWhenSearchByEmail() {
        when(userRepository.findByEmail("teste@teste.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.findByEmail("teste@teste.com"));
    }
}
