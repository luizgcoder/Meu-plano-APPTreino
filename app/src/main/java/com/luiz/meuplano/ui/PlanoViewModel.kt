package com.luiz.meuplano.ui

import android.app.Application
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luiz.meuplano.MeuPlanoApp
import com.luiz.meuplano.data.CheckinDiarioEntity
import com.luiz.meuplano.data.ExercicioEntity
import com.luiz.meuplano.data.UltimaVez
import com.luiz.meuplano.data.VideoDeSerie
import com.luiz.meuplano.data.PlanoCompleto
import com.luiz.meuplano.data.ResultadoImportacao
import com.luiz.meuplano.data.edicao
import com.luiz.meuplano.data.TrocaDoDiaEntity
import com.luiz.meuplano.feedback.DadosDaSemana
import com.luiz.meuplano.feedback.TreinoComClipes
import com.luiz.meuplano.feedback.clipesPorTreino
import com.luiz.meuplano.feedback.legendaDoClipe
import com.luiz.meuplano.feedback.montarDados
import com.luiz.meuplano.feedback.periodoDaSemana
import com.luiz.meuplano.feedback.textoDoFeedback
import com.luiz.meuplano.parser.Plano
import com.luiz.meuplano.video.Captura
import com.luiz.meuplano.video.Clipe
import com.luiz.meuplano.video.Marca
import com.luiz.meuplano.video.NivelDoVisual
import com.luiz.meuplano.video.ProcessadorDeFoto
import com.luiz.meuplano.video.capturasDaPasta
import com.luiz.meuplano.video.nomeDaCaptura
import com.luiz.meuplano.video.EdicaoDoClipe
import com.luiz.meuplano.video.OpcoesDaMontagem
import com.luiz.meuplano.video.SomDoVideo
import com.luiz.meuplano.video.Galeria
import com.luiz.meuplano.video.MontadorDeVideo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface EstadoPlano {
    data object Carregando : EstadoPlano
    data object Vazio : EstadoPlano
    data class Carregado(val plano: PlanoCompleto) : EstadoPlano
}

/**
 * O dia aberto na aba Dieta.
 *
 * @param carregado falso só no instante entre trocar de dia e o banco responder
 * @param refeicoesFeitas id da refeição -> id da opção escolhida (nulo se a refeição não tem opções)
 * @param trocas id do item da refeição -> a troca feita nele neste dia
 */
@Immutable
data class EstadoDoDia(
    val dia: LocalDate,
    val carregado: Boolean,
    val checkin: CheckinDiarioEntity,
    val refeicoesFeitas: Map<Long, Long?>,
    val trocas: Map<Long, TrocaDoDiaEntity> = emptyMap(),
)

/**
 * O que a aba Feedback mostra.
 *
 * @param textoAutomatico o texto montado a partir dos registros da semana
 * @param treinos só os treinos que têm algum vídeo gravado
 */
@Immutable
data class EstadoFeedback(
    val planoId: Long,
    val dados: DadosDaSemana,
    val textoAutomatico: String,
    val treinos: List<TreinoComClipes>,
)

/** As três fotos que o treinador pede. [arquivo] é o nome do arquivo, sem a extensão. */
enum class PosicaoDaFoto(val rotulo: String, val arquivo: String) {
    FRENTE("Frente", "frente"),
    LADO("Lado", "lado"),
    COSTAS("Costas", "costas"),
}

sealed interface EstadoDaMontagem {
    data object Parada : EstadoDaMontagem

    /** @param posicao qual vídeo está sendo montado (1 = o primeiro) de [total] */
    data class Montando(val treino: String, val posicao: Int, val total: Int, val porcento: Int) : EstadoDaMontagem
}

class PlanoViewModel(app: Application) : AndroidViewModel(app) {

    private val repositorio = (app as MeuPlanoApp).repositorio

    init {
        // Plano importado antes do conserto: exercício com a série fora da lista ficou sem série.
        viewModelScope.launch { runCatching { repositorio.consertarExerciciosSemSerie() } }
        viewModelScope.launch { runCatching { limparSobras(app) } }
    }

