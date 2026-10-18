package com.foody.delivery.rpd.restapi.dto.address;

import com.foody.delivery.rpd.domain.address.Address;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Endereço de entrega")
public record AddressResponse(
        @Schema(example = "10") Long id,
        @Schema(example = "12289020") String cep,
        @Schema(example = "Avenida Brasil") String street,
        @Schema(example = "Centro") String neighborhood,
        @Schema(example = "Apto 12", nullable = true) String complement,
        @Schema(example = "100") String number,
        @Schema(example = "Caçapava") String city,
        @Schema(example = "SP") String uf
) {

    public static AddressResponse from(final Address address) {
        return new AddressResponse(
                address.getId(),
                address.getCep(),
                address.getStreet(),
                address.getNeighborhood(),
                address.getComplement(),
                address.getNumber(),
                address.getCity(),
                address.getUf()
        );
    }
}
