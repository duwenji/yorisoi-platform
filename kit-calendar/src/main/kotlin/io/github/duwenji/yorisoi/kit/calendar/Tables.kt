package io.github.duwenji.yorisoi.kit.calendar

import java.time.LocalDate

internal data class Kou(val name: String, val reading: String, val note: String)

/** A designated day (D-5): who set it, the line on why, and where that was checked. */
internal data class Kinenbi(val month: Int, val day: Int, val key: String, val name: String, val designatedBy: String, val note: String, val source: String)

/** A day kept in some places (D-6): where, and when in [year] ([days] gives the first and last day, or null). */
internal class Regional(
    val key: String,
    val name: String,
    val reading: String,
    val regions: String,
    val note: String,
    val source: String,
    val days: (year: Int) -> Pair<LocalDate, LocalDate>?,
)

/** The calendar's tables, written by the operator (REQ-KOYOMI D-3, D-5, D-6, F-6, F-7). */
internal object Tables {
    /** The 72 pentads (七十二候, 略本暦), 立春's first one first; the k-th begins at the Sun's longitude 315 + 5k. */
    val KOU = listOf(
        Kou("東風解凍", "はるかぜこおりをとく", "春の風が、川や湖の氷を解かし始めるころ"),
        Kou("黄鶯睍睆", "うぐいすなく", "うぐいすが山里で鳴き始めるころ"),
        Kou("魚上氷", "うおこおりをいずる", "割れた氷の間から、魚が跳ね上がるころ"),
        Kou("土脉潤起", "つちのしょううるおいおこる", "雨が降って、土が湿り気を含むころ"),
        Kou("霞始靆", "かすみはじめてたなびく", "霞がたなびき始めるころ"),
        Kou("草木萌動", "そうもくめばえいずる", "草木が芽吹き始めるころ"),
        Kou("蟄虫啓戸", "すごもりむしとをひらく", "冬ごもりの虫が、戸を開いて出てくるころ"),
        Kou("桃始笑", "ももはじめてさく", "桃の花が咲き始めるころ"),
        Kou("菜虫化蝶", "なむしちょうとなる", "青虫が羽化して、紋白蝶になるころ"),
        Kou("雀始巣", "すずめはじめてすくう", "雀が巣を作り始めるころ"),
        Kou("桜始開", "さくらはじめてさく", "桜の花が咲き始めるころ"),
        Kou("雷乃発声", "かみなりすなわちこえをはっす", "遠くで春の雷が鳴り始めるころ"),
        Kou("玄鳥至", "つばめきたる", "つばめが南の国から渡ってくるころ"),
        Kou("鴻雁北", "こうがんかえる", "雁が北へ帰っていくころ"),
        Kou("虹始見", "にじはじめてあらわる", "雨上がりに、虹が見え始めるころ"),
        Kou("葭始生", "あしはじめてしょうず", "水辺の葦が芽を出し始めるころ"),
        Kou("霜止出苗", "しもやみてなえいずる", "霜が終わり、稲の苗が育つころ"),
        Kou("牡丹華", "ぼたんはなさく", "牡丹の花が咲くころ"),
        Kou("蛙始鳴", "かわずはじめてなく", "蛙が鳴き始めるころ"),
        Kou("蚯蚓出", "みみずいずる", "みみずが地上に出てくるころ"),
        Kou("竹笋生", "たけのこしょうず", "たけのこが生えてくるころ"),
        Kou("蚕起食桑", "かいこおきてくわをはむ", "蚕が桑の葉を盛んに食べるころ"),
        Kou("紅花栄", "べにばなさかう", "紅花が盛んに咲くころ"),
        Kou("麦秋至", "むぎのときいたる", "麦が熟して、麦秋を迎えるころ"),
        Kou("蟷螂生", "かまきりしょうず", "かまきりが生まれるころ"),
        Kou("腐草為蛍", "くされたるくさほたるとなる", "草むらで蛍が光り始めるころ。昔は、腐った草が蛍になると考えられていた"),
        Kou("梅子黄", "うめのみきばむ", "梅の実が黄ばんで熟すころ"),
        Kou("乃東枯", "なつかれくさかるる", "夏枯草（うつぼぐさ）が枯れるころ"),
        Kou("菖蒲華", "あやめはなさく", "あやめの花が咲くころ"),
        Kou("半夏生", "はんげしょうず", "半夏（からすびしゃく）が生えるころ"),
        Kou("温風至", "あつかぜいたる", "熱い風が吹き始めるころ"),
        Kou("蓮始開", "はすはじめてひらく", "蓮の花が開き始めるころ"),
        Kou("鷹乃学習", "たかすなわちわざをならう", "鷹の幼鳥が、飛ぶことを覚えるころ"),
        Kou("桐始結花", "きりはじめてはなをむすぶ", "桐が花を結び始めるころ"),
        Kou("土潤溽暑", "つちうるおうてむしあつし", "土が湿って、蒸し暑くなるころ"),
        Kou("大雨時行", "たいうときどきふる", "ときどき大雨が降るころ"),
        Kou("涼風至", "すずかぜいたる", "涼しい風が立ち始めるころ"),
        Kou("寒蝉鳴", "ひぐらしなく", "ひぐらしが鳴き始めるころ"),
        Kou("蒙霧升降", "ふかききりまとう", "深い霧が立ちこめるころ"),
        Kou("綿柎開", "わたのはなしべひらく", "綿を包む萼（がく）が開くころ"),
        Kou("天地始粛", "てんちはじめてさむし", "暑さがようやくおさまるころ"),
        Kou("禾乃登", "こくものすなわちみのる", "稲が実るころ"),
        Kou("草露白", "くさのつゆしろし", "草に降りた露が、白く光るころ"),
        Kou("鶺鴒鳴", "せきれいなく", "せきれいが鳴き始めるころ"),
        Kou("玄鳥去", "つばめさる", "つばめが南へ帰っていくころ"),
        Kou("雷乃収声", "かみなりすなわちこえをおさむ", "雷が鳴らなくなるころ"),
        Kou("蟄虫坏戸", "むしかくれてとをふさぐ", "虫が土の中にこもり、戸をふさぐころ"),
        Kou("水始涸", "みずはじめてかるる", "田の水を落として、稲刈りに備えるころ"),
        Kou("鴻雁来", "こうがんきたる", "雁が北から渡ってくるころ"),
        Kou("菊花開", "きくのはなひらく", "菊の花が咲くころ"),
        Kou("蟋蟀在戸", "きりぎりすとにあり", "戸口で秋の虫が鳴くころ"),
        Kou("霜始降", "しもはじめてふる", "霜が降り始めるころ"),
        Kou("霎時施", "こさめときどきふる", "ときどき小雨が降るころ"),
        Kou("楓蔦黄", "もみじつたきばむ", "もみじや蔦が色づくころ"),
        Kou("山茶始開", "つばきはじめてひらく", "さざんかの花が咲き始めるころ。「つばき」と読むが、さざんかのこと"),
        Kou("地始凍", "ちはじめてこおる", "大地が凍り始めるころ"),
        Kou("金盞香", "きんせんかさく", "水仙の花が咲き始めるころ。金盞は水仙のこと"),
        Kou("虹蔵不見", "にじかくれてみえず", "虹を見かけなくなるころ"),
        Kou("朔風払葉", "きたかぜこのはをはらう", "北風が木の葉を吹き払うころ"),
        Kou("橘始黄", "たちばなはじめてきばむ", "橘の実が黄色く色づき始めるころ"),
        Kou("閉塞成冬", "そらさむくふゆとなる", "天地の気がふさがり、本格的な冬になるころ"),
        Kou("熊蟄穴", "くまあなにこもる", "熊が冬ごもりのため穴に入るころ"),
        Kou("鱖魚群", "さけのうおむらがる", "鮭が群れをなして、川をさかのぼるころ"),
        Kou("乃東生", "なつかれくさしょうず", "夏枯草が芽を出すころ"),
        Kou("麋角解", "さわしかのつのおつる", "大鹿の角が落ちるころ"),
        Kou("雪下出麦", "ゆきわたりてむぎのびる", "雪の下で麦が芽を出すころ"),
        Kou("芹乃栄", "せりすなわちさかう", "せりがよく育つころ"),
        Kou("水泉動", "しみずあたたかをふくむ", "地中で凍っていた泉が動き始めるころ"),
        Kou("雉始雊", "きじはじめてなく", "雉の雄が鳴き始めるころ"),
        Kou("款冬華", "ふきのはなさく", "ふきのとうがつぼみを出すころ"),
        Kou("水沢腹堅", "さわみずこおりつめる", "沢に氷が厚く張りつめるころ"),
        Kou("鶏始乳", "にわとりはじめてとやにつく", "鶏が卵を産み始めるころ"),
    )

