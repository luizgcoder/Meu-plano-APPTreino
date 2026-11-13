package com.luiz.meuplano.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.luiz.meuplano.R
import com.luiz.meuplano.data.TrocaDoDiaEntity
import com.luiz.meuplano.feedback.NOME_DO_APP
import com.luiz.meuplano.trocas.ATALHOS
import com.luiz.meuplano.trocas.Alimento
import com.luiz.meuplano.trocas.Atalho
import com.luiz.meuplano.trocas.BancoDeAlimentos
import com.luiz.meuplano.trocas.Categoria
import com.luiz.meuplano.trocas.ItemReconhecido
import com.luiz.meuplano.trocas.Sentido
import com.luiz.meuplano.trocas.Troca
import com.luiz.meuplano.trocas.calcularTroca
import com.luiz.meuplano.trocas.combinaCom
import com.luiz.meuplano.trocas.destinoValido
import com.luiz.meuplano.trocas.destinosPara
import com.luiz.meuplano.trocas.detalhe
import com.luiz.meuplano.trocas.diferenca
import com.luiz.meuplano.trocas.inteiro
import com.luiz.meuplano.trocas.porUnidadeTexto
import com.luiz.meuplano.trocas.quantidadeAoMudarOrigem
import com.luiz.meuplano.trocas.quantidadeTexto
import com.luiz.meuplano.trocas.umaCasa
import kotlin.math.roundToInt

/** O exemplo com que o montador abre quando não veio de um item da dieta. */
private const val ORIGEM_DE_EXEMPLO = "Arroz branco cozido"
private const val DESTINO_DE_EXEMPLO = "Batata-doce cozida"
private const val QUANTIDADE_DE_EXEMPLO = 150.0

private val CantoDoCampo = RoundedCornerShape(12.dp)

/** A cor de cada categoria, como no material do treinador. */
private fun Categoria.cor(): Color = when (this) {
    Categoria.CARB -> Color(0xFFE9B23C)
    Categoria.PTN -> Color(0xFFC8632E)
    Categoria.FRUTA -> Color(0xFFB0455A)
    Categoria.GORD -> Color(0xFFA39E45)
}

/**
 * O item da dieta que abriu o montador.
 *
 * @param texto o item como está escrito na dieta ("280g arroz branco ou macarrão de arroz")
 * @param reconhecido o alimento do banco e a quantidade que o app leu desse texto
 * @param trocaSalva a troca já feita neste item no dia aberto, se houver
 */
@Immutable
data class ItemParaTrocar(
    val itemId: Long,
    val texto: String,
    val reconhecido: ItemReconhecido,
    val trocaSalva: TrocaDoDiaEntity?,
)

/**
 * O Montador de Trocas dentro do app: escolhe o que está na dieta, a
 * quantidade, e troca por um ou dois alimentos mantendo as calorias. Embaixo fica o
 * banco de alimentos, com busca.
 *
 * Aberto por um item da dieta ([daDieta]), já vem preenchido e ganha o botão de usar a
 * troca naquele dia. Sem item, funciona solto, como calculadora.
 *
 * @param onUsar guarda a troca para o dia: origem, quantidade, destinos e proporção
 * @param onDesfazer apaga a troca guardada do item
 */
