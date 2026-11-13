package com.luiz.meuplano.ui

import android.Manifest
import java.util.concurrent.TimeUnit
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.camera.core.FocusMeteringAction
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.Camera2CameraControl
import android.hardware.camera2.CaptureRequest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.media3.effect.Media3Effect
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SingleColorLut
import com.luiz.meuplano.R
import com.luiz.meuplano.video.NivelDoVisual
import com.luiz.meuplano.video.cuboDoVisual
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 1080p a 16 Mbps: a qualidade de uma boa câmera de celular. Menos que isso borra o
 * movimento rápido da execução. O vídeo final do treino é montado depois, à parte.
 */
private const val BITRATE_DO_VIDEO = 16_000_000

/** Contagem antes de começar a gravar, em segundos. O vídeo em si não tem limite de duração. */
private val OPCOES_DE_TIMER = listOf(0, 3, 5, 10)
/** O vermelho de gravar do design. */
private val VERMELHO = Cores.Gravar
/** O fundo escuro translúcido dos controles que ficam por cima da imagem. */
private val VIDRO = Color(0xB3141416)

/** Onde ficam guardadas as escolhas da câmera (visual e iluminação), de uma gravação para a outra. */
private const val PREFERENCIAS_DA_CAMERA = "camera"
/** Nível do visual (NivelDoVisual). A chave antiga, "filtro", era do filtro anterior e fica sem uso. */
private const val CHAVE_VISUAL = "visual"
private const val CHAVE_ILUMINACAO = "iluminacao"
private const val CHAVE_FLASH = "flash"
private const val CHAVE_FAIXA = "faixa"
private const val CHAVE_GRADE = "grade"

/**
 * O visual da câmera como efeito do Media3: uma tabela de cores (ver VisualDaCamera.kt).
 * Ele é aplicado na hora, então o que aparece na tela é o que fica gravado.
 */
@OptIn(UnstableApi::class)
private fun efeitosDoVisual(nivel: NivelDoVisual): List<Effect> =
    listOf(SingleColorLut.createFromCube(cuboDoVisual(nivel.forca)))

/**
 * Câmera do app.
 *
 * A música que está tocando não para: quem pausa a música na câmera do Android é o
 * pedido de "foco de áudio", e esta tela nunca faz esse pedido. O microfone só é usado
 * para o som do vídeo, e dá para desligar.
 *
 * Dois ajustes de imagem ficam guardados entre as gravações: o filtro (ligado ou desligado)
 * e a iluminação, que clareia ou escurece a imagem conforme o lugar. Eles ficam no painel
 * que abre pelo botão do raio, junto com a chave do som.
 *
 * @param titulo nome do exercício, mostrado no alto da tela
 * @param detalhe a carga da série ("90 kg") ou, sem carga, as séries e repetições
 * @param rpe o RPE da série ("RPE 7"), em destaque ao lado da carga, ou nulo se não houver
 * @param criarArquivo devolve o arquivo onde o próximo vídeo será gravado
 * @param onGravado chamado quando o vídeo foi salvo com sucesso; o segundo valor é onde o
 *   corte deve começar (o fim da contagem), quando a gravação começou durante a contagem
 * @param gravarDuranteAContagem a câmera do celular demora um pouco para começar a gravar.
 *   Com isso ligado, a gravação começa já no início da contagem e o vídeo é cortado
 *   sozinho no fim dela, então ela começa na hora certa. Só para vídeo que tem corte (séries).
 * @param onCarga abre o registro de carga e RPE da série, por cima da câmera; nulo esconde o botão
 * @param onFotografado com ele, a câmera ganha o modo FOTO; recebe a foto crua (o visual é
 *   aplicado depois, em quem recebe)
 * @param dica texto fixo embaixo, no lugar das dicas de uso (ex.: o roteiro do vídeo do físico)
 * @param revisar mostra Salvar / Refazer antes de entregar a foto ou o vídeo
 * @param ultimaCaptura miniatura no canto esquerdo (quando não há o botão de carga)
 */
