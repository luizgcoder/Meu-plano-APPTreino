package com.luiz.meuplano.ui

import android.media.MediaPlayer
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luiz.meuplano.R
import java.io.File
import kotlinx.coroutines.delay

/** Onde está o download de um vídeo para a galeria. */
enum class SituacaoDoDownload { LIVRE, SALVANDO, SALVO }

/**
 * Um vídeo aberto em tela cheia: o montado de um treino ou um clipe.
 *
 * @param inicioMs corte do clipe; com os dois, toca só o trecho, em repetição
 * @param onEnviar manda só este vídeo; nulo esconde o botão
 * @param onApagar apaga o vídeo; nulo esconde o botão
 * @param onCortar abre o corte do vídeo; nulo esconde o botão
 */
@Composable
fun TelaDoVideo(
    titulo: String,
    subtitulo: String?,
    arquivo: File,
    inicioMs: Long?,
    fimMs: Long?,
    download: SituacaoDoDownload,
    onBaixar: () -> Unit,
    onEnviar: (() -> Unit)?,
    onFechar: () -> Unit,
    onApagar: (() -> Unit)? = null,
    onCortar: (() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onFechar,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val contexto = LocalContext.current
        var duracao by remember { mutableIntStateOf(0) }
        var posicao by remember { mutableIntStateOf(0) }
        var tocando by remember { mutableStateOf(true) }
        var erro by remember { mutableStateOf(false) }
        var tocador by remember { mutableStateOf<MediaPlayer?>(null) }
        // Câmera lenta para conferir a execução.
        var lento by remember { mutableStateOf(false) }
        var confirmarApagar by remember { mutableStateOf(false) }

        // O trecho que toca: o corte, ou o vídeo inteiro.
        val comeco = (inicioMs ?: 0L).toInt()
        val termino = if (fimMs != null && duracao > 0) fimMs.toInt().coerceAtMost(duracao) else duracao

        fun irPara(ms: Int) {
            runCatching { tocador?.seekTo(ms.toLong(), MediaPlayer.SEEK_CLOSEST) }
        }

        val video = remember {
            VideoView(contexto).apply {
                setOnPreparedListener { preparado ->
                    tocador = preparado
                    duracao = preparado.duration.coerceAtLeast(0)
                    irPara(comeco)
                }
                setOnCompletionListener {
                    irPara(comeco)
                    if (tocando) start()
                }
                setOnErrorListener { _, _, _ ->
                    erro = true
                    true
                }
                setVideoPath(arquivo.absolutePath)
            }
        }

        // Toca em repetição e acompanha a posição.
        LaunchedEffect(tocando, duracao, lento) {
            if (duracao <= 0) return@LaunchedEffect
            if (!tocando) {
                video.pause()
                return@LaunchedEffect
            }
            video.start()
            // Só com o vídeo tocando: mudar a velocidade parado faria ele voltar a tocar.
            runCatching { tocador?.let { it.playbackParams = it.playbackParams.setSpeed(if (lento) 0.5f else 1f) } }
            while (true) {
                delay(50)
                val agora = video.currentPosition
                posicao = agora
                if (termino > comeco && agora >= termino) irPara(comeco)
            }
        }

        DisposableEffect(Unit) {
            onDispose { video.stopPlayback() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotaoQuadrado(R.drawable.ic_fechar, "Fechar", onFechar, fundo = Cores.Superficie)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titulo.uppercase(),
                        style = Tipos.TituloDeSecao,
                        color = Cores.Texto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitulo != null) {
                        Text(text = subtitulo, style = Tipos.CorpoPequeno, color = Cores.TextoSuave, maxLines = 2)
                    }
                }
            }

            // ----- o vídeo: tocar na imagem pausa e continua -----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = duracao > 0,
                    ) { tocando = !tocando },
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(factory = { video })
                when {
                    erro -> Text("Não consegui abrir este vídeo.", style = Tipos.Corpo, color = Color.White)
                    duracao <= 0 -> CircularProgressIndicator(color = Cores.Destaque)
                    !tocando -> Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "▶", style = Tipos.TituloDeTela, color = Color.White)
                    }
                    else -> Unit
                }
            }

            if (duracao > 0 && termino > comeco) {
                BarraDeProgresso(
                    fracao = (posicao - comeco).toFloat() / (termino - comeco),
                    cor = Cores.Destaque,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pilula(
                    texto = if (lento) "0,5x" else "1x",
                    fundo = if (lento) Cores.Destaque else Cores.Superficie,
                    cor = if (lento) Cores.Fundo else Cores.Texto,
                    borda = Cores.Trilho,
                    altura = 40.dp,
                    onClick = { lento = !lento },
                )
                Text(
                    text = if (lento) "Câmera lenta" else "Velocidade normal",
                    style = Tipos.CorpoPequeno,
                    color = Cores.TextoSuave,
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically),
                )
                if (onCortar != null) {
                    BotaoContorno(
                        texto = "Cortar",
                        onClick = onCortar,
                        altura = 40.dp,
                        fundo = Cores.Superficie,
                    )
                }
                if (onApagar != null) {
                    BotaoContorno(
                        texto = if (confirmarApagar) "Apagar mesmo?" else "Apagar",
                        onClick = { if (confirmarApagar) onApagar() else confirmarApagar = true },
                        altura = 40.dp,
                        fundo = Cores.Superficie,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotaoContorno(
                    texto = when (download) {
                        SituacaoDoDownload.LIVRE -> "Baixar"
                        SituacaoDoDownload.SALVANDO -> "Salvando…"
                        SituacaoDoDownload.SALVO -> "Salvo na galeria"
                    },
                    onClick = onBaixar,
                    modifier = Modifier.weight(1f),
                    icone = if (download == SituacaoDoDownload.SALVO) R.drawable.ic_feito else R.drawable.ic_baixar,
                    ativo = download != SituacaoDoDownload.SALVANDO,
                    altura = 48.dp,
                    fundo = Cores.Superficie,
                )
                if (onEnviar != null) {
                    BotaoPrincipal(
                        texto = "Enviar",
                        onClick = onEnviar,
                        modifier = Modifier.weight(1f),
                        icone = R.drawable.ic_enviar,
                    )
                }
            }
        }
    }
}

/** O botão de baixar de cada clipe: a seta, a rodinha enquanto salva e o check quando salvou. */
@Composable
fun BotaoDeBaixar(download: SituacaoDoDownload, onBaixar: () -> Unit) {
    if (download == SituacaoDoDownload.SALVANDO) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cores.Destaque, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    } else {
        BotaoQuadrado(
            icone = if (download == SituacaoDoDownload.SALVO) R.drawable.ic_feito else R.drawable.ic_baixar,
            descricao = if (download == SituacaoDoDownload.SALVO) "Salvo na galeria" else "Baixar este vídeo",
            onClick = onBaixar,
            cor = if (download == SituacaoDoDownload.SALVO) Cores.FeitoTexto else Cores.Texto,
            fundo = Cores.Fundo,
            tamanho = 40.dp,
        )
    }
}

/** Ícone de play pequeno, para indicar que a linha abre o vídeo. */
@Composable
fun IconeDeVer(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_video),
        contentDescription = null,
        tint = Cores.TextoSuave,
        modifier = modifier.size(18.dp),
    )
}
