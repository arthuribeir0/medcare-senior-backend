package com.medcare.senior.domain.dose;

/**
 * Dose com os dados do idoso, do medicamento e a tolerância do cuidador dono do idoso, lida em uma única consulta
 * (sem N+1). Só existe para doses de tratamento e medicamento não removidos (dose anonimizada não tem dono).
 *
 * @param toleranciaMinutosDoCuidador tolerância própria do cuidador (configuração de alerta), ou {@code null} se ele
 *                                    usa a padrão; resolvida por {@link PoliticaDeJanela#toleranciaEfetiva}
 * @param cor                         cor do medicamento como foi cadastrada (opcional, pode ser {@code null})
 */
public record DoseDetalhada(Dose dose,
                            Long idosoId,
                            String idosoNome,
                            Long medicamentoId,
                            String medicamentoNome,
                            String dosagem,
                            String tipo,
                            Integer toleranciaMinutosDoCuidador,
                            String cor) {

    /**
     * Dose de medicamento sem cor cadastrada.
     */
    public DoseDetalhada(Dose dose, Long idosoId, String idosoNome, Long medicamentoId, String medicamentoNome,
                         String dosagem, String tipo, Integer toleranciaMinutosDoCuidador) {
        this(dose, idosoId, idosoNome, medicamentoId, medicamentoNome, dosagem, tipo, toleranciaMinutosDoCuidador, null);
    }

    /**
     * Dose de cuidador sem tolerância própria (vale a padrão) e medicamento sem cor cadastrada.
     */
    public DoseDetalhada(Dose dose, Long idosoId, String idosoNome, Long medicamentoId, String medicamentoNome,
                         String dosagem, String tipo) {
        this(dose, idosoId, idosoNome, medicamentoId, medicamentoNome, dosagem, tipo, null, null);
    }

    /**
     * Os mesmos dados do idoso e do medicamento com outra versão da dose (por exemplo, depois de confirmada).
     */
    public DoseDetalhada comDose(Dose outraDose) {
        return new DoseDetalhada(outraDose, idosoId, idosoNome, medicamentoId, medicamentoNome, dosagem, tipo,
                toleranciaMinutosDoCuidador, cor);
    }
}
