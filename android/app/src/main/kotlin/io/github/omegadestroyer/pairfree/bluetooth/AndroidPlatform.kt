package io.github.omegadestroyer.pairfree.bluetooth

import android.os.Build
import io.github.omegadestroyer.pairfree.brands.PlatformInfo

/** This phone, as the brands and the reports are told about it. */
fun androidPlatform(): PlatformInfo = PlatformInfo(
    os = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    osAndDevice = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, ${Build.MANUFACTURER}, build ${Build.ID})",
    madeBySamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
    androidSdk = Build.VERSION.SDK_INT,
    hostLabel = "Phone",
    host = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.ID}",
)
