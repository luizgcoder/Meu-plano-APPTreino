package com.luiz.meuplano.data

import com.luiz.meuplano.parser.Plano
import com.luiz.meuplano.parser.PlanoParser
import com.luiz.meuplano.parser.SerieParser
import com.luiz.meuplano.video.EdicaoDoClipe
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

sealed interface ResultadoImportacao {
    data class Importado(val plano: Plano) : ResultadoImportacao
    /** O mesmo arquivo já tinha sido importado antes. */
    data object JaImportado : ResultadoImportacao
    /** O arquivo não é um plano do treinador. */
    data object NaoEhPlano : ResultadoImportacao
}

/**
 * @param pastaDeVideos pasta privada do app onde ficam as gravações
 */
class PlanoRepository(private val dao: PlanoDao, val pastaDeVideos: File) {

    /** A pasta de arquivos do app. Dentro dela: videos/, fotos/ e feedback/. */
    private val raiz: File = pastaDeVideos.parentFile ?: pastaDeVideos

    /** A ordenação roda fora da thread principal, para não travar a tela a cada registro. */
    val planoAtual: Flow<PlanoCompleto?> = dao.observarPlanoAtual()
        .map { it?.ordenado() }
        .flowOn(Dispatchers.Default)

    suspend fun importar(bytes: ByteArray): ResultadoImportacao = withContext(Dispatchers.Default) {
        // O Jsoup descobre a codificação pela tag <meta charset>.
        val doc = Jsoup.parse(ByteArrayInputStream(bytes), null, "")
        val plano = PlanoParser.parse(doc)
        if (plano.treinos.isEmpty() && plano.refeicoes.isEmpty()) {
            return@withContext ResultadoImportacao.NaoEhPlano
        }

        val hash = sha256(bytes)
        if (dao.idPorHash(hash) != null) {
            return@withContext ResultadoImportacao.JaImportado
        }

        val html = String(bytes, doc.charset() ?: Charsets.UTF_8)
        dao.salvar(plano, html, hash, System.currentTimeMillis())
        ResultadoImportacao.Importado(plano)
    }

    /**
     * Planos importados antes do conserto do parser: exercício cuja série veio fora da
     * lista ficou sem série nenhuma (e o botão de gravar fechava o app). Dá a ele as
     * séries achadas na observação, ou uma série livre.
     */
    suspend fun consertarExerciciosSemSerie(): Unit = trava.withLock {
        for (e in dao.exerciciosSemSerie()) {
            val achadas = SerieParser.seriesDaObservacao(e.observacao)
            dao.completarSeries(e.id, achadas.observacao, achadas.series)
        }
    }

    // ---------- registro das séries ----------

    /**
     * Cada gravação lê o registro atual e escreve por cima. A trava garante que duas
     * gravações seguidas (vídeo e, logo depois, carga) não se atropelem.
     */
    private val trava = Mutex()

    /** Grava carga, RPE e anotação da série. O vídeo que já existir é mantido. */
    suspend fun salvarRegistro(serieId: Long, cargaKg: Double?, rpe: Double?, anotacao: String?): Unit =
        trava.withLock {
            val atual = dao.registro(serieId)
            val agora = System.currentTimeMillis()
            val nota = anotacao?.trim()?.ifEmpty { null }
            // Com registro anterior, mantém o vídeo e a edição dele.
            dao.salvarRegistro(
                atual?.copy(cargaKg = cargaKg, rpe = rpe, atualizadoEm = agora, anotacao = nota)
                    ?: SerieFeitaEntity(
                        seriePrescritaId = serieId,
                        cargaKg = cargaKg,
                        rpe = rpe,
                        videoArquivo = null,
                        corteInicioMs = null,
                        corteFimMs = null,
                        atualizadoEm = agora,
                        anotacao = nota,
                    ),
            )
        }

