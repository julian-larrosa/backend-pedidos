package com.sagant.pedidos.error;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.sagant.pedidos.pedido.service.PedidoNoEncontradoException;
import com.sagant.pedidos.pedido.service.SkuDuplicadoException;

/**
 * Único punto de traducción de errores a ProblemDetail (RFC 9457). Cada caso usa un
 * {@code type} estable de {@link ProblemTypes}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDACION, "Solicitud inválida",
				"Uno o más campos no son válidos");
		List<Map<String, String>> errores = ex.getBindingResult().getAllErrors().stream()
			.map(error -> Map.of(
					"campo", error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
					"mensaje", String.valueOf(error.getDefaultMessage())))
			.toList();
		problem.setProperty("errores", errores);
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemTypes.SOLICITUD_MALFORMADA,
				"Solicitud malformada", "El cuerpo de la solicitud no es JSON válido o tiene campos de tipo incorrecto");
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemTypes.SOLICITUD_MALFORMADA,
				"Solicitud malformada", "El valor '" + ex.getValue() + "' no tiene el formato esperado");
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	@ExceptionHandler(SkuDuplicadoException.class)
	ProblemDetail handleSkuDuplicado(SkuDuplicadoException ex) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ProblemTypes.SKU_DUPLICADO, "SKU duplicado",
				ex.getMessage());
		problem.setProperty("sku", ex.getSku());
		return problem;
	}

	@ExceptionHandler(PedidoNoEncontradoException.class)
	ProblemDetail handlePedidoNoEncontrado(PedidoNoEncontradoException ex) {
		return problem(HttpStatus.NOT_FOUND, ProblemTypes.PEDIDO_NO_ENCONTRADO, "Pedido no encontrado",
				ex.getMessage());
	}

	private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(type);
		problem.setTitle(title);
		return problem;
	}

}
