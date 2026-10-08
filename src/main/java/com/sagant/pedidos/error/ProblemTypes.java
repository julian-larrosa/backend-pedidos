package com.sagant.pedidos.error;

import java.net.URI;

/**
 * Valores estables del campo {@code type} de los ProblemDetail que devuelve la API.
 * Forman parte del contrato público: no cambiarlos sin versionar la API.
 */
public final class ProblemTypes {

	public static final URI VALIDACION = URI.create("urn:sagant:pedidos:validacion");
	public static final URI SOLICITUD_MALFORMADA = URI.create("urn:sagant:pedidos:solicitud-malformada");
	public static final URI SKU_DUPLICADO = URI.create("urn:sagant:pedidos:sku-duplicado");
	public static final URI PEDIDO_NO_ENCONTRADO = URI.create("urn:sagant:pedidos:pedido-no-encontrado");

	private ProblemTypes() {
	}

}
