package com.luiz.meuplano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luiz.meuplano.R
import com.luiz.meuplano.data.SerieLocalizada
import com.luiz.meuplano.data.UltimaVez
import kotlin.math.round

/** Os RPEs que dá para escolher, de meio em meio. */
private val RPES = listOf(6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)

/** O que o RPE quer dizer em repetições de sobra. */
private fun sobra(rpe: Double): String = when (rpe) {
    6.0 -> "Sobrariam 4"
    6.5 -> "Sobrariam 3–4"
    7.0 -> "Sobrariam 3"
    7.5 -> "Sobrariam 2–3"
    8.0 -> "Sobrariam 2"
    8.5 -> "Sobrariam 1–2"
    9.0 -> "Sobraria 1"
    9.5 -> "Talvez mais 1"
    else -> "Máximo"
}

/** Arredonda para 0,5 kg, o menor passo que dá para montar na barra. */
private fun meioQuilo(kg: Double): Double = (round(kg * 2) / 2).coerceAtLeast(0.0)

private val VERDE = Color(0xFF8BE38B)
private val AZUL = Color(0xFF7DC3FF)
private val CINZA_DO_BOTAO = Color(0xFF1C1C1F)

/**
 * Registro de uma série, na folha que sobe de baixo: carga com − e + (ou digitada), o RPE
 * em botões com o prescrito marcado e uma anotação que vai para o feedback. Tudo é
 * opcional; salvar vazio marca a série só como feita.
 *
 * @param treino a letra do treino, para o cabeçalho ("TREINO E · 1x2 · RPE 8")
 * @param ultimaVez busca a carga da última semana em que o exercício apareceu
 * @param onGravar abre a câmera desta série (o botão salva antes)
 * @param onEscolherDaGaleria pega um vídeo da galeria do celular para esta série (o botão salva antes)
 * @param onVerVideo abre o vídeo já gravado desta série, para assistir e editar
 * @param outrasSemanas diz se o exercício tem vídeo gravado em outra semana (mostra o Comparar)
 * @param onComparar abre o Comparar entre semanas deste exercício
 * @param naCamera aberto por cima da câmera: só os campos, sem os botões de vídeo e de apagar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoRegistro(
    localizada: SerieLocalizada,
    onSalvar: (cargaKg: Double?, rpe: Double?, anotacao: String?) -> Unit,
    onGravar: () -> Unit,
    onVerVideo: () -> Unit,
    onApagar: () -> Unit,
    onFechar: () -> Unit,
    naCamera: Boolean = false,
    treino: String? = null,
    ultimaVez: (suspend () -> UltimaVez?)? = null,
    outrasSemanas: (suspend () -> Boolean)? = null,
    onComparar: () -> Unit = {},
    onEscolherDaGaleria: (() -> Unit)? = null,
) {
    val serie = localizada.item.serie
    val registro = localizada.item.registro
    val temVideo = registro?.videoArquivo != null
    val folha = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val anterior by produceState<UltimaVez?>(null, serie.id) { value = ultimaVez?.invoke() }
    val podeComparar by produceState(false, serie.id) { value = outrasSemanas?.invoke() == true }

    // A chave é o id da série: o texto digitado não é trocado quando o registro
    // atualiza por baixo (por exemplo, quando o vídeo termina de ser salvo).
    var carga by rememberSaveable(serie.id) {
        mutableStateOf(sugestaoDeCarga(serie, registro, localizada.cargaDaLinhaAnterior)?.semZeros().orEmpty())
    }
    var rpe by rememberSaveable(serie.id) { mutableStateOf(registro?.rpe) }
    var anotacao by rememberSaveable(serie.id) { mutableStateOf(registro?.anotacao.orEmpty()) }
    var confirmarApagar by rememberSaveable(serie.id) { mutableStateOf(false) }

    val alvo = serie.rpeMin
    val alvoMax = serie.rpeMax ?: alvo
    val numero = lerNumero(carga)

    /** Soma à carga; sem carga ainda, parte da última vez. */
    fun somar(kg: Double) {
        val base = numero ?: anterior?.cargaKg ?: 0.0
        carga = meioQuilo(base + kg).semZeros()
    }

    val resumo = buildString {
        if (numero != null) append("${numero.semZeros()} kg")
        rpe?.let { append(if (numero != null) " @ ${it.semZeros()}" else "RPE ${it.semZeros()}") }
    }

    ModalBottomSheet(
        onDismissRequest = onFechar,
        sheetState = folha,
        containerColor = Color(0xFF141416),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Cores.BordaForte),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ----- cabeçalho -----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = listOfNotNull(treino?.let { "TREINO $it" }, serie.resumo().uppercase()).joinToString(" · "),
                        style = Tipos.Rotulo.copy(fontWeight = FontWeight.ExtraBold),
                        color = Cores.Destaque,
                    )
                    Text(
                        text = localizada.exercicio.nome.uppercase(),
                        style = Tipos.TituloDeTela.copy(fontSize = 30.sp, lineHeight = 30.sp),
                        color = Cores.Texto,
                    )
                    val u = anterior
                    if (u != null) {
                        val reps = u.reps?.let { " × $it" }.orEmpty()
                        val esforco = u.rpe?.let { " @ ${it.semZeros()}" }.orEmpty()
                        Text(
                            text = "Última vez: ${u.cargaKg.semZeros()} kg$reps$esforco",
                            style = Tipos.CorpoPequeno,
                            color = Cores.TextoSuave,
                        )
                    }
                    if (podeComparar && !naCamera) {
                        Row(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(CircleShape)
                                .background(Cores.SuperficieAlta)
                                .clickable(onClickLabel = "Comparar com outras semanas", onClick = onComparar)
                                .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_video), contentDescription = null, tint = Cores.Destaque, modifier = Modifier.size(16.dp))
                            Text("Comparar com outras semanas", style = Tipos.CorpoForte.copy(fontSize = 13.sp), color = Cores.Texto)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Cores.SuperficieAlta)
                        .clickable(onClickLabel = "Fechar", onClick = onFechar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_fechar), contentDescription = "Fechar", tint = Cores.Texto, modifier = Modifier.size(18.dp))
                }
            }

            // ----- carga -----
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Rotulo(if (serie.pesoCorporal) "Carga extra" else "Carga")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotaoDePasso("−", "Diminuir 2,5 kg") { somar(-2.5) }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Cores.Fundo),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // O número também aceita digitar direto.
                        BasicTextField(
                            value = carga,
                            onValueChange = { carga = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = Tipos.NumeroGrande.copy(
                                fontSize = 52.sp,
                                lineHeight = 52.sp,
                                color = Cores.Texto,
                                textAlign = TextAlign.End,
                            ),
                            cursorBrush = SolidColor(Cores.Destaque),
                            modifier = Modifier.width(120.dp),
                            decorationBox = { campo ->
                                Box(contentAlignment = Alignment.CenterEnd) {
                                    if (carga.isEmpty()) {
                                        Text(
                                            text = "—",
                                            style = Tipos.NumeroGrande.copy(fontSize = 52.sp, lineHeight = 52.sp),
                                            color = Cores.TextoApagado,
                                        )
                                    }
                                    campo()
                                }
                            },
                        )
                        Text(
                            text = " kg",
                            style = Tipos.NumeroMedio.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                            color = Cores.TextoSuave,
                        )
                    }
                    BotaoDePasso("+", "Aumentar 2,5 kg") { somar(2.5) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-5.0, -1.0, 1.0, 5.0).forEach { d ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CINZA_DO_BOTAO)
                                .clickable { somar(d) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (if (d > 0) "+" else "−") + "${kotlin.math.abs(d).semZeros()} kg",
                                style = Tipos.Botao.copy(fontSize = 13.sp),
                                color = Color(0xFFD4D4D8),
                            )
                        }
                    }
                }
            }

            // ----- RPE -----
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Rotulo("RPE que você sentiu", modifier = Modifier.weight(1f))
                    val escolhido = rpe
                    val topo = alvoMax ?: alvo
                    val (nota, cor) = when {
                        escolhido == null -> "Toque para escolher" to Cores.TextoApagado
                        alvo == null || topo == null -> sobra(escolhido) to Cores.TextoSuave
                        escolhido in alvo..topo -> "${sobra(escolhido)} · no alvo" to VERDE
                        escolhido > topo -> "${sobra(escolhido)} · acima do alvo" to Cores.NovoTexto
                        else -> "${sobra(escolhido)} · abaixo do alvo" to AZUL
                    }
                    Text(text = nota, style = Tipos.CorpoPequeno.copy(fontWeight = FontWeight.SemiBold), color = cor)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RPES.forEach { v ->
                        val escolhido = rpe == v
                        val topo = alvoMax ?: alvo
                        val prescrito = alvo != null && topo != null && v >= alvo && v <= topo
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (escolhido) Cores.Destaque else CINZA_DO_BOTAO)
                                .border(1.dp, if (escolhido) Cores.Destaque else Cores.Trilho, RoundedCornerShape(10.dp))
                                // Tocar no escolhido de novo tira o RPE.
                                .clickable(onClickLabel = "RPE ${v.semZeros()}") { rpe = if (escolhido) null else v },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = v.semZeros(),
                                style = Tipos.NumeroMedio,
                                color = if (escolhido) Cores.Fundo else Cores.Texto,
                            )
                            if (prescrito) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 5.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(if (escolhido) Cores.Fundo else Cores.Destaque),
                                )
                            }
                        }
                    }
                }
                if (alvo != null) {
                    Text(
                        text = "O ponto marca o prescrito. Sem escolher, o vídeo usa o RPE do protocolo.",
                        style = Tipos.CorpoPequeno,
                        color = Cores.TextoSuave,
                    )
                }
            }

            // ----- anotação -----
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Rotulo("Como foi")
                    Text(" · vai para o feedback", style = Tipos.CorpoPequeno.copy(fontSize = 11.sp), color = Cores.TextoSuave)
                }
                BasicTextField(
                    value = anotacao,
                    onValueChange = { anotacao = it },
                    textStyle = Tipos.Corpo.copy(color = Cores.Texto),
                    cursorBrush = SolidColor(Cores.Destaque),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cores.Fundo)
                        .border(1.dp, Cores.Trilho, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    decorationBox = { campo ->
                        Box {
                            if (anotacao.isEmpty()) {
                                Text("Ex.: pausa sólida, ombro ok", style = Tipos.Corpo, color = Cores.TextoApagado)
                            }
                            campo()
                        }
                    },
                )
            }

            // ----- salvar (e gravar) -----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!naCamera) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CINZA_DO_BOTAO)
                            .border(1.dp, Cores.Trilho, RoundedCornerShape(16.dp))
                            .clickable(onClickLabel = if (temVideo) "Salvar e regravar o vídeo" else "Salvar e gravar vídeo") {
                                onSalvar(numero, rpe, anotacao)
                                onGravar()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .border(2.5.dp, Cores.Gravar, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Cores.Gravar),
                            )
                        }
                    }
                    // Vídeo feito com a câmera do celular: escolhe na galeria.
                    if (onEscolherDaGaleria != null) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CINZA_DO_BOTAO)
                                .border(1.dp, Cores.Trilho, RoundedCornerShape(16.dp))
                                .clickable(onClickLabel = "Salvar e escolher um vídeo da galeria") {
                                    onSalvar(numero, rpe, anotacao)
                                    onEscolherDaGaleria()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_galeria),
                                contentDescription = null,
                                tint = Cores.Texto,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                BotaoPrincipal(
                    texto = if (resumo.isEmpty()) "SALVAR" else "SALVAR · $resumo",
                    onClick = { onSalvar(numero, rpe, anotacao) },
                    modifier = Modifier.weight(1f),
                    altura = 56.dp,
                )
            }

            // ----- vídeo e apagar, discretos -----
            // Só existe vídeo com registro, então basta checar o registro.
            if (!naCamera && registro != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (temVideo) {
                        Text(
                            text = "Ver / editar o vídeo",
                            style = Tipos.CorpoForte,
                            color = Cores.Texto,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onVerVideo)
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        )
                    } else {
                        Box(Modifier)
                    }
                    Text(
                        text = if (confirmarApagar) "Apagar mesmo?" else "Apagar registro",
                        style = Tipos.CorpoForte,
                        color = Cores.Gravar,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { if (confirmarApagar) onApagar() else confirmarApagar = true }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** O − e o + grandes da carga. */
@Composable
private fun BotaoDePasso(sinal: String, descricao: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CINZA_DO_BOTAO)
            .border(1.dp, Cores.Trilho, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = descricao, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = sinal, style = Tipos.NumeroGrande.copy(fontSize = 32.sp), color = Cores.Texto)
    }
}
