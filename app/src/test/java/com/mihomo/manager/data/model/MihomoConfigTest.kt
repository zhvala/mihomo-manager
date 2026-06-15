package com.mihomo.manager.data.model

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertTrue

class MihomoConfigTest {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
        ),
    )

    @Test
    fun `preserves vless reality fields when serializing config`() {
        val config = yaml.decodeFromString(
            MihomoConfig.serializer(),
            """
            port: 7890
            mixed-port: 7890
            proxies:
            - name: bandwagonhost
              type: vless
              server: 199.180.116.217
              port: 443
              uuid: bbea94a2-5f6a-4037-ae3a-6f7d1aca27b7
              network: tcp
              tls: true
              udp: true
              flow: xtls-rprx-vision
              servername: www.microsoft.com
              client-fingerprint: chrome
              reality-opts:
                public-key: IZFD4EKwZ1Vgk8rdNWtqOVLVIkUwLo8HE06gtXqwoVk
                short-id: 8c5b2e9d
            proxy-groups:
            - name: FINAL
              type: select
              proxies:
              - bandwagonhost
            rules:
            - MATCH,FINAL
            """.trimIndent(),
        )

        val serialized = yaml.encodeToString(MihomoConfig.serializer(), config)

        assertTrue(serialized.contains("tls: true"), serialized)
        assertTrue(serialized.contains("flow: \"xtls-rprx-vision\""), serialized)
        assertTrue(serialized.contains("servername: \"www.microsoft.com\""), serialized)
        assertTrue(serialized.contains("client-fingerprint: \"chrome\""), serialized)
        assertTrue(serialized.contains("reality-opts:"), serialized)
        assertTrue(serialized.contains("public-key: \"IZFD4EKwZ1Vgk8rdNWtqOVLVIkUwLo8HE06gtXqwoVk\""), serialized)
        assertTrue(serialized.contains("short-id: \"8c5b2e9d\""), serialized)
    }
}