@SuppressLint("MissingPermission") // withAudioEnabled só é chamado depois de conferir temMicrofone
@OptIn(UnstableApi::class)
@Composable
fun TelaCamera(
    titulo: String,
    detalhe: String,
    rpe: String?,
    criarArquivo: () -> File,
    onGravado: (File, Long?) -> Unit,
    onCarga: (() -> Unit)?,
    onFechar: () -> Unit,
    onFotografado: ((File) -> Unit)? = null,
    dica: String? = null,
    gravarDuranteAContagem: Boolean = false,
    revisar: Boolean = false,
    ultimaCaptura: File? = null,
    onAbrirUltima: (() -> Unit)? = null,
) {
    val contexto = LocalContext.current
    val donoDoCiclo = LocalLifecycleOwner.current
    val escopo = rememberCoroutineScope()
    val aoGravar by rememberUpdatedState(onGravado)
    val aoFotografar by rememberUpdatedState(onFotografado)
    val temModoFoto = onFotografado != null

    // ---------- permissões ----------
    var temCamera by remember { mutableStateOf(temPermissao(contexto, Manifest.permission.CAMERA)) }
    var temMicrofone by remember { mutableStateOf(temPermissao(contexto, Manifest.permission.RECORD_AUDIO)) }
    val pedirPermissoes = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resposta ->
        temCamera = resposta[Manifest.permission.CAMERA] ?: temCamera
        temMicrofone = resposta[Manifest.permission.RECORD_AUDIO] ?: temMicrofone
    }
    val permissoes = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    LaunchedEffect(Unit) {
        if (!temCamera || !temMicrofone) pedirPermissoes.launch(permissoes)
    }

    // ---------- estado ----------
    var frontal by rememberSaveable { mutableStateOf(false) }
    var comSom by rememberSaveable { mutableStateOf(true) }
    var timer by rememberSaveable { mutableIntStateOf(5) }
    var contagem by remember { mutableIntStateOf(0) }
    var contando by remember { mutableStateOf<Job?>(null) }
    var captura by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    // Modo foto: liga a captura de foto no lugar da de vídeo.
    var modoFoto by rememberSaveable { mutableStateOf(false) }
    var fotografia by remember { mutableStateOf<ImageCapture?>(null) }
    var fotografando by remember { mutableStateOf(false) }
    var gravacao by remember { mutableStateOf<Recording?>(null) }
    var segundos by remember { mutableLongStateOf(0L) }
    // A gravação começou de verdade (o aparelho mandou o primeiro quadro), em elapsedRealtime.
    var comecouEm by remember { mutableStateOf<Long?>(null) }
    // Quanto do começo do arquivo é a contagem, para cortar depois. Zero sem gravação na contagem.
    var cortarAteMs by remember { mutableLongStateOf(0L) }
    // Gravação da contagem cancelada: o arquivo é jogado fora.
    var descartar by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    // ---------- imagem: filtro e iluminação ----------
    val preferencias = remember { contexto.getSharedPreferences(PREFERENCIAS_DA_CAMERA, Context.MODE_PRIVATE) }
    var visual by remember {
        val guardado = preferencias.getInt(CHAVE_VISUAL, NivelDoVisual.LEVE.ordinal)
        mutableStateOf(NivelDoVisual.entries.getOrElse(guardado) { NivelDoVisual.LEVE })
    }
    val comFiltro = visual != NivelDoVisual.DESLIGADO
    // A iluminação é a "compensação de exposição" da câmera, em passos. Zero é o automático.
    var iluminacao by remember { mutableIntStateOf(preferencias.getInt(CHAVE_ILUMINACAO, 0)) }
    // Até onde esta câmera deixa ir, ou nulo se ela não tem o ajuste.
    var faixaDeIluminacao by remember { mutableStateOf<IntRange?>(null) }
    // Quanto vale cada passo, em EV (cada 1 EV dobra ou corta pela metade a luz).
    var passoDeIluminacao by remember { mutableFloatStateOf(0f) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flash by remember {
        mutableStateOf(ModoDoFlash.entries.getOrElse(preferencias.getInt(CHAVE_FLASH, 0)) { ModoDoFlash.DESLIGADO })
    }
    var comFaixa by remember { mutableStateOf(preferencias.getBoolean(CHAVE_FAIXA, true)) }
    var comGrade by remember { mutableStateOf(preferencias.getBoolean(CHAVE_GRADE, true)) }
    var revisando by remember { mutableStateOf<Revisao?>(null) }
    // Clarão branco rápido quando a foto sai.
    var clarao by remember { mutableStateOf(false) }


    // Flash na foto e lanterna no vídeo, sempre que a câmera religa ou o modo muda.
    LaunchedEffect(camera, fotografia, flash, modoFoto) {
        fotografia?.flashMode = when (flash) {
            ModoDoFlash.DESLIGADO -> ImageCapture.FLASH_MODE_OFF
            ModoDoFlash.AUTO -> ImageCapture.FLASH_MODE_AUTO
            ModoDoFlash.LIGADO -> ImageCapture.FLASH_MODE_ON
        }
        val ligada = camera ?: return@LaunchedEffect
        if (ligada.cameraInfo.hasFlashUnit()) {
            runCatching { ligada.cameraControl.enableTorch(!modoFoto && flash == ModoDoFlash.LIGADO) }
        }
    }

    val visor = remember {
        PreviewView(contexto).apply {
            // A imagem ocupa a tela toda. Em telas mais compridas que 16:9 sobra um pouco
            // nas laterais: tudo o que aparece é gravado, e o vídeo pega um pouco mais.
            scaleType = PreviewView.ScaleType.FILL_CENTER
            keepScreenOn = true
        }
    }

    // ---------- foco e luz no toque, como na câmera do celular ----------
    // Onde o quadrado de foco está (em pixels do visor), ou nulo se escondido.
    var foco by remember { mutableStateOf<Offset?>(null) }
    // Foco e exposição travados (segurar o dedo, ou o cadeado ao lado do quadrado).
    var focoTravado by remember { mutableStateOf(false) }
    // Muda a cada toque ou ajuste: o quadrado some 3 s depois do último (se não estiver travado).
    var mexeuNoFoco by remember { mutableIntStateOf(0) }
    var tamanhoDoVisor by remember { mutableStateOf(IntSize.Zero) }
    val vibrar = LocalHapticFeedback.current

    /** Foca e mede a luz no ponto tocado. Com [travar], segura foco e exposição ali. */
    fun focarEm(ponto: Offset, travar: Boolean) {
        val ligada = camera ?: return
        travarExposicao(ligada, false)
        val alvo = visor.meteringPointFactory.createPoint(ponto.x, ponto.y)
        val acao = FocusMeteringAction.Builder(
            alvo,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB,
        ).apply { if (travar) disableAutoCancel() else setAutoCancelDuration(5, TimeUnit.SECONDS) }.build()
        foco = ponto
        focoTravado = travar
        mexeuNoFoco++
        runCatching {
            val medindo = ligada.cameraControl.startFocusAndMetering(acao)
            // Trava a exposição só depois de medir a luz no ponto novo.
            if (travar) {
                medindo.addListener(
                    { if (focoTravado && camera === ligada) travarExposicao(ligada, true) },
                    ContextCompat.getMainExecutor(contexto),
                )
            }
        }
    }

    /** O cadeado ao lado do quadrado: trava onde está, ou destrava. */
    fun alternarTrava() {
        val ponto = foco ?: return
        if (focoTravado) {
            camera?.let { travarExposicao(it, false) }
            focarEm(ponto, travar = false)
        } else {
            vibrar.performHapticFeedback(HapticFeedbackType.LongPress)
            focarEm(ponto, travar = true)
        }
    }

    fun mudarIluminacao(indice: Int) {
        if (indice == iluminacao) return
        iluminacao = indice
        camera?.cameraControl?.setExposureCompensationIndex(indice)
    }

    // Lente nova (virou a câmera, mudou o filtro): o foco antigo não vale mais.
    LaunchedEffect(camera) {
        foco = null
        focoTravado = false
    }
    LaunchedEffect(mexeuNoFoco, focoTravado) {
        if (foco == null || focoTravado) return@LaunchedEffect
        delay(3_000)
        foco = null
    }

    // ---------- liga a câmera (de novo a cada troca de lente ou do filtro) ----------
    DisposableEffect(temCamera, frontal, visual, modoFoto) {
        if (!temCamera) return@DisposableEffect onDispose { }
        var ativo = true
        var efeito: Media3Effect? = null
        val naPrincipal = ContextCompat.getMainExecutor(contexto)

        // O filtro não funcionou neste aparelho: desliga, e a câmera religa sozinha sem ele.
        fun desligarFiltro() {
            preferencias.edit().putInt(CHAVE_VISUAL, NivelDoVisual.DESLIGADO.ordinal).apply()
            visual = NivelDoVisual.DESLIGADO
            erro = "O filtro não funcionou neste aparelho e foi desligado."
        }

        val futuro = ProcessCameraProvider.getInstance(contexto)
        futuro.addListener(
            {
                if (ativo) {
                    try {
                        val provedor = futuro.get()
                        // Mesmo formato do vídeo (16:9), para a tela mostrar o enquadramento certo.
                        val preview = Preview.Builder()
                            .setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                                    .build(),
                            )
                            .build()
                        preview.setSurfaceProvider(visor.surfaceProvider)
                        val gravador = Recorder.Builder()
                            .setQualitySelector(
                                QualitySelector.from(
                                    Quality.FHD,
                                    FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD),
                                ),
                            )
                            .setTargetVideoEncodingBitRate(BITRATE_DO_VIDEO)
                            .build()
                        val novaCaptura = VideoCapture.withOutput(gravador)
                        val novaFoto = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()
                        val lente = if (frontal) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        val grupo = UseCaseGroup.Builder().addUseCase(preview)
                            .addUseCase(if (modoFoto) novaFoto else novaCaptura)
                        if (comFiltro) {
                            // O mesmo filtro na tela e no vídeo, para gravar exatamente o que se vê.
                            // Na foto o filtro vai só na tela; a foto recebe o mesmo visual depois de tirada.
                            val novoEfeito = Media3Effect(
                                contexto,
                                if (modoFoto) CameraEffect.PREVIEW else CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
                                naPrincipal,
                                Consumer<Throwable> { if (ativo) desligarFiltro() },
                            )
                            novoEfeito.setEffects(efeitosDoVisual(visual))
                            efeito = novoEfeito
                            grupo.addEffect(novoEfeito)
                        }

                        provedor.unbindAll()
                        val ligada = provedor.bindToLifecycle(donoDoCiclo, lente, grupo.build())
                        captura = if (modoFoto) null else novaCaptura
                        fotografia = if (modoFoto) novaFoto else null
                        camera = ligada
                        erro = null

                        // Reaplica a iluminação guardada, dentro do que esta lente aceita.
                        val exposicao = ligada.cameraInfo.exposureState
                        if (exposicao.isExposureCompensationSupported) {
                            val limites = exposicao.exposureCompensationRange
                            faixaDeIluminacao = limites.lower..limites.upper
                            passoDeIluminacao = exposicao.exposureCompensationStep.toFloat()
                            val indice = iluminacao.coerceIn(limites.lower, limites.upper)
                            iluminacao = indice
                            ligada.cameraControl.setExposureCompensationIndex(indice)
                        } else {
                            faixaDeIluminacao = null
                        }
                    } catch (e: Exception) {
                        captura = null
                        fotografia = null
                        camera = null
                        if (comFiltro) desligarFiltro() else erro = "Não consegui abrir a câmera."
                    }
                }
            },
            naPrincipal,
        )
        onDispose {
            ativo = false
            captura = null
            fotografia = null
            camera = null
            runCatching { if (futuro.isDone) futuro.get().unbindAll() }
            runCatching { efeito?.close() }
        }
    }

    // ---------- celular deitado ou em pé: o vídeo sai na orientação certa ----------
    DisposableEffect(captura, fotografia) {
        val video = captura
        val foto = fotografia
        if (video == null && foto == null) return@DisposableEffect onDispose { }
        val sensor = object : OrientationEventListener(contexto) {
            override fun onOrientationChanged(graus: Int) {
                if (graus == OrientationEventListener.ORIENTATION_UNKNOWN) return
                val rotacao = when (graus) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                video?.targetRotation = rotacao
                foto?.targetRotation = rotacao
            }
        }
        sensor.enable()
        onDispose { sensor.disable() }
    }

    // ---------- ao sair da tela ----------
    DisposableEffect(Unit) {
        onDispose {
            // Saiu no meio da contagem: o que gravou até ali não vale.
            if (contando != null) descartar = true
            contando?.cancel()
            gravacao?.stop()
        }
    }

    fun iniciarGravacao() {
        val alvo = captura ?: return
        val arquivo = criarArquivo()
        var pendente = alvo.output.prepareRecording(contexto, FileOutputOptions.Builder(arquivo).build())
        if (comSom && temMicrofone) pendente = pendente.withAudioEnabled()
        segundos = 0L
        comecouEm = null
        cortarAteMs = 0L
        descartar = false
        erro = null
        gravacao = pendente.start(ContextCompat.getMainExecutor(contexto)) { evento ->
            when (evento) {
                is VideoRecordEvent.Start -> {
                    comecouEm = SystemClock.elapsedRealtime()
                }

                is VideoRecordEvent.Status -> {
                    val gravadoMs = evento.recordingStats.recordedDurationNanos / 1_000_000L
                    // O cronômetro conta só depois da contagem.
                    segundos = ((gravadoMs - cortarAteMs) / 1000L).coerceAtLeast(0L)
                }

                is VideoRecordEvent.Finalize -> {
                    gravacao = null
                    comecouEm = null
                    // SOURCE_INACTIVE: o app foi para segundo plano no meio. O que gravou até ali vale.
                    val aproveitavel = !evento.hasError() ||
                        evento.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE
                    if (descartar) {
                        descartar = false
                        arquivo.delete()
                    } else if (aproveitavel && arquivo.length() > 0L) {
                        val corte = cortarAteMs.takeIf { it > 0L }
                        if (revisar) {
                            revisando = Revisao(arquivo, foto = false, corte = corte, segundos = segundos)
                        } else {
                            aoGravar(arquivo, corte)
                        }
                    } else {
                        arquivo.delete()
                        erro = "A gravação falhou. Tente de novo."
                    }
                }

                else -> Unit
            }
        }
    }

    /** Tira a foto e entrega o arquivo. */
    fun tirarFoto() {
        val alvo = fotografia ?: return
        val arquivo = File(contexto.cacheDir, "foto_${System.nanoTime()}.jpg")
        fotografando = true
        clarao = true
        erro = null
        alvo.takePicture(
            ImageCapture.OutputFileOptions.Builder(arquivo).build(),
            ContextCompat.getMainExecutor(contexto),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                    fotografando = false
                    if (revisar) {
                        revisando = Revisao(arquivo, foto = true, corte = null, segundos = 0L)
                    } else {
                        aoFotografar?.invoke(arquivo)
                    }
                }

                override fun onError(excecao: ImageCaptureException) {
                    fotografando = false
                    arquivo.delete()
                    erro = "Não consegui tirar a foto. Tente de novo."
                }
            },
        )
    }

    /** O que o botão faz depois da contagem: foto ou começar a gravar. */
    fun disparar() {
        if (modoFoto) tirarFoto() else iniciarGravacao()
    }

    fun botaoPrincipal() {
        val emContagem = contando
        when {
            emContagem != null -> {
                emContagem.cancel()
                contando = null
                contagem = 0
                // A gravação que já tinha começado na contagem é jogada fora.
                if (gravacao != null) {
                    descartar = true
                    gravacao?.stop()
                }
            }
            gravacao != null -> {
                gravacao?.stop()
            }
            timer == 0 -> {
                disparar()
            }
            else -> {
                val adiantar = gravarDuranteAContagem && !modoFoto
                if (adiantar) iniciarGravacao()
                contando = escopo.launch {
                    for (n in timer downTo 1) {
                        contagem = n
                        delay(1_000)
                    }
                    contagem = 0
                    if (adiantar && gravacao != null) {
                        // O vídeo de verdade começa aqui: o que veio antes é a contagem.
                        val inicio = comecouEm
                        cortarAteMs = if (inicio != null) SystemClock.elapsedRealtime() - inicio else 0L
                        segundos = 0L
                        contando = null
                    } else {
                        contando = null
                        disparar()
                    }
                }
            }
        }
    }

    // Gravando para valer: a gravação feita durante a contagem ainda não conta.
    val gravando = gravacao != null && contando == null
    // O aparelho ainda está preparando a gravação: o cronômetro espera.
    val iniciando = gravando && comecouEm == null
    val livre = !gravando && contando == null && !fotografando
    val pronta = if (modoFoto) fotografia != null else captura != null

    // Voltar durante a gravação só encerra o vídeo (e ele é salvo); na revisão, salva.
    BackHandler {
        val revisao = revisando
        when {
            revisao != null -> {
                revisando = null
                if (revisao.foto) aoFotografar?.invoke(revisao.arquivo) else aoGravar(revisao.arquivo, revisao.corte)
            }
            gravando -> gravacao?.stop()
            contando != null -> botaoPrincipal()
            else -> onFechar()
        }
    }

    var ajustesAbertos by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(clarao) {
        if (clarao) {
            delay(350)
            clarao = false
        }
    }

    val emContagem = contando != null
    // A foto ou o vídeo que acabou de sair, esperando Salvar ou Refazer.
    val emRevisao = revisando != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (temCamera) {
            AndroidView(factory = { visor }, modifier = Modifier.fillMaxSize())
            if (comGrade && !gravando) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .gradeDeTercos(),
                )
            }
            // Tocar foca e mede a luz ali; segurar trava foco e exposição.
            if (camera != null && revisando == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { tamanhoDoVisor = it }
                        .pointerInput(camera) {
                            detectTapGestures(
                                onTap = { focarEm(it, travar = false) },
                                onLongPress = {
                                    vibrar.performHapticFeedback(HapticFeedbackType.LongPress)
                                    focarEm(it, travar = true)
                                },
                            )
                        },
                )
            }
            val ponto = foco
            if (ponto != null && revisando == null) {
                MiraDeFoco(
                    ponto = ponto,
                    limite = tamanhoDoVisor,
                    travado = focoTravado,
                    iluminacao = iluminacao,
                    faixa = faixaDeIluminacao,
                    textoDaLuz = textoDaIluminacao(iluminacao * passoDeIluminacao),
                    onIluminacao = { indice ->
                        mudarIluminacao(indice)
                        mexeuNoFoco++
                    },
                    onSoltar = {
                        preferencias.edit().putInt(CHAVE_ILUMINACAO, iluminacao).apply()
                        mexeuNoFoco++
                    },
                    onTravar = ::alternarTrava,
                )
            }
            // Travado: o aviso no alto, como na câmera do celular. Tocar destrava.
            if (focoTravado && revisando == null) {
                Text(
                    text = "TRAVA AE/AF",
                    style = Tipos.Botao.copy(fontSize = 12.sp, letterSpacing = 0.08.em),
                    color = Color.Black,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .systemBarsPadding()
                        .padding(top = 64.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AMARELO_DO_FOCO)
                        .clickable(onClickLabel = "Destravar foco e exposição") { alternarTrava() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        // Gravando: borda vermelha em volta da tela.
        if (gravando) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, VERMELHO_DO_DESIGN),
            )
        }

        // Escurece o topo e a base para os controles ficarem legíveis sobre a imagem.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(170.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
        )

        // ----- topo: fechar, cronômetro, contagem e configurações -----
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            BotaoRedondo(
                icone = R.drawable.ic_fechar,
                descricao = "Fechar a câmera",
                onClick = onFechar,
                modifier = Modifier.align(Alignment.CenterStart),
                ativo = !gravando,
            )
            if (!modoFoto && !emContagem) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(if (gravando && !iniciando) VERMELHO_DO_DESIGN else VIDRO)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (gravando && !iniciando) PontoPiscando()
                    Text(
                        text = if (iniciando) "INICIANDO…" else tempo(segundos),
                        style = Tipos.NumeroMedio.copy(fontSize = 20.sp),
                        color = Color.White,
                    )
                }
            }
            if (livre) {
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // A contagem: cada toque passa para a próxima (Off, 3s, 5s, 10s).
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .widthIn(min = 44.dp)
                            .clip(CircleShape)
                            .background(if (timer > 0) Color.White else VIDRO)
                            .clickable(
                                onClickLabel = if (timer > 0) "Iniciar em ${timer}s, tocar para mudar" else "Contagem desligada, tocar para mudar",
                            ) {
                                val proximo = (OPCOES_DE_TIMER.indexOf(timer) + 1) % OPCOES_DE_TIMER.size
                                timer = OPCOES_DE_TIMER[proximo]
                            }
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val cor = if (timer > 0) Cores.Fundo else Color.White
                        Icon(painterResource(R.drawable.ic_timer), contentDescription = null, tint = cor, modifier = Modifier.size(18.dp))
                        if (timer > 0) Text("${timer}s", style = Tipos.Botao, color = cor)
                    }
                    BotaoRedondo(
                        icone = R.drawable.ic_ajustes,
                        descricao = "Configurações da câmera",
                        onClick = { ajustesAbertos = true },
                    )
                }
            }
        }

        // ----- a série que está sendo gravada, numa linha só -----
        if (comFaixa && !emRevisao) {
            Text(
                text = buildAnnotatedString {
                    append(titulo.uppercase())
                    if (detalhe.isNotBlank()) append(" · $detalhe")
                    if (rpe != null) {
                        append(" · ")
                        withStyle(SpanStyle(color = Cores.Destaque)) { append(rpe.uppercase()) }
                    }
                },
                style = Tipos.NumeroMedio.copy(fontSize = 17.sp, shadow = SOMBRA_DO_TEXTO),
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = 62.dp, start = 16.dp, end = 16.dp),
            )
        }

        // ----- sem permissão -----
        if (!temCamera) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            ) {
                Text(
                    text = "O app precisa da câmera para gravar.",
                    style = Tipos.CorpoForte,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                BotaoPrincipal("Permitir", onClick = { pedirPermissoes.launch(permissoes) })
                Text(
                    text = "Se o Android não perguntar de novo, libere em " +
                        "Configurações → Apps → Meu Plano → Permissões.",
                    style = Tipos.CorpoPequeno,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ----- contagem: tela escurecida, número grande e Cancelar -----
        if (emContagem) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                if (contagem > 0) {
                    Text(
                        text = contagem.toString(),
                        style = TextStyle(
                            fontFamily = BarlowCondensed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 200.sp,
                            lineHeight = 170.sp,
                            shadow = SOMBRA_DO_TEXTO,
                        ),
                        color = Color.White,
                    )
                }
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable { botaoPrincipal() }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancelar", style = Tipos.Botao, color = Cores.Texto)
                }
            }
        }

        // ----- base: modo, atalho da esquerda, botão de disparo e virar a câmera -----
        if (!emContagem && !emRevisao) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(start = 28.dp, end = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                erro?.let {
                    Text(text = it, style = Tipos.CorpoPequeno, color = Cores.NovoTexto, textAlign = TextAlign.Center)
                }
                val aviso = when {
                    fotografando -> "Salvando a foto…"
                    livre && dica != null -> dica
                    else -> null
                }
                if (aviso != null) {
                    Text(
                        text = aviso,
                        style = Tipos.CorpoPequeno.copy(shadow = SOMBRA_DO_TEXTO),
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                    )
                }
                if (livre && temModoFoto) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        listOf(false to "VÍDEO", true to "FOTO").forEach { (foto, rotulo) ->
                            Text(
                                text = rotulo,
                                style = Tipos.Rotulo.copy(fontSize = 13.sp, letterSpacing = 0.14.em, shadow = SOMBRA_DO_TEXTO),
                                color = if (foto == modoFoto) Cores.Destaque else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { modoFoto = foto }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Esquerda: carga e RPE da série, ou a última captura da galeria.
                    if (livre) {
                        Box(modifier = Modifier.align(Alignment.CenterStart)) {
                            when {
                                onCarga != null -> Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                        .clickable(onClickLabel = "Carga e RPE da série", onClick = onCarga),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_carga),
                                        contentDescription = "Carga e RPE",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                ultimaCaptura != null -> MiniaturaDaUltima(ultimaCaptura, onAbrirUltima)
                                else -> Unit
                            }
                        }
                    }

                    // Disparo: anel branco; vídeo = círculo vermelho, foto = círculo branco,
                    // gravando = quadrado vermelho.
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color.White.copy(alpha = if (pronta) 1f else 0.4f), CircleShape)
                            .clickable(
                                enabled = pronta && !fotografando,
                                onClickLabel = when {
                                    gravando -> "Parar a gravação"
                                    modoFoto -> "Tirar foto"
                                    else -> "Gravar"
                                },
                                onClick = { botaoPrincipal() },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (gravando) 28.dp else 64.dp)
                                .clip(if (gravando) RoundedCornerShape(7.dp) else CircleShape)
                                .background(
                                    (if (modoFoto && !gravando) Color.White else VERMELHO_DO_DESIGN)
                                        .copy(alpha = if (pronta) 1f else 0.4f),
                                ),
                        )
                    }

                    BotaoRedondo(
                        icone = R.drawable.ic_virar,
                        descricao = if (frontal) "Usar a câmera traseira" else "Usar a câmera frontal",
                        onClick = { frontal = !frontal },
                        modifier = Modifier.align(Alignment.CenterEnd),
                        ativo = livre,
                        tamanho = 48.dp,
                    )
                }
            }
        }

        if (clarao) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f)),
            )
        }

        // ----- configurações: a folha que sobe de baixo -----
        if (ajustesAbertos) {
            FolhaDeBaixo(onFechar = { ajustesAbertos = false }) {
                Text("CONFIGURAÇÕES", style = Tipos.TituloDeCartao.copy(fontWeight = FontWeight.ExtraBold), color = Cores.Texto)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Rotulo("Iniciar em")
                    Segmentos(
                        opcoes = OPCOES_DE_TIMER.map { if (it == 0) "Off" else "${it}s" },
                        selecionada = OPCOES_DE_TIMER.indexOf(timer).takeIf { it >= 0 },
                        onSelecionar = { timer = OPCOES_DE_TIMER[it] },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Rotulo("Flash")
                    Segmentos(
                        opcoes = ModoDoFlash.entries.map { it.rotulo },
                        selecionada = flash.ordinal,
                        onSelecionar = { i ->
                            flash = ModoDoFlash.entries[i]
                            preferencias.edit().putInt(CHAVE_FLASH, i).apply()
                        },
                    )
                    Text(
                        text = "No vídeo, Ligado acende a lanterna; Auto vale só para foto.",
                        style = Tipos.CorpoPequeno,
                        color = Cores.TextoApagado,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Rotulo("Visual")
                    Segmentos(
                        opcoes = NivelDoVisual.entries.map { it.rotulo },
                        selecionada = visual.ordinal,
                        onSelecionar = { i ->
                            val novo = NivelDoVisual.entries[i]
                            preferencias.edit().putInt(CHAVE_VISUAL, novo.ordinal).apply()
                            visual = novo
                        },
                    )
                }
                val limites = faixaDeIluminacao
                if (limites != null && limites.first < limites.last) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Iluminação", style = Tipos.CorpoForte, color = Cores.Texto)
                        Slider(
                            value = iluminacao.toFloat(),
                            onValueChange = { posicao ->
                                val indice = posicao.roundToInt().coerceIn(limites.first, limites.last)
                                if (indice != iluminacao) {
                                    iluminacao = indice
                                    camera?.cameraControl?.setExposureCompensationIndex(indice)
                                }
                            },
                            onValueChangeFinished = {
                                preferencias.edit().putInt(CHAVE_ILUMINACAO, iluminacao).apply()
                            },
                            valueRange = limites.first.toFloat()..limites.last.toFloat(),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Cores.Destaque,
                                activeTrackColor = Cores.Destaque,
                                inactiveTrackColor = Cores.BordaForte,
                            ),
                        )
                        Text(
                            text = textoDaIluminacao(iluminacao * passoDeIluminacao),
                            style = Tipos.CorpoForte,
                            color = Cores.Texto,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(38.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Cores.Superficie)
                        .padding(horizontal = 14.dp),
                ) {
                    LinhaDeAjuste(
                        texto = "Faixa da série",
                        detalhe = "Exercício, carga e RPE na tela",
                        ligado = comFaixa,
                        onMudar = { v ->
                            comFaixa = v
                            preferencias.edit().putBoolean(CHAVE_FAIXA, v).apply()
                        },
                    )
                    Divisor()
                    LinhaDeAjuste(
                        texto = "Grade",
                        detalhe = "Linhas para alinhar a barra",
                        ligado = comGrade,
                        onMudar = { v ->
                            comGrade = v
                            preferencias.edit().putBoolean(CHAVE_GRADE, v).apply()
                        },
                    )
                    Divisor()
                    LinhaDeAjuste(
                        texto = "Som no vídeo",
                        detalhe = if (temMicrofone) "Grava o áudio junto" else "Sem permissão do microfone",
                        ligado = comSom && temMicrofone,
                        ativo = temMicrofone,
                        onMudar = { comSom = it },
                    )
                }
                BotaoPrincipal("PRONTO", onClick = { ajustesAbertos = false }, modifier = Modifier.fillMaxWidth(), altura = 50.dp)
            }
        }

        // ----- revisão: Salvar ou Refazer o que acabou de sair -----
        val revisao = revisando
        if (revisao != null) {
            FolhaDeBaixo(onFechar = null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Rotulo(if (revisao.foto) "Foto tirada" else "Clipe gravado", cor = Cores.Destaque)
                        Text(
                            text = titulo.uppercase(),
                            style = Tipos.TituloDeTela.copy(fontSize = 30.sp, lineHeight = 30.sp),
                            color = Cores.Texto,
                            maxLines = 1,
                        )
                    }
                    if (!revisao.foto) {
                        Text(
                            text = tempo(revisao.segundos),
                            style = Tipos.TituloDeTela.copy(fontSize = 30.sp, lineHeight = 30.sp),
                            color = Cores.Texto,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotaoContorno(
                        texto = if (revisao.foto) "Refazer" else "Regravar",
                        onClick = {
                            revisao.arquivo.delete()
                            revisando = null
                        },
                        modifier = Modifier.weight(1f),
                        altura = 50.dp,
                        fundo = Cores.Superficie,
                    )
                    BotaoPrincipal(
                        texto = "SALVAR",
                        onClick = {
                            revisando = null
                            if (revisao.foto) aoFotografar?.invoke(revisao.arquivo) else aoGravar(revisao.arquivo, revisao.corte)
                        },
                        modifier = Modifier.weight(2f),
                        altura = 50.dp,
                    )
                }
            }
        }
    }
}

