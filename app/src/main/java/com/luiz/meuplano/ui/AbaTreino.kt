package com.luiz.meuplano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luiz.meuplano.R
import com.luiz.meuplano.data.ExercicioComSeries
import com.luiz.meuplano.data.PlanoCompleto
import com.luiz.meuplano.data.SerieComRegistro
import com.luiz.meuplano.data.TreinoComExercicios
import com.luiz.meuplano.feedback.NOME_DO_APP
import com.luiz.meuplano.feedback.periodoDaSemana
import java.time.LocalDate

/**
 * A tela de treino do design: os treinos da semana em botões no topo e, embaixo,
 * um cartão por exercício com a tabela de séries.
 *
 * @param onRegistrar abre o registro de carga e RPE da série (recebe o id da série)
 * @param onGravar abre a câmera para a série
 * @param onVerVideo abre o vídeo gravado, para assistir e cortar
 * @param onImportar abre o seletor de arquivo para importar o plano da semana
 */
@Composable
fun AbaTreino(
    plano: PlanoCompleto,
    onRegistrar: (Long) -> Unit,
    onGravar: (Long) -> Unit,
    onVerVideo: (Long) -> Unit,
    onImportar: () -> Unit,
) {
    var selecionado by rememberSaveable { mutableIntStateOf(0) }
    val indice = selecionado.coerceIn(0, (plano.treinos.size - 1).coerceAtLeast(0))
    val treino = plano.treinos.getOrNull(indice)
    val (inicio, fim) = remember(plano.plano.geradoEm) { periodoDaSemana(plano.plano.geradoEm, LocalDate.now()) }

    Column(modifier = Modifier.fillMaxSize()) {
        CabecalhoDeTela(
            sobretitulo = "$NOME_DO_APP · ${periodoCurto(inicio, fim)}",
            titulo = plano.plano.semana?.let { "Semana $it" } ?: "Treino",
            acao = { BotaoQuadrado(R.drawable.ic_importar, "Importar plano", onImportar) },
        )

        SeletorDeTreino(plano.treinos, indice, onSelecionar = { selecionado = it })

        if (treino != null) {
            val atual = treino.exercicioAtual()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(treino.exercicios, key = { _, item -> item.exercicio.id }) { i, item ->
                    CartaoExercicio(
                        numero = i + 1,
                        item = item,
                        atual = i == atual,
                        onRegistrar = onRegistrar,
                        onGravar = onGravar,
                        onVerVideo = onVerVideo,
                    )
                }
            }
        }
    }
}

/** Um botão por treino, com a letra e quantos exercícios já foram feitos. */
@Composable
private fun SeletorDeTreino(treinos: List<TreinoComExercicios>, indice: Int, onSelecionar: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        treinos.forEachIndexed { i, treino ->
            val escolhido = i == indice
            val canto = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(canto)
                    .background(if (escolhido) Cores.Destaque else Cores.Superficie)
                    .then(if (escolhido) Modifier else Modifier.border(1.dp, Cores.Trilho, canto))
                    .clickable { onSelecionar(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = treino.treino.letra ?: "${i + 1}",
                    style = Tipos.TituloDeSecao.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp),
                    color = if (escolhido) Cores.Fundo else Cores.Texto,
                )
                Text(
                    text = treino.progresso(),
                    style = Tipos.Rotulo.copy(letterSpacing = 0.sp),
                    color = if (escolhido) Cores.Fundo else Cores.TextoSuave,
                )
            }
        }
    }
}