    /**
     * Ao abrir o app, apaga o que operações interrompidas deixaram para trás (o app foi fechado
     * no meio de uma gravação, de um corte, de uma cópia): sem isso o espaço ocupado só crescia.
     * Só mexe em arquivos com mais de uma hora, para não pegar nada que esteja em uso.
     */
    private suspend fun limparSobras(app: Application) {
        val antigo = System.currentTimeMillis() - 60 * 60 * 1000L
        withContext(Dispatchers.IO) {
            fun File.sobrou() = isFile && lastModified() < antigo
            // Foto crua da câmera e recorte feito para baixar um vídeo.
            app.cacheDir.listFiles()
                ?.filter { it.sobrou() && (it.name.startsWith("foto_") || it.name.startsWith("recorte_")) }
                ?.forEach { it.delete() }
            // Quadros do "congelar o final" de uma montagem que não terminou.
            File(app.cacheDir, "montagem").takeIf { it.isDirectory && it.lastModified() < antigo }?.deleteRecursively()
            // Corte e cópia da galeria que pararam no meio, na aba Câmera.
            File(app.filesDir, "camera").listFiles()
                ?.filter { it.sobrou() && (it.name.endsWith(".tmp") || it.name.startsWith("corte_")) }
                ?.forEach { it.delete() }
            // O mesmo para as fotos e o vídeo do físico de cada plano.
            for (pasta in listOf("fotos", "feedback")) {
                File(app.filesDir, pasta).listFiles()?.filter { it.isDirectory }?.forEach { doPlano ->
                    doPlano.listFiles()
                        ?.filter { it.sobrou() && (it.name.endsWith(".tmp") || it.name == "fisico_novo.mp4") }
                        ?.forEach { it.delete() }
                }
            }
        }
        repositorio.apagarVideosSemSerie(antigo)
    }

