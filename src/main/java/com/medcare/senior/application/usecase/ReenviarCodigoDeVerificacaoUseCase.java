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
public class ReenviarCodigoDeVerificacaoUseCase {

    private final CuidadorRepositoryPort cuidadorRepository;
    private final CodigosPorEmailService codigos;

    /**
     * Envia outro código de confirmação ao e-mail do cuidador. E-mail já confirmado: nada a enviar.
     *
     * @return {@code true} se um código foi enviado
     * @throws com.medcare.senior.domain.exception.EsperaParaReenviarException antes de 1 minuto do anterior
     */
    public boolean executar(Long cuidadorId) {
        Cuidador cuidador = cuidadorRepository.findById(cuidadorId).orElseThrow(SessaoInvalidaException::doCuidador);
        if (cuidador.isEmailVerificado()) {
            return false;
        }
        codigos.enviar(cuidador, FinalidadeDoCodigo.VERIFICACAO);
        return true;
    }
}