@Composable
private fun CartaoExercicio(
    numero: Int,
    item: ExercicioComSeries,
    atual: Boolean,
    onRegistrar: (Long) -> Unit,
    onGravar: (Long) -> Unit,
    onVerVideo: (Long) -> Unit,
) {
    val exercicio = item.exercicio
    val feito = item.feito()
    // Nulo = ninguém tocou ainda; aí só o exercício da vez fica aberto.
    var abertoPeloUsuario by rememberSaveable(exercicio.id) { mutableStateOf<Boolean?>(null) }
    val aberto = abertoPeloUsuario ?: atual
    val abrirLink = LocalUriHandler.current

    Cartao(destacado = atual) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { abertoPeloUsuario = !aberto },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = if (aberto) Alignment.Top else Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Rotulo(
                    texto = "%02d".format(numero) + if (atual) " · AGORA" else "",
                    cor = if (atual || feito) Cores.Destaque else Cores.TextoSuave,
                )
                Text(text = exercicio.nome.uppercase(), style = Tipos.TituloDeCartao, color = Cores.Texto)
                if (aberto) {
                    exercicio.observacao?.let {
                        Text(text = it, style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
                    }
                }
            }
            if (feito) {
                Pilula("Feito", Cores.FeitoFundo, Cores.FeitoTexto, icone = R.drawable.ic_feito)
            } else if (!aberto) {
                Text(text = item.resumoCurto(), style = Tipos.NumeroMedio.copy(fontSize = 20.sp), color = Cores.TextoSuave)
            }
        }

        if (aberto) {
            Row(modifier = Modifier.padding(horizontal = 4.dp)) {
                Rotulo("Série", Modifier.width(44.dp))
                Rotulo("Reps", Modifier.weight(1f))
                Rotulo("Carga", Modifier.weight(1f))
                Rotulo("RPE", Modifier.weight(1f))
                Spacer(Modifier.width(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (linha in item.series) {
                    LinhaDaSerie(linha, onClick = { onRegistrar(linha.serie.id) })
                }
            }

            exercicio.videoUrl?.let { url ->
                Text(
                    text = "Ver vídeo de exemplo",
                    style = Tipos.CorpoPequeno.copy(fontWeight = FontWeight.SemiBold),
                    color = Cores.Destaque,
                    // Sem navegador nem YouTube instalado, openUri lança exceção.
                    modifier = Modifier.clickable { runCatching { abrirLink.openUri(url) } },
                )
            }

            AcoesDoExercicio(item, onRegistrar, onGravar, onVerVideo)
        }
    }
}

/**
 * Os botões embaixo do cartão. Cada um já aponta para a série certa:
 * registrar a próxima série sem registro, gravar a próxima que pede vídeo,
 * e ver ou regravar o vídeo que já existe.
 */
@Composable
private fun AcoesDoExercicio(
    item: ExercicioComSeries,
    onRegistrar: (Long) -> Unit,
    onGravar: (Long) -> Unit,
    onVerVideo: (Long) -> Unit,
) {
    // Sem série nenhuma não há onde guardar o vídeo (antes, gravar aqui fechava o app).
    val primeira = item.series.firstOrNull() ?: return
    val semRegistro = item.series.firstOrNull { it.registro?.cargaKg == null && it.registro?.rpe == null }
    val pedeVideo = item.series.firstOrNull { it.serie.gravar && it.registro?.videoArquivo == null }
    val comVideo = item.series.firstOrNull { it.registro?.videoArquivo != null }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (semRegistro != null) {
            BotaoPrincipal(
                texto = "Registrar carga",
                onClick = { onRegistrar(semRegistro.serie.id) },
                modifier = Modifier.weight(1f),
                icone = R.drawable.ic_mais,
            )
        }
        if (comVideo != null) {
            val registro = comVideo.registro
            val inicio = registro?.corteInicioMs
            val fim = registro?.corteFimMs
            BotaoContorno(
                texto = if (inicio != null && fim != null) "Vídeo · ${duracaoCurta(fim - inicio)}" else "Vídeo",
                onClick = { onVerVideo(comVideo.serie.id) },
                modifier = Modifier.weight(1f),
                icone = R.drawable.ic_video,
                altura = 48.dp,
            )
        }
        when {
            // Ainda falta um vídeo pedido pelo treinador: o botão vermelho de gravar.
            pedeVideo != null -> BotaoQuadrado(
                icone = R.drawable.ic_gravar,
                descricao = "Gravar série",
                onClick = { onGravar(pedeVideo.serie.id) },
                cor = Cores.Gravar,
                fundo = Cores.Fundo,
                tamanho = 48.dp,
            )
            comVideo != null && semRegistro == null -> BotaoContorno(
                texto = "Regravar",
                onClick = { onGravar(comVideo.serie.id) },
                modifier = Modifier.weight(1f),
                altura = 48.dp,
            )
            // Nada pedido: dá para gravar mesmo assim (o treinador aceita vídeo de qualquer exercício).
            comVideo == null -> BotaoQuadrado(
                icone = R.drawable.ic_gravar,
                descricao = "Gravar série",
                onClick = { onGravar((semRegistro ?: primeira).serie.id) },
                cor = Cores.Gravar,
                fundo = Cores.Fundo,
                tamanho = 48.dp,
            )
            else -> Unit
        }
    }
}

