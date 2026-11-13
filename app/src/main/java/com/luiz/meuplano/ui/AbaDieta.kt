package com.luiz.meuplano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luiz.meuplano.R
import com.luiz.meuplano.data.CheckinDiarioEntity
import com.luiz.meuplano.data.ItemRefeicaoEntity
import com.luiz.meuplano.data.PlanoCompleto
import com.luiz.meuplano.data.RefeicaoComOpcoes
import com.luiz.meuplano.data.TrocaDoDiaEntity
import com.luiz.meuplano.trocas.quantidadeTexto
import com.luiz.meuplano.trocas.reconhecerItem
import java.time.LocalDate

/**
 * O dia a dia da dieta, no layout do design: o dia no topo com as setas, os dois
 * números do dia (água e refeições), as refeições e, por fim, o fechamento do dia.
 * Tudo é salvo na hora, por data.
 *
 * @param onAbrirMontador abre o Montador de Trocas: com o id de um item da dieta, já
 *   preenchido com ele; com nulo, solto
 */
@Composable
fun AbaDieta(plano: PlanoCompleto, viewModel: PlanoViewModel, onAbrirMontador: (Long?) -> Unit) {
    val estado by viewModel.estadoDoDia.collectAsStateWithLifecycle()
    val hoje = LocalDate.now()
    val feitas = plano.refeicoes.count { it.refeicao.id in estado.refeicoesFeitas }

    Column(modifier = Modifier.fillMaxSize()) {
        // ----- o dia, com as setas -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotaoQuadrado(R.drawable.ic_anterior, "Dia anterior", viewModel::diaAnterior)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = viewModel::irParaHoje),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = diaRelativo(estado.dia, hoje).uppercase(),
                    style = Tipos.Sobretitulo,
                    color = Cores.TextoSuave,
                )
                Text(
                    text = diaEmDestaque(estado.dia),
                    style = Tipos.TituloDeTela.copy(fontSize = 34.sp, lineHeight = 34.sp),
                    color = Cores.Texto,
                )
            }
            BotaoQuadrado(
                R.drawable.ic_seguinte, "Próximo dia", viewModel::diaSeguinte,
                ativo = estado.dia.isBefore(hoje),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "numeros") {
                NumerosDoDia(
                    aguaMl = estado.checkin.aguaMl,
                    metaLitros = plano.plano.metaAguaLitros,
                    refeicoes = plano.refeicoes.map { it.refeicao.id in estado.refeicoesFeitas },
                    feitas = feitas,
                )
            }
            item(key = "agua") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotaoContorno("+250 ml", { viewModel.somarAgua(250) }, Modifier.weight(1f), fundo = Cores.Superficie)
                    BotaoContorno("+500 ml", { viewModel.somarAgua(500) }, Modifier.weight(1f), fundo = Cores.Superficie)
                    BotaoContorno(
                        "−250 ml", { viewModel.somarAgua(-250) }, Modifier.weight(1f),
                        ativo = estado.checkin.aguaMl > 0, fundo = Cores.Superficie,
                    )
                }
            }
            item(key = "montador") {
                BotaoContorno(
                    texto = "Montador de trocas",
                    onClick = { onAbrirMontador(null) },
                    modifier = Modifier.fillMaxWidth(),
                    icone = R.drawable.ic_trocar,
                    fundo = Cores.Superficie,
                )
            }
            itemsIndexed(plano.refeicoes, key = { _, item -> "refeicao-${item.refeicao.id}" }) { i, refeicao ->
                val id = refeicao.refeicao.id
                CartaoRefeicao(
                    posicao = i,
                    item = refeicao,
                    dia = estado.dia,
                    feita = id in estado.refeicoesFeitas,
                    opcaoComida = estado.refeicoesFeitas[id],
                    trocas = estado.trocas,
                    onMarcar = { opcaoId -> viewModel.alternarRefeicao(id, opcaoId) },
                    onTrocar = onAbrirMontador,
                )
            }
            item(key = "fechamento") {
                CartaoFechamento(estado = estado, onAlterar = viewModel::alterarCheckin)
            }
        }
    }
}