@Composable
fun TelaTrocas(
    daDieta: ItemParaTrocar?,
    onUsar: (origem: String, quantidade: Double, destino1: String, destino2: String?, proporcao: Int) -> Unit,
    onDesfazer: () -> Unit,
    onFechar: () -> Unit,
) {
    // Uma troca guardada só vale se os alimentos dela ainda estão no banco.
    val salva = remember(daDieta) { daDieta?.trocaSalva?.takeIf { it.calcular() != null } }
    val lido = daDieta?.reconhecido

    var origem by rememberSaveable { mutableStateOf(salva?.origem ?: lido?.alimento?.nome ?: ORIGEM_DE_EXEMPLO) }
    var quantidade by rememberSaveable {
        val inicial = when {
            salva != null -> salva.quantidade
            lido != null -> lido.quantidade
            else -> QUANTIDADE_DE_EXEMPLO
        }
        mutableStateOf(inicial?.let(::umaCasa).orEmpty())
    }
    var todas by rememberSaveable {
        // Se a troca guardada foi para outra categoria, abre já mostrando todas.
        val categoria = BancoDeAlimentos.porNome(salva?.origem)?.categoria
        val destinos = listOfNotNull(salva?.destino1, salva?.destino2)
        mutableStateOf(destinos.any { BancoDeAlimentos.porNome(it)?.categoria != categoria })
    }
    var destino1 by rememberSaveable {
        val inicial = salva?.destino1
            ?: lido?.let { destinoValido(null, it.alimento, false) }
            ?: DESTINO_DE_EXEMPLO
        mutableStateOf(inicial)
    }
    var destino2 by rememberSaveable { mutableStateOf(salva?.destino2) }
    var proporcao by rememberSaveable { mutableIntStateOf(salva?.proporcao ?: 50) }

    // Qual seletor de alimento está aberto: 0 = origem, 1 = primeiro destino, 2 = segundo. Nulo = nenhum.
    var seletor by rememberSaveable { mutableStateOf<Int?>(null) }
    var busca by rememberSaveable { mutableStateOf("") }

    val de = BancoDeAlimentos.porNome(origem) ?: BancoDeAlimentos.ALIMENTOS.first()
    val para1 = BancoDeAlimentos.porNome(destino1) ?: de
    val para2 = BancoDeAlimentos.porNome(destino2)
    val numero = lerNumero(quantidade) ?: 0.0
    val troca = remember(de, numero, para1, para2, proporcao) { calcularTroca(de, numero, para1, para2, proporcao) }

    fun escolherOrigem(novo: Alimento) {
        origem = novo.nome
        quantidade = umaCasa(quantidadeAoMudarOrigem(novo, numero))
        val primeiro = destinoValido(destino1, novo, todas)
        destino1 = primeiro
        if (destino2 != null) destino2 = destinoValido(destino2, novo, todas, setOf(primeiro))
    }

    fun mostrarTodas(valor: Boolean) {
        todas = valor
        val primeiro = destinoValido(destino1, de, valor)
        destino1 = primeiro
        if (destino2 != null) destino2 = destinoValido(destino2, de, valor, setOf(primeiro))
    }

    fun aplicar(atalho: Atalho) {
        val novaOrigem = BancoDeAlimentos.porNome(atalho.origem) ?: return
        val destinos = atalho.destinos.mapNotNull { BancoDeAlimentos.porNome(it) }
        val primeiro = destinos.firstOrNull() ?: return
        origem = novaOrigem.nome
        quantidade = umaCasa(atalho.quantidade)
        todas = destinos.any { it.categoria != novaOrigem.categoria }
        destino1 = primeiro.nome
        destino2 = destinos.getOrNull(1)?.nome
        proporcao = atalho.proporcao
    }

    BackHandler(onBack = onFechar)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cores.Fundo)
            .systemBarsPadding()
            .imePadding(),
    ) {
        CabecalhoDeTela(
            sobretitulo = NOME_DO_APP,
            titulo = "Montador de trocas",
            acao = { BotaoQuadrado(R.drawable.ic_fechar, "Fechar o montador", onFechar) },
        )

        val alimentosDoBanco = remember(busca) { BancoDeAlimentos.ALIMENTOS.filter { it.combinaCom(busca) } }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (daDieta != null) {
                item(key = "da-dieta") {
                    Cartao(espaco = 4.dp) {
                        Rotulo("Da sua dieta")
                        Text(text = daDieta.texto, style = Tipos.CorpoForte, color = Cores.Texto)
                    }
                }
            }

            item(key = "secao-montador") {
                CabecalhoDeSecao("01", "Montador de refeição", "Escolha, pese e troque — mantendo as mesmas calorias")
            }

            item(key = "montador") {
                Cartao(espaco = 10.dp) {
                    // ----- 1: o que está na dieta -----
                    Passo("1", "O que está na sua dieta")
                    CampoDeAlimento(de, mostrarCategoria = true, onClick = { seletor = 0 })
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = quantidade,
                            onValueChange = { quantidade = it },
                            singleLine = true,
                            textStyle = Tipos.NumeroMedio.copy(color = Cores.Texto, textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(110.dp),
                        )
                        Text(
                            text = if (de.emUnidades) "un" else "g",
                            style = Tipos.NumeroMedio,
                            color = Cores.TextoSuave,
                        )
                        Text(text = "=", style = Tipos.NumeroMedio, color = Cores.TextoApagado)
                        Pilula(
                            texto = "${inteiro(troca.kcalAlvo)} kcal",
                            fundo = Cores.Fundo,
                            cor = Cores.Destaque,
                            borda = Cores.Trilho,
                            altura = 36.dp,
                        )
                    }

                    Box(Modifier.height(4.dp))

                    // ----- 2: pelo que trocar -----
                    Passo("2", "Trocar por (1 ou 2 alimentos)")
                    CampoDeAlimento(para1, onClick = { seletor = 1 })
                    if (para2 == null) {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .bordaTracejada(Cores.Destaque, 18.dp)
                                .clickable { destino2 = destinoValido(null, de, todas, setOf(destino1)) }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "+ COMBINAR 2º ALIMENTO", style = Tipos.Rotulo, color = Cores.Destaque)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CantoDoCampo)
                                .background(Cores.Fundo)
                                .bordaTracejada(Cores.BordaForte, 12.dp)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CampoDeAlimento(para2, fundo = Cores.Superficie, onClick = { seletor = 2 })
                            Row {
                                Text(
                                    text = "Quanto das calorias vai no 1º alimento: ",
                                    style = Tipos.CorpoPequeno,
                                    color = Cores.TextoSuave,
                                )
                                Text(
                                    text = "$proporcao% / ${100 - proporcao}%",
                                    style = Tipos.CorpoPequeno.copy(fontWeight = Tipos.Botao.fontWeight),
                                    color = Cores.Texto,
                                )
                            }
                            Slider(
                                value = proporcao.toFloat(),
                                onValueChange = { proporcao = it.roundToInt().coerceIn(0, 100) },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Cores.Destaque,
                                    activeTrackColor = Cores.Destaque,
                                    inactiveTrackColor = Cores.BordaForte,
                                ),
                            )
                            Text(
                                text = "remover 2º alimento",
                                style = Tipos.CorpoPequeno,
                                color = Cores.Gravar,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { destino2 = null }
                                    .padding(vertical = 6.dp, horizontal = 2.dp),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { mostrarTodas(!todas) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = todas,
                            onCheckedChange = { mostrarTodas(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Cores.Destaque,
                                uncheckedColor = Cores.BordaForte,
                                checkmarkColor = Cores.Fundo,
                            ),
                        )
                        Text(
                            text = "trocar por outra categoria (ver todos os alimentos)",
                            style = Tipos.CorpoPequeno,
                            color = Cores.TextoSuave,
                        )
                    }
                }
            }

            item(key = "resultado") {
                CartaoDoResultado(troca = troca, semQuantidade = numero <= 0.0)
            }

            // Os atalhos trocam também o alimento de origem, então só fazem sentido no montador solto.
            if (daDieta == null) {
                item(key = "atalhos") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Rotulo("Atalhos rápidos")
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ATALHOS.forEach { atalho ->
                                Pilula(
                                    texto = atalho.rotulo,
                                    fundo = Cores.Superficie,
                                    cor = Cores.Texto,
                                    borda = Cores.Trilho,
                                    altura = 36.dp,
                                    onClick = { aplicar(atalho) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "aviso") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Cores.NovoFundo)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Rotulo("Leia antes de trocar", cor = Cores.Destaque)
                    Text(
                        text = "Essas trocas batem só a caloria. É uma estratégia que mira apenas as calorias — " +
                            "e cada alimento tem uma resposta diferente em cada pessoa (digestão, saciedade, " +
                            "glicemia, retenção, intestino). Por isso, antes de fixar uma troca na rotina, mande " +
                            "para o treinador qual substituição você quer fazer, para ele avaliar o quanto " +
                            "compensa de verdade no seu caso.",
                        style = Tipos.CorpoPequeno.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = Cores.Texto,
                    )
                }
            }

            item(key = "secao-banco") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.height(8.dp))
                    CabecalhoDeSecao("02", "Banco de alimentos", "Valores por 100 g (preparados) — base dos cálculos")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Categoria.entries.forEach { categoria ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Ponto(categoria)
                                Text(text = categoria.rotulo, style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
                            }
                        }
                    }
                    Text(
                        text = "* = estimativa de rótulo/USDA (fora da TACO)",
                        style = Tipos.CorpoPequeno,
                        color = Cores.TextoApagado,
                    )
                    OutlinedTextField(
                        value = busca,
                        onValueChange = { busca = it },
                        placeholder = { Text("Buscar alimento...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Cores.Superficie)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Rotulo("Alimento", modifier = Modifier.weight(1f), cor = Cores.Destaque)
                        listOf("kcal", "Prot", "Gord", "Carb").forEach { coluna ->
                            Text(
                                text = coluna.uppercase(),
                                style = Tipos.Rotulo,
                                color = Cores.Destaque,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(LARGURA_DA_COLUNA),
                            )
                        }
                    }
                }
            }

            items(alimentosDoBanco, key = { "banco-${it.nome}" }) { alimento ->
                LinhaDoBanco(alimento)
            }
            if (alimentosDoBanco.isEmpty()) {
                item(key = "banco-vazio") {
                    Text(
                        text = "Nenhum alimento com esse nome no banco.",
                        style = Tipos.CorpoPequeno,
                        color = Cores.TextoSuave,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            item(key = "fonte") {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "DÚVIDA OU QUER VALIDAR UMA TROCA? MANDA MENSAGEM.",
                        style = Tipos.TituloDeSecao,
                        color = Cores.Destaque,
                    )
                    Text(
                        text = "Valores por 100 g (cozidos/preparados) da TACO — Tabela Brasileira de Composição " +
                            "de Alimentos, 4ª ed. (NEPA/Unicamp, 2011). Itens com * não constam na TACO — valores " +
                            "de rótulo/USDA: tapioca, maltodextrina, pão de forma, pão de hambúrguer Bauducco, " +
                            "whey, atum na água, tilápia, pescada grelhada, filé/coração de boi, macarrão e " +
                            "grão-de-bico cozidos, inhame cozido, açaí e uva-passa.\n" +
                            "Material de apoio à dieta. A decisão final da troca é sempre do seu treinador.",
                        style = Tipos.CorpoPequeno,
                        color = Cores.TextoApagado,
                    )
                }
            }
        }

        // ----- usar a troca no dia (só quando veio de um item da dieta) -----
        if (daDieta != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Cores.Fundo)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (daDieta.trocaSalva != null) {
                    BotaoContorno(
                        texto = "Desfazer",
                        onClick = onDesfazer,
                        modifier = Modifier.weight(1f),
                        altura = 48.dp,
                        fundo = Cores.Superficie,
                    )
                }
                BotaoPrincipal(
                    texto = "Usar troca neste dia",
                    onClick = { onUsar(de.nome, numero, para1.nome, para2?.nome, proporcao) },
                    modifier = Modifier.weight(2f),
                    icone = R.drawable.ic_feito,
                    ativo = numero > 0.0,
                )
            }
        }
    }

    val aberto = seletor
    if (aberto != null) {
        SeletorDeAlimento(
            titulo = if (aberto == 0) "O que está na sua dieta" else "Trocar por",
            alimentos = if (aberto == 0) BancoDeAlimentos.ALIMENTOS else destinosPara(de, todas),
            selecionado = when (aberto) {
                0 -> de.nome
                1 -> para1.nome
                else -> para2?.nome
            },
            onEscolher = { escolhido ->
                when (aberto) {
                    0 -> escolherOrigem(escolhido)
                    1 -> destino1 = escolhido.nome
                    else -> destino2 = escolhido.nome
                }
                seletor = null
            },
            onFechar = { seletor = null },
        )
    }
}

