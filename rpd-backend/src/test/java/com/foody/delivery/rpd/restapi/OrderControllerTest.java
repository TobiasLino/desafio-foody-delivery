package com.foody.delivery.rpd.restapi;

import com.foody.delivery.rpd.domain.order.OrderStatus;
import com.foody.delivery.rpd.domain.user.Role;
import com.foody.delivery.rpd.domain.user.User;
import com.foody.delivery.rpd.domain.user.UserRepository;
import com.foody.delivery.rpd.domain.user.UserService;
import com.foody.delivery.rpd.infra.security.JwtService;
import com.foody.delivery.rpd.integration.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@IntegrationTest
public class OrderControllerTest extends AbstractControllerTest {



    private static final String ORDERS = "/orders";
    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private static final String VALID_ADDRESS = """
            {"cep": "12289020", "street": "Avenida Brasil", "neighborhood": "Centro",
             "complement": "Apto 12", "number": "100", "city": "Campinas", "uf": "SP"}
            """;

    private static final String VALID_ITEMS = """
            [{"description": "Pizza de calabresa"}, {"description": "Refrigerante 2L"}]
            """;

    private static final String VALID_ORDER = orderJson(VALID_ADDRESS, VALID_ITEMS);

    /** Transições permitidas, espelhando a tabela do README (independente da implementação do enum). */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            OrderStatus.RECEBIDO, Set.of(OrderStatus.EM_PREPARO, OrderStatus.CANCELADO),
            OrderStatus.EM_PREPARO, Set.of(OrderStatus.SAIU_PARA_ENTREGA, OrderStatus.CANCELADO),
            OrderStatus.SAIU_PARA_ENTREGA, Set.of(OrderStatus.ENTREGUE),
            OrderStatus.ENTREGUE, Set.of(),
            OrderStatus.CANCELADO, Set.of());

    /** Caminho válido, a partir de RECEBIDO, para levar um pedido até cada status. */
    private static final Map<OrderStatus, List<OrderStatus>> PATH_TO = Map.of(
            OrderStatus.RECEBIDO, List.of(),
            OrderStatus.EM_PREPARO, List.of(OrderStatus.EM_PREPARO),
            OrderStatus.SAIU_PARA_ENTREGA, List.of(OrderStatus.EM_PREPARO, OrderStatus.SAIU_PARA_ENTREGA),
            OrderStatus.ENTREGUE, List.of(OrderStatus.EM_PREPARO, OrderStatus.SAIU_PARA_ENTREGA,
                    OrderStatus.ENTREGUE),
            OrderStatus.CANCELADO, List.of(OrderStatus.CANCELADO));

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    private User clientA;
    private User clientB;
    private User operator;

    private String tokenA;
    private String tokenB;
    private String tokenOperator;

    @BeforeEach
    void createUsers() {
        clientA = userRepository.save(new User("Cliente A", "cliente.a@test.com", "hash"));
        clientB = userRepository.save(new User("Cliente B", "cliente.b@test.com", "hash"));
        operator = userRepository.save(new User("Operador", "operador.orders@test.com", "hash", Role.OPERATOR));

        tokenA = tokenFor(clientA);
        tokenB = tokenFor(clientB);
        tokenOperator = tokenFor(operator);
    }

    // =====================================================================================
    // Autenticação: todos os endpoints exigem um token válido
    // =====================================================================================

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of("GET", "/orders"),
                Arguments.of("GET", "/orders/1"),
                Arguments.of("POST", "/orders"),
                Arguments.of("PATCH", "/orders/1/status"));
    }

    @ParameterizedTest(name = "{0} {1} sem token")
    @MethodSource("endpoints")
    void shouldReturn401WhenTokenIsMissing(final String method, final String path) throws Exception {
        perform(request(HttpMethod.valueOf(method), path).contentType(JSON).content("{}"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest(name = "{0} {1} com token malformado")
    @MethodSource("endpoints")
    void shouldReturn401WhenTokenIsMalformed(final String method, final String path) throws Exception {
        perform(request(HttpMethod.valueOf(method), path).contentType(JSON).content("{}"), "isto-nao-e-um-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void shouldReturnProblemJsonWhenTokenIsMissing() throws Exception {
        perform(get(ORDERS), null)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void shouldReturn401WhenTokenIsExpired() throws Exception {
        final Instant now = Instant.now();
        final String expired = encode(jwtEncoder, clientA.getEmail(), "ROLE_CLIENT",
                now.minusSeconds(7200), now.minusSeconds(3600));

        perform(get(ORDERS), expired)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenTokenIsSignedWithAnotherKey() throws Exception {
        final String forged = forgedToken(clientA.getEmail(), "ROLE_CLIENT");

        perform(get(ORDERS), forged)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotAllowClientToForgeAnOperatorToken() throws Exception {
        final long orderId = createOrder(tokenA);
        final String forgedOperator = forgedToken(clientA.getEmail(), "ROLE_OPERATOR");

        patchStatus(forgedOperator, orderId, "EM_PREPARO")
                .andExpect(status().isUnauthorized());

        perform(get(ORDERS + "/" + orderId), tokenA)
                .andExpect(jsonPath("$.status").value("RECEBIDO"));
    }

    @Test
    void shouldReturn401WhenAuthorizationSchemeIsNotBearer() throws Exception {
        getMockMvc().perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenBearerTokenIsEmpty() throws Exception {
        getMockMvc().perform(get(ORDERS).header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================================================
    // POST /orders
    // =====================================================================================

    @Test
    void shouldCreateOrderAsClient() throws Exception {
        final MvcResult result = perform(post(ORDERS).contentType(JSON).content(VALID_ORDER), tokenA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.client.id").value(clientA.getId()))
                .andExpect(jsonPath("$.client.email").value("cliente.a@test.com"))
                .andExpect(jsonPath("$.client.role").value("CLIENT"))
                .andExpect(jsonPath("$.address.cep").value("12289020"))
                .andExpect(jsonPath("$.address.street").value("Avenida Brasil"))
                .andExpect(jsonPath("$.address.neighborhood").value("Centro"))
                .andExpect(jsonPath("$.address.complement").value("Apto 12"))
                .andExpect(jsonPath("$.address.number").value("100"))
                .andExpect(jsonPath("$.address.city").value("Campinas"))
                .andExpect(jsonPath("$.address.uf").value("SP"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        final List<String> descriptions = JsonPath.read(body(result), "$.items[*].description");
        assertTrue(descriptions.containsAll(List.of("Pizza de calabresa", "Refrigerante 2L")));
    }

    @Test
    void shouldReturnLocationHeaderPointingToTheCreatedOrder() throws Exception {
        final MvcResult result = perform(post(ORDERS).contentType(JSON).content(VALID_ORDER), tokenA)
                .andExpect(status().isCreated())
                .andReturn();

        final String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertTrue(location != null && location.endsWith("/orders/" + idOf(result)),
                "Location inesperado: " + location);
    }

    @Test
    void shouldCreateOrderAsOperatorOwnedByTheOperator() throws Exception {
        perform(post(ORDERS).contentType(JSON).content(VALID_ORDER), tokenOperator)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.email").value("operador.orders@test.com"))
                .andExpect(jsonPath("$.client.role").value("OPERATOR"));
    }

    @Test
    void shouldCreateOrderWithoutComplement() throws Exception {
        final String address = """
                {"cep": "12289020", "street": "Rua A", "neighborhood": "Centro",
                 "number": "10", "city": "Campinas", "uf": "SP"}
                """;

        perform(post(ORDERS).contentType(JSON).content(orderJson(address, VALID_ITEMS)), tokenA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address.complement").doesNotExist());
    }

    @Test
    void shouldCreateOrderWithRepeatedItems() throws Exception {
        final String items = """
                [{"description": "Pizza de calabresa"}, {"description": "Pizza de calabresa"}]
                """;

        perform(post(ORDERS).contentType(JSON).content(orderJson(VALID_ADDRESS, items)), tokenA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void shouldCreateOrderWithSingleItem() throws Exception {
        final String items = "[{\"description\": \"Acai 500ml\"}]";

        perform(post(ORDERS).contentType(JSON).content(orderJson(VALID_ADDRESS, items)), tokenA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void shouldAcceptLowercaseUf() throws Exception {
        final String address = address("12289020", "Rua A", "Centro", "10", "Campinas", "sp");

        perform(post(ORDERS).contentType(JSON).content(orderJson(address, VALID_ITEMS)), tokenA)
                .andExpect(status().isCreated());
    }

    @Test
    void shouldAcceptFieldsAtTheirMaximumLength() throws Exception {
        final String text250 = "x".repeat(250);
        final String address = address("12289020", text250, text250, "1234567890", text250, "SP");
        final String items = "[{\"description\": \"" + text250 + "\"}]";

        perform(post(ORDERS).contentType(JSON).content(orderJson(address, items)), tokenA)
                .andExpect(status().isCreated());
    }

    @Test
    void shouldIgnoreClientAndStatusSentInTheBody() throws Exception {
        final String body = """
                {"status": "ENTREGUE", "client": {"id": %d}, "address": %s, "items": %s}
                """.formatted(clientB.getId(), VALID_ADDRESS, VALID_ITEMS);

        perform(post(ORDERS).contentType(JSON).content(body), tokenA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.client.email").value("cliente.a@test.com"));
    }

    @Test
    void shouldNotExposePasswordInTheResponse() throws Exception {
        perform(post(ORDERS).contentType(JSON).content(VALID_ORDER), tokenA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.password").doesNotExist());
    }

    @Test
    void shouldCreateAndFetchOrderUsingATokenFromTheRealLogin() throws Exception {
        userService.register("Maria Login", "maria.login@test.com", "senha1234");

        final MvcResult login = getMockMvc().perform(post("/auth/login")
                        .contentType(JSON)
                        .content("{\"email\": \"maria.login@test.com\", \"password\": \"senha1234\"}"))
                .andExpect(status().isOk())
                .andReturn();
        final String token = JsonPath.read(body(login), "$.token");

        final long id = idOf(perform(post(ORDERS).contentType(JSON).content(VALID_ORDER), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.client.email").value("maria.login@test.com"))
                .andReturn());

        perform(get(ORDERS + "/" + id), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    static Stream<Arguments> invalidOrders() {
        final String longText = "x".repeat(251);
        return Stream.of(
                Arguments.of("sem endereço",
                        "{\"items\": " + VALID_ITEMS + "}", "address"),
                Arguments.of("endereço nulo",
                        "{\"address\": null, \"items\": " + VALID_ITEMS + "}", "address"),
                Arguments.of("sem itens",
                        "{\"address\": " + VALID_ADDRESS + "}", "items"),
                Arguments.of("itens nulos",
                        "{\"address\": " + VALID_ADDRESS + ", \"items\": null}", "items"),
                Arguments.of("lista de itens vazia",
                        orderJson(VALID_ADDRESS, "[]"), "items"),
                Arguments.of("item sem descrição",
                        orderJson(VALID_ADDRESS, "[{}]"), "items[0].description"),
                Arguments.of("item com descrição vazia",
                        orderJson(VALID_ADDRESS, "[{\"description\": \"\"}]"), "items[0].description"),
                Arguments.of("item com descrição só com espaços",
                        orderJson(VALID_ADDRESS, "[{\"description\": \"   \"}]"), "items[0].description"),
                Arguments.of("item com descrição acima de 250 caracteres",
                        orderJson(VALID_ADDRESS, "[{\"description\": \"" + longText + "\"}]"),
                        "items[0].description"),
                Arguments.of("segundo item inválido",
                        orderJson(VALID_ADDRESS, "[{\"description\": \"Ok\"}, {\"description\": \"\"}]"),
                        "items[1].description"),
                Arguments.of("cep com 7 dígitos",
                        orderJson(address("1228902", "Rua A", "Centro", "10", "Campinas", "SP"), VALID_ITEMS),
                        "address.cep"),
                Arguments.of("cep com 9 dígitos",
                        orderJson(address("122890201", "Rua A", "Centro", "10", "Campinas", "SP"), VALID_ITEMS),
                        "address.cep"),
                Arguments.of("cep com letras",
                        orderJson(address("1228902a", "Rua A", "Centro", "10", "Campinas", "SP"), VALID_ITEMS),
                        "address.cep"),
                Arguments.of("cep com hífen",
                        orderJson(address("12289-020", "Rua A", "Centro", "10", "Campinas", "SP"), VALID_ITEMS),
                        "address.cep"),
                Arguments.of("cep em branco",
                        orderJson(address("", "Rua A", "Centro", "10", "Campinas", "SP"), VALID_ITEMS),
                        "address.cep"),
                Arguments.of("rua em branco",
                        orderJson(address("12289020", "", "Centro", "10", "Campinas", "SP"), VALID_ITEMS),
                        "address.street"),
                Arguments.of("rua acima de 250 caracteres",
                        orderJson(address("12289020", longText, "Centro", "10", "Campinas", "SP"), VALID_ITEMS),
                        "address.street"),
                Arguments.of("bairro em branco",
                        orderJson(address("12289020", "Rua A", "", "10", "Campinas", "SP"), VALID_ITEMS),
                        "address.neighborhood"),
                Arguments.of("bairro acima de 250 caracteres",
                        orderJson(address("12289020", "Rua A", longText, "10", "Campinas", "SP"), VALID_ITEMS),
                        "address.neighborhood"),
                Arguments.of("número em branco",
                        orderJson(address("12289020", "Rua A", "Centro", "", "Campinas", "SP"), VALID_ITEMS),
                        "address.number"),
                Arguments.of("número acima de 10 caracteres",
                        orderJson(address("12289020", "Rua A", "Centro", "12345678901", "Campinas", "SP"),
                                VALID_ITEMS),
                        "address.number"),
                Arguments.of("cidade em branco",
                        orderJson(address("12289020", "Rua A", "Centro", "10", "", "SP"), VALID_ITEMS),
                        "address.city"),
                Arguments.of("cidade acima de 250 caracteres",
                        orderJson(address("12289020", "Rua A", "Centro", "10", longText, "SP"), VALID_ITEMS),
                        "address.city"),
                Arguments.of("uf em branco",
                        orderJson(address("12289020", "Rua A", "Centro", "10", "Campinas", ""), VALID_ITEMS),
                        "address.uf"),
                Arguments.of("uf com 1 letra",
                        orderJson(address("12289020", "Rua A", "Centro", "10", "Campinas", "S"), VALID_ITEMS),
                        "address.uf"),
                Arguments.of("uf com 3 letras",
                        orderJson(address("12289020", "Rua A", "Centro", "10", "Campinas", "SPP"), VALID_ITEMS),
                        "address.uf"),
                Arguments.of("uf com número",
                        orderJson(address("12289020", "Rua A", "Centro", "10", "Campinas", "S1"), VALID_ITEMS),
                        "address.uf"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidOrders")
    void shouldReturn400WhenOrderIsInvalid(final String description, final String body, final String field)
            throws Exception {
        perform(post(ORDERS).contentType(JSON).content(body), tokenA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").exists());
    }

    @Test
    void shouldReportEveryInvalidFieldAtOnce() throws Exception {
        final String body = orderJson(address("1", "", "", "", "", "XYZ"), "[]");

        final MvcResult result = perform(post(ORDERS).contentType(JSON).content(body), tokenA)
                .andExpect(status().isBadRequest())
                .andReturn();

        final List<String> fields = JsonPath.read(body(result), "$.errors[*].field");
        assertTrue(fields.containsAll(List.of("address.cep", "address.street", "address.neighborhood",
                "address.number", "address.city", "address.uf", "items")), "campos reportados: " + fields);
    }

    @Test
    void shouldNotCreateOrderWhenValidationFails() throws Exception {
        perform(post(ORDERS).contentType(JSON).content(orderJson(VALID_ADDRESS, "[]")), tokenA)
                .andExpect(status().isBadRequest());

        perform(get(ORDERS), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void shouldReturn400WhenBodyIsMalformedJson() throws Exception {
        perform(post(ORDERS).contentType(JSON).content("{\"address\": "), tokenA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn400WhenBodyIsMissing() throws Exception {
        perform(post(ORDERS).contentType(JSON), tokenA)
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenBodyIsAJsonArray() throws Exception {
        perform(post(ORDERS).contentType(JSON).content("[]"), tokenA)
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn415WhenContentTypeIsNotJson() throws Exception {
        perform(post(ORDERS).contentType(MediaType.TEXT_PLAIN).content(VALID_ORDER), tokenA)
                .andExpect(status().isUnsupportedMediaType());
    }

    // =====================================================================================
    // GET /orders/{id}
    // =====================================================================================

    @Test
    void shouldReturnOrderToItsOwner() throws Exception {
        final long id = createOrder(tokenA);

        final MvcResult result = perform(get(ORDERS + "/" + id), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.client.email").value("cliente.a@test.com"))
                .andExpect(jsonPath("$.client.password").doesNotExist())
                .andExpect(jsonPath("$.address.cep").value("12289020"))
                .andExpect(jsonPath("$.address.uf").value("SP"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        final List<String> descriptions = JsonPath.read(body(result), "$.items[*].description");
        assertTrue(descriptions.containsAll(List.of("Pizza de calabresa", "Refrigerante 2L")));
    }

    @Test
    void shouldReturnAnyOrderToTheOperator() throws Exception {
        final long id = createOrder(tokenA);

        perform(get(ORDERS + "/" + id), tokenOperator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.client.email").value("cliente.a@test.com"));
    }

    @Test
    void shouldReturn404WhenClientAsksForAnotherClientsOrder() throws Exception {
        final long id = createOrder(tokenA);

        perform(get(ORDERS + "/" + id), tokenB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturnTheSameResponseForAnotherClientsOrderAndForAnUnknownOne() throws Exception {
        final long id = createOrder(tokenA);

        final MvcResult foreign = perform(get(ORDERS + "/" + id), tokenB).andReturn();
        final MvcResult unknown = perform(get(ORDERS + "/" + (id + 1000)), tokenB).andReturn();

        // Não deve ser possível descobrir, pelo status, quais IDs existem.
        assertEquals(unknown.getResponse().getStatus(), foreign.getResponse().getStatus());
    }

    @Test
    void shouldReturn404WhenOrderDoesNotExist() throws Exception {
        perform(get(ORDERS + "/999999"), tokenA)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        perform(get(ORDERS + "/999999"), tokenOperator)
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "id = {0}")
    @ValueSource(strings = {"abc", "1.5", "12abc"})
    void shouldReturn400WhenIdIsNotANumber(final String id) throws Exception {
        perform(get(ORDERS + "/" + id), tokenA)
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404ForUnknownSubRoute() throws Exception {
        perform(get(ORDERS + "/1/unknown"), tokenA)
                .andExpect(status().isNotFound());
    }

    // =====================================================================================
    // GET /orders
    // =====================================================================================

    @Test
    void shouldReturnEmptyPageWhenClientHasNoOrders() throws Exception {
        createOrder(tokenA);

        perform(get(ORDERS), tokenB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void shouldListOnlyTheClientsOwnOrders() throws Exception {
        createOrder(tokenA);
        createOrder(tokenA);
        createOrder(tokenB);

        final MvcResult result = perform(get(ORDERS), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andReturn();

        final List<String> emails = JsonPath.read(body(result), "$.content[*].client.email");
        assertEquals(List.of("cliente.a@test.com", "cliente.a@test.com"), emails);
    }

    @Test
    void shouldListAllOrdersToTheOperator() throws Exception {
        createOrder(tokenA);
        createOrder(tokenA);
        createOrder(tokenB);

        final MvcResult result = perform(get(ORDERS).param("size", "100"), tokenOperator)
                .andExpect(status().isOk())
                .andReturn();

        final List<String> emails = JsonPath.read(body(result), "$.content[*].client.email");
        assertTrue(emails.stream().filter("cliente.a@test.com"::equals).count() >= 2);
        assertTrue(emails.contains("cliente.b@test.com"));
    }

    @Test
    void shouldFilterByClientIdWhenOperator() throws Exception {
        createOrder(tokenA);
        createOrder(tokenA);
        createOrder(tokenB);

        final MvcResult result = perform(get(ORDERS).param("clientId", clientA.getId().toString()), tokenOperator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andReturn();

        final List<String> emails = JsonPath.read(body(result), "$.content[*].client.email");
        assertTrue(emails.stream().allMatch("cliente.a@test.com"::equals));
    }

    @Test
    void shouldReturnEmptyPageWhenOperatorFiltersByClientWithoutOrders() throws Exception {
        createOrder(tokenA);

        perform(get(ORDERS).param("clientId", clientB.getId().toString()), tokenOperator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void shouldReturnEmptyPageWhenOperatorFiltersByUnknownClient() throws Exception {
        perform(get(ORDERS).param("clientId", "999999"), tokenOperator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void shouldAllowClientToFilterByItsOwnId() throws Exception {
        createOrder(tokenA);
        createOrder(tokenB);

        perform(get(ORDERS).param("clientId", clientA.getId().toString()), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].client.email").value("cliente.a@test.com"));
    }

    @Test
    void shouldReturn403WhenClientFiltersByAnotherClientId() throws Exception {
        createOrder(tokenB);

        perform(get(ORDERS).param("clientId", clientB.getId().toString()), tokenA)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void shouldReturn400WhenClientIdIsNotANumber() throws Exception {
        perform(get(ORDERS).param("clientId", "abc"), tokenOperator)
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldPaginateResults() throws Exception {
        createOrder(tokenA);
        createOrder(tokenA);
        createOrder(tokenA);

        perform(get(ORDERS).param("page", "0").param("size", "2"), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        perform(get(ORDERS).param("page", "1").param("size", "2"), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.number").value(1));
    }

    @Test
    void shouldReturnEmptyContentWhenPageIsBeyondTheLastOne() throws Exception {
        createOrder(tokenA);

        perform(get(ORDERS).param("page", "5").param("size", "10"), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void shouldUseDefaultPageSizeOf20() throws Exception {
        createOrder(tokenA);

        perform(get(ORDERS), tokenA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    void shouldSortByIdAscendingByDefault() throws Exception {
        final long first = createOrder(tokenA);
        final long second = createOrder(tokenA);

        final MvcResult result = perform(get(ORDERS), tokenA).andExpect(status().isOk()).andReturn();

        final List<Number> ids = JsonPath.read(body(result), "$.content[*].id");
        assertEquals(List.of(first, second), ids.stream().map(Number::longValue).toList());
    }

    @Test
    void shouldSortByIdDescending() throws Exception {
        final long first = createOrder(tokenA);
        final long second = createOrder(tokenA);

        final MvcResult result = perform(get(ORDERS).param("sort", "id,desc"), tokenA)
                .andExpect(status().isOk())
                .andReturn();

        final List<Number> ids = JsonPath.read(body(result), "$.content[*].id");
        assertEquals(List.of(second, first), ids.stream().map(Number::longValue).toList());
    }

    @Test
    void shouldSortByStatus() throws Exception {
        final long received = createOrder(tokenA);
        final long cancelled = createOrder(tokenA);
        patchStatus(tokenOperator, cancelled, "CANCELADO").andExpect(status().isOk());

        final MvcResult result = perform(get(ORDERS).param("sort", "status,asc"), tokenA)
                .andExpect(status().isOk())
                .andReturn();

        // CANCELADO vem antes de RECEBIDO na ordem alfabética
        final List<Number> ids = JsonPath.read(body(result), "$.content[*].id");
        assertEquals(List.of(cancelled, received), ids.stream().map(Number::longValue).toList());
    }

    @Test
    void shouldAcceptSortByMoreThanOneAllowedField() throws Exception {
        createOrder(tokenA);

        perform(get(ORDERS).param("sort", "status,desc").param("sort", "id,asc"), tokenA)
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "sort = {0}")
    @ValueSource(strings = {"client.password", "client.email", "foo", "address.cep", "items"})
    void shouldReturn400WhenSortFieldIsNotAllowed(final String sort) throws Exception {
        perform(get(ORDERS).param("sort", sort), tokenA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn400WhenSortFieldIsNotAllowedForTheOperatorEither() throws Exception {
        perform(get(ORDERS).param("sort", "client.password,desc"), tokenOperator)
                .andExpect(status().isBadRequest());
    }

    // =====================================================================================
    // PATCH /orders/{id}/status: operador
    // =====================================================================================

    @Test
    void shouldWalkTheWholeHappyPathAsOperator() throws Exception {
        final long id = createOrder(tokenA);

        patchStatus(tokenOperator, id, "EM_PREPARO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("EM_PREPARO"));

        patchStatus(tokenOperator, id, "SAIU_PARA_ENTREGA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SAIU_PARA_ENTREGA"));

        patchStatus(tokenOperator, id, "ENTREGUE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENTREGUE"));

        perform(get(ORDERS + "/" + id), tokenA)
                .andExpect(jsonPath("$.status").value("ENTREGUE"));
    }

    @Test
    void shouldKeepOrderDataWhenStatusChanges() throws Exception {
        final long id = createOrder(tokenA);

        patchStatus(tokenOperator, id, "EM_PREPARO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.email").value("cliente.a@test.com"))
                .andExpect(jsonPath("$.address.cep").value("12289020"))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @ParameterizedTest(name = "operador: {0} -> {1} (permitida: {2})")
    @MethodSource("allTransitions")
    void shouldApplyTheStateMachineWhenOperatorUpdatesStatus(final OrderStatus from, final OrderStatus to,
                                                             final boolean allowed) throws Exception {
        final long id = createOrder(tokenA);
        moveTo(id, from);

        patchStatus(tokenOperator, id, to.name())
                .andExpect(status().is(allowed ? 200 : 409));

        final String expectedStatus = allowed ? to.name() : from.name();
        perform(get(ORDERS + "/" + id), tokenOperator)
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    static Stream<Arguments> allTransitions() {
        return Stream.of(OrderStatus.values()).flatMap(from ->
                Stream.of(OrderStatus.values()).map(to ->
                        Arguments.of(from, to, ALLOWED.get(from).contains(to))));
    }

    @Test
    void shouldReturn404WhenUpdatingAnOrderThatDoesNotExist() throws Exception {
        patchStatus(tokenOperator, 999999L, "EM_PREPARO")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =====================================================================================
    // PATCH /orders/{id}/status: cliente
    // =====================================================================================

    @Test
    void shouldAllowClientToCancelItsOwnReceivedOrder() throws Exception {
        final long id = createOrder(tokenA);

        patchStatus(tokenA, id, "CANCELADO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        perform(get(ORDERS + "/" + id), tokenOperator)
                .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    @Test
    void shouldNotAllowClientToCancelAfterPreparationStarted() throws Exception {
        final long id = createOrder(tokenA);
        moveTo(id, OrderStatus.EM_PREPARO);

        patchStatus(tokenA, id, "CANCELADO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        perform(get(ORDERS + "/" + id), tokenA)
                .andExpect(jsonPath("$.status").value("EM_PREPARO"));
    }

    @Test
    void shouldNotAllowClientToAdvanceItsOwnOrder() throws Exception {
        final long id = createOrder(tokenA);

        patchStatus(tokenA, id, "EM_PREPARO")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        perform(get(ORDERS + "/" + id), tokenA)
                .andExpect(jsonPath("$.status").value("RECEBIDO"));
    }

    @ParameterizedTest(name = "cliente: {0} -> {1} => HTTP {2}")
    @MethodSource("clientTransitions")
    void shouldApplyClientRulesWhenClientUpdatesItsOwnOrder(final OrderStatus from, final OrderStatus to,
                                                            final int expectedHttpStatus) throws Exception {
        final long id = createOrder(tokenA);
        moveTo(id, from);

        patchStatus(tokenA, id, to.name())
                .andExpect(status().is(expectedHttpStatus));
    }

    static Stream<Arguments> clientTransitions() {
        return Stream.of(OrderStatus.values()).flatMap(from ->
                Stream.of(OrderStatus.values()).map(to -> {
                    final int expected;
                    if (to != OrderStatus.CANCELADO) {
                        expected = 403;
                    } else if (from == OrderStatus.RECEBIDO) {
                        expected = 200;
                    } else {
                        expected = 409;
                    }
                    return Arguments.of(from, to, expected);
                }));
    }

    @ParameterizedTest(name = "pedido de outro cliente, status {0}")
    @EnumSource(OrderStatus.class)
    void shouldReturn404WhenClientUpdatesAnotherClientsOrder(final OrderStatus requested) throws Exception {
        final long id = createOrder(tokenA);

        patchStatus(tokenB, id, requested.name())
                .andExpect(status().isNotFound());

        perform(get(ORDERS + "/" + id), tokenA)
                .andExpect(jsonPath("$.status").value("RECEBIDO"));
    }

    @Test
    void shouldNotAllowClientToCancelAnotherClientsOrder() throws Exception {
        final long id = createOrder(tokenA);

        patchStatus(tokenB, id, "CANCELADO")
                .andExpect(status().isNotFound());

        perform(get(ORDERS + "/" + id), tokenOperator)
                .andExpect(jsonPath("$.status").value("RECEBIDO"));
    }

    // =====================================================================================
    // PATCH /orders/{id}/status: validação do corpo e da rota
    // =====================================================================================

    static Stream<Arguments> invalidStatusBodies() {
        return Stream.of(
                Arguments.of("corpo vazio {}", "{}"),
                Arguments.of("status nulo", "{\"status\": null}"),
                Arguments.of("status inexistente", "{\"status\": \"VOANDO\"}"),
                Arguments.of("status em minúsculas", "{\"status\": \"em_preparo\"}"),
                Arguments.of("status em branco", "{\"status\": \"\"}"),
                Arguments.of("json malformado", "{\"status\": "),
                Arguments.of("array no lugar do objeto", "[]"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidStatusBodies")
    void shouldReturn400WhenStatusBodyIsInvalid(final String description, final String body) throws Exception {
        final long id = createOrder(tokenA);

        perform(patch(ORDERS + "/" + id + "/status").contentType(JSON).content(body), tokenOperator)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        perform(get(ORDERS + "/" + id), tokenA)
                .andExpect(jsonPath("$.status").value("RECEBIDO"));
    }

    @Test
    void shouldReportTheStatusFieldWhenItIsMissing() throws Exception {
        final long id = createOrder(tokenA);

        perform(patch(ORDERS + "/" + id + "/status").contentType(JSON).content("{}"), tokenOperator)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'status')]").exists());
    }

    @Test
    void shouldReturn400WhenStatusBodyIsMissing() throws Exception {
        final long id = createOrder(tokenA);

        perform(patch(ORDERS + "/" + id + "/status").contentType(JSON), tokenOperator)
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn415WhenStatusContentTypeIsNotJson() throws Exception {
        final long id = createOrder(tokenA);

        perform(patch(ORDERS + "/" + id + "/status")
                .contentType(MediaType.TEXT_PLAIN)
                .content("{\"status\": \"EM_PREPARO\"}"), tokenOperator)
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void shouldReturn400WhenStatusUpdateIdIsNotANumber() throws Exception {
        perform(patch(ORDERS + "/abc/status").contentType(JSON).content("{\"status\": \"EM_PREPARO\"}"),
                tokenOperator)
                .andExpect(status().isBadRequest());
    }

    // =====================================================================================
    // Métodos HTTP não suportados
    // =====================================================================================

    @Test
    void shouldReturn405WhenDeletingTheCollection() throws Exception {
        perform(delete(ORDERS), tokenOperator)
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void shouldReturn405WhenDeletingAnOrder() throws Exception {
        final long id = createOrder(tokenA);

        perform(delete(ORDERS + "/" + id), tokenOperator)
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void shouldReturn405WhenPostingToAnOrder() throws Exception {
        final long id = createOrder(tokenA);

        perform(post(ORDERS + "/" + id).contentType(JSON).content("{}"), tokenA)
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void shouldReturn405WhenReplacingTheStatusWithPut() throws Exception {
        final long id = createOrder(tokenA);

        perform(put(ORDERS + "/" + id + "/status").contentType(JSON).content("{\"status\": \"EM_PREPARO\"}"),
                tokenOperator)
                .andExpect(status().isMethodNotAllowed());
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private ResultActions perform(final MockHttpServletRequestBuilder request, final String token)
            throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return getMockMvc().perform(request);
    }

    private ResultActions patchStatus(final String token, final long orderId, final String status)
            throws Exception {
        return perform(patch(ORDERS + "/" + orderId + "/status")
                .contentType(JSON)
                .content("{\"status\": \"" + status + "\"}"), token);
    }

    private long createOrder(final String token) throws Exception {
        return idOf(perform(post(ORDERS).contentType(JSON).content(VALID_ORDER), token)
                .andExpect(status().isCreated())
                .andReturn());
    }

    /** Leva o pedido, usando o operador, até o status desejado seguindo apenas transições válidas. */
    private void moveTo(final long orderId, final OrderStatus target) throws Exception {
        for (final OrderStatus step : PATH_TO.get(target)) {
            patchStatus(tokenOperator, orderId, step.name()).andExpect(status().isOk());
        }
    }

    private long idOf(final MvcResult result) throws Exception {
        final Number id = JsonPath.read(body(result), "$.id");
        return id.longValue();
    }

    private String body(final MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String tokenFor(final User user) {
        final var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return jwtService.generate(new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities));
    }

    private String forgedToken(final String subject, final String scope) {
        final byte[] anotherKey = "outra-chave-secreta-com-mais-de-32-bytes".getBytes(StandardCharsets.UTF_8);
        final JwtEncoder anotherEncoder =
                new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(anotherKey, "HmacSHA256")));
        final Instant now = Instant.now();
        return encode(anotherEncoder, subject, scope, now, now.plusSeconds(3600));
    }

    private String encode(final JwtEncoder encoder, final String subject, final String scope,
                          final Instant issuedAt, final Instant expiresAt) {
        final JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("rpd-backend")
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("scope", scope)
                .build();
        final JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String orderJson(final String address, final String items) {
        return "{\"address\": %s, \"items\": %s}".formatted(address, items);
    }

    private static String address(final String cep, final String street, final String neighborhood,
                                  final String number, final String city, final String uf) {
        return """
                {"cep": "%s", "street": "%s", "neighborhood": "%s", "number": "%s", "city": "%s", "uf": "%s"}
                """.formatted(cep, street, neighborhood, number, city, uf);
    }

}