/** Os dois cartões lado a lado: litros de água sobre a meta e refeições feitas sobre o total. */
@Composable
private fun NumerosDoDia(aguaMl: Int, metaLitros: Double?, refeicoes: List<Boolean>, feitas: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Cartao(modifier = Modifier.weight(1f), espaco = 8.dp) {
            Rotulo("Água")
            NumeroComUnidade(
                numero = (aguaMl / 1000.0).let { "%.1f".format(it).replace('.', ',') },
                unidade = if (metaLitros != null) " / ${metaLitros.semZeros()} L" else " L",
            )
            val fracao = if (metaLitros != null && metaLitros > 0) (aguaMl / (metaLitros * 1000)).toFloat() else 0f
            BarraDeProgresso(fracao, Cores.Agua)
        }
        Cartao(modifier = Modifier.weight(1f), espaco = 8.dp) {
            Rotulo("Refeições")
            NumeroComUnidade(numero = feitas.toString(), unidade = " / ${refeicoes.size}")
            // Um tracinho por refeição do dia.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (feita in refeicoes) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (feita) Cores.Destaque else Cores.Trilho),
                    )
                }
            }
        }
    }
}

/** Número grande com a unidade menor ao lado, alinhados pela base: "1,5 / 4 L". */
@Composable
fun NumeroComUnidade(numero: String, unidade: String, cor: Color = Cores.Texto) {
    Row {
        Text(text = numero, style = Tipos.NumeroGrande, color = cor, modifier = Modifier.alignByBaseline())
        Text(
            text = unidade,
            style = Tipos.NumeroMedio.copy(fontWeight = FontWeight.SemiBold),
            color = Cores.TextoSuave,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * Um alimento da refeição, pronto para desenhar.
 *
 * @param trocavel verdadeiro se o alimento está no banco do Montador de Trocas
 */
private class LinhaDaRefeicao(val item: ItemRefeicaoEntity, val oQue: String, val quanto: String?, val trocavel: Boolean)

/**
 * @param opcaoComida id da opção marcada como comida neste dia (quando [feita])
 * @param trocas id do item da refeição -> a troca feita nele neste dia
 * @param onMarcar marca a refeição com a opção dada; a mesma opção de novo desmarca
 * @param onTrocar abre o Montador de Trocas para o item com este id
 */
@Composable
private fun CartaoRefeicao(
    posicao: Int,
    item: RefeicaoComOpcoes,
    dia: LocalDate,
    feita: Boolean,
    opcaoComida: Long?,
    trocas: Map<Long, TrocaDoDiaEntity>,
    onMarcar: (Long?) -> Unit,
    onTrocar: (Long) -> Unit,
) {
    val (ordinal, nome) = remember(item.refeicao.titulo, posicao) { tituloDaRefeicao(item.refeicao.titulo, posicao) }
    // Opção só olhada, ainda não marcada. Volta ao começo quando muda o dia.
    var olhando by rememberSaveable(item.refeicao.id, dia.toString()) { mutableStateOf<Long?>(null) }
    val mostrada = item.opcoes.firstOrNull { it.opcao.id == (if (feita) opcaoComida else olhando) }
        ?: item.opcoes.firstOrNull()

    Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = ordinal,
                    style = Tipos.TituloDeCartao.copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp),
                    color = Cores.Destaque,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = nome.uppercase(),
                    style = Tipos.TituloDeSecao,
                    color = Cores.Texto,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            if (feita) {
                Pilula(
                    texto = "Feita",
                    fundo = Cores.FeitoFundo,
                    cor = Cores.FeitoTexto,
                    icone = R.drawable.ic_feito,
                    altura = 36.dp,
                    onClick = { onMarcar(mostrada?.opcao?.id) },
                )
            } else {
                Pilula(
                    texto = "Marcar comida",
                    fundo = Color.Transparent,
                    cor = Cores.Texto,
                    borda = Cores.BordaForte,
                    altura = 36.dp,
                    onClick = { onMarcar(mostrada?.opcao?.id) },
                )
            }
        }

        if (item.opcoes.size > 1) {
            Segmentos(
                opcoes = remember(item.opcoes) { item.opcoes.map { rotuloDaOpcao(it.opcao.nome) } },
                selecionada = mostrada?.let { item.opcoes.indexOf(it) }?.takeIf { it >= 0 },
                onSelecionar = { i ->
                    val escolhida = item.opcoes[i].opcao.id
                    // Com a refeição já feita, trocar a opção corrige o que foi comido.
                    if (feita) {
                        if (escolhida != opcaoComida) onMarcar(escolhida)
                    } else {
                        olhando = escolhida
                    }
                },
            )
        }

        if (mostrada != null) {
            // Separar nome e quantidade e procurar no banco usam expressão regular:
            // faz uma vez por opção, não a cada desenho.
            val alimentos = remember(mostrada) {
                mostrada.itens.map { item ->
                    val (oQue, quanto) = separarQuantidade(item.texto)
                    LinhaDaRefeicao(item, oQue, quanto, trocavel = reconhecerItem(item.texto) != null)
                }
            }
            Column {
                alimentos.forEachIndexed { i, linha ->
                    val salva = trocas[linha.item.id]
                    val troca = remember(salva) { salva?.calcular() }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (troca != null) {
                            // Trocado neste dia: mostra o que comer no lugar e, embaixo, o item original.
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                troca.porcoes.forEach { porcao ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = porcao.alimento.nome,
                                            style = Tipos.Corpo,
                                            color = Cores.Texto,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = porcao.quantidadeTexto(),
                                            style = Tipos.NumeroMedio.copy(fontSize = 17.sp),
                                            color = Cores.Destaque,
                                        )
                                    }
                                }
                                Text(
                                    text = "No lugar de: ${linha.item.texto}",
                                    style = Tipos.CorpoPequeno,
                                    color = Cores.TextoApagado,
                                )
                            }
                        } else {
                            Text(
                                text = linha.oQue,
                                style = Tipos.Corpo,
                                color = if (linha.item.opcional) Cores.TextoSuave else Cores.Texto,
                                modifier = Modifier.weight(1f),
                            )
                            if (linha.quanto != null) {
                                Text(
                                    text = linha.quanto,
                                    style = Tipos.NumeroMedio.copy(fontSize = 17.sp),
                                    color = Cores.Texto,
                                )
                            }
                        }
                        if (linha.trocavel) {
                            BotaoQuadrado(
                                icone = R.drawable.ic_trocar,
                                descricao = if (troca != null) "Mudar ou desfazer a troca" else "Trocar por outro alimento",
                                onClick = { onTrocar(linha.item.id) },
                                cor = if (troca != null) Cores.Destaque else Cores.TextoSuave,
                                fundo = if (troca != null) Cores.NovoFundo else Cores.Fundo,
                                tamanho = 36.dp,
                            )
                        }
                    }
                    if (i < alimentos.size - 1) {
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
}

/**
 * O check-in do dia: só o "furei a dieta". Sem marcar, o dia conta como dieta seguida
 * no feedback. Tocar de novo desmarca.
 */
@Composable
private fun CartaoFechamento(
    estado: EstadoDoDia,
    onAlterar: ((CheckinDiarioEntity) -> CheckinDiarioEntity) -> Unit,
) {
    val furou = estado.checkin.furouDieta
    val canto = RoundedCornerShape(18.dp)
    val cantoDaCaixa = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(canto)
            .background(if (furou) Cores.NovoFundo else Cores.Superficie)
            .then(if (furou) Modifier.border(2.dp, Cores.Destaque, canto) else Modifier)
            .clickable { onAlterar { it.copy(furouDieta = !it.furouDieta) } }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(cantoDaCaixa)
                .background(if (furou) Cores.Destaque else Color.Transparent)
                .border(2.dp, if (furou) Cores.Destaque else Cores.BordaForte, cantoDaCaixa),
            contentAlignment = Alignment.Center,
        ) {
            if (furou) {
                Icon(
                    painter = painterResource(R.drawable.ic_feito),
                    contentDescription = null,
                    tint = Cores.Fundo,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = "FUREI A DIETA HOJE", style = Tipos.TituloDeSecao, color = Cores.Texto)
            Text(
                text = if (furou) {
                    "Marcado. Entra na aderência do feedback da semana."
                } else {
                    "Marque só se saiu do plano. Sem marcar, o dia conta como dieta seguida."
                },
                style = Tipos.CorpoPequeno,
                color = Cores.TextoSuave,
            )
        }
    }
}
