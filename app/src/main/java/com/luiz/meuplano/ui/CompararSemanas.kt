package com.luiz.meuplano.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luiz.meuplano.R
import com.luiz.meuplano.data.ExercicioEntity
import com.luiz.meuplano.data.VideoDeSerie
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comparar a execução de um exercício entre semanas: a lista de todos os vídeos dele
 * (de todos os planos importados), já com A = o vídeo desta série e B = o mais recente
 * de outra semana. Toque troca a escolha; com dois marcados, abre o Comparar.
 *
 * @param serieAtual a série de onde veio (o vídeo dela começa marcado como A)
 * @param planoAtual o plano desta semana, para escrever "Esta semana"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompararSemanas(
    exercicio: ExercicioEntity,
    serieAtual: Long,
    planoAtual: Long,
    viewModel: PlanoViewModel,
    onFechar: () -> Unit,
) {
    // Incrementar recarrega a lista depois de apagar um vídeo.
    var recarga by remember { mutableIntStateOf(0) }
    val carregados by produceState<List<VideoDeSerie>?>(null, exercicio.id, recarga) {
        value = viewModel.videosDoExercicio(exercicio)
    }
    // Escolha em ordem: o primeiro é o A, o segundo é o B. Nulo = ainda não tocou (usa a sugestão).
    var escolha by remember { mutableStateOf<List<String>?>(null) }
    var comparando by remember { mutableStateOf<Pair<VideoDeSerie, VideoDeSerie>?>(null) }
    // Confirmação em dois toques, por vídeo (pelo nome do arquivo).
    var confirmarApagar by remember { mutableStateOf<String?>(null) }

    val videos = carregados
    val sugestao = remember(videos) {
        if (videos == null) emptyList() else {
            val a = videos.firstOrNull { it.seriePrescritaId == serieAtual } ?: videos.firstOrNull { it.planoId == planoAtual }
            val b = videos.firstOrNull { it.planoId != (a?.planoId ?: planoAtual) }
            listOfNotNull(a, b).map { it.videoArquivo }
        }
    }
    val escolhidos = escolha ?: sugestao

    val folha = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onFechar,
        sheetState = folha,
        containerColor = Cores.Superficie,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("COMPARAR SEMANAS", style = Tipos.Rotulo.copy(fontWeight = FontWeight.ExtraBold), color = Cores.Destaque)
                Text(
                    text = exercicio.nome.uppercase(),
                    style = Tipos.TituloDeTela.copy(fontSize = 26.sp, lineHeight = 26.sp),
                    color = Cores.Texto,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Toque para marcar A e B · segure para apagar um vídeo antigo", style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
            }

            when {
                videos == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cores.Destaque)
                }
                videos.size < 2 -> Text(
                    text = "Ainda não tem dois vídeos deste exercício para comparar. Grave nesta semana e, quando o " +
                        "próximo plano chegar, o vídeo desta semana aparece aqui.",
                    style = Tipos.Corpo,
                    color = Cores.TextoSuave,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(videos, key = { it.videoArquivo }) { v ->
                        val ordem = escolhidos.indexOf(v.videoArquivo)
                        LinhaDoVideo(
                            video = v,
                            arquivo = viewModel.arquivoDeVideo(v.videoArquivo),
                            estaSemana = v.planoId == planoAtual,
                            marcador = when (ordem) {
                                0 -> "A"
                                1 -> "B"
                                else -> null
                            },
                            confirmarApagar = confirmarApagar == v.videoArquivo,
                            onClick = {
                                confirmarApagar = null
                                escolha = when {
                                    ordem >= 0 -> escolhidos - v.videoArquivo
                                    escolhidos.size >= 2 -> listOf(escolhidos[0], v.videoArquivo)
                                    else -> escolhidos + v.videoArquivo
                                }
                            },
                            onSegurar = { confirmarApagar = v.videoArquivo },
                            onApagar = {
                                if (confirmarApagar == v.videoArquivo) {
                                    escolha = escolhidos - v.videoArquivo
                                    confirmarApagar = null
                                    viewModel.apagarVideoDaSerie(v.seriePrescritaId) { recarga++ }
                                } else {
                                    confirmarApagar = v.videoArquivo
                                }
                            },
                        )
                    }
                }
            }

            if (videos != null && videos.size >= 2) {
                val a = videos.firstOrNull { it.videoArquivo == escolhidos.getOrNull(0) }
                val b = videos.firstOrNull { it.videoArquivo == escolhidos.getOrNull(1) }
                BotaoPrincipal(
                    texto = if (a != null && b != null) "COMPARAR" else "MARQUE 2 VÍDEOS",
                    onClick = { if (a != null && b != null) comparando = a to b },
                    modifier = Modifier.fillMaxWidth(),
                    ativo = a != null && b != null,
                )
            }
        }
    }

    comparando?.let { (a, b) ->
        TelaComparar(
            primeiro = ladoDaSerie(a, viewModel.arquivoDeVideo(a.videoArquivo), planoAtual),
            segundo = ladoDaSerie(b, viewModel.arquivoDeVideo(b.videoArquivo), planoAtual),
            sobretitulo = exercicio.nome,
            titulo = "SEMANAS",
            onFechar = { comparando = null },
        )
    }
}