    /** Liga um vídeo novo à série. Se já havia outro, o arquivo antigo é apagado. */
    suspend fun salvarVideo(serieId: Long, arquivo: File, corteInicial: Long? = null): Unit = trava.withLock {
        val atual = dao.registro(serieId)
        dao.salvarRegistro(
            SerieFeitaEntity(
                seriePrescritaId = serieId,
                cargaKg = atual?.cargaKg,
                rpe = atual?.rpe,
                videoArquivo = arquivo.name,
                // O corte e a edição eram do vídeo antigo (os campos de edição voltam ao padrão).
                // O vídeo novo já pode vir cortado no fim da contagem.
                corteInicioMs = corteInicial,
                corteFimMs = null,
                atualizadoEm = System.currentTimeMillis(),
                anotacao = atual?.anotacao,
            ),
        )
        val antigo = atual?.videoArquivo
        if (antigo != null && antigo != arquivo.name) apagarArquivo(antigo)
        Unit
    }

    /**
     * Guarda a edição do clipe: corte, câmera lenta, congelar o final e zoom. O arquivo não
     * é alterado. Marca a hora, para o vídeo montado do treino aparecer como desatualizado.
     */
    suspend fun salvarEdicao(serieId: Long, e: EdicaoDoClipe): Unit = trava.withLock {
        val atual = dao.registro(serieId) ?: return@withLock
        dao.salvarRegistro(
            atual.copy(
                corteInicioMs = e.inicioMs,
                corteFimMs = e.fimMs,
                lentoInicioMs = e.lentoInicioMs,
                lentoFimMs = e.lentoFimMs,
                congelarFinal = e.congelarFinal,
                zoom = e.zoom,
                centroX = e.centroX,
                centroY = e.centroY,
                velocidadeLenta = e.velocidadeLenta,
                espelhado = e.espelhado,
                atualizadoEm = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun ultimaVez(exercicio: ExercicioEntity): UltimaVez? = dao.ultimaVez(exercicio.chave, exercicio.treinoId)

    /** Os vídeos deste exercício em todas as semanas, só os que ainda existem no aparelho. */
    suspend fun videosDoExercicio(exercicio: ExercicioEntity): List<VideoDeSerie> {
        val lista = dao.videosDoExercicio(exercicio.chave)
        return withContext(Dispatchers.IO) { lista.filter { arquivoDeVideo(it.videoArquivo).isFile } }
    }

    /** Apaga o registro da série e o vídeo dela. */
    suspend fun limparRegistro(serieId: Long): Unit = trava.withLock {
        val atual = dao.registro(serieId) ?: return@withLock
        dao.apagarRegistro(serieId)
        atual.videoArquivo?.let { apagarArquivo(it) }
        Unit
    }

    /**
     * Apaga só o vídeo de uma série já registrada (usado no Comparar entre semanas, para
     * limpar vídeos antigos de teste). A carga, o RPE e a anotação ficam como estavam.
     */
    suspend fun apagarVideoDaSerie(serieId: Long): Unit = trava.withLock {
        val atual = dao.registro(serieId) ?: return@withLock
        val antigo = atual.videoArquivo ?: return@withLock
        dao.salvarRegistro(
            atual.copy(
                videoArquivo = null,
                corteInicioMs = null,
                corteFimMs = null,
                lentoInicioMs = null,
                lentoFimMs = null,
                congelarFinal = false,
                zoom = 1f,
                centroX = 0.5f,
                centroY = 0.5f,
                velocidadeLenta = 0.5f,
                espelhado = false,
            ),
        )
        apagarArquivo(antigo)
    }

    /**
     * Apaga da pasta de vídeos o que não está ligado a nenhuma série: gravação cujo app fechou
     * antes de ser guardada, cópia da galeria que parou no meio. Cada um ocupa dezenas ou
     * centenas de MB e não aparece em tela nenhuma.
     *
     * @param maisVelhosQue só arquivos mexidos antes desse momento (ms), para não pegar
     *   uma gravação em andamento
     * @return quantos arquivos foram apagados
     */
    suspend fun apagarVideosSemSerie(maisVelhosQue: Long): Int = trava.withLock {
        val usados = dao.nomesDosVideos().toSet()
        withContext(Dispatchers.IO) {
            pastaDeVideos.listFiles()
                ?.filter { it.isFile && it.name !in usados && it.lastModified() < maisVelhosQue && VIDEO_DE_SERIE.matches(it.name) }
                ?.count { it.delete() }
                ?: 0
        }
    }

    // ---------- dieta do dia e check-in ----------

    fun observarCheckin(data: String): Flow<CheckinDiarioEntity?> = dao.observarCheckin(data)

    fun observarRefeicoesFeitas(data: String): Flow<List<RefeicaoFeitaEntity>> = dao.observarRefeicoesFeitas(data)

    /** Lê o check-in do dia (ou um vazio), aplica a mudança e grava. */
    suspend fun alterarCheckin(data: String, mudanca: (CheckinDiarioEntity) -> CheckinDiarioEntity): Unit =
        trava.withLock {
            val atual = dao.checkin(data) ?: CheckinDiarioEntity.vazio(data)
            dao.salvarCheckin(mudanca(atual).copy(data = data))
        }

    /**
     * Marca a refeição como feita com a opção escolhida.
     * Tocar de novo na mesma opção desmarca a refeição.
     */
    suspend fun alternarRefeicao(data: String, refeicaoId: Long, opcaoId: Long?): Unit = trava.withLock {
        val atual = dao.refeicaoFeita(data, refeicaoId)
        if (atual != null && atual.opcaoId == opcaoId) {
            dao.apagarRefeicaoFeita(data, refeicaoId)
        } else {
            dao.salvarRefeicaoFeita(RefeicaoFeitaEntity(data, refeicaoId, opcaoId))
        }
    }

    fun observarTrocas(data: String): Flow<List<TrocaDoDiaEntity>> = dao.observarTrocas(data)

    /** Guarda a troca de um alimento naquele dia. Trocar o mesmo item de novo substitui. */
    suspend fun salvarTroca(troca: TrocaDoDiaEntity): Unit = trava.withLock { dao.salvarTroca(troca) }

    suspend fun apagarTroca(data: String, itemId: Long): Unit = trava.withLock { dao.apagarTroca(data, itemId) }

    // ---------- feedback semanal ----------

    fun observarCheckinsEntre(de: String, ate: String): Flow<List<CheckinDiarioEntity>> =
        dao.observarCheckinsEntre(de, ate)

    fun observarRefeicoesFeitasEntre(de: String, ate: String): Flow<List<RefeicaoFeitaEntity>> =
        dao.observarRefeicoesFeitasEntre(de, ate)

    /** O texto do feedback depois de editado à mão, guardado por plano. Nulo se nunca foi editado. */
    suspend fun textoEditado(planoId: Long): String? = withContext(Dispatchers.IO) {
        arquivoDoTexto(planoId).takeIf { it.isFile }?.readText()
    }

    /** Guarda o texto editado. Nulo apaga, e o feedback volta a ser o automático. */
    suspend fun salvarTextoEditado(planoId: Long, texto: String?): Unit = withContext(Dispatchers.IO) {
        val arquivo = arquivoDoTexto(planoId)
        if (texto == null) {
            arquivo.delete()
        } else {
            arquivo.parentFile?.mkdirs()
            arquivo.writeText(texto)
        }
    }

    private fun arquivoDoTexto(planoId: Long): File = File(pastaDoFeedback(planoId), "texto.txt")

    /** Fotos da semana, uma pasta por plano. */
    fun pastaDeFotos(planoId: Long): File = File(raiz, "fotos/plano_$planoId")

    /** Vídeos montados para o feedback, uma pasta por plano. */
    fun pastaDoFeedback(planoId: Long): File = File(raiz, "feedback/plano_$planoId")

    fun videoDoTreino(planoId: Long, treinoId: Long): File = File(pastaDoFeedback(planoId), "treino_$treinoId.mp4")

    fun novoArquivoDeVideo(serieId: Long): File {
        pastaDeVideos.mkdirs()
        return File(pastaDeVideos, "serie_${serieId}_${System.currentTimeMillis()}.mp4")
    }

    fun arquivoDeVideo(nome: String): File = File(pastaDeVideos, nome)

    private suspend fun apagarArquivo(nome: String) {
        withContext(Dispatchers.IO) { File(pastaDeVideos, nome).delete() }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        /** Os nomes que [novoArquivoDeVideo] cria, e o temporário de uma cópia da galeria. */
        val VIDEO_DE_SERIE = Regex("""^serie_\d+_\d+\.mp4(\.tmp)?$""")
    }
}
