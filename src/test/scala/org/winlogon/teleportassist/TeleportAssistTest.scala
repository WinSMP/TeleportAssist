package org.winlogon.teleportassist

import org.winlogon.teleportassist.TeleportAssist
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class TeleportAssistTest {

    private var server: ServerMock = _
    private var plugin: TeleportAssist = _

    @BeforeEach
    def setUp(): Unit = {
        server = MockBukkit.mock()
        plugin = MockBukkit.load(classOf[TeleportAssist])
    }

    @AfterEach
    def tearDown(): Unit = {
        MockBukkit.unmock()
    }

    @Test
    def testPluginEnables(): Unit = {
        assertTrue(plugin.isEnabled)
    }
}
