# Terminology (single source)

One term per concept in each language. The Simplified Chinese terms follow upstream OpenJBD's
`values-zh-rCN/strings.xml` (written by its Chinese-speaking authors) and the register names in
JBD's protocol documentation. English and Korean follow the same concept list.

`scripts/check-design-tokens.py` enforces the "do not use" column and checks that every
translated string keeps the same format arguments as the English source. Add a row here and a
rule in `FORBIDDEN_TERMS` together.

| Concept | English | 한국어 | 简体中文 | Do not use |
|---|---|---|---|---|
| Cell | cell | 셀 | 单体 | zh 电芯 |
| Pack voltage | pack voltage | 팩 전압 | 总电压 / 总压 | zh 电池组电压, 总体 |
| Balance / balancing | balance, balancing | 밸런스, 밸런싱 | 均衡 | zh 平衡 |
| Balance start voltage | balance start voltage | 밸런스 시작 전압 | 均衡开启电压 | |
| Balance delta | balance delta | 밸런스 편차 | 均衡压差 | |
| Over-voltage / under-voltage | over-voltage / under-voltage | 과전압 / 저전압 | 过压 / 欠压 | |
| Pack over/under-voltage | pack over/under-voltage | 팩 과전압 / 저전압 | 总压过压 / 总压欠压 | zh 总体过压, 总体欠压 |
| Over-temperature / low-temperature | over-temperature / low-temperature | 과온 / 저온 | 高温 / 低温 | en under-temperature |
| Over-current | over-current | 과전류 | 过流 | |
| Protection / release | protection / release | 보호 / 해제 | 保护 / 恢复 | |
| Calibration | calibration | 보정 | 校准 | ko 캘리브레이션, zh 标定 |
| Factory mode (JBD register-write mode `00 56 78`) | factory mode | 공장 모드 | 工厂模式 | zh 出厂模式 (reads as factory reset) |
| Charge / discharge FET | charge / discharge FET (MOS) | 충전 / 방전 FET (MOS) | 充电 / 放电 FET (MOS) | |
| Design capacity | design capacity | 설계 용량 | 设计容量 | |
| Firmware | firmware | 펌웨어 | 固件 | |

Setting field names for protection thresholds end in "protection" / "보호" / "保护"; the
matching release fields end in "release" / "해제" / "恢复".

## Review status

- 2026-11-17: all product strings were checked against this table, upstream zh-rCN terms and the
  JBD register names (issue #31). Fixed: zh 出厂模式 → 工厂模式 (6), 电芯 → 单体 (9),
  总体过压/欠压 → 总压过压/欠压, 电池组电压 → 总电压, over-current fields now end in 保护;
  en under-temperature → low-temperature; ko 캘리브레이션 → 보정; protection field names end in
  "protection"/"보호" in every language.
- Translation quality is held to the same standard in every language: terminology table,
  automatic checks and on-device review. No language requires a separate native-speaker sign-off.
