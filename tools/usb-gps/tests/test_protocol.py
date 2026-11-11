import json
import unittest

from usb_gps_helper import protocol
from usb_gps_helper.protocol import HelperError, parse_request


def line(**fields):
    return json.dumps(fields)


class ParseTests(unittest.TestCase):
    def code(self, text, role='session'):
        with self.assertRaises(HelperError) as caught:
            parse_request(text, role)
        return caught.exception.code

    def test_a_set_parses_into_its_fields(self):
        request = parse_request(line(id='r1', op='set', session='s1', generation=3, latitude=50.5, longitude=-30), 'session')
        self.assertEqual(('r1', 'set'), (request.id, request.op))
        self.assertEqual({'session': 's1', 'generation': 3, 'latitude': 50.5, 'longitude': -30.0}, request.fields)

    def test_coordinates_out_of_range_or_not_finite_are_invalid_coordinates(self):
        for latitude, longitude in ((91, 0), (-90.0001, 0), (0, 180.5), (0, -181), (True, 0), ('1', 0), (None, 0), (2 ** 80, 0)):
            text = line(id='r', op='set', session='s', generation=1, latitude=latitude, longitude=longitude)
            self.assertEqual('invalid-coordinates', self.code(text), (latitude, longitude))
        for raw in ('NaN', 'Infinity', '-Infinity', '1e999'):
            text = '{"id":"r","op":"set","session":"s","generation":1,"latitude":%s,"longitude":0}' % raw
            self.assertEqual('invalid-coordinates', self.code(text), raw)

    def test_the_edges_of_the_ranges_are_valid(self):
        request = parse_request(line(id='r', op='set', session='s', generation=0, latitude=-90, longitude=180), 'session')
        self.assertEqual((-90.0, 180.0), (request.fields['latitude'], request.fields['longitude']))

    def test_malformed_lines_are_protocol_errors(self):
        for text in ('', 'not json', '[1]', '"set"', '{"id":"r","op":"set"', '{"id":"r"}'):
            self.assertEqual('protocol', self.code(text), text)

    def test_duplicate_and_unknown_fields_are_refused(self):
        self.assertEqual('protocol', self.code('{"id":"r","op":"ping","op":"ping"}'))
        self.assertEqual('protocol', self.code(line(id='r', op='ping', extra=1)))
        self.assertEqual('protocol', self.code(line(id='r', op='set', session='s', generation=1, latitude=1, longitude=2, speed=3)))

    def test_missing_fields_are_refused(self):
        self.assertEqual('protocol', self.code(line(id='r', op='close', session='s')))
        self.assertEqual('protocol', self.code(line(id='r', op='probe'), role='control'))

    def test_ids_sessions_generations_and_udids_are_bounded(self):
        self.assertEqual('protocol', self.code(line(id='x' * 41, op='ping')))
        self.assertEqual('protocol', self.code(line(id='a b', op='ping')))
        self.assertEqual('protocol', self.code(line(id=7, op='ping')))
        self.assertEqual('protocol', self.code(line(id='r', op='close', session='s', generation=-1)))
        self.assertEqual('protocol', self.code(line(id='r', op='close', session='s', generation=1.5)))
        self.assertEqual('protocol', self.code(line(id='r', op='close', session='s', generation=2 ** 53)))
        self.assertEqual('protocol', self.code(line(id='r', op='probe', udid='../x'), role='control'))
        parse_request(line(id='r', op='probe', udid='00008030-001A2D3E0C38802E'), 'control')

    def test_an_overlong_line_is_refused_before_parsing(self):
        text = line(id='r', op='ping', pad='x' * protocol.MAX_LINE)
        self.assertEqual('protocol', self.code(text))

    def test_each_role_accepts_only_its_operations(self):
        self.assertEqual('protocol', self.code(line(id='r', op='prepare', udid='abc'), role='session'))
        self.assertEqual('protocol', self.code(line(id='r', op='close', session='s', generation=1), role='control'))
        self.assertEqual('protocol', self.code(line(id='r', op='clear', session='s', generation=1)))
        for op in ('hello', 'ping'):
            fields = {'protocol': 2} if op == 'hello' else {}
            parse_request(line(id='r', op=op, **fields), 'control')
            parse_request(line(id='r', op=op, **fields), 'session')

    def test_an_unknown_operation_is_refused(self):
        self.assertEqual('protocol', self.code(line(id='r', op='reboot')))
        self.assertEqual('protocol', self.code(line(id='r', op='enable-developer-mode', udid='abc'), role='control'))

    def test_the_bad_line_id_is_kept_for_the_reply_when_readable(self):
        with self.assertRaises(HelperError) as caught:
            parse_request(line(id='r9', op='set', session='s', generation=1, latitude=99, longitude=0), 'session')
        self.assertEqual('r9', caught.exception.request_id)


class MessageTests(unittest.TestCase):
    def test_the_error_codes_are_the_apps_keys(self):
        self.assertEqual(20, len(protocol.CODES))
        self.assertNotIn('several-devices', protocol.CODES)
        with self.assertRaises(ValueError):
            HelperError('made-up', 'x')

    def test_replies_and_events_are_one_line_of_strict_json(self):
        ok = protocol.reply('r1', {'applied': True})
        failed = protocol.failure('r2', HelperError('timeout', 'The phone did not answer'))
        event = protocol.event('progress', id='r3', receivedBytes=1, totalBytes=None)
        for text in (ok, failed, event):
            self.assertNotIn('\n', text)
            json.loads(text)
        self.assertEqual({'type': 'reply', 'id': 'r2', 'ok': False,
                          'error': {'code': 'timeout', 'detail': 'The phone did not answer'}}, json.loads(failed))
        with self.assertRaises(ValueError):
            protocol.reply('r', {'x': float('nan')})

    def test_the_hello_names_the_protocol_version(self):
        hello = json.loads(protocol.hello('session'))
        self.assertEqual(('hello', 2, 'session'), (hello['type'], hello['protocol'], hello['role']))


if __name__ == '__main__':
    unittest.main()
