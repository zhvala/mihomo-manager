package com.mihomo.manager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MihomoConfig(
    val port: Int = 7890,
    @SerialName("mixed-port") val mixedPort: Int = 7890,
    @SerialName("allow-lan") val allowLan: Boolean = false,
    @SerialName("bind-address") val bindAddress: String = "127.0.0.1",
    val mode: String = "rule",
    @SerialName("log-level") val logLevel: String = "info",
    val ipv6: Boolean = false,
    @SerialName("external-controller") val externalController: String? = null,
    val tun: TunConfig? = null,
    val dns: DnsConfig? = null,
    val proxies: List<Proxy> = emptyList(),
    @SerialName("proxy-groups") val proxyGroups: List<ProxyGroup> = emptyList(),
    val rules: List<String> = emptyList(),
)

@Serializable
data class Proxy(
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
    val password: String? = null,
    val cipher: String? = null,
    val sni: String? = null,
    @SerialName("skip-cert-verify") val skipCertVerify: Boolean? = null,
    val udp: Boolean? = null,
    val uuid: String? = null,
    val alterId: Int? = null,
    val network: String? = null,
    val tls: Boolean? = null,
    val flow: String? = null,
    val servername: String? = null,
    @SerialName("client-fingerprint") val clientFingerprint: String? = null,
    @SerialName("reality-opts") val realityOpts: RealityOptions? = null,
)

@Serializable
data class RealityOptions(
    @SerialName("public-key") val publicKey: String? = null,
    @SerialName("short-id") val shortId: String? = null,
)

@Serializable
data class ProxyGroup(
    val name: String,
    val type: String,
    val proxies: List<String> = emptyList(),
    val url: String? = null,
    val interval: Int? = null,
    val tolerance: Int? = null,
)

@Serializable
data class TunConfig(
    val enable: Boolean = false,
    val stack: String = "system",
    @SerialName("auto-route") val autoRoute: Boolean = true,
    @SerialName("auto-detect-interface") val autoDetectInterface: Boolean = true,
)

@Serializable
data class DnsConfig(
    val enable: Boolean = true,
    val listen: String = "127.0.0.1:53",
    @SerialName("enhanced-mode") val enhancedMode: String = "redir-host",
    @SerialName("fake-ip-range") val fakeIpRange: String? = null,
    val ipv6: Boolean = false,
    @SerialName("respect-rules") val respectRules: Boolean = false,
    val nameserver: List<String> = emptyList(),
)

enum class RuleType(val prefix: String) {
    DOMAIN("DOMAIN"),
    DOMAIN_SUFFIX("DOMAIN-SUFFIX"),
    DOMAIN_KEYWORD("DOMAIN-KEYWORD"),
    GEOIP("GEOIP"),
    GEOSITE("GEOSITE"),
    IP_CIDR("IP-CIDR"),
    IP_CIDR6("IP-CIDR6"),
    PROCESS_NAME("PROCESS-NAME"),
    UID("UID"),
    MATCH("MATCH"),
}

data class ParsedRule(
    val type: RuleType,
    val value: String,
    val target: String,
    val options: List<String> = emptyList(),
) {
    fun toRuleString(): String {
        val parts = mutableListOf(type.prefix)
        if (type != RuleType.MATCH) parts.add(value)
        parts.add(target)
        parts.addAll(options)
        return parts.joinToString(",")
    }

    companion object {
        fun fromString(rule: String): ParsedRule? {
            val parts = rule.split(",").map { it.trim() }
            if (parts.isEmpty()) return null
            val ruleType = RuleType.entries.find { it.prefix == parts[0] } ?: return null
            return if (ruleType == RuleType.MATCH) {
                if (parts.size < 2) return null
                ParsedRule(ruleType, "", parts[1])
            } else {
                if (parts.size < 3) return null
                ParsedRule(ruleType, parts[1], parts[2], parts.drop(3))
            }
        }
    }
}
