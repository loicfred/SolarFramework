package org.solarframework.lang;

import java.util.Arrays;

import static org.solarframework.core.util.StringUtils.similarity;

public enum Nationalities {

    English_US("United States", "United States", "U+1f1ec U+1f1e7", ":flag_us:", "US", "USA", "840"),
    English_UK("United Kingdom", "United Kingdom", "U+1f1fa U+1f1f8", ":flag_gb:", "GB", "GBR", "826"),
    Dutch("Netherlands", "Magyar", "U+1f1f3 U+1f1f1", ":flag_nl:", "NL", "NLD", "528"),
    French("France", "Français", "U+1f1eb U+1f1f7", ":flag_fr:", "FR", "FRA", "250"),
    Italian("Italy", "Italiano", "U+1f1ee U+1f1f9", ":flag_it:", "IT", "ITA", "380"),
    Spanish("Spain", "Español", "U+1f1ea U+1f1f8", ":flag_es:", "ES", "ESP", "724"),
    Japanese("Japan", "日本語", "U+1f1ef U+1f1f5", ":flag_jp:", "JP", "JPN", "392"),
    German("Germany", "Deutsch", "U+1f1e9 U+1f1ea", ":flag_de:", "DE", "DEU", "276"),
    Polish("Poland", "Polski", "U+1f1f5 U+1f1f1", ":flag_pl:", "PL", "POL", "616"),
    Hindi("India", "हिन्दी", "U+1f1ee U+1f1f3", ":flag_in:", "IN", "IND", "356"),
    Swedish("Sweden", "Svenska", "U+1f1f8 U+1f1ea", ":flag_se:", "SE", "SWE", "752"),
    Korean("Korea", "한국어", "U+1f1f0 U+1f1f7", ":flag_kr:", "KR", "KOR", "410"),
    Bulgarian("Bulgaria", "български", "U+1f1e7 U+1f1ec", ":flag_bg:", "BG", "BGR", "100"),
    Russian("Russia", "Pусский", "U+1f1f7 U+1f1fa", ":flag_ru:", "RU", "RUS", "643"),
    Greek("Greece", "Ελληνικά", "U+1f1ec U+1f1f7", ":flag_gr:", "GR", "GRC", "300"),
    Turkish("Turkey", "Türkçe", "U+1f1f9 U+1f1f7", ":flag_tr:", "TR", "TUR", "792"),
    Ukrainian("Ukraine", "Українська", "U+1f1fa U+1f1e6", ":flag_ua:", "UA", "UKR", "804"),
    Hungarian("Hungary", "Magyar", "U+1f1ed U+1f1fa", ":flag_hu:", "HU", "HUN", "348"),
    Finnish("Finland", "Suomi", "U+1f1eb U+1f1ee", ":flag_fi:", "FI", "FIN", "246"),
    Romanian("Romania", "Română", "U+1f1f7 U+1f1f4", ":flag_ro:", "RO", "ROU", "642"),
    Norwegian("Norway", "Norsk", "U+1f1f3 U+1f1f4", ":flag_no:", "NO", "NOR", "578"),
    Croatian("Croatia", "Hrvatski", "U+1f1ed U+1f1f7", ":flag_hr:", "HR", "HRV", "191"),
    Danish("Denmark", "Dansk", "U+1f1e9 U+1f1f0", ":flag_dk:", "DK", "DNK", "208"),
    Thai("Thai", "ไทย", "U+1f1f9 U+1f1ed", ":flag_th:", "TH", "THA", "764"),
    Czech("Czechia", "Čeština", "U+1f1e8 U+1f1ff", ":flag_cz:", "CZ", "CZE", "203"),
    Vietnamese("Vietnam", "Tiếng Việt", "U+1f1fb U+1f1f3", ":flag_vn:", "VN", "VNM", "704"),
    Lithuanian("Lithuania", "Lietuviškai", "U+1f1f1 U+1f1f9", ":flag_lt:", "LT", "LTU", "440"),
    Chinese("China", "中文", "U+1f1e8 U+1f1f3", ":flag_cn:", "CN", "CHN", "156"),
    Taiwanese("Taiwan, China", "繁體中文, 中文", "U+1f1fc U+1f1f8", ":flag_tw:", "TW", "TWN", "158"),
    Georgian("Georgia", "საქართველო", "U+1f1ec U+1f1ea", ":flag_ge:", "GE", "GEO", "268"),
    Canadian("Canada", "Canada", "U+1f1e8 U+1f1e6", ":flag_ca:", "CA", "CAN", "124"),
    Mauritian("Mauritius", "Maurice", "U+1f1f2 U+1f1fa", ":flag_mu:", "MU", "MUS", "480"),
    Portuguese("Portugal", "Portugal", "U+1f1f5 U+1f1f9", ":flag_pt:", "PT", "PRT", "620"),
    Brazilian("Brazil", "Brasil", "U+1f1e7 U+1f1f7", ":flag_br:", "BR", "BRA", "076"),
    Peruvian("Peru", "Perú", "U+1f1f5 U+1f1ea", ":flag_pe:", "PE", "PER", "604"),
    Colombian("Colombia", "Colombia", "U+1f1e8 U+1f1f4", ":flag_co:", "CO", "COL", "170"),
    Venezuelan("Venezuela", "Venezuela", "U+1f1fb U+1f1ea", ":flag_ve:", "VE", "VEN", "862"),
    Austrian("Austria", "Österreich", "U+1f1e6 U+1f1f9", ":flag_at:", "AT", "AUT", "040"),
    Belgian("Belgium", "Belgique", "U+1f1e7 U+1f1ea", ":flag_be:", "BE", "BEL", "056"),
    Indonesian("Indonesia", "Indonesia", "U+1f1ee U+1f1e9", ":flag_id:", "ID", "IDN", "360"),
    Hong_Konger("Hong Kong", "香港", "U+1f1ed U+1f1f0", ":flag_hk:", "HK", "HKG", "344"),
    Moroccan("Morocco", "المغرب", "U+1f1f2 U+1f1e6", ":flag_ma:", "MA", "MAR", "504"),
    Tunisian("Tunisia", "تونس", "U+1f1f9 U+1f1f3", ":flag_tn:", "TN", "TUN", "788"),
    Nigerian("Nigeria", "Nigeria", "U+1f1f3 U+1f1ec", ":flag_ng:", "NG", "NGA", "566"),
    Guadeloupean("Guadeloupe", "Guadeloupe", "U+1f1ec U+1f1f5", ":flag_gp:", "GP", "GLP", "312"),
    Luxembourger("Luxembourg", "Lëtzebuerg", "U+1f1f1 U+1f1fa", ":flag_lu:", "LU", "LUX", "442"),
    Chilean("Chile", "Chile", "U+1f1e8 U+1f1f1", ":flag_cl:", "CL", "CHL", "152"),
    Macedonian("Macedonia", "Македонија", "U+1f1f2 U+1f1f0", ":flag_mk:", "MK", "MKD", "807"),
    Irish("Ireland", "Éire", "U+1f1ee U+1f1ea", ":flag_ie:", "IE", "IRL", "372"),
    Swiss("Switzerland", "Schweiz", "U+1f1e8 U+1f1ed", ":flag_ch:", "CH", "CHE", "756"),
    Sammarinese("San Marino", "San Marino", "U+1f1f8 U+1f1f2", ":flag_sm:", "SM", "SMR", "674"),
    Mexican("Mexico", "México", "U+1f1f2 U+1f1fd", ":flag_mx:", "MX", "MEX", "484"),
    Albanian("Albania", "Shqipëria", "U+1f1e6 U+1f1f1", ":flag_al:", "AL", "ALB", "008"),
    Andorran("Andorra", "Andorra", "U+1f1e6 U+1f1e9", ":flag_ad:", "AD", "AND", "020"),
    Armenian("Armenia", "Հայաստան", "U+1f1e6 U+1f1f2", ":flag_am:", "AM", "ARM", "051"),
    Azerbaijani("Azerbaijan", "Azərbaycan", "U+1f1e6 U+1f1ff", ":flag_az:", "AZ", "AZE", "031"),
    Belarusian("Belarus", "Беларусь", "U+1f1e7 U+1f1fe", ":flag_by:", "BY", "BLR", "112"),
    Bosnian("Bosnia and Herzegovina", "Bosna i Hercegovina", "U+1f1e7 U+1f1e6", ":flag_ba:", "BA", "BIH", "070"),
    Cypriot("Cyprus", "Κύπρος", "U+1f1e8 U+1f1fe", ":flag_cy:", "CY", "CYP", "196"),
    Estonian("Estonia", "Eesti", "U+1f1ea U+1f1ea", ":flag_ee:", "EE", "EST", "233"),
    Icelander("Iceland", "Ísland", "U+1f1ee U+1f1f8", ":flag_is:", "IS", "ISL", "352"),
    Kazakhstani("Kazakhstan", "Қазақстан", "U+1f1f0 U+1f1ff", ":flag_kz:", "KZ", "KAZ", "398"),
    Latvian("Latvia", "Latvija", "U+1f1f1 U+1f1fb", ":flag_lv:", "LV", "LVA", "428"),
    Liechtensteiner("Liechtenstein", "Liechtenstein", "U+1f1f1 U+1f1ee", ":flag_li:", "LI", "LIE", "438"),
    Maltese("Malta", "Malta", "U+1f1f2 U+1f1f9", ":flag_mt:", "MT", "MLT", "470"),
    Moldovan("Moldova", "Moldova", "U+1f1f2 U+1f1e9", ":flag_md:", "MD", "MDA", "498"),
    Monacan("Monaco", "Monaco", "U+1f1f2 U+1f1e8", ":flag_mc:", "MC", "MCO", "492"),
    Montenegrin("Montenegro", "Црна Гора", "U+1f1f2 U+1f1ea", ":flag_me:", "ME", "MNE", "499"),
    Serbian("Serbia", "Србија", "U+1f1f7 U+1f1f8", ":flag_rs:", "RS", "SRB", "688"),
    Slovak("Slovakia", "Slovensko", "U+1f1f8 U+1f1f0", ":flag_sk:", "SK", "SVK", "703"),
    Slovene("Slovenia", "Slovenija", "U+1f1f8 U+1f1ee", ":flag_si:", "SI", "SVN", "705"),
    Vatican("Vatican City", "Vaticano", "U+1f1fb U+1f1e6", ":flag_va:", "VA", "VAT", "336"),
    Lebanese("Lebanon", "Lubnan", "U+1f1f1 U+1f1e7", ":flag_lb:", "LB", "LBN", "422"),
    Australian("Australia", "Australia", "U+1f1e6 U+1f1fa", ":flag_au:", "AU", "AUS", "036"),
    Argentinian("Argentina", "Argentina", "U+1f1e6 U+1f1f7", ":flag_ar:", "AR", "ARG", "032"),
    Egyptian("Egypt", "مصر‎", "U+1f1ea U+1f1ec", ":flag_eg:", "EG", "EGY", "818"),
    South_African("South Africa", "South Africa", "U+1f1ff U+1f1e6", ":flag_za:", "ZA", "ZAF", "710"),
    Saudi_Arabian("Saudi Arabia", "المملكة العربية السعودية", "U+1f1f8 U+1f1e6", ":flag_sa:", "SA", "SAU", "682"),
    New_Zealander("New Zealand", "New Zealand", "U+1f1f3 U+1f1ff", ":flag_nz:", "NZ", "NZL", "554"),
    Singaporean("Singapore", "Singapore", "U+1f1f8 U+1f1ec", ":flag_sg:", "SG", "SGP", "702"),
    Malaysian("Malaysia", "Malaysia", "U+1f1f2 U+1f1fe", ":flag_my:", "MY", "MYS", "458"),
    Filipino("Philippines", "Pilipinas", "U+1f1f5 U+1f1ed", ":flag_ph:", "PH", "PHL", "608"),
    Pakistani("Pakistan", "پاکستان", "U+1f1f5 U+1f1f0", ":flag_pk:", "PK", "PAK", "586"),
    Bangladeshi("Bangladesh", "বাংলাদেশ", "U+1f1e7 U+1f1e9", ":flag_bd:", "BD", "BGD", "050"),
    Sri_Lankan("Sri Lanka", "ශ්‍රී ලංකාව", "U+1f1f1 U+1f1f0", ":flag_lk:", "LK", "LKA", "144"),
    Nepali("Nepal", "नेपाल", "U+1f1f3 U+1f1f5", ":flag_np:", "NP", "NPL", "524"),
    Afghan("Afghanistan", "افغانستان", "U+1f1e6 U+1f1eb", ":flag_af:", "AF", "AFG", "004"),
    Iraqi("Iraq", "العراق", "U+1f1ee U+1f1f6", ":flag_iq:", "IQ", "IRQ", "368"),
    Iranian("Iran", "ایران", "U+1f1ee U+1f1f7", ":flag_ir:", "IR", "IRN", "364"),
    Syrian("Syria", "سوريا", "U+1f1f8 U+1f1fe", ":flag_sy:", "SY", "SYR", "760"),
    Jordanian("Jordan", "الأردن", "U+1f1ef U+1f1f4", ":flag_jo:", "JO", "JOR", "400"),
    Kuwaiti("Kuwait", "الكويت", "U+1f1f0 U+1f1fc", ":flag_kw:", "KW", "KWT", "414"),
    Omani("Oman", "عمان", "U+1f1f4 U+1f1f2", ":flag_om:", "OM", "OMN", "512"),
    Emirati("United Arab Emirates", "الإمارات العربية المتحدة", "U+1f1e6 U+1f1ea", ":flag_ae:", "AE", "ARE", "784"),
    Qatari("Qatar", "قطر", "U+1f1f6 U+1f1e6", ":flag_qa:", "QA", "QAT", "634"),
    Bahraini("Bahrain", "البحرين", "U+1f1e7 U+1f1ed", ":flag_bh:", "BH", "BHR", "048"),
    Yemeni("Yemen", "اليمن", "U+1f1fe U+1f1ea", ":flag_ye:", "YE", "YEM", "887"),
    Palestinian("Palestine", "فلسطين", "U+1f1f5 U+1f1f8", ":flag_ps:", "PS", "PSE", "275"),
    Algerian("Algeria", "الجزائر", "U+1f1e9 U+1f1ff", ":flag_dz:", "DZ", "DZA", "012"),
    Libyan("Libya", "ليبيا", "U+1f1f1 U+1f1fe", ":flag_ly:", "LY", "LBY", "434"),
    Kenyan("Kenya", "Kenya", "U+1f1f0 U+1f1ea", ":flag_ke:", "KE", "KEN", "404"),
    Ugandan("Uganda", "Uganda", "U+1f1fa U+1f1ec", ":flag_ug:", "UG", "UGA", "800"),
    Tanzanian("Tanzania", "Tanzania", "U+1f1f9 U+1f1ff", ":flag_tz:", "TZ", "TZA", "834"),
    Ethiopian("Ethiopia", "ኢትዮጵያ", "U+1f1ea U+1f1f9", ":flag_et:", "ET", "ETH", "231"),
    Somalian("Somalia", "Soomaaliya", "U+1f1f8 U+1f1f4", ":flag_so:", "SO", "SOM", "706"),
    Sudanese("Sudan", "السودان", "U+1f1f8 U+1f1e9", ":flag_sd:", "SD", "SDN", "729"),
    Ghanaian("Ghana", "Ghana", "U+1f1ec U+1f1ed", ":flag_gh:", "GH", "GHA", "288"),
    Ivorian("Ivory Coast", "Côte d'Ivoire", "U+1f1e8 U+1f1ee", ":flag_ci:", "CI", "CIV", "384"),
    Senegalese("Senegal", "Sénégal", "U+1f1f8 U+1f1f3", ":flag_sn:", "SN", "SEN", "686"),
    Zimbabwean("Zimbabwe", "Zimbabwe", "U+1f1ff U+1f1fc", ":flag_zw:", "ZW", "ZWE", "716"),
    Zambian("Zambia", "Zambia", "U+1f1ff U+1f1f2", ":flag_zm:", "ZM", "ZMB", "894"),
    Malawian("Malawi", "Malawi", "U+1f1f2 U+1f1fc", ":flag_mw:", "MW", "MWI", "454"),
    Angolan("Angola", "Angola", "U+1f1e6 U+1f1f4", ":flag_ao:", "AO", "AGO", "024"),
    Cameroonian("Cameroon", "Cameroun", "U+1f1e8 U+1f1f2", ":flag_cm:", "CM", "CMR", "120"),
    Congolese("Congo", "Congo", "U+1f1e8 U+1f1ec", ":flag_cg:", "CG", "COG", "178"),
    Gabonese("Gabon", "Gabon", "U+1f1ec U+1f1e6", ":flag_ga:", "GA", "GAB", "266"),
    Burundian("Burundi", "Burundi", "U+1f1e7 U+1f1ee", ":flag_bi:", "BI", "BDI", "108"),
    Mozambican("Mozambique", "Moçambique", "U+1f1f2 U+1f1ff", ":flag_mz:", "MZ", "MOZ", "508"),
    Botswanan("Botswana", "Botswana", "U+1f1e7 U+1f1fc", ":flag_bw:", "BW", "BWA", "072"),
    Namibian("Namibia", "Namibia", "U+1f1f3 U+1f1e6", ":flag_na:", "NA", "NAM", "516"),
    Madagascan("Madagascar", "Madagasikara", "U+1f1f2 U+1f1ec", ":flag_mg:", "MG", "MDG", "450"),
    Rwandan("Rwanda", "Rwanda", "U+1f1f7 U+1f1fc", ":flag_rw:", "RW", "RWA", "646"),
    Beninese("Benin", "Bénin", "U+1f1e7 U+1f1ef", ":flag_bj:", "BJ", "BEN", "204"),
    Togolese("Togo", "Togo", "U+1f1f9 U+1f1ec", ":flag_tg:", "TG", "TGO", "768"),
    Seychellois("Seychelles", "Seychelles", "U+1f1f8 U+1f1e8", ":flag_sc:", "SC", "SYC", "690"),
    Malian("Mali", "Mali", "U+1f1f2 U+1f1f1", ":flag_ml:", "ML", "MLI", "466"),
    Nigerien("Niger", "Niger", "U+1f1f3 U+1f1ea", ":flag_ne:", "NE", "NER", "562"),
    Chadian("Chad", "Tchad", "U+1f1f9 U+1f1e9", ":flag_td:", "TD", "TCD", "148"),
    Central_African("Central African Republic", "République centrafricaine", "U+1f1e8 U+1f1eb", ":flag_cf:", "CF", "CAF", "140"),
    Eritrean("Eritrea", "ኤርትራ", "U+1f1ea U+1f1f7", ":flag_er:", "ER", "ERI", "232"),
    Mauritanian("Mauritania", "موريتانيا", "U+1f1f2 U+1f1f7", ":flag_mr:", "MR", "MRT", "478"),
    Sierra_Leonean("Sierra Leone", "Sierra Leone", "U+1f1f8 U+1f1f1", ":flag_sl:", "SL", "SLE", "694"),
    Gambian("Gambia", "Gambia", "U+1f1ec U+1f1f2", ":flag_gm:", "GM", "GMB", "270"),
    Burkinabe("Burkina Faso", "Burkina Faso", "U+1f1e7 U+1f1eb", ":flag_bf:", "BF", "BFA", "854"),
    Guinean("Guinea", "Guinée", "U+1f1ec U+1f1f3", ":flag_gn:", "GN", "GIN", "324"),
    Guinean_Bissauan("Guinea-Bissau", "Guiné-Bissau", "U+1f1ec U+1f1fc", ":flag_gw:", "GW", "GNB", "624"),
    Equatorial_Guinean("Equatorial Guinea", "Guinea Ecuatorial", "U+1f1ec U+1f1f6", ":flag_gq:", "GQ", "GNQ", "226"),
    Cape_Verdean("Cape Verde", "Cabo Verde", "U+1f1e8 U+1f1fb", ":flag_cv:", "CV", "CPV", "132"),
    Lesothan("Lesotho", "Lesotho", "U+1f1f1 U+1f1f8", ":flag_ls:", "LS", "LSO", "426"),
    Liberian("Liberia", "Liberia", "U+1f1f1 U+1f1f7", ":flag_lr:", "LR", "LBR", "430"),
    Maldivian("Maldives", "ދިވެހިރާއްޖޭގެ", "U+1f1f2 U+1f1fb", ":flag_mv:", "MV", "MDV", "462"),
    Bhutanese("Bhutan", "འབྲུག་ཡུལ", "U+1f1e7 U+1f1f9", ":flag_bt:", "BT", "BTN", "064"),
    Mongolian("Mongolia", "Монгол", "U+1f1f2 U+1f1f3", ":flag_mn:", "MN", "MNG", "496"),
    Uzbekistani("Uzbekistan", "O'zbekiston", "U+1f1fa U+1f1ff", ":flag_uz:", "UZ", "UZB", "860"),
    Turkmenistani("Turkmenistan", "Türkmenistan", "U+1f1f9 U+1f1f2", ":flag_tm:", "TM", "TKM", "795"),
    Kyrgyzstani("Kyrgyzstan", "Кыргызстан", "U+1f1f0 U+1f1ec", ":flag_kg:", "KG", "KGZ", "417"),
    Tajikistani("Tajikistan", "Тоҷикистон", "U+1f1f9 U+1f1ef", ":flag_tj:", "TJ", "TJK", "762"),
    Israeli("Israel", "יִשְׂרָאֵל", "U+1f1ee U+1f1f1", ":flag_il:", "IL", "ISR", "376"),
    Nepalese("Nepal", "नेपाल", "U+1f1f3 U+1f1f5", ":flag_np:", "NP", "NPL", "524"),
    Afghani("Afghanistan", "افغانستان", "U+1f1e6 U+1f1eb", ":flag_af:", "AF", "AFG", "004"),
    Bruneian("Brunei", "Negara Brunei Darussalam", "U+1f1e7 U+1f1f3", ":flag_bn:", "BN", "BRN", "096"),
    Laotian("Laos", "ສ.ປ.ປ ລາວ", "U+1f1f1 U+1f1e6", ":flag_la:", "LA", "LAO", "418"),
    Cambodian("Cambodia", "កម្ពុជា", "U+1f1f0 U+1f1ed", ":flag_kh:", "KH", "KHM", "116"),
    Burmese("Myanmar", "မြန်မာ", "U+1f1f2 U+1f1f2", ":flag_mm:", "MM", "MMR", "104"),
    East_Timorese("East Timor", "Timor-Leste", "U+1f1f9 U+1f1f1", ":flag_tl:", "TL", "TLS", "626"),
    South_Korean("South Korea", "대한민국", "U+1f1f0 U+1f1f7", ":flag_kr:", "KR", "KOR", "410"),
    North_Korean("North Korea", "조선민주주의인민공화국", "U+1f1f0 U+1f1f5", ":flag_kp:", "KP", "PRK", "408"),
    Macanese("Macau", "澳門", "U+1f1f2 U+1f1f4", ":flag_mo:", "MO", "MAC", "446"),
    Tibetan("Tibet", "བོད་", "U+1f1e8 U+1f1f3", ":flag_cn:", "CN", "CHN", "156"),
    Malagasy("Madagascar", "Madagasikara", "U+1f1f2 U+1f1ec", ":flag_mg:", "MG", "MDG", "450"),
    South_Sudanese("South Sudan", "South Sudan", "U+1f1f8 U+1f1f8", ":flag_ss:", "SS", "SSD", "728"),
    Djiboutian("Djibouti", "Djibouti", "U+1f1e9 U+1f1ef", ":flag_dj:", "DJ", "DJI", "262"),
    Somali("Somalia", "Soomaaliya", "U+1f1f8 U+1f1f4", ":flag_so:", "SO", "SOM", "706"),
    Congolese_DR("Democratic Republic of the Congo", "République démocratique du Congo", "U+1f1e8 U+1f1e9", ":flag_cd:", "CD", "COD", "180"),
    Congolese_Brazzaville("Republic of the Congo", "République du Congo", "U+1f1e8 U+1f1ec", ":flag_cg:", "CG", "COG", "178"),
    Equatoguinean("Equatorial Guinea", "Guinea Ecuatorial", "U+1f1ec U+1f1f6", ":flag_gq:", "GQ", "GNQ", "226"),
    Comoran("Comoros", "Comores", "U+1f1f0 U+1f1f2", ":flag_km:", "KM", "COM", "174"),
    Swazi("Eswatini", "eSwatini", "U+1f1f8 U+1f1ff", ":flag_sz:", "SZ", "SWZ", "748"),
    Santoméan("São Tomé and Príncipe", "São Tomé e Príncipe", "U+1f1f8 U+1f1f9", ":flag_st:", "ST", "STP", "678"),
    Fijian("Fiji", "Fiji", "U+1f1eb U+1f1ef", ":flag_fj:", "FJ", "FJI", "242"),
    Papua_New_Guinean("Papua New Guinea", "Papua New Guinea", "U+1f1f5 U+1f1ec", ":flag_pg:", "PG", "PNG", "598"),
    Solomon_Islander("Solomon Islands", "Solomon Islands", "U+1f1f8 U+1f1e7", ":flag_sb:", "SB", "SLB", "090"),
    Vanuatuan("Vanuatu", "Vanuatu", "U+1f1fb U+1f1fa", ":flag_vu:", "VU", "VUT", "548"),
    Samoan("Samoa", "Samoa", "U+1f1fc U+1f1f8", ":flag_ws:", "WS", "WSM", "882"),
    Tongan("Tonga", "Tonga", "U+1f1f9 U+1f1f4", ":flag_to:", "TO", "TON", "776"),
    Palauan("Palau", "Palau", "U+1f1f5 U+1f1fc", ":flag_pw:", "PW", "PLW", "585"),
    Marshallese("Marshall Islands", "Marshall Islands", "U+1f1f2 U+1f1ed", ":flag_mh:", "MH", "MHL", "584"),
    Micronesian("Micronesia", "Micronesia", "U+1f1eb U+1f1f2", ":flag_fm:", "FM", "FSM", "583"),
    Nauruan("Nauru", "Nauru", "U+1f1f3 U+1f1f7", ":flag_nr:", "NR", "NRU", "520"),
    Tuvaluan("Tuvalu", "Tuvalu", "U+1f1f9 U+1f1fb", ":flag_tv:", "TV", "TUV", "798"),
    Kiribati("Kiribati", "Kiribati", "U+1f1f0 U+1f1ee", ":flag_ki:", "KI", "KIR", "296"),
    Surinamese("Suriname", "Suriname", "U+1f1f8 U+1f1f7", ":flag_sr:", "SR", "SUR", "740"),
    Guyanese("Guyana", "Guyana", "U+1f1ec U+1f1fe", ":flag_gy:", "GY", "GUY", "328"),
    Trinidadian("Trinidad and Tobago", "Trinidad and Tobago", "U+1f1f9 U+1f1f9", ":flag_tt:", "TT", "TTO", "780"),
    Jamaican("Jamaica", "Jamaica", "U+1f1ef U+1f1f2", ":flag_jm:", "JM", "JAM", "388"),
    Barbadian("Barbados", "Barbados", "U+1f1e7 U+1f1e7", ":flag_bb:", "BB", "BRB", "052"),
    Bahamian("Bahamas", "Bahamas", "U+1f1e7 U+1f1f8", ":flag_bs:", "BS", "BHS", "044"),
    Grenadian("Grenada", "Grenada", "U+1f1ec U+1f1e9", ":flag_gd:", "GD", "GRD", "308"),
    Saint_Lucian("Saint Lucia", "Saint Lucia", "U+1f1f1 U+1f1e8", ":flag_lc:", "LC", "LCA", "662"),
    Saint_Vincentian("Saint Vincent and the Grenadines", "Saint Vincent and the Grenadines", "U+1f1fb U+1f1e8", ":flag_vc:", "VC", "VCT", "670"),
    Antiguan("Antigua and Barbuda", "Antigua and Barbuda", "U+1f1e6 U+1f1ec", ":flag_ag:", "AG", "ATG", "028"),
    Dominican_Commonwealth("Dominica", "Dominica", "U+1f1e9 U+1f1f2", ":flag_dm:", "DM", "DMA", "212"),
    Dominican_Republic("Dominican Republic", "República Dominicana", "U+1f1e9 U+1f1f4", ":flag_do:", "DO", "DOM", "214"),
    Haitian("Haiti", "Haïti", "U+1f1ed U+1f1f9", ":flag_ht:", "HT", "HTI", "332"),
    Cuban("Cuba", "Cuba", "U+1f1e8 U+1f1fa", ":flag_cu:", "CU", "CUB", "192"),
    Belizean("Belize", "Belize", "U+1f1e7 U+1f1ff", ":flag_bz:", "BZ", "BLZ", "084"),
    Panamanian("Panama", "Panamá", "U+1f1f5 U+1f1e6", ":flag_pa:", "PA", "PAN", "591"),
    Costa_Rican("Costa Rica", "Costa Rica", "U+1f1e8 U+1f1f7", ":flag_cr:", "CR", "CRI", "188"),
    Nicaraguan("Nicaragua", "Nicaragua", "U+1f1f3 U+1f1ee", ":flag_ni:", "NI", "NIC", "558"),
    Honduran("Honduras", "Honduras", "U+1f1ed U+1f1f3", ":flag_hn:", "HN", "HND", "340"),
    Salvadoran("El Salvador", "El Salvador", "U+1f1f8 U+1f1fb", ":flag_sv:", "SV", "SLV", "222"),
    Guatemalan("Guatemala", "Guatemala", "U+1f1ec U+1f1f9", ":flag_gt:", "GT", "GTM", "320"),
    Ecuadorian("Ecuador", "Ecuador", "U+1f1ea U+1f1e8", ":flag_ec:", "EC", "ECU", "218"),
    Bolivian("Bolivia", "Bolivia", "U+1f1e7 U+1f1f4", ":flag_bo:", "BO", "BOL", "068"),
    Paraguayan("Paraguay", "Paraguay", "U+1f1f5 U+1f1fe", ":flag_py:", "PY", "PRY", "600"),
    Uruguayan("Uruguay", "Uruguay", "U+1f1fa U+1f1fe", ":flag_uy:", "UY", "URY", "858"),
    Kittitian("Saint Kitts and Nevis", "Saint Kitts and Nevis", "U+1f1f0 U+1f1f3", ":flag_kn:", "KN", "KNA", "659"),
    Puerto_Rican("Puerto Rico", "Puerto Rico", "U+1f1f5 U+1f1f7", ":flag_pr:", "PR", "PRI", "630"),
    Réunionnais("Réunion", "La Réunion", "U+1f1f7 U+1f1ea", ":flag_re:", "RE", "REU", "638"),
    Martinican("Martinique", "Martinique", "U+1f1f2 U+1f1f6", ":flag_mq:", "MQ", "MTQ", "474"),
    Guianese("French Guiana", "Guyane française", "U+1f1ec U+1f1eb", ":flag_gf:", "GF", "GUF", "254"),
    Mayotte("Mayotte", "Mayotte", "U+1f1fe U+1f1f9", ":flag_yt:", "YT", "MYT", "175"),
    Saint_Martin("Saint Martin", "Saint-Martin", "U+1f1f2 U+1f1eb", ":flag_mf:", "MF", "MAF", "663"),
    Saint_Barthélemy("Saint Barthélemy", "Saint-Barthélemy", "U+1f1e7 U+1f1f1", ":flag_bl:", "BL", "BLM", "652"),
    Saint_Pierre_Miquelonais("Saint Pierre and Miquelon", "Saint-Pierre-et-Miquelon", "U+1f1f5 U+1f1f2", ":flag_pm:", "PM", "SPM", "666"),
    Wallisian("Wallis and Futuna", "Wallis-et-Futuna", "U+1f1fc U+1f1eb", ":flag_wf:", "WF", "WLF", "876"),
    New_Caledonian("New Caledonia", "Nouvelle-Calédonie", "U+1f1f3 U+1f1e8", ":flag_nc:", "NC", "NCL", "540"),
    French_Polynesian("French Polynesia", "Polynésie française", "U+1f1f5 U+1f1eb", ":flag_pf:", "PF", "PYF", "258"),
    Greenlandic("Greenland", "Kalaallit Nunaat", "U+1f1ec U+1f1f1", ":flag_gl:", "GL", "GRL", "304"),
    Faroese("Faroe Islands", "Føroyar", "U+1f1eb U+1f1f4", ":flag_fo:", "FO", "FRO", "234"),
    Ålandic("Åland Islands", "Åland", "U+1f1e6 U+1f1fd", ":flag_ax:", "AX", "ALA", "248"),
    Bermudian("Bermuda", "Bermuda", "U+1f1e7 U+1f1f2", ":flag_bm:", "BM", "BMU", "060"),
    Caymanian("Cayman Islands", "Cayman Islands", "U+1f1f0 U+1f1fe", ":flag_ky:", "KY", "CYM", "136"),
    Turks_Caicos_Islander("Turks and Caicos Islands", "Turks and Caicos Islands", "U+1f1f9 U+1f1e8", ":flag_tc:", "TC", "TCA", "796"),
    Virgin_Islander_British("British Virgin Islands", "British Virgin Islands", "U+1f1fb U+1f1ec", ":flag_vg:", "VG", "VGB", "092"),
    Virgin_Islander_US("U.S. Virgin Islands", "U.S. Virgin Islands", "U+1f1fb U+1f1ee", ":flag_vi:", "VI", "VIR", "850"),
    Anguillian("Anguilla", "Anguilla", "U+1f1e6 U+1f1ee", ":flag_ai:", "AI", "AIA", "660"),
    Montserratian("Montserrat", "Montserrat", "U+1f1f2 U+1f1f8", ":flag_ms:", "MS", "MSR", "500"),
    Falkland_Islander("Falkland Islands", "Falkland Islands", "U+1f1eb U+1f1f0", ":flag_fk:", "FK", "FLK", "238"),
    Gibraltar("Gibraltar", "Gibraltar", "U+1f1ec U+1f1ee", ":flag_gi:", "GI", "GIB", "292"),
    Manx("Isle of Man", "Ellan Vannin", "U+1f1ee U+1f1f2", ":flag_im:", "IM", "IMN", "833"),
    Jersey("Jersey", "Jersey", "U+1f1ef U+1f1ea", ":flag_je:", "JE", "JEY", "832"),
    Guernsey("Guernsey", "Guernsey", "U+1f1ec U+1f1ec", ":flag_gg:", "GG", "GGY", "831"),
    Aruba("Aruba", "Aruba", "U+1f1e6 U+1f1fc", ":flag_aw:", "AW", "ABW", "533"),
    Curaçaoan("Curaçao", "Curaçao", "U+1f1e8 U+1f1fc", ":flag_cw:", "CW", "CUW", "531"),
    Sint_Maarten("Sint Maarten", "Sint Maarten", "U+1f1f8 U+1f1fd", ":flag_sx:", "SX", "SXM", "534"),
    Caribbean_Netherlands("Caribbean Netherlands", "Caribisch Nederland", "U+1f1e7 U+1f1f6", ":flag_bq:", "BQ", "BES", "535"),
    Guamanian("Guam", "Guam", "U+1f1ec U+1f1fa", ":flag_gu:", "GU", "GUM", "316"),
    American_Samoan("American Samoa", "American Samoa", "U+1f1e6 U+1f1f8", ":flag_as:", "AS", "ASM", "016"),
    Northern_Mariana_Islander("Northern Mariana Islands", "Northern Mariana Islands", "U+1f1f2 U+1f1f5", ":flag_mp:", "MP", "MNP", "580"),
    Cook_Islander("Cook Islands", "Cook Islands", "U+1f1e8 U+1f1f0", ":flag_ck:", "CK", "COK", "184"),
    Niuean("Niue", "Niue", "U+1f1f3 U+1f1fa", ":flag_nu:", "NU", "NIU", "570"),
    Tokelauan("Tokelau", "Tokelau", "U+1f1f9 U+1f1f0", ":flag_tk:", "TK", "TKL", "772"),
    Pitcairn_Islander("Pitcairn Islands", "Pitcairn Islands", "U+1f1f5 U+1f1f3", ":flag_pn:", "PN", "PCN", "612"),
    Norfolk_Islander("Norfolk Island", "Norfolk Island", "U+1f1f3 U+1f1eb", ":flag_nf:", "NF", "NFK", "574"),
    Christmas_Islander("Christmas Island", "Christmas Island", "U+1f1e8 U+1f1fd", ":flag_cx:", "CX", "CXR", "162"),
    Cocos_Islander("Cocos (Keeling) Islands", "Cocos (Keeling) Islands", "U+1f1e8 U+1f1e8", ":flag_cc:", "CC", "CCK", "166"),
    Sahrawi("Western Sahara", "الصحراء الغربية", "U+1f1ea U+1f1ed", ":flag_eh:", "EH", "ESH", "732"),
    Antarctic("Antarctica", "Antarctica", "U+1f1e6 U+1f1f6", ":flag_aq:", "AQ", "ATA", "010"),
    Bouvet_Islander("Bouvet Island", "Bouvetøya", "U+1f1e7 U+1f1fb", ":flag_bv:", "BV", "BVT", "074"),
    Heard_McDonald_Islander("Heard Island and McDonald Islands", "Heard Island and McDonald Islands", "U+1f1ed U+1f1f2", ":flag_hm:", "HM", "HMD", "334"),
    South_Georgia_Sandwich_Islander("South Georgia and the South Sandwich Islands", "South Georgia and the South Sandwich Islands", "U+1f1ec U+1f1f8", ":flag_gs:", "GS", "SGS", "239"),

