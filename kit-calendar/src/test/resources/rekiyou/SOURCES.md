# テストの表の出どころ

`kit-calendar/tools/rekiyou_fixtures.py` で作った（2026-11-15）。

- sekki.tsv・phases.tsv: 国立天文台 暦計算室「暦要項」の二十四節気および雑節・朔弦望。2016〜2024 年は PDF（https://eco.mtk.nao.ac.jp/koyomi/yoko/pdf/yoko{年}.pdf）、2025〜2027 年は HTML（https://eco.mtk.nao.ac.jp/koyomi/yoko/{年}/rekiyou{下 2 桁}2.html・…3.html）。名前は残さず、「月 日 時 分」の組だけを残す
- holidays.tsv: 内閣府「国民の祝日について」の祝日の一覧（https://www8.cao.go.jp/chosei/shukujitsu/syukujitsu.csv）から 2016〜2027 年
