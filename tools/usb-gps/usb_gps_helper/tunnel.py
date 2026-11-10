"""The developer services (RSD) through a userspace tunnel over an already open USB lockdown.

pymobiledevice3's convenient tunnel helper picks a device again, with no connection type: a phone that lost its cable
could then be reached over Wi-Fi. Building the tunnel from our own USB lockdown keeps it on that cable and that UDID.
The tunnel lives in this process (no kernel interface, no admin rights, no route changes); the library's switch for
that is set for the whole process, which only ever wants the userspace tunnel.
"""
from contextlib import AsyncExitStack, asynccontextmanager

from .protocol import HelperError


@asynccontextmanager
async def developer_services(deps, lockdown):
    deps.tunnel.USE_USERSPACE_TUNNEL = True
    async with AsyncExitStack() as stack:
        provider = await deps.tunnel.CoreDeviceTunnelProxy.create(lockdown)
        stack.push_async_callback(provider.close)
        result = await stack.enter_async_context(provider.start_tcp_tunnel())
        tun = result.client.tun
        if not isinstance(tun, deps.tun_type):
            raise HelperError('helper-failed', 'The developer tunnel did not stay inside the helper.')
        tun.set_peer(result.address)
        dial = await stack.enter_async_context(deps.dial_plane(tun, result.address))
        rsd = deps.rsd((result.address, result.port), open_connection=dial.dial,
                       auxiliary_metadata=result.auxiliary_metadata)
        stack.push_async_callback(rsd.close)
        await rsd.connect()
        yield rsd
