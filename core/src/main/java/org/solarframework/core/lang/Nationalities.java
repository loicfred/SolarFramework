package org.solarframework.core.lang;

import java.util.Arrays;

public enum Nationalities {

    English_US("United States", "United States", "U+1f1ec U+1f1e7"),
    English_UK("United Kingdom", "United Kingdom", "U+1f1fa U+1f1f8"),
    Dutch("Netherlands", "Magyar", "U+1f1f3 U+1f1f1"),
    French("France", "Français", "U+1f1eb U+1f1f7"),
    Italian("Italy", "Italiano", "U+1f1ee U+1f1f9"),
    Spanish("Spain", "Español", "U+1f1ea U+1f1f8"),
    Japanese("Japan", "日本語", "U+1f1ef U+1f1f5"),
    German("Germany", "Deutsch", "U+1f1e9 U+1f1ea"),
    Polish("Poland", "Polski", "U+1f1f5 U+1f1f1"),
    Hindi("India", "हिन्दी", "U+1f1ee U+1f1f3"),
    Swedish("Sweden", "Svenska", "U+1f1f8 U+1f1ea"),
    Korean("Korea", "한국어", "U+1f1f0 U+1f1f7"),
    Bulgarian("Bulgaria", "български", "U+1f1e7 U+1f1ec"),
    Russian("Russia", "Pусский", "U+1f1f7 U+1f1fa"),
    Greek("Greece", "Ελληνικά", "U+1f1ec U+1f1f7"),
    Turkish("Turkey", "Türkçe", "U+1f1f9 U+1f1f7"),
    Ukrainian("Ukraine", "Українська", "U+1f1fa U+1f1e6"),
    Hungarian("Hungary", "Magyar", "U+1f1ed U+1f1fa"),
    Finnish("Finland", "Suomi", "U+1f1eb U+1f1ee"),
    Romanian("Romania", "Română", "U+1f1f7 U+1f1f4"),
    Norwegian("Norway", "Norsk", "U+1f1f3 U+1f1f4"),
    Croatian("Croatia", "Hrvatski", "U+1f1ed U+1f1f7"),
    Danish("Denmark", "Dansk", "U+1f1e9 U+1f1f0"),
    Thai("Thai", "ไทย", "U+1f1f9 U+1f1ed"),
    Czech("Czechia", "Čeština", "U+1f1e8 U+1f1ff"),
    Vietnamese("Vietnam", "Tiếng Việt", "U+1f1fb U+1f1f3"),
    Lithuanian("Lithuania", "Lietuviškai", "U+1f1f1 U+1f1f9"),
    Chinese("China", "中文", "U+1f1e8 U+1f1f3"),
    Taiwanese("Taiwan","繁體中文", "U+1f1fc U+1f1f8"),
    Georgian("Georgia", "საქართველო", "U+1f1ec U+1f1ea"),
    Canadian("Canada", "Canada", "U+1f1e8 U+1f1e6"),
    Mauritian("Mauritius", "Maurice", "U+1f1f2 U+1f1fa"),
    Portuguese("Portugal", "Portugal", "U+1f1f5 U+1f1f9"),
    Brazilian("Brazil", "Brasil", "U+1f1e7 U+1f1f7"),
    Peruvian("Peru", "Perú", "U+1f1f5 U+1f1ea"),
    Colombian("Colombia", "Colombia", "U+1f1e8 U+1f1f4"),
    Venezuelan("Venezuela", "Venezuela", "U+1f1fb U+1f1ea"),
    Austrian("Austria", "Österreich", "U+1f1e6 U+1f1f9"),
    Belgian("Belgium", "Belgique", "U+1f1e7 U+1f1ea"),
    Indonesian("Indonesia", "Indonesia", "U+1f1ee U+1f1e9"),
    Hong_Konger("Hong Kong", "香港", "U+1f1ed U+1f1f0"),
    Moroccan("Morocco", "المغرب", "U+1f1f2 U+1f1e6"),
    Tunisian("Tunisia", "تونس", "U+1f1f9 U+1f1f3"),
    Nigerian("Nigeria", "Nigeria", "U+1f1f3 U+1f1ec"),
    Guadeloupean("Guadeloupe", "Guadeloupe", "U+1f1ec U+1f1f5"),
    Luxembourger("Luxembourg", "Lëtzebuerg", "U+1f1f1 U+1f1fa"),
    Chilean("Chile", "Chile", "U+1f1e8 U+1f1f1"),
    Macedonian("Macedonia", "Македонија", "U+1f1f2 U+1f1f0"),
    Irish("Ireland", "Éire", "U+1f1ee U+1f1ea"),
    Swiss("Switzerland", "Schweiz", "U+1f1e8 U+1f1ed"),
    Sammarinese("San Marino", "San Marino", "U+1f1f8 U+1f1f2"),
    Mexican("Mexico", "México", "U+1f1f2 U+1f1fd"),
    Albanian("Albania", "Shqipëria", "U+1f1e6 U+1f1f1"),
    Andorran("Andorra", "Andorra", "U+1f1e6 U+1f1e9"),
    Armenian("Armenia", "Հայաստան", "U+1f1e6 U+1f1f2"),
    Azerbaijani("Azerbaijan", "Azərbaycan", "U+1f1e6 U+1f1ff"),
    Belarusian("Belarus", "Беларусь", "U+1f1e7 U+1f1fe"),
    Bosnian("Bosnia and Herzegovina", "Bosna i Hercegovina", "U+1f1e7 U+1f1e6"),
    Cypriot("Cyprus", "Κύπρος", "U+1f1e8 U+1f1fe"),
    Estonian("Estonia", "Eesti", "U+1f1ea U+1f1ea"),
    Icelander("Iceland", "Ísland", "U+1f1ee U+1f1f8"),
    Kazakhstani("Kazakhstan", "Қазақстан", "U+1f1f0 U+1f1ff"),
    Latvian("Latvia", "Latvija", "U+1f1f1 U+1f1fb"),
    Liechtensteiner("Liechtenstein", "Liechtenstein", "U+1f1f1 U+1f1ee"),
    Maltese("Malta", "Malta", "U+1f1f2 U+1f1f9"),
    Moldovan("Moldova", "Moldova", "U+1f1f2 U+1f1e9"),
    Monacan("Monaco", "Monaco", "U+1f1f2 U+1f1e8"),
    Montenegrin("Montenegro", "Црна Гора", "U+1f1f2 U+1f1ea"),
    Serbian("Serbia", "Србија", "U+1f1f7 U+1f1f8"),
    Slovak("Slovakia", "Slovensko", "U+1f1f8 U+1f1f0"),
    Slovene("Slovenia", "Slovenija", "U+1f1f8 U+1f1ee"),
    Vatican("Vatican City", "Vaticano", "U+1f1fb U+1f1e6"),
    Lebanese("Lebanon", "Lubnan", "U+1f1f1 U+1f1e7"),
    Australian("Australia", "Australia", "U+1f1e6 U+1f1fa"),
    Argentinian("Argentina", "Argentina", "U+1f1e6 U+1f1f7"),
    Egyptian("Egypt", "مصر‎", "U+1f1ea U+1f1ec"),
    South_African("South Africa", "South Africa", "U+1f1ff U+1f1e6"),
    Saudi_Arabian("Saudi Arabia", "المملكة العربية السعودية", "U+1f1f8 U+1f1e6"),
    New_Zealander("New Zealand", "New Zealand", "U+1f1f3 U+1f1ff"),
    Singaporean("Singapore", "Singapore", "U+1f1f8 U+1f1ec"),
    Malaysian("Malaysia", "Malaysia", "U+1f1f2 U+1f1fe"),
    Filipino("Philippines", "Pilipinas", "U+1f1f5 U+1f1ed"),
    Pakistani("Pakistan", "پاکستان", "U+1f1f5 U+1f1f0"),
    Bangladeshi("Bangladesh", "বাংলাদেশ", "U+1f1e7 U+1f1e9"),
    Sri_Lankan("Sri Lanka", "ශ්‍රී ලංකාව", "U+1f1f1 U+1f1f0"),
    Nepali("Nepal", "नेपाल", "U+1f1f3 U+1f1f5"),
    Afghan("Afghanistan", "افغانستان", "U+1f1e6 U+1f1eb"),
    Iraqi("Iraq", "العراق", "U+1f1ee U+1f1f6"),
    Iranian("Iran", "ایران", "U+1f1ee U+1f1f7"),
    Syrian("Syria", "سوريا", "U+1f1f8 U+1f1fe"),
    Jordanian("Jordan", "الأردن", "U+1f1ef U+1f1f4"),
    Kuwaiti("Kuwait", "الكويت", "U+1f1f0 U+1f1fc"),
    Omani("Oman", "عمان", "U+1f1f4 U+1f1f2"),
    Emirati("United Arab Emirates", "الإمارات العربية المتحدة", "U+1f1e6 U+1f1ea"),
    Qatari("Qatar", "قطر", "U+1f1f6 U+1f1e6"),
    Bahraini("Bahrain", "البحرين", "U+1f1e7 U+1f1ed"),
    Yemeni("Yemen", "اليمن", "U+1f1fe U+1f1ea"),
    Palestinian("Palestine", "فلسطين", "U+1f1f5 U+1f1f8"),
    Algerian("Algeria", "الجزائر", "U+1f1e9 U+1f1ff"),
    Libyan("Libya", "ليبيا", "U+1f1f1 U+1f1fe"),
    Kenyan("Kenya", "Kenya", "U+1f1f0 U+1f1ea"),
    Ugandan("Uganda", "Uganda", "U+1f1fa U+1f1ec"),
    Tanzanian("Tanzania", "Tanzania", "U+1f1f9 U+1f1ff"),
    Ethiopian("Ethiopia", "ኢትዮጵያ", "U+1f1ea U+1f1f9"),
    Somalian("Somalia", "Soomaaliya", "U+1f1f8 U+1f1f4"),
    Sudanese("Sudan", "السودان", "U+1f1f8 U+1f1e9"),
    Ghanaian("Ghana", "Ghana", "U+1f1ec U+1f1ed"),
    Ivorian("Ivory Coast", "Côte d'Ivoire", "U+1f1e8 U+1f1ee"),
    Senegalese("Senegal", "Sénégal", "U+1f1f8 U+1f1f3"),
    Zimbabwean("Zimbabwe", "Zimbabwe", "U+1f1ff U+1f1fc"),
    Zambian("Zambia", "Zambia", "U+1f1ff U+1f1f2"),
    Malawian("Malawi", "Malawi", "U+1f1f2 U+1f1fc"),
    Angolan("Angola", "Angola", "U+1f1e6 U+1f1f4"),
    Cameroonian("Cameroon", "Cameroun", "U+1f1e8 U+1f1f2"),
    Congolese("Congo", "Congo", "U+1f1e8 U+1f1ec"),
    Gabonese("Gabon", "Gabon", "U+1f1ec U+1f1e6"),
    Burundian("Burundi", "Burundi", "U+1f1e7 U+1f1ee"),
    Mozambican("Mozambique", "Moçambique", "U+1f1f2 U+1f1ff"),
    Botswanan("Botswana", "Botswana", "U+1f1e7 U+1f1fc"),
    Namibian("Namibia", "Namibia", "U+1f1f3 U+1f1e6"),
    Madagascan("Madagascar", "Madagasikara", "U+1f1f2 U+1f1ec"),
    Rwandan("Rwanda", "Rwanda", "U+1f1f7 U+1f1fc"),
    Beninese("Benin", "Bénin", "U+1f1e7 U+1f1ef"),
    Togolese("Togo", "Togo", "U+1f1f9 U+1f1ec"),
    Seychellois("Seychelles", "Seychelles", "U+1f1f8 U+1f1e8"),
    Malian("Mali", "Mali", "U+1f1f2 U+1f1f1"),
    Nigerien("Niger", "Niger", "U+1f1f3 U+1f1ea"),
    Chadian("Chad", "Tchad", "U+1f1f9 U+1f1e9"),
    Central_African("Central African Republic", "République centrafricaine", "U+1f1e8 U+1f1eb"),
    Eritrean("Eritrea", "ኤርትራ", "U+1f1ea U+1f1f7"),
    Mauritanian("Mauritania", "موريتانيا", "U+1f1f2 U+1f1f7"),
    Sierra_Leonean("Sierra Leone", "Sierra Leone", "U+1f1f8 U+1f1f1"),
    Gambian("Gambia", "Gambia", "U+1f1ec U+1f1f2"),
    Burkinabe("Burkina Faso", "Burkina Faso", "U+1f1e7 U+1f1eb"),
    Guinean("Guinea", "Guinée", "U+1f1ec U+1f1f3"),
    Guinean_Bissauan("Guinea-Bissau", "Guiné-Bissau", "U+1f1ec U+1f1fc"),
    Equatorial_Guinean("Equatorial Guinea", "Guinea Ecuatorial", "U+1f1ec U+1f1f6"),
    Cape_Verdean("Cape Verde", "Cabo Verde", "U+1f1e8 U+1f1fb"),
    Lesothan("Lesotho", "Lesotho", "U+1f1f1 U+1f1f8"),
    Liberian("Liberia", "Liberia", "U+1f1f1 U+1f1f7"),
    Maldivian("Maldives", "ދިވެހިރާއްޖޭގެ", "U+1f1f2 U+1f1fb"),
    Bhutanese("Bhutan", "འབྲུག་ཡུལ", "U+1f1e7 U+1f1f9"),
    Mongolian("Mongolia", "Монгол", "U+1f1f2 U+1f1f3"),
    Uzbekistani("Uzbekistan", "O'zbekiston", "U+1f1fa U+1f1ff"),
    Turkmenistani("Turkmenistan", "Türkmenistan", "U+1f1f9 U+1f1f2"),
    Kyrgyzstani("Kyrgyzstan", "Кыргызстан", "U+1f1f0 U+1f1ec"),
    Tajikistani("Tajikistan", "Тоҷикистон", "U+1f1f9 U+1f1ef"),
    Israeli("Israel", "יִשְׂרָאֵל", "U+1f1ee U+1f1f1"),
    Nepalese("Nepal", "नेपाल", "U+1f1f3 U+1f1f5"),
    Afghani("Afghanistan", "افغانستان", "U+1f1e6 U+1f1eb"),
    Bruneian("Brunei", "Negara Brunei Darussalam", "U+1f1e7 U+1f1f3"),
    Laotian("Laos", "ສ.ປ.ປ ລາວ", "U+1f1f1 U+1f1e6"),
    Cambodian("Cambodia", "កម្ពុជា", "U+1f1f0 U+1f1ed"),
    Burmese("Myanmar", "မြန်မာ", "U+1f1f2 U+1f1f2"),
    East_Timorese("East Timor", "Timor-Leste", "U+1f1f9 U+1f1f1"),
    South_Korean("South Korea", "대한민국", "U+1f1f0 U+1f1f7"),
    North_Korean("North Korea", "조선민주주의인민공화국", "U+1f1f0 U+1f1f5"),
    Macanese("Macau", "澳門", "U+1f1f2 U+1f1f4"),
    Tibetan("Tibet", "བོད་", "U+1f1e8 U+1f1f3"),
    Malagasy("Madagascar", "Madagasikara", "U+1f1f2 U+1f1ec"),
    South_Sudanese("South Sudan", "South Sudan", "U+1f1f8 U+1f1f8"),
    Djiboutian("Djibouti", "Djibouti", "U+1f1e9 U+1f1ef"),
    Somali("Somalia", "Soomaaliya", "U+1f1f8 U+1f1f4"),
    Congolese_DR("Democratic Republic of the Congo", "République démocratique du Congo", "U+1f1e8 U+1f1e9"),
    Congolese_Brazzaville("Republic of the Congo", "République du Congo", "U+1f1e8 U+1f1ec"),
    Equatoguinean("Equatorial Guinea", "Guinea Ecuatorial", "U+1f1ec U+1f1f6"),

    International("United Nations", "International", "U+1f1fa U+1f1f3");


    private final String country;
    private final String nativeName;
    private final String flagUnicode;

    Nationalities(String country, String nativeName, String flagUnicode) {
        this.country = country;
        this.nativeName = nativeName;
        this.flagUnicode = flagUnicode;
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
    public String getCodepoints() {
        StringBuilder flagEmoji = new StringBuilder();
        for (String codePoint : flagUnicode.split(" ")) {
            if (codePoint.startsWith("U+")) codePoint = codePoint.substring(2);
            flagEmoji.append(new String(Character.toChars(Integer.parseInt(codePoint, 16))));
        }
        return flagEmoji.toString();
    }

    public static Nationalities get(String nationality) {
        return Arrays.stream(values()).filter(N -> N.toString().equalsIgnoreCase(nationality) || N.country.equalsIgnoreCase(nationality) || N.nativeName.equalsIgnoreCase(nationality)).findFirst().orElse(null);
    }
}