/** Uma série na tabela. Tocar abre o registro de carga e RPE dela. */
@Composable
private fun LinhaDaSerie(linha: SerieComRegistro, onClick: () -> Unit) {
    val serie = linha.serie
    val registro = linha.registro
    val canto = RoundedCornerShape(12.dp)
    // Tracejada enquanto depende da série de cima ("diminuir 10%") e ainda não foi feita.
    val pendenteDaAnterior = serie.ajustePercentual != null && registro?.cargaKg == null

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(canto)
                .then(
                    if (pendenteDaAnterior) Modifier.bordaTracejada(Cores.BordaForte, 12.dp)
                    else Modifier.background(Cores.SuperficieAlta),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!serie.interpretada) {
                // Linha que o parser não entendeu: mostra como o treinador escreveu.
                Text(
                    text = serie.textoOriginal,
                    style = Tipos.Corpo,
                    color = Cores.Texto,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
            } else {
                val celulas = remember(linha) { linha.celulas() }
                Text(
                    text = celulas.series,
                    style = Tipos.NumeroMedio,
                    color = Cores.TextoSuave,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(44.dp),
                )
                Text(text = celulas.reps, style = Tipos.Numero, color = Cores.Texto, modifier = Modifier.weight(1f))
                Row(modifier = Modifier.weight(1f)) {
                    Text(
                        text = celulas.carga,
                        style = if (celulas.cargaEmKg) Tipos.Numero else Tipos.NumeroMedio,
                        color = when {
                            celulas.cargaRegistrada -> Cores.Texto
                            celulas.cargaEmKg -> Cores.TextoSuave
                            else -> Cores.TextoApagado
                        },
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (celulas.cargaEmKg) {
                        Text(
                            text = " kg",
                            style = Tipos.Botao,
                            color = Cores.TextoSuave,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                Text(
                    text = celulas.rpe,
                    style = Tipos.Numero,
                    color = if (celulas.rpe == "—") Cores.TextoApagado else Cores.Destaque,
                    modifier = Modifier.weight(1f),
                )
            }
            // Situação da série: vídeo gravado, registro feito ou vídeo ainda devido.
            Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                when {
                    registro?.videoArquivo != null -> Icon(
                        painterResource(R.drawable.ic_video),
                        contentDescription = "Vídeo gravado",
                        tint = Cores.FeitoTexto,
                        modifier = Modifier.size(16.dp),
                    )
                    serie.gravar -> Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Cores.Gravar),
                    )
                    registro != null -> Icon(
                        painterResource(R.drawable.ic_feito),
                        contentDescription = "Registrada",
                        tint = Cores.FeitoTexto,
                        modifier = Modifier.size(14.dp),
                    )
                    else -> Unit
                }
            }
        }
        serie.observacao?.let {
            Text(
                text = it,
                style = Tipos.CorpoPequeno,
                color = Cores.TextoSuave,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