private val LARGURA_DA_COLUNA = 44.dp

/** O número laranja grande com o título da seção ao lado, como no material do treinador. */
@Composable
private fun CabecalhoDeSecao(numero: String, titulo: String, subtitulo: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = numero,
            style = Tipos.TituloDeTela.copy(fontSize = 48.sp, lineHeight = 46.sp),
            color = Cores.Destaque,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = titulo.uppercase(), style = Tipos.TituloDeCartao, color = Cores.Texto)
            Text(text = subtitulo, style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
        }
    }
}

/** "① O QUE ESTÁ NA SUA DIETA" */
@Composable
private fun Passo(numero: String, texto: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Cores.Destaque),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = numero, style = Tipos.Rotulo.copy(letterSpacing = 0.sp), color = Cores.Fundo)
        }
        Rotulo(texto, cor = Cores.Texto)
    }
}

/** A bolinha da cor da categoria. */
@Composable
private fun Ponto(categoria: Categoria) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(categoria.cor()),
    )
}

/** O campo que mostra o alimento escolhido e abre a lista para trocar. */
@Composable
private fun CampoDeAlimento(
    alimento: Alimento,
    onClick: () -> Unit,
    mostrarCategoria: Boolean = false,
    fundo: Color = Cores.Fundo,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CantoDoCampo)
            .background(fundo)
            .border(1.dp, Cores.BordaForte, CantoDoCampo)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ponto(alimento.categoria)
        Text(
            text = alimento.nomeComMarca(),
            style = Tipos.CorpoForte,
            color = Cores.Texto,
            modifier = Modifier.weight(1f),
        )
        if (mostrarCategoria) {
            Pilula(
                texto = alimento.categoria.rotulo.uppercase(),
                fundo = alimento.categoria.cor(),
                cor = Cores.Fundo,
                altura = 22.dp,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_abaixo),
            contentDescription = "Escolher outro alimento",
            tint = Cores.TextoSuave,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** O nome com o asterisco dos valores que não vêm da TACO. */
private fun Alimento.nomeComMarca(): String = if (estimado) "$nome *" else nome

/** O cartão escuro do resultado: quanto pesar de cada alimento e a comparação dos macros. */
@Composable
private fun CartaoDoResultado(troca: Troca, semQuantidade: Boolean) {
    Cartao(destacado = true, espaco = 10.dp) {
        if (semQuantidade) {
            Rotulo("Resultado", cor = Cores.Destaque)
            Text(
                text = "Digite a quantidade do alimento da dieta para ver a troca.",
                style = Tipos.Corpo,
                color = Cores.TextoSuave,
            )
        } else {
            Rotulo("Pese assim para bater ${inteiro(troca.kcalAlvo)} kcal", cor = Cores.Destaque)
            troca.porcoes.forEachIndexed { i, porcao ->
                if (i > 0) Divisoria()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = porcao.quantidadeTexto(),
                        style = Tipos.Numero,
                        color = Cores.Destaque,
                        modifier = Modifier.width(92.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = porcao.alimento.nomeComMarca(), style = Tipos.CorpoForte, color = Cores.Texto)
                        Text(text = porcao.detalhe(), style = Tipos.CorpoPequeno, color = Cores.TextoSuave)
                    }
                }
            }
            Divisoria()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaixaDeMacro("kcal", "", troca.de.kcal, troca.para.kcal, Modifier.weight(1f))
                CaixaDeMacro("Proteína", "g", troca.de.proteina, troca.para.proteina, Modifier.weight(1f))
                CaixaDeMacro("Carbo", "g", troca.de.carbo, troca.para.carbo, Modifier.weight(1f))
                CaixaDeMacro("Gordura", "g", troca.de.gordura, troca.para.gordura, Modifier.weight(1f))
            }
            val aviso = troca.aviso
            if (aviso != null) {
                Text(text = aviso, style = Tipos.CorpoPequeno, color = Cores.NovoTexto)
            }
        }
    }
}