    /** The five seasonal festivals and the year's customs kept alike everywhere (sekku). */
    fun sekku(year: Int): List<Event> = listOf(
        Triple("nanakusa", "七草", 1 to 7) to "七草がゆを食べて、一年の無病息災を願う。五節句の一つ（人日）",
        Triple("koshougatsu", "小正月", 1 to 15) to "正月の終わりとされる日。小豆がゆを食べる習わしがある",
        Triple("hinamatsuri", "ひな祭り", 3 to 3) to "女の子の成長を願う。五節句の一つ（上巳・桃の節句）",
        Triple("hanamatsuri", "花祭り", 4 to 8) to "お釈迦さまの誕生を祝う日（灌仏会）",
        Triple("tango", "端午の節句", 5 to 5) to "男の子の成長を願う。五節句の一つ。菖蒲湯に入る習わしがある",
        Triple("tanabata", "七夕", 7 to 7) to "星に願いをかける。五節句の一つ",
        Triple("chouyou", "重陽の節句", 9 to 9) to "菊を飾って長寿を願う。五節句の一つ（菊の節句）",
        Triple("shichigosan", "七五三", 11 to 15) to "子どもの成長を祝って、神社やお寺にお参りする日",
        Triple("oomisoka", "大晦日", 12 to 31) to "一年の最後の日。年越しそばを食べる習わしがある",
    ).map { (what, note) ->
        val (key, name, md) = what
        Event(KoyomiKind.SEKKU, key, name, LocalDate.of(year, md.first, md.second), note = note)
    }

