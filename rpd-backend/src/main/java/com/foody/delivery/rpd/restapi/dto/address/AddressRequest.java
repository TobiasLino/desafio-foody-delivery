package com.foody.delivery.rpd.restapi.dto.address;

import com.foody.delivery.rpd.domain.address.Address;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Endereço de entrega")
public record AddressRequest(
        @Schema(description = "CEP, somente dígitos", example = "12289020")
        @Pattern(regexp = "\\d{8}", message = "CEP deve conter 8 dígitos")
        @NotBlank String cep,

        @Schema(description = "Logradouro", example = "Avenida Brasil")
        @NotBlank @Size(max = 250) String street,

        @Schema(description = "Bairro", example = "Centro")
        @NotBlank @Size(max = 250) String neighborhood,

        @Schema(description = "Complemento (opcional)", example = "Apto 12")
        @Size(max = 250) String complement,

        @Schema(description = "Número", example = "100")
        @NotBlank @Size(max = 10) String number,

        @Schema(description = "Cidade", example = "Caçapava")
        @NotBlank @Size(max = 250) String city,

        @Schema(description = "Sigla do estado (2 letras)", example = "SP")
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "UF deve ter 2 letras")
        String uf
) {

    public Address toAddress() {
        return Address.builder()
                .cep(cep)
                .street(street)
                .neighborhood(neighborhood)
                .complement(complement)
                .number(number)
                .city(city)
                .uf(uf)
                .build();
    }
}
