package com.foody.delivery.rpd.security;

import com.foody.delivery.rpd.domain.user.User;
import com.foody.delivery.rpd.domain.user.UserRepository;
import com.foody.delivery.rpd.infra.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Transactional
public class UserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void shouldLoadUserByUsernameEmail() {
        when(userRepository.findByEmail("teste@teste.com")).thenReturn(Optional.of(new User("Teste", "teste@teste.com", "hash")));
        final UserDetails userDetails = userDetailsService.loadUserByUsername("teste@teste.com");

        assertEquals("teste@teste.com", userDetails.getUsername());
        assertEquals("hash", userDetails.getPassword());
        final Optional<? extends GrantedAuthority> role = userDetails.getAuthorities().stream().findFirst();
        assertTrue(role.isPresent());
        assertEquals("ROLE_CLIENT", role.get().getAuthority());
    }

    @Test
    void shouldThrowExceptionWhenUsernameNotFound() {
        when(userRepository.findByEmail("teste@teste.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername("teste@teste.com"));
    }
}
