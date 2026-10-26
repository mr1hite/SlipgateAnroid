package com.slipgate.android

data class DpiStrategy(
    val id: String,
    val name: String,
    val description: String,
    val rawArgs: String
) {
    val argsList: List<String>
        get() = rawArgs.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
}

object Strategies {
    val presets = listOf(
        DpiStrategy(
            id = "sg_1",
            name = "SG-1: Adaptive Auto-TTL + UDP",
            description = "Адаптивная: не зависает на ближних CDN YouTube и пробивает голос в Discord.",
            rawArgs = "--fake -1 --ttl 8 --auto=ssl_err --fake -1 --ttl 5 --udp-fake 1"
        ),
        DpiStrategy(
            id = "sg_2",
            name = "SG-2: Rocket Multi-Cascade",
            description = "Скоростная каскадная нарезка SNI + MD5Sig для домашних провайдеров.",
            rawArgs = "-d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -r1+s -S -a1 -As"
        ),
        DpiStrategy(
            id = "sg_3",
            name = "SG-3: Combo OOB + Fake 2026",
            description = "Мультитриггерная стратегия (Redirect + Timeout + SSL).",
            rawArgs = "-s1 -o1 -Ar -o1 -At -f-1 -r1+s -As"
        ),
        DpiStrategy(
            id = "sg_4",
            name = "SG-4: Mobile OOB + Split + UDP",
            description = "Оптимизирована для мобильных сетей (МТС, Мегафон, Т2, Билайн).",
            rawArgs = "--split 1+s --disorder 3+s --oob 1+s --udp-fake 1"
        ),
        DpiStrategy(
            id = "sg_5",
            name = "SG-5: Light Disorder + TLSRec",
            description = "Лёгкая без фейков: минимальный пинг, не конфликтует с обычными сайтами.",
            rawArgs = "--disorder 1 --tlsrec 1+s --udp-fake 1"
        ),
        DpiStrategy(
            id = "sg_6",
            name = "SG-6: Deep Buffer Desync",
            description = "Тяжёлая артиллерия против жёстких региональных ТСПУ.",
            rawArgs = "-s1 -q1 -Y -Ar -s5 -o25000+s -At -f-1 -r1+s -As -s1 -o1+s -s-1 -An -b+500"
        ),
        DpiStrategy(
            id = "sg_7",
            name = "SG-7: SNI Disorder Cascade",
            description = "Фрагментация длинных заголовков TLS 1.3 для YouTube и Cloudflare.",
            rawArgs = "-d1 -s25+s -d30+s -r1+s"
        ),
        DpiStrategy(
            id = "sg_8",
            name = "SG-8: Fake SNI (translate.google.ru)",
            description = "Маскировка трафика под разрешённый домен переводчика Google.",
            rawArgs = "--disoob 2+sni --fake 517 --ttl 5 --tls-sni=translate.google.ru"
        )
    )

    val testTargets = listOf(
        "YouTube Web" to "https://www.youtube.com/generate_204",
        "YouTube Video (GGC)" to "https://rr1---sn-gvnuxaxjvh-n8me.googlevideo.com",
        "Discord API" to "https://discord.com/api/v9/experiments",
        "Discord CDN" to "https://cdn.discordapp.com",
        "Cloudflare" to "https://cp.cloudflare.com/generate_204"
    )
}
