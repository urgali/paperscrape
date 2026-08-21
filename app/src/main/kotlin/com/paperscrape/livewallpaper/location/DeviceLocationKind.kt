package com.paperscrape.livewallpaper.location

import android.Manifest
import android.location.LocationManager

/**
 * Which of the device's own positioning systems a fix is allowed to come from.
 *
 * The distinction used to be invisible: one "Phone" setting asked [LocationManager] for the
 * network provider and quietly fell back to GPS if it was not enabled, so a user who chose the
 * cheap option could end up powering the GNSS receiver without ever being told. The two are now
 * separate choices with separate costs, and neither can turn into the other.
 */
enum class DeviceLocationKind(
    /** The single [LocationManager] provider this kind is allowed to use. Never a list. */
    val providerName: String,
    /** The permission this kind needs, and the strongest one it may ask for. */
    val permission: String,
) {

    /**
     * Cell towers and Wi-Fi, through [LocationManager.NETWORK_PROVIDER]. Coarse on purpose.
     *
     * Accurate to a neighbourhood rather than a doorstep, which is all a forecast needs -- weather
     * services resolve a request to a grid cell measured in kilometres, so a metre-accurate fix is
     * spent battery that buys the scene nothing. This kind must **never** fall back to
     * [LocationManager.GPS_PROVIDER]: a user who picks it has said no to the GNSS receiver, and
     * silently using it anyway because the network provider was unavailable would make the choice
     * a lie. When it cannot answer, the caller falls back to the last saved position instead.
     */
    NETWORK(LocationManager.NETWORK_PROVIDER, Manifest.permission.ACCESS_COARSE_LOCATION),

    /**
     * The GNSS receiver, through [LocationManager.GPS_PROVIDER]. Precise, and the expensive one.
     *
     * Offered because a user may want sunrise and sunset computed from exactly where they are, and
     * because it is the honest name for what it does.
     */
    GPS(LocationManager.GPS_PROVIDER, Manifest.permission.ACCESS_FINE_LOCATION),
    ;

    companion object {

        /** Reading a stored value, tolerant of anything that is not one of the two. */
        fun fromStorageId(id: String?): DeviceLocationKind =
            entries.firstOrNull { it.storageId == id } ?: NETWORK
    }

    /**
     * The string persisted in preferences.
     *
     * Lower-case names rather than [ordinal], so reordering the enum can never silently reinterpret
     * everybody's saved choice.
     */
    val storageId: String get() = name.lowercase()
}