/** O que acabou de ser gravado ou fotografado, esperando Salvar ou Refazer. */
private class Revisao(val arquivo: File, val foto: Boolean, val corte: Long?, val segundos: Long)

/** Flash: na foto, o flash do aparelho; no vídeo, "Ligado" acende a lanterna. */
private enum class ModoDoFlash(val rotulo: String) {
    DESLIGADO("Desligado"),
    AUTO("Auto"),
    LIGADO("Ligado"),
}

private val VERMELHO_DO_DESIGN = Color(0xFFFF3B3B)
private val SOMBRA_DO_TEXTO = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 6f)

/** A bolinha branca que pisca durante a gravação. */
@Composable
private fun PontoPiscando() {
    val transicao = rememberInfiniteTransition(label = "ponto")
    val alfa by transicao.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "alfa",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer { alpha = alfa }
            .clip(CircleShape)
            .background(Color.White),
    )
}

/** A última foto ou vídeo da galeria, no canto esquerdo, para abrir rápido. */
@Composable
private fun MiniaturaDaUltima(arquivo: File, onAbrir: (() -> Unit)?) {
    val canto = RoundedCornerShape(12.dp)
    val quadro by produceState<ImageBitmap?>(null, arquivo) {
        value = withContext(Dispatchers.IO) { miniaturaDoArquivo(arquivo) }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(canto)
            .background(Cores.Trilho)
            .border(2.dp, Color.White.copy(alpha = 0.8f), canto)
            .clickable(enabled = onAbrir != null, onClickLabel = "Abrir a última captura") { onAbrir?.invoke() },
    ) {
        quadro?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Uma miniatura pequena de foto ou vídeo. */
private fun miniaturaDoArquivo(arquivo: File): ImageBitmap? = runCatching {
    val bruto = if (arquivo.extension.equals("jpg", ignoreCase = true)) {
        val opcoes = android.graphics.BitmapFactory.Options().apply { inSampleSize = 16 }
        android.graphics.BitmapFactory.decodeFile(arquivo.absolutePath, opcoes)
    } else {
        val leitor = android.media.MediaMetadataRetriever()
        try {
            leitor.setDataSource(arquivo.absolutePath)
            leitor.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            runCatching { leitor.release() }
        }
    } ?: return@runCatching null
    val lado = 144
    val largura = (bruto.width * lado / bruto.height.coerceAtLeast(1)).coerceAtLeast(1)
    val pequena = android.graphics.Bitmap.createScaledBitmap(bruto, largura, lado, true)
    // O quadro inteiro do vídeo (uns 8 MB em 1080p) não é mais preciso.
    if (pequena !== bruto) bruto.recycle()
    pequena.asImageBitmap()
}.getOrNull()

/** A folha escura que sobe de baixo, com a alça no topo e o fundo escurecido. */
@Composable
private fun FolhaDeBaixo(onFechar: (() -> Unit)?, conteudo: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = onFechar != null,
                    onClickLabel = "Fechar",
                ) { onFechar?.invoke() },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Cores.Fundo)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                .navigationBarsPadding()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Cores.BordaForte),
            )
            conteudo()
        }
    }
}

