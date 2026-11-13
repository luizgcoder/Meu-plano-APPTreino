package com.luiz.meuplano.data

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Relation
import com.luiz.meuplano.video.EdicaoDoClipe

/*
 * @Immutable avisa o Compose que estas classes não mudam depois de criadas (as listas
 * nunca são alteradas, só trocadas por outras). Com isso, quando chega um plano novo do
 * banco, só os cartões cujo conteúdo mudou de fato são redesenhados.
 *
 * O plano inteiro em uma leitura só. O Room não garante a ordem das listas
 * de @Relation, por isso quem usa deve chamar ordenado().
 */

@Immutable
data class OpcaoComItens(
    @Embedded val opcao: OpcaoRefeicaoEntity,
    @Relation(parentColumn = "id", entityColumn = "opcaoId")
    val itens: List<ItemRefeicaoEntity>,
)

@Immutable
data class RefeicaoComOpcoes(
    @Embedded val refeicao: RefeicaoEntity,
    @Relation(entity = OpcaoRefeicaoEntity::class, parentColumn = "id", entityColumn = "refeicaoId")
    val opcoes: List<OpcaoComItens>,
)

/** A linha do plano e, se você já registrou, o que foi feito nela. */
@Immutable
data class SerieComRegistro(
    @Embedded val serie: SeriePrescritaEntity,
    @Relation(parentColumn = "id", entityColumn = "seriePrescritaId")
    val registro: SerieFeitaEntity?,
)

@Immutable
data class ExercicioComSeries(
    @Embedded val exercicio: ExercicioEntity,
    @Relation(entity = SeriePrescritaEntity::class, parentColumn = "id", entityColumn = "exercicioId")
    val series: List<SerieComRegistro>,
)

@Immutable
data class TreinoComExercicios(
    @Embedded val treino: TreinoEntity,
    @Relation(entity = ExercicioEntity::class, parentColumn = "id", entityColumn = "treinoId")
    val exercicios: List<ExercicioComSeries>,
)

@Immutable
data class PlanoCompleto(
    @Embedded val plano: PlanoEntity,
    @Relation(entity = RefeicaoEntity::class, parentColumn = "id", entityColumn = "planoId")
    val refeicoes: List<RefeicaoComOpcoes>,
    @Relation(entity = TreinoEntity::class, parentColumn = "id", entityColumn = "planoId")
    val treinos: List<TreinoComExercicios>,
    @Relation(parentColumn = "id", entityColumn = "planoId")
    val prs: List<PrEntity>,
    @Relation(parentColumn = "id", entityColumn = "planoId")
    val blocos: List<BlocoInfoEntity>,
)

fun PlanoCompleto.ordenado(): PlanoCompleto = copy(
    refeicoes = refeicoes.sortedBy { it.refeicao.ordem }.map { r ->
        r.copy(opcoes = r.opcoes.sortedBy { it.opcao.ordem }.map { o ->
            o.copy(itens = o.itens.sortedBy { it.ordem })
        })
    },
    treinos = treinos.sortedBy { it.treino.ordem }.map { t ->
        t.copy(exercicios = t.exercicios.sortedBy { it.exercicio.ordem }.map { e ->
            e.copy(series = e.series.sortedBy { it.serie.ordem })
        })
    },
    prs = prs.sortedBy { it.ordem },
    blocos = blocos.sortedBy { it.ordem },
)

/** Uma série achada pelo id, com o que as telas de registro e de câmera precisam saber. */
@Immutable
data class SerieLocalizada(
    val exercicio: ExercicioEntity,
    val item: SerieComRegistro,
    /** Carga registrada na linha de cima do mesmo exercício (base do "diminuir 10%"). */
    val cargaDaLinhaAnterior: Double?,
)

fun PlanoCompleto.acharSerie(serieId: Long?): SerieLocalizada? {
    if (serieId == null) return null
    for (treino in treinos) {
        for (exercicio in treino.exercicios) {
            val i = exercicio.series.indexOfFirst { it.serie.id == serieId }
            if (i >= 0) {
                val anterior = exercicio.series.getOrNull(i - 1)?.registro?.cargaKg
                return SerieLocalizada(exercicio.exercicio, exercicio.series[i], anterior)
            }
        }
    }
    return null
}

/** O alimento da dieta com este id, em qualquer refeição e opção do plano. */
fun PlanoCompleto.acharItemDaDieta(itemId: Long?): ItemRefeicaoEntity? {
    if (itemId == null) return null
    for (refeicao in refeicoes) {
        for (opcao in refeicao.opcoes) {
            opcao.itens.firstOrNull { it.id == itemId }?.let { return it }
        }
    }
    return null
}

/** O que foi feito no exercício na última semana em que ele apareceu. */
data class UltimaVez(val cargaKg: Double, val rpe: Double?, val reps: Int?)

/**
 * Um vídeo de série gravado em qualquer semana, para comparar a execução do mesmo
 * exercício ao longo do tempo (busca pela chave do exercício).
 */
data class VideoDeSerie(
    val seriePrescritaId: Long,
    val videoArquivo: String,
    val corteInicioMs: Long?,
    val cargaKg: Double?,
    val rpe: Double?,
    val reps: Int?,
    val atualizadoEm: Long,
    val planoId: Long,
    val semana: Int?,
    /** Posição da linha de série dentro do exercício (0 = primeira). */
    val ordem: Int,
)

/** A edição guardada do clipe desta série, no formato da montagem. */
fun SerieFeitaEntity.edicao(): EdicaoDoClipe = EdicaoDoClipe(
    inicioMs = corteInicioMs,
    fimMs = corteFimMs,
    lentoInicioMs = lentoInicioMs,
    lentoFimMs = lentoFimMs,
    congelarFinal = congelarFinal,
    zoom = zoom,
    centroX = centroX,
    centroY = centroY,
    velocidadeLenta = velocidadeLenta,
    espelhado = espelhado,
)
