package com.luiz.meuplano.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * As peças do design que se repetem nas telas: cartão, cabeçalho, botões,
 * pílula, barra de progresso e o seletor de opções.
 */

private val CantoDoCartao = RoundedCornerShape(18.dp)
private val CantoDoBotao = RoundedCornerShape(12.dp)

/** O bloco cinza-chumbo de cantos arredondados. [destacado] põe o contorno laranja do "agora". */
@Composable
fun Cartao(
    modifier: Modifier = Modifier,
    destacado: Boolean = false,
    espaco: Dp = 12.dp,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CantoDoCartao)
            .background(Cores.Superficie)
            .then(if (destacado) Modifier.border(2.dp, Cores.Destaque, CantoDoCartao) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(espaco),
        content = conteudo,
    )
}

/** Cartão pequeno de número: "TREINOS 1/5", "SQUAT 140 kg". */
@Composable
fun Azulejo(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Cores.Superficie)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = conteudo,
    )
}

/** Etiqueta pequena em maiúsculas. */
@Composable
fun Rotulo(texto: String, modifier: Modifier = Modifier, cor: Color = Cores.TextoSuave) {
    Text(text = texto.uppercase(), style = Tipos.Rotulo, color = cor, modifier = modifier)
}

/** O topo de cada tela: uma linha pequena, o título grande e, se houver, um botão à direita. */
@Composable
fun CabecalhoDeTela(sobretitulo: String, titulo: String, acao: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = sobretitulo.uppercase(), style = Tipos.Sobretitulo, color = Cores.TextoSuave)
            Text(text = titulo.uppercase(), style = Tipos.TituloDeTela, color = Cores.Texto)
        }
        if (acao != null) acao()
    }
}

/** Botão cheio, laranja: a ação principal do cartão. */
@Composable
fun BotaoPrincipal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icone: Int? = null,
    ativo: Boolean = true,
    altura: Dp = 48.dp,
) {
    Row(
        modifier = modifier
            .height(altura)
            .clip(CantoDoBotao)
            .background(if (ativo) Cores.Destaque else Cores.Trilho)
            .clickable(enabled = ativo, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cor = if (ativo) Cores.Fundo else Cores.Desligado
        if (icone != null) Icon(painterResource(icone), contentDescription = null, tint = cor, modifier = Modifier.size(18.dp))
        Text(text = texto, style = Tipos.Botao, color = cor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botão de contorno: ações secundárias ("Regravar", "+250 ml", "Montar"). */
@Composable
fun BotaoContorno(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icone: Int? = null,
    ativo: Boolean = true,
    altura: Dp = 44.dp,
    fundo: Color = Cores.Fundo,
) {
    Row(
        modifier = modifier
            .height(altura)
            .clip(CantoDoBotao)
            .background(if (ativo) fundo else Cores.Fundo)
            .border(1.dp, if (ativo) Cores.Trilho else Cores.BordaApagada, CantoDoBotao)
            .clickable(enabled = ativo, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cor = if (ativo) Cores.Texto else Cores.Desligado
        if (icone != null) Icon(painterResource(icone), contentDescription = null, tint = cor, modifier = Modifier.size(18.dp))
        Text(text = texto, style = Tipos.Botao, color = cor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botão quadrado só com ícone: importar, gravar, copiar, dia anterior. */
@Composable
fun BotaoQuadrado(
    @DrawableRes icone: Int,
    descricao: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ativo: Boolean = true,
    cor: Color = Cores.Texto,
    fundo: Color = Cores.Superficie,
    tamanho: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(tamanho)
            .clip(CantoDoBotao)
            .background(if (ativo) fundo else Cores.Fundo)
            .border(1.dp, if (ativo) Cores.Trilho else Cores.BordaApagada, CantoDoBotao)
            .clickable(enabled = ativo, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icone),
            contentDescription = descricao,
            tint = if (ativo) cor else Cores.Desligado,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Marca arredondada: "✓ Feito", "NOVO 144 kg". Com [onClick], vira um botão em forma de pílula. */
@Composable
fun Pilula(
    texto: String,
    fundo: Color,
    cor: Color,
    modifier: Modifier = Modifier,
    @DrawableRes icone: Int? = null,
    borda: Color? = null,
    altura: Dp = 26.dp,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .height(altura)
            .clip(CircleShape)
            .background(fundo)
            .then(if (borda != null) Modifier.border(1.dp, borda, CircleShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (altura > 30.dp) 14.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icone != null) Icon(painterResource(icone), contentDescription = null, tint = cor, modifier = Modifier.size(14.dp))
        Text(text = texto, style = Tipos.Botao.copy(fontSize = Tipos.CorpoPequeno.fontSize), color = cor, maxLines = 1)
    }
}

/** Barra fina de progresso. [fracao] vai de 0 a 1. */
@Composable
fun BarraDeProgresso(fracao: Float, cor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Cores.Trilho),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fracao.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(cor),
        )
    }
}

/**
 * Seletor de opções lado a lado, com a escolhida em destaque: opção A/B da refeição,
 * sim/não, nível de fome.
 *
 * @param selecionada posição da opção escolhida, ou nulo se nenhuma
 */
@Composable
fun Segmentos(
    opcoes: List<String>,
    selecionada: Int?,
    onSelecionar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CantoDoBotao)
            .background(Cores.Fundo)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        opcoes.forEachIndexed { i, texto ->
            val escolhida = i == selecionada
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (escolhida) Cores.Trilho else Color.Transparent)
                    .clickable { onSelecionar(i) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = texto,
                    style = Tipos.Botao.copy(fontSize = Tipos.CorpoPequeno.fontSize),
                    color = if (escolhida) Cores.Texto else Cores.TextoSuave,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Contorno tracejado: linha de série que depende da anterior, espaço vazio de foto. */
fun Modifier.bordaTracejada(cor: Color, raio: Dp): Modifier = drawBehind {
    val canto = raio.toPx()
    drawRoundRect(
        color = cor,
        cornerRadius = CornerRadius(canto, canto),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        ),
    )
}
