"""The request loop shared by both roles: hello first, one reply per request, a bounded end.

The end comes with the end of input (also what a dead parent leaves), the session's close, a watchdog when the
parent stays silent for [idle_timeout], or a refused protocol version. Requests still running get a bounded time to
finish, then the handlers' shutdown runs its own bounded cleanup.
"""
import asyncio

from . import VERSION, library_version, protocol
from .errors import classify
from .lines import OVERLONG, UNREADABLE
from .protocol import PROTOCOL, HelperError, parse_request


class Server:
    def __init__(self, role, handlers, write, idle_timeout=None, max_pending=16, drain_timeout=6.0):
        self.role = role
        self.handlers = handlers
        self.write = write
        self.idle_timeout = idle_timeout
        self.max_pending = max_pending
        self.drain_timeout = drain_timeout
        self.pending = {}
        self.finished = asyncio.Event()

    async def run(self, read):
        self.write(protocol.hello(self.role, VERSION, library_version()))
        greeted = False
        reading = asyncio.ensure_future(read())
        ending = asyncio.ensure_future(self.finished.wait())
        try:
            while True:
                done, _ = await asyncio.wait({reading, ending}, timeout=self.idle_timeout,
                                             return_when=asyncio.FIRST_COMPLETED)
                if ending in done:
                    return await self._end('close', 0)
                if not done:
                    return await self._end('watchdog', 0)
                line = reading.result()
                if line is None:
                    return await self._end('eof', 0)
                reading = asyncio.ensure_future(read())
                request = self._parse(line)
                if request is None:
                    continue
                if request.op == 'hello':
                    if request.fields['protocol'] != PROTOCOL:
                        self._fail(request.id, f'This helper speaks protocol {PROTOCOL}.')
                        return await self._end('protocol', 2)
                    greeted = True
                    self.write(protocol.reply(request.id, {'protocol': PROTOCOL}))
                elif not greeted:
                    self._fail(request.id, 'Send hello first.')
                elif request.op == 'ping':
                    self.write(protocol.reply(request.id))
                else:
                    self._dispatch(request)
        finally:
            reading.cancel()
            ending.cancel()

    def _parse(self, line):
        try:
            if line is OVERLONG:
                raise HelperError('protocol', f'A request must fit in {protocol.MAX_LINE} bytes.')
            if line is UNREADABLE:
                raise HelperError('protocol', 'A request must be UTF-8.')
            return parse_request(line, self.role)
        except HelperError as error:
            self.write(protocol.failure(error.request_id, error))
            return None

    def _fail(self, request_id, detail):
        self.write(protocol.failure(request_id, HelperError('protocol', detail)))

    def _dispatch(self, request):
        if request.id in self.pending or self.handlers.holds(request.id):
            return self._fail(request.id, 'This id still waits for its reply.')
        if len(self.pending) >= self.max_pending:
            return self._fail(request.id, 'Too many requests wait for their replies.')
        work = self.handlers.handle(request)
        if work is not None:
            self.pending[request.id] = asyncio.ensure_future(self._answer(request, work))

    async def _answer(self, request, work):
        try:
            result = await work
        except asyncio.CancelledError:
            self.write(protocol.failure(request.id, HelperError('cancelled', 'The helper is ending.')))
            raise
        except Exception as error:
            self.write(protocol.failure(request.id, classify(error)))
        else:
            self.write(protocol.reply(request.id, result))
        finally:
            self.pending.pop(request.id, None)
            if self.handlers.finished:
                self.finished.set()

    async def _end(self, reason, code):
        tasks = list(self.pending.values())
        if tasks:
            _, late = await asyncio.wait(tasks, timeout=self.drain_timeout)
            for task in late:
                task.cancel()
            if late:
                await asyncio.wait(late, timeout=1)
        await self.handlers.shutdown(reason)
        return code


class SessionHandlers:
    def __init__(self, session):
        self.session = session

    @property
    def finished(self):
        return self.session.state == 'closed'

    def holds(self, request_id):
        return self.session.holds(request_id)

    def handle(self, request):
        session = self.session
        if request.op == 'set':
            session.offer(request)
            return None
        return {'open': session.open, 'return': session.return_to, 'clear': session.clear,
                'close': session.close}[request.op](request)

    async def shutdown(self, reason):
        await self.session.shutdown(reason)
