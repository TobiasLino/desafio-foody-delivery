package com.foody.delivery.rpd.restapi;

import com.foody.delivery.rpd.domain.user.Role;
import com.foody.delivery.rpd.domain.user.User;
import com.foody.delivery.rpd.domain.user.UserRepository;
import com.foody.delivery.rpd.domain.user.UserService;
import com.foody.delivery.rpd.integration.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@IntegrationTest
public class LoginControllerTest extends AbstractControllerTest {


    private static final String LOGIN = "/auth/login";
    private static final String REGISTER = "/auth/register";
    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final String EMAIL = "maria.silva@test.com";
    private static final String PASSWORD = "senha1234";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    // =====================================================================================
    // POST /auth/register
    // =====================================================================================

    @Test
    void shouldRegisterNewUser() throws Exception {
        postJson(REGISTER, registerJson("Maria Silva", EMAIL, PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("CLIENT"));

        assertTrue(userRepository.findByEmail(EMAIL).isPresent());
    }

    @Test
    void shouldNotExposeThePasswordInTheRegisterResponse() throws Exception {
        final MvcResult result = postJson(REGISTER, registerJson("Maria Silva", EMAIL, PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        assertFalse(body(result).contains(PASSWORD));
        assertFalse(body(result).contains("$2"), "o hash BCrypt não deve aparecer na resposta");
    }

    @Test
    void shouldStoreThePasswordHashed() throws Exception {
        postJson(REGISTER, registerJson("Maria Silva", EMAIL, PASSWORD))
                .andExpect(status().isCreated());

        final String stored = userRepository.findByEmail(EMAIL).orElseThrow().getPassword();
        assertNotEquals(PASSWORD, stored);
        assertTrue(stored.startsWith("$2"), "esperava um hash BCrypt, mas veio: " + stored);
        assertTrue(passwordEncoder.matches(PASSWORD, stored));
    }

    @Test
    void shouldNormalizeEmailToLowercaseOnRegister() throws Exception {
        postJson(REGISTER, registerJson("Maria Silva", "Maria.SILVA@Test.COM", PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("maria.silva@test.com"));

        assertTrue(userRepository.findByEmail("maria.silva@test.com").isPresent());
    }

    @Test
    void shouldAcceptAccentedCharactersInTheName() throws Exception {
        final MvcResult result = postJson(REGISTER, registerJson("José da Conceição", EMAIL, PASSWORD))
                .andExpect(status().isCreated())
                .andReturn();

        final String name = JsonPath.read(body(result), "$.name");
        assertEquals("José da Conceição", name);
        assertEquals("José da Conceição", userRepository.findByEmail(EMAIL).orElseThrow().getName());
    }

    @Test
    void shouldAllowDifferentUsersWithTheSameName() throws Exception {
        postJson(REGISTER, registerJson("Maria Silva", "maria.um@test.com", PASSWORD))
                .andExpect(status().isCreated());
        postJson(REGISTER, registerJson("Maria Silva", "maria.dois@test.com", PASSWORD))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldAlwaysRegisterAsClientEvenWhenRoleIsSentInTheBody() throws Exception {
        final String body = """
                {"name": "Maria Silva", "email": "%s", "password": "%s", "role": "OPERATOR"}
                """.formatted(EMAIL, PASSWORD);

        postJson(REGISTER, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CLIENT"));

        assertEquals(Role.CLIENT, userRepository.findByEmail(EMAIL).orElseThrow().getRole());
    }

    @ParameterizedTest(name = "senha com {0} caracteres")
    @ValueSource(ints = {8, 72})
    void shouldAcceptPasswordsAtTheLengthLimits(final int length) throws Exception {
        postJson(REGISTER, registerJson("Maria Silva", EMAIL, "a".repeat(length)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldAcceptNameWith250Characters() throws Exception {
        postJson(REGISTER, registerJson("n".repeat(250), EMAIL, PASSWORD))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturn409WhenEmailIsAlreadyRegistered() throws Exception {
        postJson(REGISTER, registerJson("Maria Silva", EMAIL, PASSWORD))
                .andExpect(status().isCreated());

        postJson(REGISTER, registerJson("Outra Maria", EMAIL, "outraSenha123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturn409WhenEmailIsAlreadyRegisteredWithDifferentCase() throws Exception {
        postJson(REGISTER, registerJson("Maria Silva", EMAIL, PASSWORD))
                .andExpect(status().isCreated());

        postJson(REGISTER, registerJson("Outra Maria", EMAIL.toUpperCase(), PASSWORD))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn409WhenEmailBelongsToAUserNotCreatedThroughTheApi() throws Exception {
        userRepository.save(new User("Operador", "operador.login@test.com", "hash", Role.OPERATOR));

        postJson(REGISTER, registerJson("Intruso", "operador.login@test.com", PASSWORD))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldNotChangeTheExistingUserWhenRegisteringADuplicate() throws Exception {
        postJson(REGISTER, registerJson("Maria Silva", EMAIL, PASSWORD))
                .andExpect(status().isCreated());
        postJson(REGISTER, registerJson("Impostora", EMAIL, "senhaDaImpostora"))
                .andExpect(status().isConflict());

        final User stored = userRepository.findByEmail(EMAIL).orElseThrow();
        assertEquals("Maria Silva", stored.getName());
        assertTrue(passwordEncoder.matches(PASSWORD, stored.getPassword()));
    }

    static Stream<Arguments> invalidRegistrations() {
        final String validEmail = "novo.usuario@test.com";
        return Stream.of(
                // nome
                Arguments.of("sem nome",
                        "{\"email\": \"" + validEmail + "\", \"password\": \"senha1234\"}", "name"),
                Arguments.of("nome nulo",
                        "{\"name\": null, \"email\": \"" + validEmail + "\", \"password\": \"senha1234\"}", "name"),
                Arguments.of("nome em branco",
                        registerJson("", validEmail, "senha1234"), "name"),
                Arguments.of("nome só com espaços",
                        registerJson("   ", validEmail, "senha1234"), "name"),
                Arguments.of("nome acima de 250 caracteres",
                        registerJson("n".repeat(251), validEmail, "senha1234"), "name"),
                // email
                Arguments.of("sem email",
                        "{\"name\": \"Maria\", \"password\": \"senha1234\"}", "email"),
                Arguments.of("email nulo",
                        "{\"name\": \"Maria\", \"email\": null, \"password\": \"senha1234\"}", "email"),
                Arguments.of("email em branco",
                        registerJson("Maria", "", "senha1234"), "email"),
                Arguments.of("email sem arroba",
                        registerJson("Maria", "maria.test.com", "senha1234"), "email"),
                Arguments.of("email sem domínio",
                        registerJson("Maria", "maria@", "senha1234"), "email"),
                Arguments.of("email sem usuário",
                        registerJson("Maria", "@test.com", "senha1234"), "email"),
                Arguments.of("email com espaço no meio",
                        registerJson("Maria", "ma ria@test.com", "senha1234"), "email"),
                Arguments.of("email com dois arrobas",
                        registerJson("Maria", "maria@@test.com", "senha1234"), "email"),
                // senha
                Arguments.of("sem senha",
                        "{\"name\": \"Maria\", \"email\": \"" + validEmail + "\"}", "password"),
                Arguments.of("senha nula",
                        "{\"name\": \"Maria\", \"email\": \"" + validEmail + "\", \"password\": null}", "password"),
                Arguments.of("senha em branco",
                        registerJson("Maria", validEmail, ""), "password"),
                Arguments.of("senha só com espaços",
                        registerJson("Maria", validEmail, "        "), "password"),
                Arguments.of("senha com 7 caracteres",
                        registerJson("Maria", validEmail, "1234567"), "password"),
                Arguments.of("senha com 73 caracteres",
                        registerJson("Maria", validEmail, "a".repeat(73)), "password"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRegistrations")
    void shouldReturn400WhenRegistrationIsInvalid(final String description, final String body, final String field)
            throws Exception {
        postJson(REGISTER, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").exists());

        assertTrue(userRepository.findByEmail("novo.usuario@test.com").isEmpty());
    }

    @Test
    void shouldReportEveryInvalidFieldAtOnce() throws Exception {
        final MvcResult result = postJson(REGISTER, registerJson("", "invalido", "123"))
                .andExpect(status().isBadRequest())
                .andReturn();

        final List<String> fields = JsonPath.read(body(result), "$.errors[*].field");
        assertTrue(fields.containsAll(List.of("name", "email", "password")), "campos reportados: " + fields);
    }

    @Test
    void shouldReturn400WhenRegisterBodyIsMalformedJson() throws Exception {
        postJson(REGISTER, "{\"name\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn400WhenRegisterBodyIsMissing() throws Exception {
        getMockMvc().perform(post(REGISTER).contentType(JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenRegisterBodyIsAJsonArray() throws Exception {
        postJson(REGISTER, "[]")
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn415WhenRegisterContentTypeIsNotJson() throws Exception {
        getMockMvc().perform(post(REGISTER).contentType(MediaType.TEXT_PLAIN)
                        .content(registerJson("Maria", EMAIL, PASSWORD)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void shouldReturn405WhenCallingRegisterWithAnotherMethod() throws Exception {
        getMockMvc().perform(get(REGISTER)).andExpect(status().isMethodNotAllowed());
        getMockMvc().perform(put(REGISTER).contentType(JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        getMockMvc().perform(delete(REGISTER)).andExpect(status().isMethodNotAllowed());
    }

    // =====================================================================================
    // POST /auth/login
    // =====================================================================================

    @Test
    void shouldLoginWithEmailAndPassword() throws Exception {
        registerUser("Maria Silva", EMAIL, PASSWORD);

        final MvcResult result = postJson(LOGIN, loginJson(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(JSON))
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        final String token = JsonPath.read(body(result), "$.token");
        assertEquals(3, token.split("\\.").length, "o token deve ter 3 partes (header.payload.assinatura)");
    }

    @Test
    void shouldIssueATokenWithTheExpectedClaims() throws Exception {
        registerUser("Maria Silva", EMAIL, PASSWORD);

        final Jwt jwt = jwtDecoder.decode(loginAndGetToken(EMAIL, PASSWORD));

        assertEquals(EMAIL, jwt.getSubject());
        assertEquals("ROLE_CLIENT", jwt.getClaimAsString("scope"));
        assertEquals("rpd-backend", jwt.getClaimAsString("iss"));
        assertEquals(Duration.ofSeconds(3600), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
    }

    @Test
    void shouldIssueOperatorScopeWhenTheUserIsAnOperator() throws Exception {
        userService.createOperatorIfAbsent("Operador", "operador.login@test.com", "operador123");

        final Jwt jwt = jwtDecoder.decode(loginAndGetToken("operador.login@test.com", "operador123"));

        assertEquals("operador.login@test.com", jwt.getSubject());
        assertEquals("ROLE_OPERATOR", jwt.getClaimAsString("scope"));
    }

    @Test
    void shouldLoginIgnoringTheEmailCase() throws Exception {
        registerUser("Maria Silva", EMAIL, PASSWORD);

        final Jwt jwt = jwtDecoder.decode(loginAndGetToken("MARIA.Silva@TEST.com", PASSWORD));

        assertEquals(EMAIL, jwt.getSubject(), "o subject do token deve ser o e-mail normalizado");
    }

    @Test
    void shouldLoginWithAnEmailRegisteredInUppercase() throws Exception {
        postJson(REGISTER, registerJson("Maria Silva", "MARIA.SILVA@TEST.COM", PASSWORD))
                .andExpect(status().isCreated());

        postJson(LOGIN, loginJson(EMAIL, PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRegisterNewUserWithEmailAndPasswordAndLogin() throws Exception {
        postJson(REGISTER, registerJson("Maria Silva", EMAIL, PASSWORD))
                .andExpect(status().isCreated());

        final String token = loginAndGetToken(EMAIL, PASSWORD);

        getMockMvc().perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void shouldOpenProtectedEndpointsOnlyWithTheTokenFromLogin() throws Exception {
        registerUser("Maria Silva", EMAIL, PASSWORD);
        final String token = loginAndGetToken(EMAIL, PASSWORD);

        getMockMvc().perform(get("/orders"))
                .andExpect(status().isUnauthorized());

        getMockMvc().perform(get("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldLetAClientCreateAnOrderWithTheTokenFromLogin() throws Exception {
        registerUser("Maria Silva", EMAIL, PASSWORD);
        final String token = loginAndGetToken(EMAIL, PASSWORD);

        final String order = """
                {"address": {"cep": "12289020", "street": "Rua A", "neighborhood": "Centro",
                             "number": "10", "city": "Campinas", "uf": "SP"},
                 "items": [{"description": "Pizza de calabresa"}]}
                """;

        getMockMvc().perform(post("/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(JSON)
                        .content(order))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.email").value(EMAIL));
    }

    static Stream<Arguments> wrongCredentials() {
        return Stream.of(
                Arguments.of("senha errada", EMAIL, "senhaErrada1"),
                Arguments.of("senha com maiúsculas trocadas", EMAIL, "SENHA1234"),
                Arguments.of("senha com espaço no final", EMAIL, "senha1234 "),
                Arguments.of("senha com espaço no início", EMAIL, " senha1234"),
                Arguments.of("senha parecida (um caractere a menos)", EMAIL, "senha123"),
                Arguments.of("email inexistente", "nao.existe@test.com", PASSWORD),
                Arguments.of("email de outro usuário com a senha desta", "outro@test.com", PASSWORD));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrongCredentials")
    void shouldReturn401WhenCredentialsAreWrong(final String description, final String email, final String password)
            throws Exception {
        registerUser("Maria Silva", EMAIL, PASSWORD);
        registerUser("Outro Usuario", "outro@test.com", "outraSenha123");

        postJson(LOGIN, loginJson(email, password))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void shouldReturnProblemJsonWhenCredentialsAreWrong() throws Exception {
        registerUser("Maria Silva", EMAIL, PASSWORD);

        postJson(LOGIN, loginJson(EMAIL, "senhaErrada1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void shouldReturnTheSameResponseForUnknownEmailAndWrongPassword() throws Exception {
        registerUser("Maria Silva", EMAIL, PASSWORD);

        final MvcResult wrongPassword = postJson(LOGIN, loginJson(EMAIL, "senhaErrada1")).andReturn();
        final MvcResult unknownEmail = postJson(LOGIN, loginJson("nao.existe@test.com", "senhaErrada1")).andReturn();

        // A resposta não pode permitir descobrir quais e-mails estão cadastrados.
        assertEquals(wrongPassword.getResponse().getStatus(), unknownEmail.getResponse().getStatus());
        final String detailWrongPassword = JsonPath.read(body(wrongPassword), "$.detail");
        final String detailUnknownEmail = JsonPath.read(body(unknownEmail), "$.detail");
        assertEquals(detailWrongPassword, detailUnknownEmail);
    }

    @Test
    void shouldReportInvalidCredentialsMessage() throws Exception {
        registerUser("Maria Silva", EMAIL, PASSWORD);

        final MvcResult result = postJson(LOGIN, loginJson(EMAIL, "senhaErrada1"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        final String detail = JsonPath.read(body(result), "$.detail");
        assertEquals("Email ou senha inválidos", detail);
    }

    @Test
    void shouldLoginAfterFailedAttempts() throws Exception {
        registerUser("Maria Silva", EMAIL, PASSWORD);

        postJson(LOGIN, loginJson(EMAIL, "errada1234")).andExpect(status().isUnauthorized());
        postJson(LOGIN, loginJson(EMAIL, "errada5678")).andExpect(status().isUnauthorized());

        postJson(LOGIN, loginJson(EMAIL, PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void shouldNotLoginWithTheHashStoredInTheDatabase() throws Exception {
        registerUser("Maria Silva", EMAIL, PASSWORD);
        final String hash = userRepository.findByEmail(EMAIL).orElseThrow().getPassword();

        postJson(LOGIN, loginJson(EMAIL, hash))
                .andExpect(status().isUnauthorized());
    }

    static Stream<Arguments> invalidLogins() {
        return Stream.of(
                Arguments.of("corpo vazio {}", "{}", "email"),
                Arguments.of("sem email", "{\"password\": \"senha1234\"}", "email"),
                Arguments.of("email nulo", "{\"email\": null, \"password\": \"senha1234\"}", "email"),
                Arguments.of("email em branco", loginJson("", "senha1234"), "email"),
                Arguments.of("email sem arroba", loginJson("maria.test.com", "senha1234"), "email"),
                Arguments.of("email sem domínio", loginJson("maria@", "senha1234"), "email"),
                Arguments.of("sem senha", "{\"email\": \"" + EMAIL + "\"}", "password"),
                Arguments.of("senha nula", "{\"email\": \"" + EMAIL + "\", \"password\": null}", "password"),
                Arguments.of("senha em branco", loginJson(EMAIL, ""), "password"),
                Arguments.of("senha só com espaços", loginJson(EMAIL, "     "), "password"));
    }

    /**
     * Exige {@code @NotBlank} em {@code email} e {@code password} no {@code LoginRequest}. Hoje só há
     * {@code @Email}: um email ausente causa NPE em {@code request.email().trim()} (500), e os demais casos
     * chegam ao AuthenticationManager e retornam 401 em vez de 400.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLogins")
    void shouldReturn400WhenLoginRequestIsInvalid(final String description, final String body, final String field)
            throws Exception {
        postJson(LOGIN, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").exists());
    }

    @Test
    void shouldReturn400WhenLoginBodyIsMalformedJson() throws Exception {
        postJson(LOGIN, "{\"email\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn400WhenLoginBodyIsMissing() throws Exception {
        getMockMvc().perform(post(LOGIN).contentType(JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenLoginBodyIsAJsonArray() throws Exception {
        postJson(LOGIN, "[]")
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn415WhenLoginContentTypeIsNotJson() throws Exception {
        getMockMvc().perform(post(LOGIN).contentType(MediaType.TEXT_PLAIN).content(loginJson(EMAIL, PASSWORD)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void shouldReturn405WhenCallingLoginWithAnotherMethod() throws Exception {
        getMockMvc().perform(get(LOGIN)).andExpect(status().isMethodNotAllowed());
        getMockMvc().perform(put(LOGIN).contentType(JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        getMockMvc().perform(delete(LOGIN)).andExpect(status().isMethodNotAllowed());
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private ResultActions postJson(final String path, final String json) throws Exception {
        return getMockMvc().perform(post(path).contentType(JSON).content(json));
    }

    /** Cadastra pelo service (com hash BCrypt real), sem passar pela API. */
    private void registerUser(final String name, final String email, final String password) {
        userService.register(name, email, password);
    }

    private String loginAndGetToken(final String email, final String password) throws Exception {
        final MvcResult result = postJson(LOGIN, loginJson(email, password))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(body(result), "$.token");
    }

    private String body(final MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String registerJson(final String name, final String email, final String password) {
        return "{\"name\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}".formatted(name, email, password);
    }

    private static String loginJson(final String email, final String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
    }
}