@Composable
private fun Divisor() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Cores.Divisor),
    )
}

/** Botão redondo de vidro escuro, só com ícone: fechar, ajustes, virar a câmera. */
@Composable
private fun BotaoRedondo(
    @DrawableRes icone: Int,
    descricao: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ativo: Boolean = true,
    tamanho: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(tamanho)
            .clip(CircleShape)
            .background(VIDRO)
            .clickable(enabled = ativo, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icone),
            contentDescription = descricao,
            tint = Color.White.copy(alpha = if (ativo) 1f else 0.35f),
            modifier = Modifier.size(tamanho * 0.45f),
        )
    }
}

/** Uma linha das configurações: nome e explicação à esquerda, a chave à direita. */
@Composable
private fun LinhaDeAjuste(
    texto: String,
    detalhe: String,
    ligado: Boolean,
    onMudar: (Boolean) -> Unit,
    ativo: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = texto, style = Tipos.CorpoForte, color = if (ativo) Cores.Texto else Cores.TextoApagado)
            Text(text = detalhe, style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
        }
        Switch(
            checked = ligado,
            onCheckedChange = onMudar,
            enabled = ativo,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Cores.Destaque,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Cores.BordaForte,
                uncheckedBorderColor = Cores.BordaForte,
            ),
        )
    }
}

