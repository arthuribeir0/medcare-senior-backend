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
 *  ACESSO_NEGADO                   403   papel diferente do exigido pela operação
 *  RECURSO_NAO_ENCONTRADO          404   recurso inexistente, removido ou de outro dono
 *  METODO_NAO_PERMITIDO            405   método HTTP não suportado pela rota
 *  EMAIL_DUPLICADO                 409   RN-001
 *  IDOSO_DUPLICADO                 409   RN-003
 *  TRATAMENTO_ATIVO                409   RN-006 (remoção sem forcarRemocao=true)
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
    ACESSO_NEGADO,
    RECURSO_NAO_ENCONTRADO,
    METODO_NAO_PERMITIDO,
    EMAIL_DUPLICADO,
    IDOSO_DUPLICADO,
    TRATAMENTO_ATIVO,
    LIMITE_TENTATIVAS,
    ERRO_INTERNO
}
