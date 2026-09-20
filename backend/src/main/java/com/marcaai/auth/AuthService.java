package com.marcaai.auth;

import com.marcaai.config.LogSeguranca;
import com.marcaai.shared.PacienteRepository;
import com.marcaai.shared.Profissional;
import com.marcaai.shared.ProfissionalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final TentativasLoginService tentativasLoginService;

    public LoginResponse login(LoginRequest request) {
        try {
            tentativasLoginService.verificarBloqueio(request.login());
        } catch (LoginBloqueadoException ex) {
            LogSeguranca.loginBloqueado(request.login());
            throw ex;
        }

        Usuario usuario = usuarioRepository.findByLogin(request.login()).orElse(null);
        if (usuario == null || !passwordEncoder.matches(request.senha(), usuario.getSenhaHash())) {
            // Mesma mensagem e mesma contagem para login inexistente e senha errada: não revela quem existe.
            tentativasLoginService.registrarFalha(request.login());
            LogSeguranca.loginFalhou(request.login());
            throw new BadCredentialsException("Login ou senha inválidos");
        }
        tentativasLoginService.registrarSucesso(request.login());
        atualizarHashSeFraco(usuario, request.senha());
        LogSeguranca.loginOk(usuario.getLogin());

        String token = jwtService.gerarToken(usuario.getId(), usuario.getLogin(), usuario.getPapel());

        // Permite ao cliente identificar o próprio pacienteId/profissionalId sem precisar perguntar ao
        // usuário — nenhum dos dois é dado sensível de saúde, só o id de vínculo (ADR-004/ADR-005).
        Long pacienteId = pacienteRepository.findByUsuarioId(usuario.getId()).map(p -> p.getId()).orElse(null);
        Long profissionalId = profissionalRepository.findByUsuarioId(usuario.getId())
                .map(Profissional::getId).orElse(null);

        return new LoginResponse(token, usuario.getId(), usuario.getNome(), usuario.getPapel(), pacienteId, profissionalId);
    }

    /**
     * Senha gravada com custo de BCrypt menor que o atual (as da seed usam o custo 6 do pgcrypto) é
     * re-gravada com o custo atual no primeiro login certo — único momento em que a senha em claro existe.
     */
    private void atualizarHashSeFraco(Usuario usuario, String senha) {
        if (passwordEncoder.upgradeEncoding(usuario.getSenhaHash())) {
            usuario.setSenhaHash(passwordEncoder.encode(senha));
            usuarioRepository.save(usuario);
        }
    }
}