/** A grade de terços por cima da imagem: duas linhas em pé e duas deitadas. */
private fun Modifier.gradeDeTercos(): Modifier = drawBehind {
    val cor = Color.White.copy(alpha = 0.22f)
    val traco = 1.dp.toPx()
    for (i in 1..2) {
        val x = size.width * i / 3f
        val y = size.height * i / 3f
        drawLine(color = cor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = traco)
        drawLine(color = cor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = traco)
    }
}

private fun temPermissao(contexto: Context, permissao: String): Boolean =
    ContextCompat.checkSelfPermission(contexto, permissao) == PackageManager.PERMISSION_GRANTED

/** A iluminação em EV, com sinal: "+0,7", "−1,3" ou "0" no automático. */
private fun textoDaIluminacao(ev: Float): String {
    if (kotlin.math.abs(ev) < 0.05f) return "0"
    val valor = "%.1f".format(kotlin.math.abs(ev)).replace('.', ',')
    return (if (ev > 0) "+" else "−") + valor
}

/** A cor do quadrado de foco, a mesma das câmeras de celular. */
private val AMARELO_DO_FOCO = Color(0xFFFFD60A)

/**
 * Trava (ou solta) a exposição e o balanço de branco: a luz para de mudar quando alguém
 * passa na frente ou a cena escurece. Precisa da interop com o Camera2.
 */
