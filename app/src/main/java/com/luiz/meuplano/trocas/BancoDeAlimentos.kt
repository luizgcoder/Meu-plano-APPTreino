package com.luiz.meuplano.trocas

/**
 * O banco de alimentos do "Montador de Trocas". Os valores são por 100 g do alimento já preparado e vêm da
 * TACO (Tabela Brasileira de Composição de Alimentos, 4ª ed., NEPA/Unicamp, 2011).
 * Os marcados como estimados não constam na TACO: são valores de rótulo ou da USDA.
 *
 * Não altere os números à mão: eles são a base de todas as contas de troca.
 * Colunas: nome, categoria, kcal, proteína, gordura, carboidrato, como pesar,
 * estimado, gramas por unidade (0 = alimento pesado em gramas).
 */
object BancoDeAlimentos {

    val ALIMENTOS: List<Alimento> = listOf(
        // ----- carboidratos -----
        Alimento("Arroz branco cozido", Categoria.CARB, 128.0, 2.5, 0.2, 28.1, "cozido", false, 0),
        Alimento("Arroz integral cozido", Categoria.CARB, 124.0, 2.6, 1.0, 25.8, "cozido", false, 0),
        Alimento("Batata-doce cozida", Categoria.CARB, 77.0, 0.6, 0.1, 18.4, "cozida", false, 0),
        Alimento("Batata inglesa cozida", Categoria.CARB, 52.0, 1.2, 0.0, 11.9, "cozida", false, 0),
        Alimento("Batata baroa (mandioquinha) cozida", Categoria.CARB, 80.0, 0.9, 0.2, 18.9, "cozida", false, 0),
        Alimento("Mandioca cozida", Categoria.CARB, 125.0, 0.6, 0.3, 30.1, "cozida", false, 0),
        Alimento("Cará cozido", Categoria.CARB, 78.0, 1.5, 0.1, 18.9, "cozido", false, 0),
        Alimento("Inhame cozido", Categoria.CARB, 110.0, 2.3, 0.2, 26.0, "cozido", true, 0),
        Alimento("Abóbora cabotiá cozida", Categoria.CARB, 48.0, 1.4, 0.7, 10.8, "cozida", false, 0),
        Alimento("Milho verde (escorrido)", Categoria.CARB, 98.0, 3.2, 2.4, 17.1, "escorrido", false, 0),
        Alimento("Cuscuz de milho cozido", Categoria.CARB, 113.0, 2.2, 0.7, 25.3, "cozido", false, 0),
        Alimento("Polenta pronta", Categoria.CARB, 103.0, 2.3, 0.3, 23.3, "pronta", false, 0),
        Alimento("Macarrão cozido", Categoria.CARB, 150.0, 5.5, 0.8, 30.0, "cozido", true, 0),
        Alimento("Feijão carioca cozido", Categoria.CARB, 76.0, 4.8, 0.5, 13.6, "cozido", false, 0),
        Alimento("Feijão preto cozido", Categoria.CARB, 77.0, 4.5, 0.5, 14.0, "cozido", false, 0),
        Alimento("Feijão fradinho cozido", Categoria.CARB, 78.0, 5.1, 0.6, 13.5, "cozido", false, 0),
        Alimento("Lentilha cozida", Categoria.CARB, 93.0, 6.3, 0.5, 16.3, "cozida", false, 0),
        Alimento("Grão-de-bico cozido", Categoria.CARB, 160.0, 8.9, 2.6, 27.0, "cozido", true, 0),
        Alimento("Ervilha (escorrida)", Categoria.CARB, 74.0, 4.6, 0.4, 13.4, "escorrida", false, 0),
        Alimento("Aveia em flocos", Categoria.CARB, 394.0, 13.9, 8.5, 66.6, "seca", false, 0),
        Alimento("Maizena (amido de milho)", Categoria.CARB, 361.0, 0.6, 0.0, 87.1, "seca", false, 0),
        Alimento("Farinha de arroz", Categoria.CARB, 363.0, 1.3, 0.3, 85.5, "seca", false, 0),
        Alimento("Farinha de mandioca", Categoria.CARB, 365.0, 1.2, 0.3, 89.2, "seca", false, 0),
        Alimento("Tapioca (goma da terrinha)", Categoria.CARB, 240.0, 0.3, 0.1, 59.0, "goma pesada", true, 0),
        Alimento("Maltodextrina (Body Action)", Categoria.CARB, 400.0, 0.0, 0.0, 98.0, "pó pesado", true, 0),
        Alimento("Pão francês", Categoria.CARB, 300.0, 8.0, 3.1, 58.6, "unidade", false, 50),
        Alimento("Pão de forma (fatia)", Categoria.CARB, 280.0, 9.0, 3.5, 50.0, "fatia", true, 25),
        Alimento("Pão de forma fermentação natural (fatia)", Categoria.CARB, 260.0, 9.0, 3.0, 48.0, "fatia", true, 25),
        Alimento("Pão de hambúrguer Bauducco", Categoria.CARB, 274.0, 13.0, 5.0, 44.0, "unidade", true, 50),
        // ----- proteínas -----
        Alimento("Frango (peito) grelhado", Categoria.PTN, 159.0, 32.0, 2.5, 0.0, "grelhado", false, 0),
        Alimento("Frango (coxa) s/ pele cozida", Categoria.PTN, 167.0, 26.9, 5.8, 0.0, "cozida", false, 0),
        Alimento("Frango (sobrecoxa) s/ pele assada", Categoria.PTN, 233.0, 29.2, 12.0, 0.0, "assada", false, 0),
        Alimento("Patinho grelhado", Categoria.PTN, 219.0, 35.9, 7.3, 0.0, "grelhado", false, 0),
        Alimento("Filé mignon bovino grelhado", Categoria.PTN, 220.0, 32.8, 8.8, 0.0, "grelhado", false, 0),
        Alimento("Alcatra (miolo) grelhada", Categoria.PTN, 241.0, 31.9, 11.6, 0.0, "grelhada", false, 0),
        Alimento("Coxão mole cozido", Categoria.PTN, 219.0, 32.4, 8.9, 0.0, "cozido", false, 0),
        Alimento("Músculo bovino cozido", Categoria.PTN, 194.0, 31.2, 6.7, 0.0, "cozido", false, 0),
        Alimento("Carne moída (acém) cozida", Categoria.PTN, 212.0, 26.7, 10.9, 0.0, "cozida", false, 0),
        Alimento("Fígado bovino grelhado", Categoria.PTN, 225.0, 29.9, 9.0, 4.2, "grelhado", false, 0),
        Alimento("Coração de frango grelhado", Categoria.PTN, 207.0, 22.4, 12.1, 0.6, "grelhado", false, 0),
        Alimento("Coração bovino grelhado", Categoria.PTN, 165.0, 28.0, 5.0, 0.0, "grelhado", true, 0),
        Alimento("Lombo suíno assado", Categoria.PTN, 210.0, 35.7, 6.4, 0.0, "assado", false, 0),
        Alimento("Filé mignon suíno grelhado", Categoria.PTN, 165.0, 29.0, 5.0, 0.0, "grelhado", true, 0),
        Alimento("Pernil suíno assado", Categoria.PTN, 262.0, 32.1, 13.9, 0.0, "assado", false, 0),
        Alimento("Merluza (filé) assada", Categoria.PTN, 122.0, 26.6, 0.9, 0.0, "assada", false, 0),
        Alimento("Tilápia (filé) grelhada", Categoria.PTN, 128.0, 26.2, 2.7, 0.0, "grelhada", true, 0),
        Alimento("Pescada (filé) grelhada", Categoria.PTN, 118.0, 19.0, 4.3, 0.0, "grelhada", true, 0),
        Alimento("Salmão grelhado", Categoria.PTN, 243.0, 26.1, 14.5, 0.0, "grelhado", false, 0),
        Alimento("Sardinha assada", Categoria.PTN, 164.0, 32.2, 3.0, 0.0, "assada", false, 0),
        Alimento("Atum (lata, na água)", Categoria.PTN, 120.0, 26.0, 1.5, 0.0, "escorrido", true, 0),
        Alimento("Atum (lata, no óleo)", Categoria.PTN, 166.0, 26.2, 6.0, 0.0, "escorrido", false, 0),
        Alimento("Camarão cozido", Categoria.PTN, 90.0, 19.0, 1.0, 0.0, "cozido", false, 0),
        Alimento("Ovo de galinha cozido", Categoria.PTN, 146.0, 13.3, 9.5, 0.6, "unidade", false, 50),
        Alimento("Clara de ovo cozida", Categoria.PTN, 59.0, 13.4, 0.1, 0.0, "unidade", false, 33),
        Alimento("Whey 100% Integral Médica (pó)", Categoria.PTN, 400.0, 70.0, 6.0, 8.0, "pó pesado", true, 0),
        Alimento("Ricota", Categoria.PTN, 140.0, 12.6, 8.1, 3.8, "pesada", false, 0),
        Alimento("Queijo minas frescal", Categoria.PTN, 264.0, 17.4, 20.2, 3.2, "pesado", false, 0),
        Alimento("Iogurte natural desnatado", Categoria.PTN, 41.0, 3.8, 0.3, 5.8, "pesado", false, 0),
        // ----- frutas -----
        Alimento("Banana", Categoria.FRUTA, 92.0, 1.4, 0.1, 23.8, "unidade", false, 98),
        Alimento("Maçã", Categoria.FRUTA, 58.0, 0.3, 0.2, 15.5, "unidade", false, 140),
        Alimento("Pera", Categoria.FRUTA, 53.0, 0.6, 0.1, 14.0, "unidade", false, 140),
        Alimento("Laranja", Categoria.FRUTA, 45.0, 1.0, 0.1, 11.5, "unidade", false, 145),
        Alimento("Tangerina / mexerica", Categoria.FRUTA, 48.0, 0.9, 0.1, 12.0, "unidade", false, 130),
        Alimento("Kiwi", Categoria.FRUTA, 51.0, 1.3, 0.6, 11.5, "unidade", false, 80),
        Alimento("Caqui", Categoria.FRUTA, 71.0, 0.4, 0.1, 19.3, "unidade", false, 100),
        Alimento("Figo", Categoria.FRUTA, 41.0, 1.0, 0.2, 10.2, "unidade", false, 50),
        Alimento("Morango", Categoria.FRUTA, 30.0, 0.9, 0.3, 6.8, "pesado", false, 0),
        Alimento("Abacaxi", Categoria.FRUTA, 48.0, 0.9, 0.1, 12.3, "pesado", false, 0),
        Alimento("Mamão", Categoria.FRUTA, 45.0, 0.8, 0.1, 11.6, "pesado", false, 0),
        Alimento("Melancia", Categoria.FRUTA, 33.0, 0.9, 0.0, 8.1, "pesada", false, 0),
        Alimento("Melão", Categoria.FRUTA, 29.0, 0.7, 0.0, 7.5, "pesado", false, 0),
        Alimento("Uva", Categoria.FRUTA, 51.0, 0.7, 0.2, 13.6, "pesada", false, 0),
        Alimento("Manga", Categoria.FRUTA, 65.0, 0.5, 0.2, 17.0, "pesada", false, 0),
        Alimento("Goiaba", Categoria.FRUTA, 54.0, 1.1, 0.4, 13.0, "pesada", false, 0),
        Alimento("Ameixa", Categoria.FRUTA, 53.0, 0.8, 0.0, 13.9, "pesada", false, 0),
        Alimento("Pêssego", Categoria.FRUTA, 36.0, 0.8, 0.0, 9.3, "pesado", false, 0),
        Alimento("Maracujá (polpa)", Categoria.FRUTA, 68.0, 2.0, 2.1, 12.3, "pesado", false, 0),
        Alimento("Acerola", Categoria.FRUTA, 33.0, 0.9, 0.2, 8.0, "pesada", false, 0),
        Alimento("Abacate", Categoria.FRUTA, 96.0, 1.2, 8.4, 6.0, "pesado", false, 0),
        Alimento("Açaí (polpa pura)", Categoria.FRUTA, 60.0, 0.8, 3.9, 6.2, "pesado", true, 0),
        Alimento("Uva-passa", Categoria.FRUTA, 300.0, 3.0, 0.5, 79.0, "pesada", true, 0),
        // ----- gorduras -----
        Alimento("Azeite de oliva", Categoria.GORD, 884.0, 0.0, 100.0, 0.0, "pesado", false, 0),
        Alimento("Óleo (soja/coco)", Categoria.GORD, 884.0, 0.0, 100.0, 0.0, "pesado", false, 0),
        Alimento("Pasta de amendoim integral", Categoria.GORD, 600.0, 25.0, 50.0, 20.0, "pesada", true, 0),
        Alimento("Amendoim torrado", Categoria.GORD, 606.0, 22.5, 54.0, 18.7, "pesado", false, 0),
        Alimento("Castanha-do-pará", Categoria.GORD, 643.0, 14.5, 63.5, 15.1, "pesada", true, 0),
        Alimento("Manteiga", Categoria.GORD, 726.0, 0.4, 82.4, 0.1, "pesada", false, 0),
    )

    private val POR_NOME: Map<String, Alimento> = ALIMENTOS.associateBy { it.nome }

    /** O alimento com exatamente este nome, ou nulo se não está no banco. */
    fun porNome(nome: String?): Alimento? = POR_NOME[nome]

    /** Os alimentos de uma categoria, na ordem do banco. */
    fun daCategoria(categoria: Categoria): List<Alimento> = ALIMENTOS.filter { it.categoria == categoria }
}
