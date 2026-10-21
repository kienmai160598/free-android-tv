# Debloat lists

One folder per TV model. `debloat.txt` is applied with `scripts/tvctl disable` and undone with
`scripts/tvctl restore`. Notes about each model (stock launcher, setupwraith, what must stay,
quirks) are in the comment header of its `debloat.txt`; read it before applying.

## Format

- Folder `<brand>-<model>/`, one `debloat.txt`.
- First line: a comment naming the TV, Android version and build (`scripts/tvctl probe` prints them).
- One package per line, `#` comments and trailing `# notes` allowed, grouped by purpose, no duplicates.
- Only packages actually disabled and lived with; say what broke and what was re-enabled.
- Name a non-default stock launcher or Live TV package in the header.
- Add a row below. `make test` checks every list and that this table matches the folders.

| Folder | TV | OS |
|---|---|---|
| `aipro2k/` | AiPro2K (Expressluck, dongnae; Changhong AI PONT family) | Android TV 14 |
| `aiwa-ai-pont-sa/` | AIWA AI PONT SA (Changhong, osaka, ISDB86AW) | Google TV 14 |
| `amazon-fire-tv-stick-4k-2018/` | Amazon Fire TV Stick 4K, 1st gen 2018 (AFTMM, mantis) | Fire OS 6.7.1.1 / Android 7.1 |
| `amazon-fire-tv-stick-lite-2020/` | Amazon Fire TV Stick Lite, 1st gen 2020 (AFTSS, sheldon) | Fire OS 7.7.1.7 / Android 9 |
| `benq-e55-735/` | BenQ E55-735 (Changhong AI PONT, DVB96BQ) | Google TV 14 |
| `benq-e55-750/` | BenQ E55-750 (Changhong AI PONT, DVB96BQ) | Google TV (Android 11) |
| `byintek-t5-max/` | BYINTEK T5 Max projector (built-in Google TV stick: SkyworthDigital YYT, Amlogic), Brazil | Google TV 14 |
| `chiq-gtvu65/` | CHiQ AI PONT (Changhong GTVU65, dangni, MediaTek mt5867) | Google TV 14 |
| `chiq-ai-pont/` | CHiQ AI PONT (DVB93CQ) | Android TV 11 |
| `google-chromecast-gtv-4k/` | Chromecast with Google TV 4K (sabrina) | Google TV 14 |
| `ffalcon-65s585c-pro/` | FFalcon (雷鸟) 鹤6 PRO 65S585C Pro (China, tcl_mt5879_cn, MediaTek MT9653) | Android TV 11 |
| `freebox-player-pop/` | Freebox Player POP (Free, fbx8am, Amlogic), IPTV/DVB-T box | Android TV 10 |
| `haier-android-tv-dvb2k/` | Haier Android TV DVB2K (broadway, HaierATVT56DVB) | Android TV 11 |
| `haier-h32s80efx/` | Haier H32S80EFX (MatrixTV CE, HRT56EU, deto, MediaTek), Thailand | Google TV 14 |
| `hisense-e5q-pro/` | Hisense E5Q Pro (VIDAA_TV, MediaTek MT9653), China | Android TV 11 |
| `hisense-hg9/` | Hisense HG9 (HiSmart 4K A4FFM) | Android TV 9 |
| `hisense-hismarttv-a4-android11/` | Hisense HiSmartTV A4 (xiaoyushan, MStar m7322), Android 11 firmware RTT2.221108.001 | Android TV 11 |
| `hisense-hismarttv-a4/` | Hisense HiSmartTV A4 (xiaoyushan, MStar m7322), India | Android TV 9 |
| `hisense-smarttv-4k-ffm/` | Hisense SmartTV 4K FFM (huangshan, MediaTek) | Android TV 10 |
| `hisense-u75-hengshan/` | Hisense U75 (hengshan, MediaTek), Latin America | Google TV 12 |
| `hitachi-65qledsm20/` | Hitachi 65QLEDSM20 (MTC, bangbae, MediaTek m7322), Australia | Android TV 11 |
| `hitachi-cdh-le32smart21/` | Hitachi CDH-LE32SMART21 (TCL-built C06, C06_2K_GB, MediaTek MT9221, 1 GB), Argentina | Android TV 11 |
| `insignia-ns55f301na25/` | Insignia 55" F30 4K Fire TV (NS-55F301NA25, AFTDCT31, duckie) | Fire OS 7 / Android 9 |
| `jmgo-n1s-holan/` | JMGO N1S projector (K80A, holan, MediaTek m7332), Australia | Google TV 14 |
| `kalley-r10g/` | Kalley R10G (device R10G, product ACER_FFM, MOKA, Realtek RTD2875P), Latin America | Google TV 14 |
| `mtc-uk-2k-android-tv/` | MTC UK 2K Android TV (tabata, MediaTek m5621; JVC-family firmware, `android.autoinstalls.config.JVC` present), UK | Android TV 11 |
| `nvidia-shield-tv-2019/` | NVIDIA SHIELD TV 2019 tube (P3430, sif) | Android TV 11 |
| `nvidia-shield-tv-pro-2019/` | NVIDIA SHIELD TV Pro 2019 (P2897, mdarcy) | Android TV 11 |
| `oneplus-tv-dubai/` | OnePlus TV (D00, Dubai) | Google TV 14 |
| `onn-4k-streaming-box-2023/` | onn 4K Streaming Box (2023, Walmart; YOC, Amlogic), US | Google TV 14 |
| `panasonic-4ktv-0su/` | Panasonic 4KTV-0SU (Skyworth-built SW4H, MediaTek m7332), Gulf region | Android TV 10 |
| `panasonic-4ktv-jup/` | Panasonic 4KTV-JUP (Jupiter_PA, MediaTek) | Android TV 11 |
| `panasonic-th-40hs550k/` | Panasonic TH-40HS550K (Skyworth-built, GS655 shibuya, MediaTek) | Android TV 11 |
| `philips-50pus8517/` | Philips 50PUS8517/12 (TP Vision, PH1M_EA_9970A, MediaTek MT5895), Europe | Android TV 11 |
| `philips-55oled707/` | Philips 55OLED707/12 (TP Vision, PH1M_EA_9970A, MediaTek MT5895), UK | Android TV 11 |
| `philips-55pus7503/` | Philips 55PUS7503/12 (TP Vision TPM171E, PH7M_EU_5596, MediaTek MT5891), Europe | Android TV 8.0 |
| `philips-65pus8102/` | Philips 65PUS8102/12 (TP Vision TPM171E, PH7M_EU_5596, MediaTek MT5891), Europe | Android TV 8.0 |
| `philips-r8g-google-tv/` | Philips R8G Google TV (MOKA, whitefield, Realtek RTD2851F), US | Google TV 14 |
| `philips-smart-4k-google-tv/` | Philips Smart 4K TV (Skyworth, eleonas, MediaTek), India | Google TV 14 |
| `philips-tpm191e/` | Philips TPM191E (TP Vision, PH9M_EA_5599, MediaTek mt5887), Europe | Android TV 12 |
| `portronics-beem-470/` | Portronics Beem 470 Smart LED Projector (Allwinner H713, 1 GB), India | Android 11 |
| `prismplus-q65-ultra-r10g/` | PRISM+ Q65 Ultra 2026 QLED 4K AI Google TV (R10G, PRISMPLUS_FFM, MOKA, Realtek RTD2875 SoC, RTD6748 platform) | Google TV 14 |
| `rca-ai-pont/` | RCA AI PONT SA (Changhong, redwood, ISDB92RC; also sold as realme 40-inch) | Android 14 |
| `redmi-mitv-antl0/` | Redmi TV (MiTV-ANTL0, starwars) | Android 5.1.1 (not Android TV) |
| `sanyo-atv-r2/` | SANYO ATV R2 (SCBC, Realtek RTD285O) | Android TV 9 |
| `skyworth-swtv-21ae/` | Skyworth SWTV-21AE (sunnyvale, MediaTek) | Android TV 14 |
| `skyworth-swtv-24na-4k-nf/` | Skyworth SWTV-24NA-4K NF (eleonas, MediaTek mt5896), US | Google TV 14 |
| `sony-bravia-ae2/` | Sony BRAVIA 4K AE2 (BRAVIA_AE2, MediaTek MT5897), North America | Google TV 14 |
| `sony-bravia-4k-gb/` | Sony BRAVIA 4K GB (BRAVIA_ATV2_EU, BRAVIA_ATV2) | Android TV 9 |
| `sony-bravia-4k-gb-us/` | Sony BRAVIA 4K GB (BRAVIA_ATV2_UC, MediaTek mt5891) | Android TV 9 |
| `sony-bravia-4k-ur2/` | Sony BRAVIA 4K UR2 (BRAVIA_UR2_4K, MediaTek) | Android TV 10 |
| `sony-bravia-4k-vh2/` | Sony BRAVIA 4K VH2 (KD-55X80J, BRAVIA_VH2, MediaTek), EU | Google TV 12 |
| `sony-bravia-4k-vh21/` | Sony BRAVIA 4K VH21 (KD-65X85K, 2022, BRAVIA_VH21, MediaTek MT5895), Australia | Google TV 12 |
| `sony-bravia-9-ii/` | Sony BRAVIA 9 II (K-65XR90M2, BRAVIA_AE3, MediaTek MT5838) | Google TV 14 |
| `sony-bravia-bf1/` | Sony BRAVIA BF1 (BRAVIA 2 II class, BRAVIA_BF1_4K_A, MediaTek) | Google TV 12 |
| `sony-kd-55x8509c/` | Sony BRAVIA KD-55X8509C (2015, "BRAVIA 4K 2015", SVP-DTV15, MediaTek), EU firmware | Android TV 7.0 |
| `sony-kd-55x85j/` | Sony BRAVIA KD-55X85J (2021, BRAVIA_VH2, MediaTek MT5895), South America | Google TV 12 |
| `sony-kd-55xe8596/` | Sony BRAVIA KD-55XE8596 (2017, "BRAVIA 4K GB", BRAVIA_ATV2_EU, MediaTek MT5891), UK | Android TV 9 |
| `sony-kd-65x80j/` | Sony BRAVIA KD-65X80J (2021, BRAVIA_VH2, MediaTek), India | Google TV 10 |
| `sony-kd-65x9300d/` | Sony BRAVIA KD-65X9300D (2016, SVP-DTV15, MediaTek), Asia-Pacific | Android TV 8.0 |
| `sony-bravia-tl2/` | Sony BRAVIA TL2 (OD0M_EA_T32, TPV-built, MediaTek), Europe | Android TV 11 |
| `sony-bravia-vu31/` | Sony BRAVIA VU31 4K (BRAVIA_VU31_4K, Realtek), India | Google TV 14 |
| `sony-xbr-x900e/` | Sony BRAVIA XBR-65X900E (2017, BRAVIA_ATV2) | Android TV 9 |
| `sony-xbr-65x900f/` | Sony BRAVIA XBR-65X900F (2018, BRAVIA_ATV3_4K, MediaTek MT5891), US/Canada | Android TV 9 |
| `stellamore-t950s/` | Stellamore T950S projector (Droidlogic be30a3, Amlogic txhd2) | Android 13 |
| `tcl-32s5400a/` | TCL 32S5400A (C06, C06_2K_GB, MediaTek MT9221, 1 GB), 2023 | Android TV 11 |
| `tcl-40s5400a/` | TCL 40S5400A (C06, C06_2K_GB, MediaTek MT9221, 1 GB) | Android TV 11 |
| `tcl-43c645/` | TCL 43C645 (Smart TV Pro, Realtek RTD2851 / R51MT05, V8-R51MT05-LF1V652) | Google TV launcher on Android TV 11 |
| `tcl-43ep660/` | TCL 43EP660 (Europe, BeyondTV, Realtek rtd285o, V8-R851T02-LF1V378) | Android TV 9 |
| `tcl-50ep680/` | TCL 50EP680 (Europe, BeyondTV, Realtek rtd285o) | Android TV 11 |
| `tcl-g10-google-tv/` | TCL 55" 4K Google TV, 2023 (G10, G10_4K_US_NF); also TCL 55P6K, Spain (G10_4K_GB_NF_32BIT) | Google TV 12 |
| `tcl-55c815/` | TCL 55C815 (2020, Europe/Denmark, BeyondTV2, Realtek rtd285o) | Android TV 11 |
| `tcl-q10g-pro/` | TCL 55Q10G Pro (China, tcl_mt5879_cn, MediaTek MT9952) | Android TV 11 |
| `tcl-55s470g/` | TCL 55S470G (G10, G10_4K_US_NF, Realtek), US | Google TV 12 |
| `tcl-65c725/` | TCL 65C725 (Smart TV Pro, BeyondTV4) | Android TV 11 |
| `tcl-65c735/` | TCL 65C735 (Smart TV Pro, G05, MediaTek), France | Google TV 11 |
| `tcl-ak30a5/` | TCL ak30a5 (China, Amlogic) | Android TV 9 |
| `tcl-beyondtv/` | TCL BeyondTV (India) | Android TV 9 |
| `tcl-beyondtv-rt2851/` | TCL BeyondTV, EU/Nordic (Realtek RTD2851, V8-R851T02-LF1V606) | Android TV 9 |
| `tcl-beyondtv2-latin-america/` | TCL BeyondTV2 (Latin America / Brazil, Realtek RTD2851) | Android TV 9 |
| `tcl-c755-google-tv/` | TCL C755 QD-Mini LED, 2024, Australia (G08, MediaTek tcl9618) | Google TV 14 |
| `tcl-qm6k-google-tv/` | TCL QM6K Pro, 2025 (G08, MediaTek tcl9618) | Google TV 14 |
| `tcl-g07-google-tv/` | TCL Smart TV (G07, G07_4K_GB_NF, Realtek RTD2851A, 2 GB) | Google TV 11 |
| `tcl-g05-google-tv/` | TCL Smart TV Pro (G05, G05_4K_US, MediaTek MT5889) | Google TV 11 |
| `tcl-uniontv-latin-america/` | TCL UnionTV, Latin America (TCL-built like Thomson 32HE5800, Realtek RT2841) | Android TV 9 |
| `thomson-32he5800/` | Thomson 32HE5800 (TCL-built UnionTV, Realtek RT2841), Spain | Android TV 9 |
| `toshiba-fire-tv-aftha004/` | Toshiba Fire TV (AFTHA004, hazel) | Fire OS 7 / Android 9 |
| `vidda-mt9653/` | Vidda VIDAA_TV (Hisense sub-brand; device MT9653, board mt5879), China JuUI | Android TV 11 |
| `vu-4k-lushan/` | Vu 4K (HVCL, lushan, MediaTek), India | Google TV 12 |
| `vu-premium-fhd-43-hamamatsucho/` | Vu Premium FHD 43-inch (Smart 2K ATV4, hamamatsucho), India | Android TV 9 |
| `wanbo-x5-pro/` | Wanbo X5 Pro projector (built-in Google TV stick: SkyworthDigital YYT, Amlogic), Australia | Google TV 14 |
| `xgimi-horizon-s-pro/` | XGIMI Horizon S Pro projector (XK03H, xgimigalileo, MediaTek) | Android TV 11 |
| `xiaomi-mi-box-s/` | Xiaomi Mi Box S (MIBOX4, oneday), streaming box | Android TV 9 |
| `xiaomi-mi-box-s-2nd-gen/` | Xiaomi Mi Box S 2nd gen (MiTV-AFKR0, jaws), streaming box | Google TV 11 |
| `xiaomi-mitv-mooq0/` | Xiaomi Mi TV (MiTV-MOOQ0, croods) | Android TV 10 |
| `xiaomi-mi-tv-4a/` | Xiaomi Mi TV 4A (MiTV-MOOQ1) | Android TV 10 |
| `xiaomi-mitv-aesp0/` | Xiaomi MiTV-AESP0 stick (aquaman, 1 GB) | Android TV 9 |
| `xiaomi-mitv-astp0/` | Xiaomi MiTV-ASTP0 (mulan, L55M7-EA), China firmware 22.12.2.1630, 1.5 GB RAM, 1080p | Android TV 9 (CN build) |
| `xiaomi-mitv-astp1/` | Xiaomi MiTV-ASTP1 (maverick), China firmware 2.40.641.0514.cn, 1 GB RAM, 720p | Android TV 9 (CN build) |
| `xiaomi-mitv-axfr1/` | Xiaomi MiTV-AXFR1 (rango, Amlogic, 1 GB), India | Android TV 11 |
| `xiaomi-mitv-axso0/` | Xiaomi MiTV-AXSO0 (dangal; Mi TV 4C Pro 2019 / 4A Pro) | Android TV 9 |
| `xiaomi-mitv-axso2/` | Xiaomi MiTV-AXSO2 (dangalUHD, India, 2 GB) | Android TV 9 |
| `xiaomi-mitv-moor2/` | Xiaomi MiTV-MOOR2 (venom, MediaTek) | Google TV 11 |
| `xiaomi-mitv-mosr4/` | Xiaomi MiTV-MOSR4 (irobot, MediaTek) | Google TV 11 |
| `xiaomi-mitv-mssp3/` | Xiaomi MiTV-MSSP3 (machuca, MediaTek) | Android TV 11 |
| `xiaomi-mitv-mssp3-android9/` | Xiaomi MiTV-MSSP3 (machuca, MediaTek), Android 9 firmware PI.7524 | Android TV 9 |
| `xiaomi-tv-a-pro-2026/` | Xiaomi TV A Pro 75" 2026 (MiTV-MZTU0, river, MediaTek) | Google TV 14 |
| `xiaomi-mitv-afmu0/` | Xiaomi TV Box S 3rd gen (MiTV-AFMU0, twilight, Amlogic), streaming box | Google TV 14 |

Models without a tested list: [`docs/devices/`](../../docs/devices/).