@OptIn(ExperimentalCamera2Interop::class)
private fun travarExposicao(camera: Camera, travar: Boolean) {
    runCatching {
        Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, travar)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, travar)
                .build(),
        )
    }
}

/**
 * O quadrado de foco no ponto tocado e, do lado, o sol da luz: arrastar para cima clareia,
 * para baixo escurece. Embaixo, o cadeado que trava foco e exposição.
 */
@Composable
private fun MiraDeFoco(
    ponto: Offset,
    limite: IntSize,
    travado: Boolean,
    iluminacao: Int,
    faixa: IntRange?,
    textoDaLuz: String,
    onIluminacao: (Int) -> Unit,
    onSoltar: () -> Unit,
    onTravar: () -> Unit,
) {
    val densidade = LocalDensity.current
    val lado = 76.dp
    val trilho = 140.dp
    val ladoPx = with(densidade) { lado.toPx() }
    val trilhoPx = with(densidade) { trilho.toPx() }
    val larguraPx = with(densidade) { (lado + 44.dp).toPx() }
    val alturaPx = with(densidade) { (trilho + 44.dp).toPx() }
    // O quadrado centrado no toque, sem sair da tela.
    val x = (ponto.x - ladoPx / 2).coerceIn(0f, (limite.width - larguraPx).coerceAtLeast(0f))
    val y = (ponto.y - trilhoPx / 2).coerceIn(0f, (limite.height - alturaPx).coerceAtLeast(0f))
    var arrastando by remember { mutableStateOf(false) }
    var valor by remember { mutableFloatStateOf(iluminacao.toFloat()) }
    val atual by rememberUpdatedState(iluminacao)
    val cor = AMARELO_DO_FOCO

    Row(
        modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            modifier = Modifier.height(trilho),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(lado)
                    .border(1.5.dp, cor, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(8.dp))
            // O cadeado: trava foco e exposição onde o quadrado está.
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(CircleShape)
                    .background(if (travado) cor else Color.Black.copy(alpha = 0.45f))
                    .clickable(onClickLabel = if (travado) "Destravar foco e exposição" else "Travar foco e exposição", onClick = onTravar)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(if (travado) R.drawable.ic_cadeado else R.drawable.ic_cadeado_aberto),
                    contentDescription = null,
                    tint = if (travado) Color.Black else cor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (travado) "AE/AF" else "Travar",
                    style = Tipos.Botao.copy(fontSize = 12.sp),
                    color = if (travado) Color.Black else cor,
                )
            }
        }
        if (faixa != null && faixa.last > faixa.first) {
            // O sol da luz: uma faixa larga para o dedo, o sol anda pela linha.
            val total = (faixa.last - faixa.first).toFloat()
            val fracao = ((iluminacao - faixa.first) / total).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(trilho)
                    .pointerInput(faixa) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                arrastando = true
                                valor = atual.toFloat()
                            },
                            onDragEnd = {
                                arrastando = false
                                onSoltar()
                            },
                            onDragCancel = {
                                arrastando = false
                                onSoltar()
                            },
                        ) { mudanca, dy ->
                            mudanca.consume()
                            // Para cima clareia: a faixa inteira vai do mínimo ao máximo.
                            valor = (valor - dy / size.height * total).coerceIn(faixa.first.toFloat(), faixa.last.toFloat())
                            onIluminacao(valor.roundToInt())
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(1.5.dp)
                        .height(trilho)
                        .background(cor.copy(alpha = 0.6f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, ((1f - fracao) * (trilhoPx - with(densidade) { 28.dp.toPx() })).roundToInt()) }
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_sol), contentDescription = null, tint = cor, modifier = Modifier.size(20.dp))
                }
                if (arrastando) {
                    Text(
                        text = textoDaLuz,
                        style = Tipos.Botao.copy(fontSize = 12.sp),
                        color = cor,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-18).dp),
                    )
                }
            }
        }
    }
}

/** 75 -> "01:15" */
private fun tempo(segundos: Long): String = "%02d:%02d".format(segundos / 60, segundos % 60)
