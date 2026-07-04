package info.meuse24.pdf_scanner.domain.common

import java.net.NetworkInterface
import java.util.Collections

/**
 * Interface-agnostic view of a [NetworkInterface] so the selection logic below
 * is unit-testable without needing real (or mocked) platform network state.
 */
data class NetworkInterfaceInfo(
    val name: String,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val isVirtual: Boolean,
    val ipv4Addresses: List<String>
)

private val WIFI_LIKE_NAME_PREFIXES = listOf("wlan", "ap", "swlan")

// Excludes cellular data (rmnet/ccmni), VPN tunnels (tun/ppp), CLAT (464xlat) and
// loopback-style aliases – none of these are reachable from a PC on the same LAN.
private val EXCLUDED_NAME_PREFIXES = listOf("tun", "ppp", "rmnet", "ccmni", "clat", "v4-", "lo", "dummy")

/**
 * Picks the local IPv4 address a PC on the same network could use to reach this device.
 * Prefers a Wi-Fi/hotspot-style interface name; falls back to any other private IPv4
 * address so Wi-Fi Direct / tethering setups still work. Returns null if no such
 * interface is currently up (e.g. cellular-only connectivity, no network at all).
 */
fun selectLocalIPv4Address(interfaces: List<NetworkInterfaceInfo>): String? {
    val candidates = interfaces
        .asSequence()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .filter { iface -> EXCLUDED_NAME_PREFIXES.none { prefix -> iface.name.startsWith(prefix) } }
        .flatMap { iface -> iface.ipv4Addresses.asSequence().filter(::isPrivateIPv4).map { iface.name to it } }
        .toList()

    val wifiCandidate = candidates.firstOrNull { (name, _) ->
        WIFI_LIKE_NAME_PREFIXES.any { name.startsWith(it) }
    }
    return (wifiCandidate ?: candidates.firstOrNull())?.second
}

internal fun isPrivateIPv4(address: String): Boolean {
    val octets = address.split(".").mapNotNull { it.toIntOrNull() }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    val (a, b) = octets
    return when {
        a == 10 -> true
        a == 172 && b in 16..31 -> true
        a == 192 && b == 168 -> true
        else -> false
    }
}

/** Real platform adapter; the actual selection logic lives in [selectLocalIPv4Address] above. */
fun findLocalIPv4Address(): String? {
    val interfaces = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
    }.getOrElse { return null }

    val infos = interfaces.map { iface ->
        val ipv4Addresses = Collections.list(iface.inetAddresses)
            .mapNotNull { it.hostAddress }
            .filter { ':' !in it }
        NetworkInterfaceInfo(
            name = iface.name.orEmpty(),
            isUp = runCatching { iface.isUp }.getOrDefault(false),
            isLoopback = runCatching { iface.isLoopback }.getOrDefault(false),
            isVirtual = runCatching { iface.isVirtual }.getOrDefault(false),
            ipv4Addresses = ipv4Addresses
        )
    }
    return selectLocalIPv4Address(infos)
}