/** O lado do Comparar para um vídeo de série: começa no corte e a etiqueta diz a semana e a carga. */
private fun ladoDaSerie(v: VideoDeSerie, arquivo: File, planoAtual: Long): LadoDoComparar {
    val semana = when {
        v.planoId == planoAtual -> "Esta sem."
        v.semana != null -> "Sem ${v.semana}"
        else -> diaCurto(v.atualizadoEm)
    }
    val carga = v.cargaKg?.let { " · ${it.semZeros()} kg" }.orEmpty()
    return LadoDoComparar(arquivo = arquivo, foto = false, quando = semana + carga, inicioMs = v.corteInicioMs ?: 0L)
}

/** "Seg 05/10" */
private fun diaCurto(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("pt-BR")))
        .replace(".", "")
        .replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinhaDoVideo(
    video: VideoDeSerie,
    arquivo: File,
    estaSemana: Boolean,
    marcador: String?,
    confirmarApagar: Boolean,
    onClick: () -> Unit,
    onSegurar: () -> Unit,
    onApagar: () -> Unit,
) {
    val haptico = LocalHapticFeedback.current
    val canto = RoundedCornerShape(14.dp)
    val miniatura by produceState<ImageBitmap?>(null, arquivo) {
        value = withContext(Dispatchers.IO) { quadroDoVideo(arquivo, video.corteInicioMs ?: 0L) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(canto)
            .background(Cores.Fundo)
            .border(
                width = if (marcador != null || confirmarApagar) 2.dp else 1.dp,
                color = when {
                    confirmarApagar -> Cores.Gravar
                    marcador == "A" -> Cores.Texto
                    marcador == "B" -> Cores.Destaque
                    else -> Cores.Trilho
                },
                shape = canto,
            )
            .combinedClickable(
                onClickLabel = "Marcar para comparar",
                onLongClickLabel = "Apagar este vídeo",
                onLongClick = {
                    haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSegurar()
                },
                onClick = onClick,
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Cores.SuperficieAlta),
        ) {
            miniatura?.let {
                Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = when {
                    estaSemana -> "ESTA SEMANA"
                    video.semana != null -> "SEMANA ${video.semana}"
                    else -> "SEMANA ANTERIOR"
                },
                style = Tipos.Rotulo.copy(fontWeight = FontWeight.ExtraBold),
                color = if (estaSemana) Cores.Destaque else Cores.TextoSuave,
            )
            val carga = video.cargaKg?.let { "${it.semZeros()} kg" }
            val reps = video.reps?.let { "× $it" }
            val rpe = video.rpe?.let { "@ ${it.semZeros()}" }
            Text(
                text = listOfNotNull("Série ${video.ordem + 1}", listOfNotNull(carga, reps, rpe).joinToString(" ").ifBlank { null })
                    .joinToString(" · "),
                style = Tipos.CorpoForte,
                color = Cores.Texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (confirmarApagar) "Segurar marcou para apagar — toque no ícone para confirmar" else diaCurto(video.atualizadoEm),
                style = Tipos.CorpoPequeno,
                color = if (confirmarApagar) Cores.Gravar else Cores.TextoSuave,
                maxLines = 2,
            )
        }
        if (confirmarApagar) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Cores.Gravar)
                    .clickable(onClickLabel = "Apagar de vez", onClick = onApagar),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_lixeira), contentDescription = null, tint = Cores.Fundo, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when (marcador) {
                            "A" -> Cores.Texto
                            "B" -> Cores.Destaque
                            else -> Color.Transparent
                        },
                    )
                    .then(if (marcador == null) Modifier.border(2.dp, Cores.Trilho, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (marcador != null) {
                    Text(marcador, style = Tipos.NumeroMedio.copy(fontSize = 15.sp), color = Cores.Fundo)
                }
            }
        }
    }
}

/** Um quadro pequeno do vídeo, no início do corte. */
private fun quadroDoVideo(arquivo: File, ms: Long): ImageBitmap? {
    val leitor = MediaMetadataRetriever()
    return try {
        leitor.setDataSource(arquivo.absolutePath)
        val quadro = leitor.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        val altura = 160
        val largura = (quadro.width * altura / quadro.height.coerceAtLeast(1)).coerceAtLeast(1)
        val pequena = Bitmap.createScaledBitmap(quadro, largura, altura, true)
        if (pequena !== quadro) quadro.recycle()
        pequena.asImageBitmap()
    } catch (e: Exception) {
        null
    } finally {
        runCatching { leitor.release() }
    }
}
