package io.github.numberonebot.chairplay.platform.windows

import io.github.numberonebot.chairplay.platform.Bluetooth
import io.github.numberonebot.chairplay.platform.DesktopShell
import io.github.numberonebot.chairplay.platform.Firewall
import io.github.numberonebot.chairplay.platform.HardwareVideo
import io.github.numberonebot.chairplay.platform.KeepAliveTuning
import io.github.numberonebot.chairplay.platform.LocationSource
import io.github.numberonebot.chairplay.platform.OneCopyClaim
import io.github.numberonebot.chairplay.platform.PcNetwork
import io.github.numberonebot.chairplay.platform.Platform
import io.github.numberonebot.chairplay.platform.SystemAudio
import io.github.numberonebot.chairplay.platform.WindowHooks
import io.github.numberonebot.chairplay.ui.WebViewBackend
import io.github.numberonebot.chairplay.update.UpdateResult
import java.nio.file.Path
import org.bytedeco.ffmpeg.global.avutil.AV_HWDEVICE_TYPE_D3D11VA
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_D3D11

/** Windows, through today's adapters; each is made the first time it is asked for, so none loads its DLLs before. */
internal class WindowsPlatform : Platform {
    override val shell: DesktopShell by lazy { WindowsShell() }
    override val oneCopy: OneCopyClaim by lazy { OneCopyClaim { name, timeoutMillis -> NamedMutex.claim(name, timeoutMillis) } }
    override val keepAlive: KeepAliveTuning by lazy { WinsockKeepAlive() }
    override val location: LocationSource by lazy { WindowsLocation }
    override val firewall: Firewall by lazy { WindowsFirewall() }
    override val network: PcNetwork by lazy { WindowsNetwork() }
    override val bluetooth: Bluetooth by lazy { WindowsBluetooth }
    override val audio: SystemAudio by lazy { WindowsAudio() }
    override val video: HardwareVideo? by lazy {
        HardwareVideo(AV_HWDEVICE_TYPE_D3D11VA, AV_PIX_FMT_D3D11, "D3D11", "D3D11VA")
    }
    override val windows: WindowHooks by lazy { WindowsWindowHooks() }

    override fun webView(userData: Path, debug: Boolean): WebViewBackend = WindowsWebView(userData, debug)

    override fun updateDialog(result: UpdateResult): Boolean = WindowsUpdateDialog.show(result)
}
