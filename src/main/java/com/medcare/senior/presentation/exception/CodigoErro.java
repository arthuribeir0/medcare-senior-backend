package com.medcare.senior.presentation.exception;

/**
 * Códigos estáveis do campo {@code codigo} do {@link ErroResponse}. O app decide o que fazer pelo código (nunca pela
 * mensagem, que pode mudar). Não renomeie um código existente: crie outro.
 *
 * <pre>
 *  Código                          HTTP  Origem
 *  VALIDACAO                       400   bean validation (campos[] preenchido), parâmetro com tipo/formato errado
 *  CORPO_INVALIDO                  400   JSON ausente, malformado ou com valor em formato errado
 *  REQUISICAO_INVALIDA             400   outros erros 4xx do Spring MVC (ex.: parâmetro obrigatório ausente)
 *  SENHA_FRACA                     400   RN-002
 *  CONSENTIMENTO_OBRIGATORIO       400   termo LGPD não aceito no cadastro
 *  TOKEN_DISPOSITIVO_INVALIDO      400   token FCM vazio ou maior que 512 caracteres
 *  CODIGO_INVALIDO                 400   código de vínculo inexistente, expirado ou já usado (um código só, de propósito)
 *  DADOS_OBRIGATORIOS_MEDICAMENTO  400   RN-005
 *  TRATAMENTO_INVALIDO             400   RN-007 (horários, dias da semana, período)
 *  CREDENCIAIS_INVALIDAS           401   login com e-mail inexistente ou senha errada (mesma mensagem)
 *  NAO_AUTENTICADO                 401   sem token, ou token inválido ou expirado (Spring Security, antes do controller)
 *  SESSAO_INVALIDA                 401   token válido de usuário que não existe mais (o app volta ao pareamento)
 *  ACESSO_NEGADO                   403   papel diferente do exigido pela operação ou rota fora da lista do SecurityConfig
 *  RECURSO_NAO_ENCONTRADO          404   recurso inexistente, removido ou de outro dono
 *  METODO_NAO_PERMITIDO            405   método HTTP não suportado pela rota
 *  EMAIL_DUPLICADO                 409   RN-001
 *  PACIENTE_DUPLICADO              409   RN-003
 *  TRATAMENTO_ATIVO                409   RN-006 (remoção sem forcarRemocao=true)
 *  DOSE_JA_REGISTRADA              409   confirmação de dose que já tem status registrado (RN-008), inclusive corrida
 *  DOSE_MUITO_CEDO                 409   confirmação antes da abertura da janela
 *  DOSE_ENCERRADA                  409   confirmação depois do encerramento da janela
 *  PERIODO_SEM_REGISTROS           422   exportação do histórico em PDF de período sem nenhuma dose (UC-012, 4a)
 *  LIMITE_TENTATIVAS               429   rate limiting do pareamento (header Retry-After)
 *  ERRO_INTERNO                    500   erro inesperado (sem detalhes na resposta)
 * </pre>
 */
public enum CodigoErro {
    VALIDACAO,
    CORPO_INVALIDO,
    REQUISICAO_INVALIDA,
    SENHA_FRACA,
    CONSENTIMENTO_OBRIGATORIO,
    TOKEN_DISPOSITIVO_INVALIDO,
    CODIGO_INVALIDO,
    DADOS_OBRIGATORIOS_MEDICAMENTO,
    TRATAMENTO_INVALIDO,
    CREDENCIAIS_INVALIDAS,
    SESSAO_INVALIDA,
    NAO_AUTENTICADO,
    ACESSO_NEGADO,
    RECURSO_NAO_ENCONTRADO,
    METODO_NAO_PERMITIDO,
    EMAIL_DUPLICADO,
    PACIENTE_DUPLICADO,
    TRATAMENTO_ATIVO,
    DOSE_JA_REGISTRADA,
    DOSE_MUITO_CEDO,
    DOSE_ENCERRADA,
    PERIODO_SEM_REGISTROS,
    LIMITE_TENTATIVAS,
    ERRO_INTERNO
}
