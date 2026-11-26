package io.github.duwenji.yorisoi.kit.llm

import io.github.duwenji.yorisoi.core.text.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Replies the app won't read out (AI-ROLES H-6 ③). */
class OutputCheckTest {
    @Test
    fun `URLs, email addresses and phone numbers are caught, in any width`() {
        assertEquals("a URL", OutputCheck.violation("詳しくは https://example.com で"))
        assertEquals("a URL", OutputCheck.violation("ｗｗｗ．example．com をご覧ください"))
        assertEquals("an email address", OutputCheck.violation("問い合わせは info@example.co.jp まで"))
        assertEquals("a phone number", OutputCheck.violation("電話は0467-22-0000です"))
        assertEquals("a phone number", OutputCheck.violation("０１２０ー１２３ー４５６へ"))
        assertEquals("a phone number", OutputCheck.violation("(0467)22-0000"))
        assertEquals("a phone number", OutputCheck.violation("09012345678"))
    }

    @Test
    fun `a reply written as notes is caught, before and after its marks are taken out`() {
        // A reply the operator's AI gave before a song: research notes, read out as they were.
        val notes = "# 調べた手がかり\n- 唱歌「うさぎ」は月を題材とし、秋の十五夜を連想させることが多い。"
        assertEquals(OutputCheck.NOTES, OutputCheck.violation(notes))
        assertEquals(OutputCheck.NOTES, OutputCheck.violation(OutputCheck.spoken(notes)))
        assertEquals(OutputCheck.NOTES, OutputCheck.violation("見どころ\n・雷門\n・仲見世"))
        assertNull(OutputCheck.violation("1：秋の風ですね。\n2：鳥の声がします。"))
        assertNull(OutputCheck.violation("雷門・仲見世・宝蔵門と続く参道です。"))
    }

    @Test
    fun `ordinary narration passes`() {
        assertNull(OutputCheck.violation("本宮は1180年に今の場所へ移されました。石段は61段あります。"))
        assertNull(OutputCheck.violation("ここから約450m、徒歩で6分ほどです。コーヒーの香りがします。"))
        assertNull(OutputCheck.violation("10月16日、気温は18℃です。"))
    }

    @Test
    fun `stage directions about the delivery are taken out`() {
        assertEquals(
            "そろそろ雨にぬれた路地の先に、日輪寺が見えてくる頃ですね。",
            OutputCheck.withoutStageDirections("（しっとりと、落ち着いた口調で）そろそろ雨にぬれた路地の先に、日輪寺が見えてくる頃ですね。"),
        )
        assertEquals(
            "門が見えてきました。ここは江戸のころからの古いお寺です。",
            OutputCheck.withoutStageDirections("門が見えてきました。(少し間を置いて) ここは江戸のころからの古いお寺です。"),
        )
        assertEquals("いい風ですね。", OutputCheck.withoutStageDirections("（明るい声で）いい風ですね。"))
    }

    @Test
    fun `readings and other brackets stay`() {
        val reading = "すぐ近くにある「吽形（うんぎょう）」というアート作品ですね。"
        assertEquals(reading, OutputCheck.withoutStageDirections(reading))
        val aside = "浅草寺（せんそうじ）の本堂です。（本堂は戦後に再建されました）"
        assertEquals(aside, OutputCheck.withoutStageDirections(aside))
    }

    @Test
    fun `Markdown marks are taken out, so the marks after them are seen`() {
        // A reply the operator's AI gave: 「**続き：**」 was read out with its marks.
        assertEquals(
            "のどかな景色です。\n\n続き：  \nそういえばこの「一之江」という地名は。\n\n続き：  \n車の流れを眺めていると落ち着きます。",
            OutputCheck.spoken("のどかな景色です。\n\n**続き：**  \nそういえばこの「一之江」という地名は。\n\n**続き：**  \n車の流れを眺めていると落ち着きます。"),
        )
        assertEquals("1：秋の風ですね。\n2：鳥の声がします。", OutputCheck.spoken("**1：**秋の風ですね。\n**2：**鳥の声がします。"))
        assertEquals("ここは大仏です。とても大きいですね。", OutputCheck.spoken("ここは*大仏*です。__とても__大きいですね。"))
        assertEquals("鶴岡八幡宮\n源氏の守り神です。", OutputCheck.spoken("## 鶴岡八幡宮\n源氏の守り神です。"))
        assertEquals("いい風ですね。", OutputCheck.spoken("（明るい声で）**いい風ですね。**"))
        assertEquals("いい風ですね。", OutputCheck.spoken("**（明るい声で）**いい風ですね。"))
    }

    @Test
    fun `ordinary narration is spoken as is`() {
        val text = "石段は61段、#1 の札がある門です。snake_case も残ります。"
        assertEquals(text, OutputCheck.spoken(text))
    }

    @Test
    fun `notes on how the reply was written are taken out`() {
        // A reply the operator's AI gave on 2026-11-21 ended with a note to itself.
        val reply = "1897年に開業した駅です。\n\n" +
            "そういえばさっきの亀有駅だけど、この辺りはお寺が多いんだよ。\n\n" +
            "創建年が1897年というのを踏まえて、ひと言触れました。区役所等はとても範囲外なので言及しません。「さっきの」で始め、本文と別の余談（この地域の特徴）を短く。"
        assertEquals(
            "1897年に開業した駅です。\n\nそういえばさっきの亀有駅だけど、この辺りはお寺が多いんだよ。",
            OutputCheck.spoken(reply),
        )
    }

    @Test
    fun `speech that says 余談 or 続き stays`() {
        val text = "余談ですが、この橋は二代目です。\n続き：そういえばさっきの橋、夜も明るいんです。"
        assertEquals(text, OutputCheck.spoken(text))
    }

    @Test
    fun `a reply mostly in another language than the app's is unusable, names in their own script are not`() {
        val japanese = "ここは浅草寺です。とても古いお寺です。"
        assertEquals(null, OutputCheck.violation(japanese))
        assertEquals(OutputCheck.OTHER_LANGUAGE, OutputCheck.violation(japanese, AppLanguage.EN))
        assertEquals(OutputCheck.OTHER_LANGUAGE, OutputCheck.violation(japanese, AppLanguage.ZH_HANS))
        assertEquals(OutputCheck.OTHER_LANGUAGE, OutputCheck.violation(japanese, AppLanguage.KO))
        assertEquals(null, OutputCheck.violation("This is Sensō-ji (浅草寺), the oldest temple in Tokyo.", AppLanguage.EN))
        assertEquals(null, OutputCheck.violation("这里是浅草寺，是东京最古老的寺院。", AppLanguage.ZH_HANS))
        assertEquals(null, OutputCheck.violation("여기는 센소지(浅草寺)입니다. 도쿄에서 가장 오래된 절입니다.", AppLanguage.KO))
    }
}
