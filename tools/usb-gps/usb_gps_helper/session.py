"""The one location simulation session of a session helper.

Points are latest-wins: at most one set is in flight, and while it is, only the newest offered point waits; an older
waiting one is answered {"applied": false}. After a slow set no burst follows, just that newest point. return, clear
and close go first: they drop the waiting point, wait (bounded) for the set in flight, then run. return and close end
the stream; later sets are refused with "cancelled", so nothing overwrites the return point.

[dirty] is set before a set goes out and cleared only by a clear the service took: a set whose reply was lost still
leaves cleanup needed. Each request must name the open session and its generation, so a late clear meant for an
older session is refused instead of reaching this one.
"""
import asyncio
from contextlib import AsyncExitStack, suppress

from . import protocol
from .errors import classify
from .protocol import HelperError
from .selection import select_device

OPEN_TIMEOUT = 25.0
TEARDOWN_TIMEOUT = 3.0
SHUTDOWN_BUDGET = 8.0
PRESENCE_MISSES = 3


class SimulationSession:
    def __init__(self, backend, write, rpc_timeout=5.0, presence_interval=1.0, open_timeout=OPEN_TIMEOUT,
                 shutdown_budget=SHUTDOWN_BUDGET):
        self.backend = backend
        self.write = write
        self.rpc_timeout = rpc_timeout
        self.presence_interval = presence_interval
        self.open_timeout = open_timeout
        self.shutdown_budget = shutdown_budget
        self.id = None
        self.generation = None
        self.udid = None
        self.state = 'idle'  # idle -> open -> lost | closed
        self.fenced = False
        self.dirty = False
        self.acknowledged = 0
        self._cleanup_result = 'not-needed'
        self._driver = None
        self._stack = None
        self._slot = None
        self._inflight = None
        self._inflight_id = None
        self._waiting = 0
        self._lock = asyncio.Lock()
        self._monitor = None

    async def open(self, request):
        if self.state != 'idle':
            raise HelperError('protocol', 'This helper already has its session.')
        fields = request.fields
        stack = AsyncExitStack()
        try:
            async with asyncio.timeout(self.open_timeout):
                select_device(await self.backend.usb_devices(), fields['udid'])
                self._driver = await stack.enter_async_context(self.backend.location(fields['udid']))
        except asyncio.CancelledError:
            await _close(stack)
            raise
        except Exception as error:
            await _close(stack)
            raise classify(error) from error
        self._stack = stack
        self.id, self.generation, self.udid = fields['session'], fields['generation'], fields['udid']
        self.state = 'open'
        if self.presence_interval:
            self._monitor = asyncio.ensure_future(self._watch())
        return {'session': self.id}

    def offer(self, request):
        """A set: answered later, when it went out, failed, or was replaced by a newer point."""
        try:
            self._check_owner(request)
            self._check_live()
            if self.fenced:
                raise HelperError('cancelled', 'The stream has ended in this session.')
        except HelperError as error:
            self.write(protocol.failure(request.id, error))
            return
        if self._inflight is not None or self._waiting:
            self._supersede()
            self._slot = request
        else:
            self._start(request)

    async def clear(self, request):
        self._check_owner(request)
        self._check_live()

        async def action():
            self._check_live()
            await self._rpc(lambda: self._driver.clear())
            self.dirty = False
            return {}
        return await self._first(False, action)

    async def close(self, request):
        self._check_owner(request)
        if self.state == 'closed':
            return {'cleanup': self._cleanup_result}

        async def action():
            self._cleanup_result = await self._cleanup()
            await self._teardown()
            return {'cleanup': self._cleanup_result}
        return await self._first(True, action)

    async def shutdown(self, reason):
        """End of input, a watchdog or a signal: a bounded cleanup attempt, then the session's end."""
        if self.state in ('open', 'lost'):
            self.fenced = True
            self._waiting += 1
            self._supersede()
            try:
                async with asyncio.timeout(self.shutdown_budget):
                    await self._drain()
                    self._cleanup_result = await self._cleanup()
            except TimeoutError:
                self._cleanup_result = 'unconfirmed' if self.dirty else 'not-needed'
            finally:
                self._waiting -= 1
            await self._teardown()
        self.write(protocol.event('bye', reason=reason, cleanup=self._cleanup_result))

    def _check_owner(self, request):
        fields = request.fields
        if self.id is None:
            raise HelperError('protocol', 'No session is open.')
        if fields['session'] != self.id or fields['generation'] != self.generation:
            raise HelperError('protocol', 'The request is for another session.')

    def _check_live(self):
        if self.state == 'lost':
            raise HelperError('link-lost', 'The iPhone is no longer connected.')
        if self.state != 'open':
            raise HelperError('cancelled', 'The session is closed.')

    def holds(self, request_id):
        """Whether a set with this id still waits for its reply."""
        return request_id in (self._inflight_id, self._slot.id if self._slot is not None else None)

    def _start(self, request):
        self._inflight_id = request.id
        self._inflight = asyncio.ensure_future(self._run_set(request))

    async def _run_set(self, request):
        fields = request.fields
        self.dirty = True
        try:
            await self._rpc(lambda: self._driver.set(fields['latitude'], fields['longitude']))
        except HelperError as error:
            self.write(protocol.failure(request.id, error))
        else:
            self.acknowledged += 1
            self.write(protocol.reply(request.id, {'applied': True, 'acknowledged': self.acknowledged}))
        finally:
            self._inflight = None
            self._inflight_id = None
            self._next()

    def _next(self):
        if self._slot is not None and not self._waiting and self.state == 'open' and not self.fenced:
            request, self._slot = self._slot, None
            self._start(request)

    def _supersede(self):
        if self._slot is not None:
            request, self._slot = self._slot, None
            self.write(protocol.reply(request.id, {'applied': False}))

    async def _first(self, fence, action):
        self._waiting += 1
        if fence:
            self.fenced = True
        self._supersede()
        try:
            async with self._lock:
                await self._drain()
                return await action()
        finally:
            self._waiting -= 1
            self._next()

    async def _drain(self):
        task = self._inflight
        if task is not None:
            await asyncio.wait({task}, timeout=self.rpc_timeout + 1)

    async def _rpc(self, call):
        try:
            await asyncio.wait_for(call(), self.rpc_timeout)
        except Exception as error:
            failure = classify(error)
            if failure.code == 'link-lost':
                self._lose('link-lost')
            raise failure from error

    async def _cleanup(self):
        if not self.dirty:
            return 'not-needed'
        if self.state != 'open':
            return 'unconfirmed'
        try:
            await self._rpc(lambda: self._driver.clear())
        except HelperError:
            return 'unconfirmed'
        self.dirty = False
        return 'done'

    def _lose(self, code):
        if self.state != 'open':
            return
        self.state = 'lost'
        if self._slot is not None:
            request, self._slot = self._slot, None
            self.write(protocol.failure(request.id, HelperError('link-lost', 'The iPhone is no longer connected.')))
        self.write(protocol.event('lost', session=self.id, generation=self.generation, code=code))

    async def _watch(self):
        misses = 0
        while self.state == 'open':
            await asyncio.sleep(self.presence_interval)
            try:
                present = await asyncio.wait_for(self.backend.present(self.udid), 2)
                misses = 0 if present else PRESENCE_MISSES
            except Exception:
                misses += 1
            if misses >= PRESENCE_MISSES:
                self._lose('link-lost')
                return

    async def _teardown(self):
        self.state = 'closed'
        self.fenced = True
        if self._monitor is not None:
            self._monitor.cancel()
            with suppress(BaseException):
                await self._monitor
            self._monitor = None
        if self._stack is not None:
            stack, self._stack = self._stack, None
            await _close(stack)


async def _close(stack):
    with suppress(Exception):
        await asyncio.wait_for(stack.aclose(), TEARDOWN_TIMEOUT)
