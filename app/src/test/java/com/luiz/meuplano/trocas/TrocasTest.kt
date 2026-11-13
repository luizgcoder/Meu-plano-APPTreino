package com.luiz.meuplano.trocas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Os números esperados aqui são os que o Montador de Trocas do treinador mostra para os mesmos casos. */
class TrocasTest {

    private fun alimento(nome: String): Alimento =
        BancoDeAlimentos.porNome(nome) ?: error("não está no banco: $nome")

    @Test
    fun `o banco tem os 87 alimentos do material`() {
        assertEquals(87, BancoDeAlimentos.ALIMENTOS.size)
        assertEquals(29, BancoDeAlimentos.daCategoria(Categoria.CARB).size)
        assertEquals(29, BancoDeAlimentos.daCategoria(Categoria.PTN).size)
        assertEquals(23, BancoDeAlimentos.daCategoria(Categoria.FRUTA).size)
        assertEquals(6, BancoDeAlimentos.daCategoria(Categoria.GORD).size)
        // Nomes únicos: a troca do dia é guardada pelo nome.
        assertEquals(87, BancoDeAlimentos.ALIMENTOS.map { it.nome }.toSet().size)
    }

    @Test
    fun `arroz por batata-doce, o exemplo de abertura`() {
        val troca = calcularTroca(alimento("Arroz branco cozido"), 150.0, alimento("Batata-doce cozida"))
        assertEquals("192", inteiro(troca.kcalAlvo))
        val porcao = troca.porcoes.single()
        assertEquals("250 g", porcao.quantidadeTexto())
        assertEquals("pesar cozida · 193 kcal", porcao.detalhe())
        assertEquals("192,5", umaCasa(troca.para.kcal))
        assertEquals("+0,5" to Sentido.SUBIU, diferenca(troca.de.kcal, troca.para.kcal))
        assertEquals("1,5", umaCasa(troca.para.proteina))
        assertEquals("−2,3" to Sentido.DESCEU, diferenca(troca.de.proteina, troca.para.proteina))
        assertEquals("46", umaCasa(troca.para.carbo))
        assertEquals("+3,9" to Sentido.SUBIU, diferenca(troca.de.carbo, troca.para.carbo))
        assertEquals("igual" to Sentido.IGUAL, diferenca(troca.de.gordura, troca.para.gordura))
        assertNull(troca.aviso)
    }

    @Test
    fun `alimento de unidade sai em unidades inteiras`() {
        // 2 bananas (98 g cada) por mamão, e 150 g de frango por ovos.
        val mamao = calcularTroca(alimento("Banana"), 2.0, alimento("Mamão")).porcoes.single()
        assertEquals("400 g", mamao.quantidadeTexto())
        val ovos = calcularTroca(alimento("Frango (peito) grelhado"), 150.0, alimento("Ovo de galinha cozido")).porcoes.single()
        assertEquals("3 un", ovos.quantidadeTexto())
        assertEquals("≈ 150 g · pesar unidade · 219 kcal", ovos.detalhe())
    }

    @Test
    fun `dois alimentos dividem as calorias pela proporcao`() {
        val troca = calcularTroca(
            alimento("Arroz branco cozido"), 200.0,
            alimento("Arroz branco cozido"), alimento("Feijão carioca cozido"), proporcao = 60,
        )
        assertEquals(listOf("120 g", "135 g"), troca.porcoes.map { it.quantidadeTexto() })
        assertNull(troca.aviso)
    }

    @Test
    fun `avisa quando a unidade inteira ja passa do total`() {
        // 35 g de arroz são 45 kcal; um ovo sozinho já tem 73.
        val troca = calcularTroca(
            alimento("Arroz branco cozido"), 35.0,
            alimento("Ovo de galinha cozido"), alimento("Batata-doce cozida"), proporcao = 50,
        )
        assertEquals(AVISO_PASSOU_DO_TOTAL, troca.aviso)
        assertEquals(listOf("1 un", "0 g"), troca.porcoes.map { it.quantidadeTexto() })
    }

    @Test
    fun `gramas muito caloricas arredondam de 1 em 1 e as grandes de 10 em 10`() {
        // 2 g de azeite por manteiga; 280 g de arroz por batata inglesa.
        assertEquals("2 g", calcularTroca(alimento("Azeite de oliva"), 2.0, alimento("Manteiga")).porcoes.single().quantidadeTexto())
        assertEquals(
            "690 g",
            calcularTroca(alimento("Arroz branco cozido"), 280.0, alimento("Batata inglesa cozida")).porcoes.single().quantidadeTexto(),
        )
    }

