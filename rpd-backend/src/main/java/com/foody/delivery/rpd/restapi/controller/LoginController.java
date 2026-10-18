package com.foody.delivery.rpd.restapi.controller;

import com.foody.delivery.rpd.domain.user.User;
import com.foody.delivery.rpd.domain.user.UserService;
import com.foody.delivery.rpd.infra.security.JwtService;
import com.foody.delivery.rpd.restapi.dto.login.LoginRequest;
import com.foody.delivery.rpd.restapi.dto.login.TokenResponse;
import com.foody.delivery.rpd.restapi.dto.user.NewUserRequest;
import com.foody.delivery.rpd.restapi.dto.user.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação", description = "Cadastro de usuários e login")
@SecurityRequirements
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class LoginController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    @Operation(
            summary = "Autentica um usuário",
            description = "Valida e-mail e senha e devolve um token JWT, válido por 1 hora. "
                    + "Use-o no botão Authorize ou no cabeçalho `Authorization: Bearer <token>`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login realizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Campos inválidos",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        final Authentication auth = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password()));
        return ResponseEntity.ok(new TokenResponse(jwtService.generate(auth)));
    }

    @Operation(
            summary = "Cadastra um novo usuário",
            description = "Cria um usuário com papel CLIENT. O e-mail é normalizado (minúsculas, sem espaços "
                    + "nas pontas) e deve ser único.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário criado",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos inválidos",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "E-mail já cadastrado",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody NewUserRequest request) {
        final User user = userService.register(request.name(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponse.from(user));
    }
}
