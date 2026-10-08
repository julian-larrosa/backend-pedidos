package com.sagant.pedidos.pedido.service;

import java.util.UUID;

public class PedidoNoEncontradoException extends RuntimeException {

	private final UUID id;

	public PedidoNoEncontradoException(UUID id) {
		super("No existe un pedido con id " + id);
		this.id = id;
	}

	public UUID getId() {
		return id;
	}

}