/** A linha fina que separa as partes de um cartão. */
@Composable
private fun Divisoria() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Cores.Divisor),
    )
}

/** Um macro depois da troca e o quanto mudou em relação ao alimento original. */
@Composable
private fun CaixaDeMacro(rotulo: String, unidade: String, de: Double, para: Double, modifier: Modifier = Modifier) {
    val (texto, sentido) = diferenca(de, para)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Cores.Fundo)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = rotulo.uppercase(),
            style = Tipos.Rotulo.copy(fontSize = 9.sp, letterSpacing = 0.sp),
            color = Cores.TextoSuave,
            maxLines = 1,
        )
        Text(text = umaCasa(para) + unidade, style = Tipos.NumeroMedio, color = Cores.Texto, maxLines = 1)
        Text(
            text = texto,
            style = Tipos.CorpoPequeno.copy(fontSize = 11.sp, fontWeight = Tipos.Botao.fontWeight),
            color = when (sentido) {
                Sentido.SUBIU -> Cores.FeitoTexto
                Sentido.DESCEU -> Cores.NovoTexto
                Sentido.IGUAL -> Cores.TextoApagado
            },
            maxLines = 1,
        )
    }
}

/** Uma linha da tabela do banco: o alimento e seus valores por 100 g. */
@Composable
private fun LinhaDoBanco(alimento: Alimento) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ponto(alimento.categoria)
                Column {
                    Text(text = alimento.nomeComMarca(), style = Tipos.CorpoPequeno.copy(fontSize = 14.sp), color = Cores.Texto)
                    alimento.porUnidadeTexto()?.let {
                        Text(text = it, style = Tipos.CorpoPequeno.copy(fontSize = 11.sp), color = Cores.TextoApagado)
                    }
                }
            }
            Text(
                text = umaCasa(alimento.kcal),
                style = Tipos.NumeroMedio.copy(fontSize = 16.sp),
                color = Cores.Destaque,
                textAlign = TextAlign.End,
                modifier = Modifier.width(LARGURA_DA_COLUNA),
            )
            listOf(alimento.proteina, alimento.gordura, alimento.carbo).forEach { valor ->
                Text(
                    text = umaCasa(valor),
                    style = Tipos.CorpoPequeno,
                    color = Cores.TextoSuave,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(LARGURA_DA_COLUNA),
                )
            }
        }
        Divisoria()
    }
}

