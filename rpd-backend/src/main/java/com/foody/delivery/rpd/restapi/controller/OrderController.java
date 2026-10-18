package com.foody.delivery.rpd.restapi.controller;

import com.foody.delivery.rpd.domain.order.Order;
import com.foody.delivery.rpd.domain.order.OrderService;
import com.foody.delivery.rpd.infra.exception.InvalidRequestException;
import com.foody.delivery.rpd.infra.security.AuthenticatedUser;
import com.foody.delivery.rpd.restapi.dto.order.OrderRequest;
import com.foody.delivery.rpd.restapi.dto.order.OrderResponse;
import com.foody.delivery.rpd.restapi.dto.order.UpdateStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.util.Set;

@Tag(name = "Pedidos", description = "Criação, consulta e atualização de status de pedidos")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String PROBLEM = "application/problem+json";

    private final OrderService orderService;

    private static final Set<String> SORTABLE = Set.of("id", "status");

    @Operation(
            summary = "Lista pedidos (paginado)",
            description = "Cliente: retorna apenas os próprios pedidos. Operador: retorna todos, com filtro "
                    + "opcional por `clientId`. Ordenação aceita apenas `id` e `status` (ex.: `sort=status,asc`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de pedidos"),
            @ApiResponse(responseCode = "400", description = "Campo de ordenação inválido",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Cliente tentando consultar pedidos de outro cliente",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PagedModel<OrderResponse>> findAllOrders(
            @Parameter(description = "Filtra pelo ID do cliente (clientes só podem usar o próprio ID)", example = "11")
            @RequestParam(required = false) Long clientId,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable,
            @Parameter(hidden = true) Authentication authentication
    ) throws AccessDeniedException {
        validateSort(pageable);
        return ResponseEntity.ok(new PagedModel<>(
                orderService.findAll(clientId, pageable, AuthenticatedUser.from(authentication))
                        .map(OrderResponse::from)));
    }

    @Operation(
            summary = "Busca um pedido por ID",
            description = "Cliente só acessa os próprios pedidos; para pedidos de terceiros a resposta é 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado (ou pertencente a outro cliente)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse findById(
            @Parameter(description = "ID do pedido", example = "10") @PathVariable("id") final Long id,
            @Parameter(hidden = true) Authentication authentication) {
        return OrderResponse.from(orderService.findById(id, AuthenticatedUser.from(authentication)));
    }

    @Operation(
            summary = "Atualiza o status de um pedido",
            description = "Operador: qualquer transição válida. Cliente: somente cancelar o próprio pedido, "
                    + "e apenas enquanto estiver `RECEBIDO`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atualizado"),
            @ApiResponse(responseCode = "400", description = "Status ausente ou inválido",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Cliente tentando um status diferente de CANCELADO",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado (ou pertencente a outro cliente)",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Transição de status inválida",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping(value = "/{id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderResponse updateOrderStatus(
            @Parameter(description = "ID do pedido", example = "10") @PathVariable("id") final Long id,
            @Valid @RequestBody final UpdateStatusRequest request,
            @Parameter(hidden = true) Authentication authentication) throws AccessDeniedException {
        return OrderResponse.from(orderService.updateStatus(id, request.status(), AuthenticatedUser.from(authentication)));
    }

    @Operation(
            summary = "Cria um pedido",
            description = "O cliente do pedido é o usuário autenticado e o status inicial é sempre `RECEBIDO`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido criado",
                    headers = @Header(name = "Location", description = "URL do pedido criado"),
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos inválidos",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado",
                    content = @Content(mediaType = PROBLEM, schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponse> createNewOrder(
            @Valid @RequestBody final OrderRequest orderRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        final Order order = orderService.createNewOrder(orderRequest, jwt.getSubject());

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.getId())
                .toUri();

        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    private void validateSort(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidRequestException("Campo de ordenação inválido: " + order.getProperty());
            }
        });
    }
}
