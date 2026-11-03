# APAC decoder

The decoder and C ABI bridge are copied unchanged from LuKa's
[mib2q-carplay-rgi](https://github.com/luka-dev/mib2q-carplay-rgi), commit
`df73e061a29c4d996550716987f42a9243daa09a`.

`sources.json` records every copied path and its upstream Git blob hash:

- `include/` and `src/` are the corresponding `apac_decoder/` directories.
- `bridge/` contains `hook/audio/apac_bridge.cpp` and `apac_bridge.h`.
- `LICENSE` and `NOTICE.md` are the project's original notices.

`gradle/apac.gradle.kts` builds these sources with the same pinned Zig used by
ChairPlay's other native libraries. The resulting Windows x64 library is packaged
as `apac/chairplay_apac.dll`. The app offers APAC only when the library loads and
the selected Windows output is available. Without it, Buffered AAC remains usable.

ChairPlay's changes are confined to the Windows build and the Kotlin consumer of
the existing ABI. No decoder source changes are applied. When updating the donor,
verify the copied files and ABI, update the manifest and notices, and compare
decoded PCM and reset behavior against the reference fixtures. Replace this copy
when DiPlay supplies an equivalent Windows decoder integration.

## Provenance and terms

The donor's GPL license covers its own work; its NOTICE does not relicense
third-party material. The donor describes this decoder as a port derived from
Apple's decoder in a Pioneer licensee binary, with tables extracted from that
binary. Its [APAC codec note](https://github.com/luka-dev/mib2q-carplay-rgi/blob/df73e061a29c4d996550716987f42a9243daa09a/docs/audio/APAC_CODEC.md)
states: "The port is derived work lifted from a licensee's binary: fine locally,
not publishable." This source provenance is not a grant of redistribution rights.
The same notice accompanies the application in `licenses/APAC-NOTICE.txt`.
