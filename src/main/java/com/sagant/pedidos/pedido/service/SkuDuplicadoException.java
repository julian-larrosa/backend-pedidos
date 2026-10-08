package com.sagant.pedidos.pedido.service;

public class SkuDuplicadoException extends RuntimeException {

	private final String sku;

	public SkuDuplicadoException(String sku) {
		super("El SKU " + sku + " aparece en más de una línea de pedido");
		this.sku = sku;
	}

	public String getSku() {
		return sku;
	}

}
