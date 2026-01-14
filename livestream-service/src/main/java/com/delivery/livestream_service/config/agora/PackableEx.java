package com.delivery.livestream_service.config.agora;

public interface PackableEx extends Packable {
    void unmarshal(ByteBuf in);
}