    val estado: StateFlow<EstadoPlano> = repositorio.planoAtual
        .map { if (it == null) EstadoPlano.Vazio else EstadoPlano.Carregado(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, EstadoPlano.Carregando)

    /** Aviso curto para a tela mostrar uma vez (resultado da importação). */
    private val _mensagem = MutableStateFlow<String?>(null)
    val mensagem: StateFlow<String?> = _mensagem.asStateFlow()

    fun mensagemMostrada() {
        _mensagem.value = null
    }

    fun importar(uri: Uri) {
        viewModelScope.launch {
            _mensagem.value = try {
                val bytes = withContext(Dispatchers.IO) { lerArquivo(uri) }
                when (val resultado = repositorio.importar(bytes)) {
                    is ResultadoImportacao.Importado -> {
                        // Semana nova: o texto editado era do feedback da semana anterior.
                        _textoEditado.value = null
                        planoDoTexto = null
                        resumoDaImportacao(resultado.plano)
                    }
                    ResultadoImportacao.JaImportado -> "Esse plano já estava importado."
                    ResultadoImportacao.NaoEhPlano -> "Esse arquivo não parece ser um plano do treinador."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ArquivoGrandeDemais) {
                "Arquivo grande demais para ser um plano."
            } catch (e: Exception) {
                "Não consegui abrir o arquivo."
            }
        }
    }

    // ---------- dieta do dia e check-in ----------

    private val diaAberto = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val estadoDoDia: StateFlow<EstadoDoDia> = diaAberto
        .flatMapLatest { dia ->
            val data = dia.toString()
            combine(
                repositorio.observarCheckin(data),
                repositorio.observarRefeicoesFeitas(data),
                repositorio.observarTrocas(data),
            ) { checkin, feitas, trocas ->
                EstadoDoDia(
                    dia = dia,
                    carregado = true,
                    checkin = checkin ?: CheckinDiarioEntity.vazio(data),
                    refeicoesFeitas = feitas.associate { it.refeicaoId to it.opcaoId },
                    trocas = trocas.associateBy { it.itemId },
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            LocalDate.now().let { EstadoDoDia(it, false, CheckinDiarioEntity.vazio(it.toString()), emptyMap()) },
        )

    fun diaAnterior() {
        diaAberto.value = diaAberto.value.minusDays(1)
    }

    /** Não passa de hoje: não faz sentido registrar um dia que ainda não aconteceu. */
    fun diaSeguinte() {
        val seguinte = diaAberto.value.plusDays(1)
        if (!seguinte.isAfter(LocalDate.now())) diaAberto.value = seguinte
    }

    fun irParaHoje() {
        diaAberto.value = LocalDate.now()
    }

    /** Muda o check-in do dia aberto. Ex.: alterarCheckin { it.copy(cardioFeito = true) } */
    fun alterarCheckin(mudanca: (CheckinDiarioEntity) -> CheckinDiarioEntity) {
        val data = diaAberto.value.toString()
        viewModelScope.launch { repositorio.alterarCheckin(data, mudanca) }
    }

    fun somarAgua(ml: Int) {
        alterarCheckin { it.copy(aguaMl = (it.aguaMl + ml).coerceAtLeast(0)) }
    }

    /** Marca a refeição com a opção tocada. Tocar na mesma opção de novo desmarca. */
    fun alternarRefeicao(refeicaoId: Long, opcaoId: Long?) {
        val data = diaAberto.value.toString()
        viewModelScope.launch { repositorio.alternarRefeicao(data, refeicaoId, opcaoId) }
    }

    /**
     * Guarda, só para o dia aberto, a troca de um alimento da dieta.
     *
     * @param quantidade quanto do alimento original: em unidades se ele é de unidade, senão em gramas
     * @param proporcao quanto das calorias vai no primeiro alimento, de 0 a 100 (só vale com [destino2])
     */
    fun usarTroca(itemId: Long, origem: String, quantidade: Double, destino1: String, destino2: String?, proporcao: Int) {
        val troca = TrocaDoDiaEntity(diaAberto.value.toString(), itemId, origem, quantidade, destino1, destino2, proporcao)
        viewModelScope.launch { repositorio.salvarTroca(troca) }
    }

    /** Volta o item da dieta ao alimento original no dia aberto. */
    fun desfazerTroca(itemId: Long) {
        val data = diaAberto.value.toString()
        viewModelScope.launch { repositorio.apagarTroca(data, itemId) }
    }

    // ---------- registro das séries ----------

    fun salvarRegistro(serieId: Long, cargaKg: Double?, rpe: Double?, anotacao: String?) {
        viewModelScope.launch { repositorio.salvarRegistro(serieId, cargaKg, rpe, anotacao) }
    }

    /** A carga da última semana em que o exercício apareceu, para o registro mostrar. */
    suspend fun ultimaVez(exercicio: ExercicioEntity): UltimaVez? =
        runCatching { repositorio.ultimaVez(exercicio) }.getOrNull()

    /** Os vídeos deste exercício em todas as semanas (para o Comparar entre semanas). */
    suspend fun videosDoExercicio(exercicio: ExercicioEntity): List<VideoDeSerie> =
        runCatching { repositorio.videosDoExercicio(exercicio) }.getOrDefault(emptyList())

    fun limparRegistro(serieId: Long) {
        viewModelScope.launch { repositorio.limparRegistro(serieId) }
    }

    /** Apaga só o vídeo de uma série (mantém carga, RPE e anotação), usado no Comparar entre semanas. */
    fun apagarVideoDaSerie(serieId: Long, aoTerminar: () -> Unit = {}) {
        viewModelScope.launch {
            repositorio.apagarVideoDaSerie(serieId)
            aoTerminar()
        }
    }

    /** Onde a câmera deve gravar o próximo vídeo desta série. */
    fun novoArquivoDeVideo(serieId: Long): File = repositorio.novoArquivoDeVideo(serieId)

    /** @param corteInicial onde o vídeo começa de verdade (o fim da contagem), ou nulo */
    fun videoGravado(serieId: Long, arquivo: File, corteInicial: Long? = null) {
        viewModelScope.launch { repositorio.salvarVideo(serieId, arquivo, corteInicial) }
    }

    fun arquivoDeVideo(nomeDoArquivo: String): File = repositorio.arquivoDeVideo(nomeDoArquivo)

    fun salvarEdicao(serieId: Long, edicao: EdicaoDoClipe) {
        viewModelScope.launch { repositorio.salvarEdicao(serieId, edicao) }
    }

    // ---------- feedback semanal ----------

    @OptIn(ExperimentalCoroutinesApi::class)
    val feedback: StateFlow<EstadoFeedback?> = repositorio.planoAtual
        .flatMapLatest { plano ->
            if (plano == null) {
                flowOf<EstadoFeedback?>(null)
            } else {
                val hoje = LocalDate.now()
                val (inicio, fim) = periodoDaSemana(plano.plano.geradoEm, hoje)
                combine(
                    repositorio.observarCheckinsEntre(inicio.toString(), fim.toString()),
                    repositorio.observarRefeicoesFeitasEntre(inicio.toString(), fim.toString()),
                ) { checkins, refeicoes ->
                    val dados = montarDados(plano, hoje, checkins, refeicoes)
                    EstadoFeedback(plano.plano.id, dados, textoDoFeedback(dados), clipesPorTreino(plano))
                }
            }
        }
        // Montar o feedback percorre o plano inteiro: fica fora da thread principal.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** O texto depois de mexido à mão. Nulo = usar o automático. */
    private val _textoEditado = MutableStateFlow<String?>(null)
    val textoEditado: StateFlow<String?> = _textoEditado.asStateFlow()

    /** De qual plano é o texto que está em [_textoEditado]. */
    private var planoDoTexto: Long? = null
    private var salvamentoDoTexto: Job? = null

    /** Traz do disco o texto editado deste plano, uma vez. Fechar o app não perde o que foi escrito. */
    fun carregarTextoEditado(planoId: Long) {
        if (planoDoTexto == planoId) return
        planoDoTexto = planoId
        viewModelScope.launch {
            val salvo = repositorio.textoEditado(planoId)
            // Se já começou a digitar enquanto o disco respondia, vale o que foi digitado.
            if (_textoEditado.value == null) _textoEditado.value = salvo
        }
    }

    fun editarTexto(planoId: Long, texto: String) {
        planoDoTexto = planoId
        _textoEditado.value = texto
        // Grava um instante depois da última tecla, e não a cada letra.
        salvamentoDoTexto?.cancel()
        salvamentoDoTexto = viewModelScope.launch {
            delay(400)
            repositorio.salvarTextoEditado(planoId, texto)
        }
    }

    fun voltarAoTextoAutomatico(planoId: Long) {
        salvamentoDoTexto?.cancel()
        _textoEditado.value = null
        viewModelScope.launch { repositorio.salvarTextoEditado(planoId, null) }
    }

    private val _fotos = MutableStateFlow<List<File>>(emptyList())
    val fotos: StateFlow<List<File>> = _fotos.asStateFlow()

    /** Id do treino -> vídeo já montado. */
    private val _videosProntos = MutableStateFlow<Map<Long, File>>(emptyMap())
    val videosProntos: StateFlow<Map<Long, File>> = _videosProntos.asStateFlow()

    private val _montagem = MutableStateFlow<EstadoDaMontagem>(EstadoDaMontagem.Parada)
    val montagem: StateFlow<EstadoDaMontagem> = _montagem.asStateFlow()
    private var trabalhoDeMontagem: Job? = null

    /** Relê do disco as fotos e os vídeos montados deste plano. */
    fun atualizarArquivosDoFeedback(planoId: Long, treinoIds: List<Long>) {
        viewModelScope.launch {
            val (fotos, videos) = withContext(Dispatchers.IO) {
                val fotos = repositorio.pastaDeFotos(planoId).listFiles()
                    ?.filter { it.isFile && it.extension != "tmp" }
                    ?.sortedBy { it.name }
                    .orEmpty()
                val videos = treinoIds.associateWith { repositorio.videoDoTreino(planoId, it) }
                    .filterValues { it.isFile && it.length() > 0 }
                _fisico.value = arquivoDoFisico(planoId).takeIf { it.isFile && it.length() > 0 }
                fotos to videos
            }
            _fotos.value = fotos
            _videosProntos.value = videos
        }
    }

    /**
     * Guarda a foto escolhida na galeria no lugar daquela posição (frente, lado ou costas).
     * A foto que já estivesse ali é substituída.
     */
    fun definirFoto(planoId: Long, treinoIds: List<Long>, posicao: PosicaoDaFoto, uri: Uri) {
        viewModelScope.launch {
            val copiou = withContext(Dispatchers.IO) {
                val pasta = repositorio.pastaDeFotos(planoId).apply { mkdirs() }
                // Copia primeiro para um temporário: se a cópia falhar, a foto antiga continua lá.
                val temporario = File(pasta, "${posicao.arquivo}.tmp")
                try {
                    val resolvedor = getApplication<Application>().contentResolver
                    // Só letras e números: a extensão vem de outro app e entra no nome do arquivo.
                    val extensao = resolvedor.getType(uri)
                        ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                        ?.takeIf { e -> e.length in 2..5 && e.all { it.isLetterOrDigit() } }
                        ?: "jpg"
                    val entrada = resolvedor.openInputStream(uri) ?: throw IOException("Foto indisponível")
                    entrada.use { origem -> temporario.outputStream().use { origem.copyTo(it) } }
                    pasta.listFiles()
                        ?.filter { it.nameWithoutExtension == posicao.arquivo && it != temporario }
                        ?.forEach { it.delete() }
                    temporario.renameTo(File(pasta, "${posicao.arquivo}.$extensao"))
                } catch (e: Exception) {
                    false
                } finally {
                    temporario.delete()
                }
            }
            if (!copiou) _mensagem.value = "Não consegui copiar a foto."
            atualizarArquivosDoFeedback(planoId, treinoIds)
        }
    }

    fun removerFoto(planoId: Long, treinoIds: List<Long>, foto: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { foto.delete() }
            atualizarArquivosDoFeedback(planoId, treinoIds)
        }
    }

    /**
     * Monta um vídeo por treino, um de cada vez, com os clipes cortados e a legenda.
     *
     * @param treinos os que vão ser montados agora (pode ser um só)
     * @param todosOsTreinoIds todos os treinos com vídeo, para a lista de prontos continuar
     *   completa depois: sem isso, montar o treino B fazia o A deixar de aparecer como pronto
     */
    fun montarVideos(planoId: Long, treinos: List<TreinoComClipes>, todosOsTreinoIds: List<Long>) {
        if (trabalhoDeMontagem?.isActive == true) return
        val treinoIds = todosOsTreinoIds
        trabalhoDeMontagem = viewModelScope.launch {
            val montador = MontadorDeVideo(getApplication<Application>())
            var saidaAtual: File? = null
            val simplificados = mutableListOf<String>()
            try {
                for ((i, treino) in treinos.withIndex()) {
                    val nome = treino.treino.nome
                    val clipes = treino.clipes.map {
                        Clipe(
                            arquivo = repositorio.arquivoDeVideo(it.videoArquivo),
                            edicao = it.registro.edicao(),
                            titulo = it.exercicio.nome,
                            detalhe = legendaDoClipe(it.serie, it.registro),
                        )
                    }.filter { it.arquivo.isFile }
                    if (clipes.isEmpty()) continue

                    val saida = repositorio.videoDoTreino(planoId, treino.treino.id)
                    saidaAtual = saida
                    _montagem.value = EstadoDaMontagem.Montando(nome, i + 1, treinos.size, 0)
                    val simplificou = montador.montar(clipes, saida, _opcoes.value) { porcento ->
                        _montagem.value = EstadoDaMontagem.Montando(nome, i + 1, treinos.size, porcento)
                    }
                    if (simplificou) simplificados += nome
                    saidaAtual = null
                }
                _mensagem.value = if (simplificados.isEmpty()) {
                    "Vídeos prontos."
                } else {
                    "Pronto. ${simplificados.joinToString()} saiu sem câmera lenta, congelamento e " +
                        "transições: o aparelho não aguentou esses efeitos."
                }
            } catch (e: CancellationException) {
                saidaAtual?.delete()
                throw e
            } catch (e: Exception) {
                // Um vídeo pela metade não serve para enviar.
                saidaAtual?.delete()
                _mensagem.value = "Não consegui montar o vídeo: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _montagem.value = EstadoDaMontagem.Parada
                atualizarArquivosDoFeedback(planoId, treinoIds)
            }
        }
    }

    fun cancelarMontagem() {
        trabalhoDeMontagem?.cancel()
    }

    // ---------- opções da montagem (transição, legenda, som) ----------

    private val preferenciasDaMontagem =
        app.getSharedPreferences("montagem", android.content.Context.MODE_PRIVATE)
    private val pastaDaMusica = File(app.filesDir, "musica")

    private val _opcoes = MutableStateFlow(lerOpcoes())
    val opcoesDaMontagem: StateFlow<OpcoesDaMontagem> = _opcoes.asStateFlow()

    private fun lerOpcoes(): OpcoesDaMontagem {
        val p = preferenciasDaMontagem
        val musica = p.getString("musica", null)?.let { File(pastaDaMusica, it) }?.takeIf { it.isFile }
        return OpcoesDaMontagem(
            transicoes = p.getBoolean("transicoes", true),
            legendaAnimada = p.getBoolean("legendaAnimada", true),
            som = SomDoVideo.entries.getOrElse(p.getInt("som", 0)) { SomDoVideo.MUDO },
            musica = musica,
            nomeDaMusica = if (musica != null) p.getString("nomeDaMusica", null) else null,
            mudadasEm = p.getLong("mudadasEm", 0L),
        )
    }

    private fun gravarOpcoes(o: OpcoesDaMontagem) {
        val agora = System.currentTimeMillis()
        preferenciasDaMontagem.edit()
            .putBoolean("transicoes", o.transicoes)
            .putBoolean("legendaAnimada", o.legendaAnimada)
            .putInt("som", o.som.ordinal)
            .putString("musica", o.musica?.name)
            .putString("nomeDaMusica", o.nomeDaMusica)
            .putLong("mudadasEm", agora)
            .apply()
        _opcoes.value = o.copy(mudadasEm = agora)
    }

    /** Ex.: mudarOpcoes { it.copy(transicoes = false) } */
    fun mudarOpcoes(mudanca: (OpcoesDaMontagem) -> OpcoesDaMontagem) {
        gravarOpcoes(mudanca(_opcoes.value))
    }

    /** Copia a música escolhida para dentro do app e passa a usá-la como som do vídeo. */
    fun escolherMusica(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val (arquivo, nome) = withContext(Dispatchers.IO) {
                    val resolver = app.contentResolver
                    var nome: String? = null
                    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) nome = it.getString(0) }
                    val extensao = nome?.substringAfterLast('.', "")
                        ?.takeIf { e -> e.length in 2..4 && e.all { it.isLetterOrDigit() } } ?: "m4a"
                    pastaDaMusica.deleteRecursively()
                    pastaDaMusica.mkdirs()
                    val destino = File(pastaDaMusica, "musica.$extensao")
                    val entrada = resolver.openInputStream(uri) ?: throw IOException("música indisponível")
                    entrada.use { e -> destino.outputStream().use { e.copyTo(it) } }
                    destino to (nome ?: destino.name)
                }
                gravarOpcoes(_opcoes.value.copy(som = SomDoVideo.MUSICA, musica = arquivo, nomeDaMusica = nome))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensagem.value = "Não consegui usar essa música: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    // ---------- vídeo do físico (frente, lado, costas e perna) ----------

    private val _fisico = MutableStateFlow<File?>(null)
    /** O vídeo do físico desta semana, se já foi gravado. */
    val fisico: StateFlow<File?> = _fisico.asStateFlow()

    private fun arquivoDoFisico(planoId: Long) = File(repositorio.pastaDoFeedback(planoId), "fisico.mp4")

    /** Onde a câmera grava o vídeo novo; só substitui o antigo quando a gravação termina bem. */
    fun novoArquivoDoFisico(planoId: Long): File =
        File(repositorio.pastaDoFeedback(planoId).apply { mkdirs() }, "fisico_novo.mp4")

    fun fisicoGravado(planoId: Long, gravado: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val final = arquivoDoFisico(planoId)
                final.delete()
                if (!gravado.renameTo(final)) {
                    gravado.copyTo(final, overwrite = true)
                    gravado.delete()
                }
                _fisico.value = final.takeIf { it.isFile }
            }
        }
    }

