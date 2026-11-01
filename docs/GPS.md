# GPS satellite reports

On the GPS page, **Send satellites in view** (`gps.satellites`) enables predicted GPS satellite reports. After enabling it, reconnect so the iPhone can request GSV. The switch is off by default and requires GPS spoofing to be enabled. Turning it off stops GSV immediately; reconnect also removes the advertised capability.

When the iPhone requests GSV, ChairPlay calculates the visible GPS satellites at the virtual car's current latitude, longitude, and UTC time, and sends a complete `$GPGSV` cycle once a second. It uses healthy satellites at least 5 degrees above the horizon, with azimuth and elevation rounded to integer degrees. The observer height is zero, matching the current GGA altitude model. Calculations run inside the application; no Python process, external feeder, or experimental environment variable is needed.

The app downloads the [latest CelesTrak Yuma almanac](https://celestrak.org/GPS/almanac/Yuma/) over HTTPS in the background when satellite reports are needed, and stores `gps-almanac.yuma` next to the exe. This file survives deployment. A recent cache avoids downloading at startup; refreshes are attempted every six hours of use, with a 15-minute retry delay after failures. Coordinates are never sent to CelesTrak.

An offline session uses the cached constellation while its orbital epoch is within 14 days of the current time. This is a freshness policy, not a positioning accuracy guarantee. Missing, malformed, or expired data causes GSV to be omitted while position and wheel-speed updates continue. Downloads and cache loading do not block those updates. Refresh failures and cache use are recorded under `GPS almanac:` in the application log.

GSV reports a calculated satellite geometry, not measured reception. Signal levels are simulated at 45 dB-Hz; the almanac does not provide signal strength or account for buildings and interference. GPS–UTC is currently 18 seconds (verified 2026-10-26); this model would need updating after a future leap second. The existing GGA satellite count and DOP, and modern-dialect GSA used-satellite list, remain modeled separately. The phone receives GSV sentences, not orbital almanac records or a radio-frequency GPS signal.

These reports do not promise a larger accepted displacement. Range experiments on 2026-10-26 found no detectable difference between disabled GSV, a fixed local sky, and a sky following the transmitted position at the 4 km comparison resolution. Finer comparisons between modes remain unmeasured.