    @Test
    fun `os atalhos usam alimentos que existem`() {
        for (atalho in ATALHOS) {
            assertNotNull(atalho.rotulo, BancoDeAlimentos.porNome(atalho.origem))
            atalho.destinos.forEach { assertNotNull(atalho.rotulo, BancoDeAlimentos.porNome(it)) }
        }
    }

    @Test
    fun `mudar a origem acerta a quantidade entre gramas e unidades`() {
        assertEquals(1.0, quantidadeAoMudarOrigem(alimento("Banana"), 150.0), 0.0)
        assertEquals(150.0, quantidadeAoMudarOrigem(alimento("Arroz branco cozido"), 2.0), 0.0)
        assertEquals(3.0, quantidadeAoMudarOrigem(alimento("Banana"), 3.0), 0.0)
        assertEquals(280.0, quantidadeAoMudarOrigem(alimento("Arroz branco cozido"), 280.0), 0.0)
    }

    @Test
    fun `os destinos ficam na categoria da origem, a menos que se peça todas`() {
        val arroz = alimento("Arroz branco cozido")
        assertEquals(setOf(Categoria.CARB), destinosPara(arroz, false).map { it.categoria }.toSet())
        assertEquals(87, destinosPara(arroz, true).size)
    }

    // ---------- reconhecer os itens da dieta ----------

    private fun reconhecido(texto: String): Pair<String, Double?>? =
        reconhecerItem(texto)?.let { it.alimento.nome to it.quantidade }

    @Test
    fun `reconhece os itens do plano de exemplo`() {
        assertEquals("Whey 100% Integral Médica (pó)" to 30.0, reconhecido("30g whey"))
        assertEquals("Pão de forma (fatia)" to 2.0, reconhecido("2 fatias de pão de forma"))
        assertEquals("Ovo de galinha cozido" to 3.0, reconhecido("3 ovos"))
        assertEquals("Azeite de oliva" to 2.0, reconhecido("2g azeite"))
        assertEquals("Arroz branco cozido" to 280.0, reconhecido("280g arroz branco ou macarrão de arroz"))
        assertEquals("Frango (peito) grelhado" to 100.0, reconhecido("100g filé de frango ou coração bovino"))
        assertEquals("Banana" to 1.0, reconhecido("1 banana ou 300ml água de coco ou 100g biomassa de banana verde"))
        assertEquals("Maizena (amido de milho)" to 70.0, reconhecido("70g maizena"))
        assertEquals(
            "Pão de forma fermentação natural (fatia)" to 4.0,
            reconhecido("4 fatias de pão de forma de fermentação natural"),
        )
        assertEquals("Abacaxi" to 200.0, reconhecido("200g abacaxi ou mamão ou 200g açaí"))
    }

    @Test
    fun `nao reconhece o que nao esta no banco`() {
        assertNull(reconhecido("20g geleia de qualquer fruta (opcional) ou 30g requeijão light"))
        assertNull(reconhecido("200g Crucíferos (Brócolis, couve, couve flor, couve de bruxelas, repolho)"))
        assertNull(reconhecido("150 a 200g iogurte natural ou kefir de leite (Opcional)"))
        assertNull(reconhecido("Salada à vontade"))
        assertNull(reconhecido(""))
    }

    @Test
    fun `o alimento precisa ser o assunto do item`() {
        assertNull(reconhecido("300ml suco de laranja"))
        assertNull(reconhecido("100g biomassa de banana verde"))
        assertNull(reconhecido("100g pão de queijo"))
        assertNull(reconhecido("100g creme de arroz"))
    }

    @Test
    fun `escolhe o alimento mais especifico`() {
        assertEquals("Coração de frango grelhado" to 100.0, reconhecido("100g coração de frango"))
        assertEquals("Clara de ovo cozida" to 2.0, reconhecido("2 claras de ovo"))
        assertEquals("Frango (sobrecoxa) s/ pele assada" to 150.0, reconhecido("150g sobrecoxa de frango"))
        assertEquals("Atum (lata, no óleo)" to 120.0, reconhecido("120g atum no óleo"))
        assertEquals("Filé mignon suíno grelhado" to 100.0, reconhecido("100g filé mignon suíno"))
        assertEquals("Uva-passa" to 100.0, reconhecido("100g uvas passas"))
        assertEquals("Batata-doce cozida" to 150.0, reconhecido("150g batata doce"))
    }

    @Test
    fun `converte a medida para a do alimento`() {
        assertEquals("Melancia" to 1500.0, reconhecido("1,5kg melancia"))
        assertEquals("Pão francês" to 1.0, reconhecido("50g pão francês"))
        // Medida que não dá para converter: reconhece o alimento e deixa a quantidade em branco.
        assertEquals("Pasta de amendoim integral" to null, reconhecido("2 colheres de pasta de amendoim"))
        assertEquals("Goiaba" to null, reconhecido("2 goiabas"))
    }
}
