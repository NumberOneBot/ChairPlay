"""The helper's JSON-lines protocol, version 2: one JSON object per line on stdin and stdout, UTF-8.

The helper starts with  {"type":"hello","protocol":2,"role":...}  and refuses every request until the client's own
{"id":..,"op":"hello","protocol":2}  succeeds. Each request has a client-chosen "id" and gets exactly one reply:
    {"type":"reply","id":..,"ok":true,"result":{..}}
    {"type":"reply","id":..,"ok":false,"error":{"code":<stable code>,"detail":<short English, for logs>}}
Events carry no reply: {"type":"event","event":"progress"|"lost"|"bye", ..}; a progress event names its request's id.

Unknown, duplicate or missing fields, an unknown operation, a line over MAX_LINE bytes and anything that is not one
JSON object are "protocol" errors; a coordinate that is not a finite number in range is "invalid-coordinates".
Both are refused before anything reaches the phone. stdout carries nothing else; diagnostics go to stderr.
"""
import json
import math
import re
from dataclasses import dataclass, field

PROTOCOL = 2
MAX_LINE = 4096

# The app's UsbErrorCode keys (gps/UsbHelper.kt); the page words them, the helper never sends text for users.
CODES = frozenset((
    'no-device', 'several-devices', 'drivers-missing', 'usbmux-unavailable', 'locked', 'not-trusted',
    'developer-mode-off', 'image-missing', 'service-unavailable', 'unsupported-ios', 'download-failed', 'disk-full',
    'verification-failed', 'mount-failed', 'store-unavailable', 'timeout', 'cancelled', 'link-lost',
    'invalid-coordinates', 'helper-failed', 'protocol',
))


class HelperError(Exception):
    """A failure with one of [CODES]; [detail] is a short English sentence for logs, never shown as is."""

    def __init__(self, code, detail, request_id=None):
        if code not in CODES:
            raise ValueError(f'unknown error code {code!r}')
        super().__init__(detail)
        self.code = code
        self.detail = detail
        self.request_id = request_id


@dataclass(frozen=True)
class Request:
    id: str
    op: str
    fields: dict = field(default_factory=dict)


_NAME = re.compile(r'[A-Za-z0-9._-]{1,40}')
_UDID = re.compile(r'[A-Za-z0-9-]{1,64}')
_MAX_GENERATION = 2 ** 53


def _name(value):
    if type(value) is not str or not _NAME.fullmatch(value):
        raise HelperError('protocol', 'Use 1 to 40 letters, digits, dots, dashes or underscores.')
    return value


def _udid(value):
    if type(value) is not str or not _UDID.fullmatch(value):
        raise HelperError('protocol', 'A UDID is 1 to 64 letters, digits or dashes.')
    return value


def _generation(value):
    if type(value) is not int or not 0 <= value < _MAX_GENERATION:
        raise HelperError('protocol', 'A generation is a whole number from 0 to 2^53 - 1.')
    return value


def _protocol(value):
    if type(value) is not int or not 1 <= value <= 1000:
        raise HelperError('protocol', 'The protocol version is a whole number.')
    return value


def _coordinate(limit):
    def check(value):
        # Range first: a huge integer would overflow float(); bool is an int but no coordinate.
        if type(value) not in (int, float) or not -limit <= value <= limit or not math.isfinite(value):
            raise HelperError('invalid-coordinates', 'Use a finite latitude in [-90, 90] and longitude in [-180, 180].')
        return float(value)
    return check


_POINT = {'latitude': _coordinate(90), 'longitude': _coordinate(180)}
_SESSION = {'session': _name, 'generation': _generation}
_DEVICE = {'udid': _udid}

_COMMON = {'hello': {'protocol': _protocol}, 'ping': {}}
OPERATIONS = {
    'control': {**_COMMON, 'components': {}, 'discover': {}, 'probe': _DEVICE, 'reveal': _DEVICE,
                'tools-status': _DEVICE, 'prepare': _DEVICE, 'cancel': {'target': _name}},
    'session': {**_COMMON, 'open': {**_DEVICE, **_SESSION}, 'set': {**_SESSION, **_POINT},
                'return': {**_SESSION, **_POINT}, 'clear': _SESSION, 'close': _SESSION},
}


def _unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise HelperError('protocol', 'Duplicate JSON fields are not allowed.')
        result[key] = value
    return result


def parse_request(line, role):
    """One request line, for a helper in [role] ("control" or "session"); raises HelperError."""
    if len(line.encode('utf-8', 'surrogatepass')) > MAX_LINE:
        raise HelperError('protocol', f'A request must fit in {MAX_LINE} bytes.')
    try:
        value = json.loads(line, object_pairs_hook=_unique)
    except HelperError:
        raise
    except (ValueError, RecursionError) as error:
        raise HelperError('protocol', 'Send one JSON object per line.') from error
    if not isinstance(value, dict):
        raise HelperError('protocol', 'Send one JSON object per line.')
    request_id = value.get('id')
    try:
        request_id = _name(request_id)
    except HelperError:
        raise HelperError('protocol', 'Every request needs a short string id.') from None
    try:
        return _validate(value, role, request_id)
    except HelperError as error:
        error.request_id = request_id
        raise


def _validate(value, role, request_id):
    op = value.get('op')
    schema = OPERATIONS[role].get(op) if type(op) is str else None
    if schema is None:
        raise HelperError('protocol', f'This helper does not take the operation {op!r}.')
    unknown = value.keys() - {'id', 'op'} - schema.keys()
    if unknown:
        raise HelperError('protocol', 'Unknown fields: ' + ', '.join(sorted(unknown)))
    missing = schema.keys() - value.keys()
    if missing:
        raise HelperError('protocol', 'Missing fields: ' + ', '.join(sorted(missing)))
    return Request(request_id, op, {name: check(value[name]) for name, check in schema.items()})


def _line(value):
    return json.dumps(value, allow_nan=False, separators=(',', ':'))


def hello(role, helper_version='', library_version=''):
    return _line({'type': 'hello', 'protocol': PROTOCOL, 'role': role, 'helper': helper_version,
                  'library': library_version})


def reply(request_id, result=None):
    return _line({'type': 'reply', 'id': request_id, 'ok': True, 'result': result or {}})


def failure(request_id, error):
    return _line({'type': 'reply', 'id': request_id, 'ok': False, 'error': {'code': error.code, 'detail': error.detail}})


def event(name, **fields):
    return _line({'type': 'event', 'event': name, **fields})