    International("United Nations", "International", "U+1f1fa U+1f1f3", ":flag_un:", "UN", "UNO", "000");


    private final String country;
    private final String nativeName;
    private final String flagUnicode;
    private final String discordFlag;
    private final String iso2Code;
    private final String iso3Code;
    private final String numericCode;

    Nationalities(String country, String nativeName, String flagUnicode, String discordFlag, String iso2Code, String iso3Code, String numericCode) {
        this.country = country;
        this.nativeName = nativeName;
        this.flagUnicode = flagUnicode;
        this.discordFlag = discordFlag;
        this.iso2Code = iso2Code;
        this.iso3Code = iso3Code;
        this.numericCode = numericCode;
    }

    public String getCountry() {
        return country;
    }

    public String getNativeName() {
        return nativeName;
    }

    public String getUnicode() {
        return flagUnicode;
    }

    public String getDiscordFlag() {
        return discordFlag;
    }

    public String getIso2Code() {
        return iso2Code;
    }

    public String getIso3Code() {
        return iso3Code;
    }

    public String getNumericCode() {
        return numericCode;
    }

    public String getCodepoints() {
        StringBuilder flagEmoji = new StringBuilder();
        for (String codePoint : flagUnicode.split(" ")) {
            if (codePoint.startsWith("U+")) codePoint = codePoint.substring(2);
            flagEmoji.append(new String(Character.toChars(Integer.parseInt(codePoint, 16))));
        }
        return flagEmoji.toString();
    }

    public static Nationalities get(String query) {
        Nationalities best = null;
        double bestScore = 40;
        for (Nationalities c : Nationalities.values()) {
            if (c.name().equalsIgnoreCase(query) || c.getCountry().equalsIgnoreCase(query) || c.getNativeName().equalsIgnoreCase(query)) return c;
            double score = Math.max(Math.max(similarity(c.name(), query, true), similarity(c.getCountry(), query, true)), similarity(c.getNativeName(), query, true));
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }
}