    /** The festivals of the old calendar (lunar). */
    fun lunar(year: Int): List<Event> = listOfNotNull(
        Lunisolar.dateOf(year, 8, 15)?.let { Event(KoyomiKind.LUNAR, "jugoya", "十五夜", it, note = "中秋の名月。月を眺め、すすきや団子を供える習わしがある", reading = "じゅうごや") },
        Lunisolar.dateOf(year, 9, 13)?.let { Event(KoyomiKind.LUNAR, "jusanya", "十三夜", it, note = "十五夜の次に美しいとされる「後の月」。栗や豆を供える習わしがある", reading = "じゅうさんや") },
    )

    private const val UN_DAYS = "https://www.unic.or.jp/activities/international_observances/days/"

    /** Designated days (kinenbi). Checked against each body's own page (or the UN Information Centre's list) on 2026-11-15. */
    val KINENBI = listOf(
        Kinenbi(1, 17, "bousai-volunteer", "防災とボランティアの日", "内閣（閣議了解）", "1995 年 1 月 17 日の阪神・淡路大震災で、ボランティアの大切さが知られたことから定められた", "https://www.bousai.go.jp/kaigirep/hakusho/h17/bousai2005/html/honmon/hm130300.htm"),
        Kinenbi(1, 26, "bunkazai-bouka", "文化財防火デー", "文化庁・消防庁（もとは文化財保護委員会・国家消防本部）", "1949 年 1 月 26 日に法隆寺金堂の壁画が焼けたことから定められた", "https://www.bunka.go.jp/seisaku/bunkazai/hogofukyu/boka_day.html"),
        Kinenbi(2, 2, "wetlands", "世界湿地の日", "ラムサール条約事務局", "1971 年 2 月 2 日に、湿地を守るラムサール条約が採択されたことから", "https://www.env.go.jp/nature/ramsar/conv/World_Wetlands_Day.html"),
        Kinenbi(2, 7, "hoppou", "北方領土の日", "内閣（閣議了解）", "1855 年 2 月 7 日に日魯通好条約が結ばれたことから", "https://www8.cao.go.jp/hoppo/henkan/02.html"),
        Kinenbi(3, 3, "mimi", "耳の日", "日本耳鼻咽喉科学会（今の日本耳鼻咽喉科頭頸部外科学会）", "難聴などに悩む人を少しでも減らそうと定められた。「3」が耳の形に似ていることなどから", "https://www.jibika.or.jp/modules/citizens/index.php?content_id=43"),
        Kinenbi(3, 21, "forests", "国際森林デー", "国際連合", "森林の大切さを知ってもらうために、国際連合が定めた", UN_DAYS),
        Kinenbi(4, 7, "world-health", "世界保健デー", "世界保健機関（WHO）", "1948 年 4 月 7 日に世界保健機関ができたことから", UN_DAYS),
        Kinenbi(4, 18, "hatsumei", "発明の日", "通商産業省（今の経済産業省）", "1885 年 4 月 18 日に、今の特許法のもとになる専売特許条例が出たことから", "https://www.jpo.go.jp/introduction/rekishi/hatsumei.html"),
        Kinenbi(5, 22, "biodiversity", "国際生物多様性の日", "国際連合", "1992 年 5 月 22 日に、生物多様性条約の条文がまとまったことから", UN_DAYS),
        Kinenbi(5, 31, "no-tobacco", "世界禁煙デー", "世界保健機関（WHO）", "たばこを吸わないことを広めるために、世界保健機関が定めた", UN_DAYS),
        Kinenbi(6, 1, "kishou", "気象記念日", "気象庁", "1875 年 6 月 1 日に、東京気象台で気象の業務が始まったことから", "https://www.jma.go.jp/jma/kishou/intro/gyomu/index2.html"),
        Kinenbi(6, 5, "kankyou", "環境の日", "環境基本法", "1972 年 6 月 5 日に、国連人間環境会議が開かれたことから", "https://www.env.go.jp/guide/envmonth/"),
        Kinenbi(6, 10, "toki", "時の記念日", "生活改善同盟会・東京天文台", "日本書紀に、671 年のこの日に天智天皇が水時計で時を知らせたとあることから", "https://eco.mtk.nao.ac.jp/koyomi/wiki/BBFEB9EF2FBBFEA4CEB5ADC7B0C6FC.html"),
        Kinenbi(7, 1, "kokumin-anzen", "国民安全の日", "内閣（閣議了解）", "産業や交通の事故、火災などを防ぐ気持ちを高めるために定められた", "https://www.cao.go.jp/others/soumu/kokuminanzen/kokuminanzen.html"),
        Kinenbi(7, 11, "population", "世界人口デー", "国際連合", "1987 年 7 月 11 日に、世界の人口が 50 億人になったとされることから", UN_DAYS),
        Kinenbi(8, 1, "mizu", "水の日", "国（水循環基本法。もとは閣議了解）", "水の大切さを知ってもらうために定められた。8 月 1 日からの 1 週間は「水の週間」", "https://www.cas.go.jp/jp/seisaku/mizu_junkan/event/mizunohi.html"),
        Kinenbi(8, 10, "michi", "道の日", "建設省（今の国土交通省）", "1920 年 8 月 10 日に、日本で初めての道路の長期計画が始まったことから", "https://www.mlit.go.jp/road/toukei_chousa/road_db/pdf/2023/road_day.pdf"),
        Kinenbi(9, 1, "bousai", "防災の日", "内閣（閣議了解）", "1923 年 9 月 1 日の関東大震災の教訓を忘れないために定められた", "https://www.bousai.go.jp/kyoiku/week/bousaiweek.html"),
        Kinenbi(9, 21, "peace", "国際平和デー", "国際連合", "世界の停戦と非暴力を呼びかける日として、国際連合が定めた", UN_DAYS),
        Kinenbi(10, 1, "hou", "法の日", "内閣（閣議了解）", "1928 年 10 月 1 日に陪審法が施行されたことにちなむ「司法記念日」をもとに定められた", "https://www.moj.go.jp/hisho/kouhou/hisho06_00547.html"),
        Kinenbi(10, 14, "tetsudou", "鉄道の日", "国（今は国土交通省）", "1872 年 10 月 14 日に、新橋と横浜の間に日本で初めての鉄道が開業したことから", "https://www.mlit.go.jp/tetudo/tetudo_tk1_000004.html"),
        Kinenbi(11, 1, "keiryou", "計量記念日", "経済産業省", "今の計量法が施行された日にちなむ。11 月は「計量強調月間」", "https://www.meti.go.jp/policy/economy/hyojun/techno_infra/21_gaiyou_fukyukeihatsu.html"),
        Kinenbi(11, 9, "119", "119番の日", "消防庁", "消防の電話番号「119」にちなんで、1987 年に定められた", "https://www.fdma.go.jp/publication/hakusho/h21/4/cat2/1890.html"),
        Kinenbi(12, 1, "aids", "世界エイズデー", "世界保健機関（WHO）", "エイズへの理解を広めるために、世界保健機関が定めた", UN_DAYS),
        Kinenbi(12, 10, "human-rights", "人権デー", "国際連合", "1948 年 12 月 10 日に世界人権宣言が採択されたことから", UN_DAYS),
    )

