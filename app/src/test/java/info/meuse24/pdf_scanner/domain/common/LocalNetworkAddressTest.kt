package info.meuse24.pdf_scanner.domain.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalNetworkAddressTest {

    @Test
    fun `picks wifi interface when present among several`() {
        val interfaces = listOf(
            NetworkInterfaceInfo("lo", isUp = true, isLoopback = true, isVirtual = false, ipv4Addresses = listOf("127.0.0.1")),
            NetworkInterfaceInfo("rmnet0", isUp = true, isLoopback = false, isVirtual = false, ipv4Addresses = listOf("10.20.30.40")),
            NetworkInterfaceInfo("wlan0", isUp = true, isLoopback = false, isVirtual = false, ipv4Addresses = listOf("192.168.178.42"))
        )

        assertEquals("192.168.178.42", selectLocalIPv4Address(interfaces))
    }

    @Test
    fun `falls back to other private interface when no wifi-like name present`() {
        val interfaces = listOf(
            NetworkInterfaceInfo("lo", isUp = true, isLoopback = true, isVirtual = false, ipv4Addresses = listOf("127.0.0.1")),
            NetworkInterfaceInfo("eth0", isUp = true, isLoopback = false, isVirtual = false, ipv4Addresses = listOf("192.168.1.5"))
        )

        assertEquals("192.168.1.5", selectLocalIPv4Address(interfaces))
    }

    @Test
    fun `ignores vpn tunnel and cellular interfaces`() {
        val interfaces = listOf(
            NetworkInterfaceInfo("tun0", isUp = true, isLoopback = false, isVirtual = false, ipv4Addresses = listOf("10.8.0.2")),
            NetworkInterfaceInfo("rmnet_data0", isUp = true, isLoopback = false, isVirtual = false, ipv4Addresses = listOf("10.90.1.2")),
            NetworkInterfaceInfo("wlan0", isUp = true, isLoopback = false, isVirtual = false, ipv4Addresses = listOf("192.168.0.10"))
        )

        assertEquals("192.168.0.10", selectLocalIPv4Address(interfaces))
    }

    @Test
    fun `returns null when only cellular is up (no local network)`() {
        val interfaces = listOf(
            NetworkInterfaceInfo("lo", isUp = true, isLoopback = true, isVirtual = false, ipv4Addresses = listOf("127.0.0.1")),
            NetworkInterfaceInfo("rmnet0", isUp = true, isLoopback = false, isVirtual = false, ipv4Addresses = listOf("10.90.1.2"))
        )

        assertNull(selectLocalIPv4Address(interfaces))
    }

    @Test
    fun `returns null when no interface is up`() {
        val interfaces = listOf(
            NetworkInterfaceInfo("wlan0", isUp = false, isLoopback = false, isVirtual = false, ipv4Addresses = listOf("192.168.0.10"))
        )

        assertNull(selectLocalIPv4Address(interfaces))
    }

    @Test
    fun `ignores public (non-RFC1918) addresses`() {
        val interfaces = listOf(
            NetworkInterfaceInfo("wlan0", isUp = true, isLoopback = false, isVirtual = false, ipv4Addresses = listOf("8.8.8.8"))
        )

        assertNull(selectLocalIPv4Address(interfaces))
    }

    @Test
    fun `isPrivateIPv4 covers all RFC1918 ranges and rejects others`() {
        assertEquals(true, isPrivateIPv4("10.0.0.1"))
        assertEquals(true, isPrivateIPv4("172.16.0.1"))
        assertEquals(true, isPrivateIPv4("172.31.255.255"))
        assertEquals(true, isPrivateIPv4("192.168.0.1"))
        assertEquals(false, isPrivateIPv4("172.32.0.1"))
        assertEquals(false, isPrivateIPv4("172.15.0.1"))
        assertEquals(false, isPrivateIPv4("11.0.0.1"))
        assertEquals(false, isPrivateIPv4("not-an-ip"))
    }
}
