package com.marcaai.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Autentica um usuário e retorna um token JWT com o papel embutido",
            description = "Rota pública. O token vai no header `Authorization: Bearer <token>` das outras rotas. "
                    + "A resposta já traz o `pacienteId` (paciente) ou o `profissionalId` (médico, especialista, "
                    + "laboratório) do usuário, para o cliente não precisar pedir o próprio cadastro. "
                    + "Cinco senhas erradas seguidas bloqueiam aquele login por 5 minutos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login aceito; devolve o token"),
            @ApiResponse(responseCode = "400", description = "Login ou senha em branco"),
            @ApiResponse(responseCode = "401", description = "Login ou senha inválidos"),
            @ApiResponse(responseCode = "429", description = "Login bloqueado por excesso de tentativas; aguarde 5 minutos")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