    fun kinenbi(year: Int): List<Event> = KINENBI.map {
        Event(KoyomiKind.KINENBI, it.key, it.name, LocalDate.of(year, it.month, it.day), note = it.note, designatedBy = it.designatedBy)
    }

    /** Days kept differently by place (regional). The places are typical ones, not all of them. Checked on 2026-11-15. */
    val REGIONAL = listOf(
        Regional("bon-july", "お盆（7 月）", "おぼん", "東日本を中心とした一部の地域",
            "先祖の霊を迎えてまつる。新暦の 7 月 13 日から 16 日ごろに行う地域がある", "https://www.pref.hiroshima.lg.jp/site/rekishih/7gatu.html") { y -> LocalDate.of(y, 7, 13) to LocalDate.of(y, 7, 16) },
        Regional("bon-august", "お盆（8 月）", "おぼん", "全国の多くの地域",
            "先祖の霊を迎えてまつる。月遅れの 8 月 13 日から 16 日ごろに行う地域が多い", "https://www.town.arita.lg.jp/rekishi/kiji0031467/index.html") { y -> LocalDate.of(y, 8, 13) to LocalDate.of(y, 8, 16) },
        Regional("bon-lunar", "旧盆", "きゅうぼん", "沖縄・奄美など",
            "旧暦の 7 月 13 日から 15 日に、先祖の霊を迎えてまつる", "https://www.okinawa41.go.jp/sp080831") { y ->
            Lunisolar.dateOf(y, 7, 13)?.let { it to it.plusDays(2) }
        },
        Regional("jizobon", "地蔵盆", "じぞうぼん", "京都を中心とした地域",
            "地蔵菩薩の縁日（8 月 24 日）のころに、町内のお地蔵さまをまつる。子どもが主役の行事で、日にちは町内ごとに違う", "https://kyo-tsunagu.city.kyoto.lg.jp/jizo/kyonojizobon/") { y -> LocalDate.of(y, 8, 23) to LocalDate.of(y, 8, 24) },
        Regional("tookanya", "十日夜", "とおかんや", "関東・中部など",
            "旧暦 10 月 10 日の、田の神を送る収穫の行事。子どもがわらの束で地面を打って回る。今は新暦の 11 月 10 日に行うところもある", "https://www.tdc.ac.jp/igh/tabid/754/Default.aspx?itemid=1483&dispmid=2323") { y ->
            Lunisolar.dateOf(y, 10, 10)?.let { it to it }
        },
        Regional("inoko", "亥の子", "いのこ", "西日本を中心とした地域",
            "旧暦 10 月の亥の日に、収穫を祝い、無病息災を願う行事。亥の子餅を食べる習わしがある", "https://crd.ndl.go.jp/reference/entry/index.php?id=1000129287&page=ref_view") { y ->
            Lunisolar.dateOf(y, 10, 1)?.let { first -> (0L..11L).map { first.plusDays(it) }.first { Kanshi.branch(it) == 11 } }?.let { it to it }
        },
    )

    fun regional(year: Int): List<Event> = REGIONAL.mapNotNull { r ->
        r.days(year)?.let { (start, end) -> Event(KoyomiKind.REGIONAL, r.key, r.name, start, end, r.note, r.reading, regions = r.regions) }
    }
}
