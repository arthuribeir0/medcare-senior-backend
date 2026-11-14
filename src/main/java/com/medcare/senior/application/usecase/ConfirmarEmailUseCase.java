package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.CodigosPorEmailService;
import com.medcare.senior.domain.email.FinalidadeDoCodigo;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfirmarEmailUseCase {

    private final CuidadorRepositoryPort cuidadorRepository;
    private final CodigosPorEmailService codigos;

    /**
     * Confirma o e-mail do cuidador com o código enviado a ele. Já confirmado: devolve como está (idempotente).
     *
     * @return o cuidador, com o e-mail confirmado
     * @throws com.medcare.senior.domain.exception.CodigoPorEmailInvalidoException código errado, vencido ou bloqueado
     */
    public Cuidador executar(Long cuidadorId, String codigo) {
        Cuidador cuidador = cuidadorRepository.findById(cuidadorId).orElseThrow(SessaoInvalidaException::doCuidador);
        if (cuidador.isEmailVerificado()) {
            return cuidador;
        }
        codigos.conferir(cuidadorId, FinalidadeDoCodigo.VERIFICACAO, codigo);
        cuidadorRepository.marcarEmailVerificado(cuidadorId);
        cuidador.setEmailVerificado(true);
        return cuidador;
    }
}