    fun removerFisico(planoId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { arquivoDoFisico(planoId).delete() }
            _fisico.value = null
        }
    }

    // ---------- aba Câmera: fotos e vídeos livres ----------

    private val pastaDaCamera = File(app.filesDir, "camera")

    private val _capturas = MutableStateFlow<List<Captura>>(emptyList())
    /** As fotos e vídeos da aba Câmera, do mais novo para o mais velho. */
    val capturas: StateFlow<List<Captura>> = _capturas.asStateFlow()

    private val _processandoFoto = MutableStateFlow(false)
    /** Verdadeiro enquanto a última foto recebe o visual. */
    val processandoFoto: StateFlow<Boolean> = _processandoFoto.asStateFlow()

    fun atualizarCapturas() {
        viewModelScope.launch {
            _capturas.value = withContext(Dispatchers.IO) {
                capturasDaPasta(pastaDaCamera.listFiles()?.filter { it.isFile }.orEmpty())
            }
        }
    }

    /** Onde a câmera grava o próximo vídeo livre. */
    fun novoArquivoDeCaptura(marca: Marca): File =
        File(pastaDaCamera.apply { mkdirs() }, nomeDaCaptura(marca, System.currentTimeMillis(), foto = false))

    fun capturaGravada() = atualizarCapturas()

    /** Copia um arquivo escolhido no celular (galeria, câmera do celular) para dentro do app. */
    private fun copiarDoCelular(uri: Uri, destino: File): Boolean {
        destino.parentFile?.mkdirs()
        // Primeiro num temporário: se a cópia falhar no meio, não fica arquivo pela metade.
        val temporario = File(destino.parentFile, destino.name + ".tmp")
        return try {
            val entrada = getApplication<Application>().contentResolver.openInputStream(uri)
                ?: throw IOException("Arquivo indisponível")
            entrada.use { origem -> temporario.outputStream().use { origem.copyTo(it) } }
            if (!temporario.renameTo(destino)) temporario.copyTo(destino, overwrite = true)
            true
        } catch (e: Exception) {
            // Inclui SecurityException (o outro app tirou a permissão) e disco cheio.
            destino.delete()
            false
        } finally {
            // Um vídeo copiado pela metade tem centenas de MB: nunca fica para trás.
            temporario.delete()
        }
    }

    /**
     * Um vídeo feito com a câmera do celular, escolhido na galeria, vira o vídeo da série
     * como se tivesse sido gravado aqui. [aoImportar] recebe o arquivo, para abrir o corte.
     */
    fun importarVideoDaSerie(serieId: Long, uri: Uri, aoImportar: (File) -> Unit) {
        viewModelScope.launch {
            val destino = repositorio.novoArquivoDeVideo(serieId)
            val copiou = withContext(Dispatchers.IO) { copiarDoCelular(uri, destino) }
            if (!copiou) {
                _mensagem.value = "Não consegui trazer esse vídeo."
                return@launch
            }
            repositorio.salvarVideo(serieId, destino, null)
            aoImportar(destino)
        }
    }

    /** Foto ou vídeo da galeria do celular para a aba Câmera, com a marca escolhida. */
    fun importarCaptura(uri: Uri, marca: Marca) {
        viewModelScope.launch {
            val tipo = getApplication<Application>().contentResolver.getType(uri).orEmpty()
            val foto = tipo.startsWith("image")
            val destino = File(pastaDaCamera, nomeDaCaptura(marca, System.currentTimeMillis(), foto))
            val copiou = withContext(Dispatchers.IO) { copiarDoCelular(uri, destino) }
            _mensagem.value = if (copiou) {
                (if (foto) "Foto" else "Vídeo") + " adicionado em ${marca.rotulo}."
            } else {
                "Não consegui trazer esse arquivo."
            }
            atualizarCapturas()
        }
    }

    /** Enquanto um vídeo da aba Câmera está sendo cortado. */
    private val _cortandoCaptura = MutableStateFlow(false)
    val cortandoCaptura: StateFlow<Boolean> = _cortandoCaptura.asStateFlow()

    /**
     * Corta de verdade um vídeo da aba Câmera (aquecimento, backoff, livre): o arquivo
     * passa a ter só o trecho escolhido. O nome continua o mesmo, então ele fica no mesmo dia.
     */
    fun cortarCaptura(captura: Captura, inicioMs: Long?, fimMs: Long?) {
        if (inicioMs == null && fimMs == null) return
        viewModelScope.launch {
            _cortandoCaptura.value = true
            val app = getApplication<Application>()
            // Na mesma pasta, com outro nome: a galeria só lista os nomes do padrão.
            val temporario = File(captura.arquivo.parentFile, "corte_${System.nanoTime()}.mp4")
            try {
                MontadorDeVideo(app).recortar(captura.arquivo, inicioMs, fimMs, temporario)
                withContext(Dispatchers.IO) {
                    if (!temporario.isFile || temporario.length() == 0L) throw IOException("Corte vazio")
                    captura.arquivo.delete()
                    if (!temporario.renameTo(captura.arquivo)) temporario.copyTo(captura.arquivo, overwrite = true)
                }
                _mensagem.value = "Vídeo cortado."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensagem.value = "Não consegui cortar o vídeo: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { temporario.delete() }
                _cortandoCaptura.value = false
                atualizarCapturas()
            }
        }
    }

    /** Recebe a foto crua da câmera e aplica o mesmo visual escolhido na câmera. */
    fun fotoTirada(crua: File, marca: Marca) {
        viewModelScope.launch {
            _processandoFoto.value = true
            val app = getApplication<Application>()
            val guardado = app.getSharedPreferences("camera", android.content.Context.MODE_PRIVATE)
                .getInt("visual", NivelDoVisual.LEVE.ordinal)
            val nivel = NivelDoVisual.entries.getOrElse(guardado) { NivelDoVisual.LEVE }
            val destino = File(pastaDaCamera.apply { mkdirs() }, nomeDaCaptura(marca, System.currentTimeMillis(), foto = true))
            try {
                ProcessadorDeFoto.aplicarVisual(crua, destino, nivel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Sem memória ou foto estranha: guarda a foto sem o visual, para não perder.
                withContext(Dispatchers.IO) { runCatching { crua.copyTo(destino, overwrite = true) } }
                _mensagem.value = "A foto foi salva sem o visual."
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { crua.delete() }
                _processandoFoto.value = false
                atualizarCapturas()
            }
        }
    }

    /** Apaga de uma vez as fotos e vídeos marcados na aba Câmera. */
    fun apagarCapturas(lista: List<Captura>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { lista.forEach { it.arquivo.delete() } }
            atualizarCapturas()
            if (lista.isNotEmpty()) _mensagem.value = if (lista.size == 1) "1 item apagado." else "${lista.size} itens apagados."
        }
    }

    fun apagarCaptura(captura: Captura) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { captura.arquivo.delete() }
            atualizarCapturas()
        }
    }

    /** Salva uma foto da aba Câmera na galeria do celular, em Imagens/MeuPlano. */
    fun baixarFoto(chave: String, arquivo: File, nome: String) {
        if (chave in _baixando.value) return
        _baixando.value = _baixando.value + chave
        viewModelScope.launch {
            try {
                val nomeDaFoto = nomeNaGaleria(nome, LocalDateTime.now()).removeSuffix(".mp4") + ".jpg"
                val salvou = Galeria.salvarFoto(getApplication(), arquivo, nomeDaFoto)
                if (salvou) {
                    _baixados.value = _baixados.value + chave
                    _mensagem.value = "Foto salva na galeria, em Imagens/${Galeria.PASTA}."
                } else {
                    _mensagem.value = "Este Android não deixa o app salvar na galeria. Use o Enviar."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensagem.value = "Não consegui salvar a foto: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _baixando.value = _baixando.value - chave
            }
        }
    }

    // ---------- baixar vídeos para a galeria ----------

    private val _baixando = MutableStateFlow<Set<String>>(emptySet())
    /** Chaves dos vídeos sendo salvos na galeria agora. */
    val baixando: StateFlow<Set<String>> = _baixando.asStateFlow()

    private val _baixados = MutableStateFlow<Set<String>>(emptySet())
    /** Chaves dos vídeos já salvos na galeria (enquanto o app está aberto). */
    val baixados: StateFlow<Set<String>> = _baixados.asStateFlow()

    /**
     * Salva um vídeo na galeria, em Filmes/MeuPlano. Com corte, salva só o trecho cortado
     * (com o som original e sem legenda); sem corte, copia o arquivo como está.
     *
     * @param chave identifica o vídeo na tela, para mostrar "salvando" e "salvo"
     * @param nome base do nome do arquivo na galeria ("Agachamento low bar", "Treino A")
     */
    fun baixarVideo(chave: String, arquivo: File, inicioMs: Long?, fimMs: Long?, nome: String) {
        if (chave in _baixando.value) return
        _baixando.value = _baixando.value + chave
        viewModelScope.launch {
            val app = getApplication<Application>()
            val recorte = File(app.cacheDir, "recorte_${System.nanoTime()}.mp4")
            try {
                val origem = if (inicioMs != null || fimMs != null) {
                    MontadorDeVideo(app).recortar(arquivo, inicioMs, fimMs, recorte)
                    recorte
                } else {
                    arquivo
                }
                val salvou = Galeria.salvarVideo(app, origem, nomeNaGaleria(nome, LocalDateTime.now()))
                if (salvou) {
                    _baixados.value = _baixados.value + chave
                    _mensagem.value = "Vídeo salvo na galeria, em Filmes/${Galeria.PASTA}."
                } else {
                    _mensagem.value = "Este Android não deixa o app salvar na galeria. Use o Enviar."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mensagem.value = "Não consegui salvar o vídeo: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { recorte.delete() }
                _baixando.value = _baixando.value - chave
            }
        }
    }

    /** Endereço de um arquivo do app (foto ou vídeo) que outro app, como o WhatsApp, consegue abrir. */
    fun uriParaCompartilhar(arquivo: File): Uri {
        val app = getApplication<Application>()
        return FileProvider.getUriForFile(app, "${app.packageName}.arquivos", arquivo)
    }

    private fun resumoDaImportacao(plano: Plano): String {
        val series = plano.treinos.flatMap { it.exercicios }.flatMap { it.series }
        val exercicios = plano.treinos.sumOf { it.exercicios.size }
        val semana = plano.semana?.let { "Semana $it importada" } ?: "Plano importado"
        val foraDoPadrao = plano.avisos.size
        return buildString {
            append("$semana: $exercicios exercícios, ${series.count { it.gravar }} gravações.")
            if (foraDoPadrao > 0) append(" $foraDoPadrao trecho(s) fora do padrão, confira o treino.")
        }
    }

    private fun lerArquivo(uri: Uri): ByteArray {
        val entrada = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw IOException("Arquivo indisponível")
        return entrada.use { stream ->
            val saida = ByteArrayOutputStream()
            val bloco = ByteArray(16 * 1024)
            while (true) {
                val lidos = stream.read(bloco)
                if (lidos < 0) break
                saida.write(bloco, 0, lidos)
                if (saida.size() > TAMANHO_MAXIMO) throw ArquivoGrandeDemais()
            }
            saida.toByteArray()
        }
    }

    private class ArquivoGrandeDemais : IOException()

    private companion object {
        /** O plano tem uns 20 KB. 5 MB é folga de sobra e evita abrir um arquivo errado enorme. */
        const val TAMANHO_MAXIMO = 5 * 1024 * 1024
    }
}