/** A lista para escolher um alimento, separada por categoria e com busca. */
@Composable
private fun SeletorDeAlimento(
    titulo: String,
    alimentos: List<Alimento>,
    selecionado: String?,
    onEscolher: (Alimento) -> Unit,
    onFechar: () -> Unit,
) {
    var busca by remember { mutableStateOf("") }
    val filtrados = remember(alimentos, busca) { alimentos.filter { it.combinaCom(busca) } }

    Dialog(onDismissRequest = onFechar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Cores.Superficie)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = titulo.uppercase(), style = Tipos.TituloDeSecao, color = Cores.Texto)
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                placeholder = { Text("Buscar alimento...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                Categoria.entries.forEach { categoria ->
                    val daCategoria = filtrados.filter { it.categoria == categoria }
                    if (daCategoria.isNotEmpty()) {
                        item(key = "categoria-${categoria.name}") {
                            Rotulo(
                                texto = categoria.plural,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                cor = categoria.cor(),
                            )
                        }
                        items(daCategoria, key = { it.nome }) { alimento ->
                            val escolhido = alimento.nome == selecionado
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (escolhido) Cores.Trilho else Color.Transparent)
                                    .clickable { onEscolher(alimento) }
                                    .padding(horizontal = 10.dp, vertical = 11.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = alimento.nomeComMarca(),
                                    style = if (escolhido) Tipos.CorpoForte else Tipos.Corpo,
                                    color = Cores.Texto,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${umaCasa(alimento.kcal)} kcal",
                                    style = Tipos.CorpoPequeno,
                                    color = Cores.TextoSuave,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                    }
                }
                if (filtrados.isEmpty()) {
                    item(key = "vazio") {
                        Text(
                            text = "Nenhum alimento com esse nome.",
                            style = Tipos.CorpoPequeno,
                            color = Cores.TextoSuave,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
            BotaoContorno("Cancelar", onFechar, Modifier.fillMaxWidth(), fundo = Cores.Superficie)
        }
    }
}
