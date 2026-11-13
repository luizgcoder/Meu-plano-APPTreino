package com.luiz.meuplano.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luiz.meuplano.R
import com.luiz.meuplano.data.BlocoInfoEntity
import com.luiz.meuplano.data.PlanoCompleto
import com.luiz.meuplano.data.PrEntity

/**
 * A tela Info do design: o total (agachamento + supino + terra), os recordes em
 * dois por linha e, embaixo, todos os textos de consulta do plano (suplementação,
 * cardio, diretrizes), cada um em um cartão que abre ao tocar.
 */
@Composable
fun AbaInfo(plano: PlanoCompleto) {
    val total = remember(plano.prs) { totalSbd(plano.prs) }

    Column(modifier = Modifier.fillMaxSize()) {
        CabecalhoDeTela(sobretitulo = "Recordes pessoais", titulo = "Info")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (total != null) {
                item(key = "total") { CartaoDoTotal(total) }
            }
            // Os recordes, dois por linha.
            items(plano.prs.chunked(2), key = { par -> "pr-${par.first().id}" }) { par ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (pr in par) AzulejoDoPr(pr, Modifier.weight(1f))
                    if (par.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            items(plano.blocos, key = { "bloco-${it.id}" }) { CartaoDeTexto(it) }
        }
    }
}

@Composable
private fun CartaoDoTotal(total: TotalSbd) {
    Cartao {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Rotulo("Total · SBD", cor = Cores.Destaque)
                Row {
                    Text(
                        text = total.total.semZeros(),
                        style = Tipos.NumeroGrande.copy(fontSize = 64.sp, lineHeight = 58.sp),
                        color = Cores.Texto,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        text = " kg",
                        style = Tipos.TituloDeCartao.copy(fontWeight = FontWeight.SemiBold),
                        color = Cores.TextoSuave,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val estilo = Tipos.NumeroMedio.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("S ${total.agachamento.semZeros()}", style = estilo, color = Cores.TextoSuave)
                Text("B ${total.supino.semZeros()}", style = estilo, color = Cores.TextoSuave)
                Text("D ${total.terra.semZeros()}", style = estilo, color = Cores.TextoSuave)
            }
        }
    }
}

@Composable
private fun AzulejoDoPr(pr: PrEntity, modifier: Modifier = Modifier) {
    Azulejo(modifier = modifier) {
        Rotulo(pr.exercicio)
        val carga = pr.cargaKg
        if (carga != null) {
            Row {
                Text(
                    text = carga.semZeros(),
                    style = Tipos.NumeroGrande.copy(fontSize = 34.sp, lineHeight = 34.sp),
                    color = Cores.Texto,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = " kg",
                    style = Tipos.Botao.copy(fontSize = 15.sp),
                    color = Cores.TextoSuave,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        } else {
            // O treinador escreveu de um jeito que não é "número + kg": mostra como veio.
            Text(text = pr.texto, style = Tipos.CorpoForte, color = Cores.Texto)
        }
        val notas = remember(pr.texto) { notasDoPr(pr.texto) }
        for (nota in notas) {
            Pilula(nota, Cores.NovoFundo, Cores.NovoTexto, altura = 22.dp)
        }
    }
}

/** Um bloco de texto do plano. Fechado mostra só o título; tocar abre as linhas. */
@Composable
private fun CartaoDeTexto(bloco: BlocoInfoEntity) {
    val linhas = remember(bloco.texto) { linhasDeInfo(bloco.texto) }
    // Bloco de uma linha só já vem aberto: não há o que esconder.
    var aberto by rememberSaveable(bloco.id) { mutableStateOf(linhas.size <= 1) }

    Cartao(espaco = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { aberto = !aberto },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = semSimbolosNoInicio(bloco.titulo ?: bloco.secao).uppercase(),
                style = Tipos.TituloDeSecao,
                color = Cores.Texto,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(if (aberto) R.drawable.ic_abaixo else R.drawable.ic_seguinte),
                contentDescription = if (aberto) "Fechar" else "Abrir",
                tint = Cores.TextoSuave,
                modifier = Modifier.size(18.dp),
            )
        }
        if (aberto) {
            linhas.forEachIndexed { i, linha ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = linha.titulo,
                        // Com detalhe embaixo, a linha de cima é o nome do item; sozinha, é texto corrido.
                        style = if (linha.detalhe != null) Tipos.CorpoForte else Tipos.Corpo,
                        color = Cores.Texto,
                    )
                    linha.detalhe?.let {
                        Text(text = it, style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
                    }
                }
                if (i < linhas.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Cores.Divisor),
                    )
                }
            }
        }
    }
}
