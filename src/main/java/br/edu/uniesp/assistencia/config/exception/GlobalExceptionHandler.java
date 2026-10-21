package br.edu.uniesp.assistencia.config.exception;

import br.edu.uniesp.assistencia.shared.exception.RecursoNaoEncontradoException;
import br.edu.uniesp.assistencia.shared.exception.RegraNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail tratarJsonInvalido(HttpMessageNotReadableException exception, HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "O corpo da requisição deve conter um JSON válido com os tipos de campos esperados.");
        problema.setTitle("Dados de requisição inválidos");
        problema.setInstance(URI.create(request.getRequestURI()));
        return problema;
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail tratarRecursoNaoEncontrado(RecursoNaoEncontradoException exception,
            HttpServletRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());

        problem.setTitle("Recurso não encontrado");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("recurso", exception.getRecurso());
        problem.setProperty("identificador", exception.getIdentificador());

        return problem;
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ProblemDetail tratarRegraNegocio(RegraNegocioException exception, HttpServletRequest request) {

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());

        problema.setTitle("Regra de negócio violada");
        problema.setInstance(URI.create(request.getRequestURI()));
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail tratarValidacao(org.springframework.web.bind.MethodArgumentNotValidException exception, HttpServletRequest request) {

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Erro na validação dos campos informados");

        problema.setTitle("Dados de requisição inválidos");
        problema.setInstance(URI.create(request.getRequestURI()));

        java.util.Map<String, String> erros = new java.util.HashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                erros.put(error.getField(), error.getDefaultMessage()));

        problema.setProperty("erros", erros);
        return problema;
    }

}
