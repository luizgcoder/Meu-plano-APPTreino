package com.luiz.meuplano.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.luiz.meuplano.parser.Plano
import com.luiz.meuplano.parser.SeriePrescrita
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlanoDao {

    /** O plano mais recente pela data em que o treinador gerou o arquivo. */
    @Transaction
    @Query("SELECT * FROM plano ORDER BY COALESCE(geradoEm, '') DESC, id DESC LIMIT 1")
    abstract fun observarPlanoAtual(): Flow<PlanoCompleto?>

    @Query("SELECT id FROM plano WHERE hash = :hash LIMIT 1")
    abstract suspend fun idPorHash(hash: String): Long?

    @Query("SELECT html FROM plano_html WHERE planoId = :planoId")
    abstract suspend fun htmlOriginal(planoId: Long): String?

    // ----- o que foi feito em cada série -----

    @Query("SELECT * FROM serie_feita WHERE seriePrescritaId = :serieId")
    abstract suspend fun registro(serieId: Long): SerieFeitaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarRegistro(e: SerieFeitaEntity)

    @Query("DELETE FROM serie_feita WHERE seriePrescritaId = :serieId")
    abstract suspend fun apagarRegistro(serieId: Long)

    /** Os nomes de todos os arquivos de vídeo ligados a alguma série, de todos os planos. */
    @Query("SELECT videoArquivo FROM serie_feita WHERE videoArquivo IS NOT NULL")
    abstract suspend fun nomesDosVideos(): List<String>

    /**
     * A última vez que este exercício (pela chave) teve carga registrada em outra semana:
     * a carga, o RPE e as repetições pedidas.
     */
    @Query(
        "SELECT sf.cargaKg AS cargaKg, sf.rpe AS rpe, sp.repsMin AS reps " +
            "FROM serie_feita sf " +
            "JOIN serie_prescrita sp ON sp.id = sf.seriePrescritaId " +
            "JOIN exercicio e ON e.id = sp.exercicioId " +
            "JOIN treino t ON t.id = e.treinoId " +
            "WHERE e.chave = :chave AND sf.cargaKg IS NOT NULL " +
            "AND t.planoId != (SELECT planoId FROM treino WHERE id = :treinoAtual) " +
            "ORDER BY sf.atualizadoEm DESC LIMIT 1",
    )
    abstract suspend fun ultimaVez(chave: String, treinoAtual: Long): UltimaVez?

    /** Todos os vídeos de série deste exercício (pela chave), de todas as semanas, do mais novo ao mais antigo. */
    @Query(
        "SELECT sf.seriePrescritaId AS seriePrescritaId, sf.videoArquivo AS videoArquivo, " +
            "sf.corteInicioMs AS corteInicioMs, sf.cargaKg AS cargaKg, sf.rpe AS rpe, sp.repsMin AS reps, " +
            "sf.atualizadoEm AS atualizadoEm, t.planoId AS planoId, p.semana AS semana, sp.ordem AS ordem " +
            "FROM serie_feita sf " +
            "JOIN serie_prescrita sp ON sp.id = sf.seriePrescritaId " +
            "JOIN exercicio e ON e.id = sp.exercicioId " +
            "JOIN treino t ON t.id = e.treinoId " +
            "JOIN plano p ON p.id = t.planoId " +
            "WHERE e.chave = :chave AND sf.videoArquivo IS NOT NULL " +
            "ORDER BY sf.atualizadoEm DESC",
    )
    abstract suspend fun videosDoExercicio(chave: String): List<VideoDeSerie>

    // ----- dieta do dia e check-in -----

    @Query("SELECT * FROM checkin_diario WHERE data = :data")
    abstract fun observarCheckin(data: String): Flow<CheckinDiarioEntity?>

    @Query("SELECT * FROM checkin_diario WHERE data = :data")
    abstract suspend fun checkin(data: String): CheckinDiarioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarCheckin(e: CheckinDiarioEntity)

    @Query("SELECT * FROM refeicao_feita WHERE data = :data")
    abstract fun observarRefeicoesFeitas(data: String): Flow<List<RefeicaoFeitaEntity>>

    @Query("SELECT * FROM refeicao_feita WHERE data = :data AND refeicaoId = :refeicaoId")
    abstract suspend fun refeicaoFeita(data: String, refeicaoId: Long): RefeicaoFeitaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarRefeicaoFeita(e: RefeicaoFeitaEntity)

    @Query("DELETE FROM refeicao_feita WHERE data = :data AND refeicaoId = :refeicaoId")
    abstract suspend fun apagarRefeicaoFeita(data: String, refeicaoId: Long)

    @Query("SELECT * FROM troca_do_dia WHERE data = :data")
    abstract fun observarTrocas(data: String): Flow<List<TrocaDoDiaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun salvarTroca(e: TrocaDoDiaEntity)

    @Query("DELETE FROM troca_do_dia WHERE data = :data AND itemId = :itemId")
    abstract suspend fun apagarTroca(data: String, itemId: Long)

    // ----- feedback semanal -----

    @Query("SELECT * FROM checkin_diario WHERE data BETWEEN :de AND :ate ORDER BY data")
    abstract fun observarCheckinsEntre(de: String, ate: String): Flow<List<CheckinDiarioEntity>>

    @Query("SELECT * FROM refeicao_feita WHERE data BETWEEN :de AND :ate")
    abstract fun observarRefeicoesFeitasEntre(de: String, ate: String): Flow<List<RefeicaoFeitaEntity>>

    // ----- importação do plano -----

    @Insert abstract suspend fun inserirPlano(e: PlanoEntity): Long
    @Insert abstract suspend fun inserirHtml(e: PlanoHtmlEntity)
    @Insert abstract suspend fun inserirRefeicao(e: RefeicaoEntity): Long
    @Insert abstract suspend fun inserirOpcao(e: OpcaoRefeicaoEntity): Long
    @Insert abstract suspend fun inserirItens(e: List<ItemRefeicaoEntity>)
    @Insert abstract suspend fun inserirTreino(e: TreinoEntity): Long
    @Insert abstract suspend fun inserirExercicio(e: ExercicioEntity): Long
    @Insert abstract suspend fun inserirSeries(e: List<SeriePrescritaEntity>)
    @Insert abstract suspend fun inserirPrs(e: List<PrEntity>)
    @Insert abstract suspend fun inserirBlocos(e: List<BlocoInfoEntity>)

    /** Grava o plano inteiro de uma vez: ou entra tudo, ou não entra nada. */
    @Transaction
    open suspend fun salvar(plano: Plano, html: String, hash: String, agora: Long): Long {
        val planoId = inserirPlano(
            PlanoEntity(
                nomeAluno = plano.nomeAluno,
                geradoEm = plano.geradoEm,
                semana = plano.semana,
                tituloTreino = plano.tituloTreino,
                tituloDieta = plano.tituloDieta,
                metaAguaLitros = plano.metas.aguaLitros,
                cardioDiasSemana = plano.metas.cardioDiasSemana,
                cardioMinutos = plano.metas.cardioMinutos,
                importadoEm = agora,
                hash = hash,
            ),
        )
        inserirHtml(PlanoHtmlEntity(planoId, html))

        plano.refeicoes.forEachIndexed { i, refeicao ->
            val refeicaoId = inserirRefeicao(RefeicaoEntity(planoId = planoId, ordem = i, titulo = refeicao.titulo))
            refeicao.opcoes.forEachIndexed { j, opcao ->
                val opcaoId = inserirOpcao(OpcaoRefeicaoEntity(refeicaoId = refeicaoId, ordem = j, nome = opcao.nome))
                inserirItens(
                    opcao.itens.mapIndexed { k, item ->
                        ItemRefeicaoEntity(opcaoId = opcaoId, ordem = k, texto = item.texto, opcional = item.opcional)
                    },
                )
            }
        }

        plano.treinos.forEachIndexed { i, treino ->
            val treinoId = inserirTreino(
                TreinoEntity(planoId = planoId, ordem = i, nome = treino.nome, letra = treino.letra),
            )
            treino.exercicios.forEachIndexed { j, exercicio ->
                val exercicioId = inserirExercicio(
                    ExercicioEntity(
                        treinoId = treinoId,
                        ordem = j,
                        nomeOriginal = exercicio.nomeOriginal,
                        nome = exercicio.nome,
                        chave = exercicio.chave,
                        observacao = exercicio.observacao,
                        videoUrl = exercicio.videoUrl,
                    ),
                )
                inserirSeries(exercicio.series.mapIndexed { k, s -> s.paraEntidade(exercicioId, k) })
            }
        }

        inserirPrs(
            plano.prs.mapIndexed { i, pr ->
                PrEntity(planoId = planoId, ordem = i, exercicio = pr.exercicio, texto = pr.texto, cargaKg = pr.cargaKg)
            },
        )
        inserirBlocos(
            plano.blocos.mapIndexed { i, bloco ->
                BlocoInfoEntity(
                    planoId = planoId,
                    ordem = i,
                    categoria = bloco.categoria.name,
                    secao = bloco.secao,
                    titulo = bloco.titulo,
                    texto = bloco.linhas.joinToString("\n"),
                )
            },
        )
        return planoId
    }

    // ----- conserto de planos já importados -----

    /** Exercícios sem nenhuma série (a série veio escrita fora da lista no HTML). */
    @Query("SELECT * FROM exercicio WHERE id NOT IN (SELECT exercicioId FROM serie_prescrita)")
    abstract suspend fun exerciciosSemSerie(): List<ExercicioEntity>

    @Query("UPDATE exercicio SET observacao = :observacao WHERE id = :id")
    abstract suspend fun mudarObservacao(id: Long, observacao: String?)

    /** Dá as séries que faltavam a um exercício e tira da observação o que virou série. */
    @Transaction
    open suspend fun completarSeries(exercicioId: Long, observacao: String?, series: List<SeriePrescrita>) {
        inserirSeries(series.mapIndexed { k, s -> s.paraEntidade(exercicioId, k) })
        mudarObservacao(exercicioId, observacao)
    }
}

/** A série lida do HTML, do jeito que fica no banco. */
internal fun SeriePrescrita.paraEntidade(exercicioId: Long, ordem: Int) = SeriePrescritaEntity(
    exercicioId = exercicioId,
    ordem = ordem,
    textoOriginal = textoOriginal,
    series = series,
    repsMin = repsMin,
    repsMax = repsMax,
    unidade = unidade,
    cargaMinKg = cargaMinKg,
    cargaMaxKg = cargaMaxKg,
    cargaPorLado = cargaPorLado,
    pesoCorporal = pesoCorporal,
    ajustePercentual = ajustePercentual,
    rpeMin = rpeMin,
    rpeMax = rpeMax,
    gravar = gravar,
    informarCarga = informarCarga,
    observacao = observacao,
    interpretada = interpretada,
)
