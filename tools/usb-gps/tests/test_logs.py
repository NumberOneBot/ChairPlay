import io
import logging
import unittest

from tests.fakes import UDID
from usb_gps_helper import logs
from usb_gps_helper.errors import classify


class LogTests(unittest.TestCase):
    def tearDown(self):
        logs.configure(stream=io.StringIO())

    def test_udids_never_reach_the_diagnostics(self):
        stream = io.StringIO()
        logs.configure(stream=stream)
        logging.getLogger('usb_gps_helper').warning('probe of %s failed', UDID)
        logging.getLogger('x').warning('old style %s', 'a' * 40)
        self.assertNotIn(UDID, stream.getvalue())
        self.assertNotIn('a' * 40, stream.getvalue())
        self.assertIn('<udid>', stream.getvalue())

    def test_the_diagnostics_stop_at_their_limit(self):
        stream = io.StringIO()
        logs.configure(stream=stream, limit=500)
        for index in range(100):
            logging.getLogger('usb_gps_helper').warning('line %d %s', index, 'x' * 50)
        self.assertLess(len(stream.getvalue()), 700)
        self.assertIn('limit reached', stream.getvalue())

    def test_error_details_carry_no_udid(self):
        self.assertNotIn(UDID, classify(RuntimeError(f'device {UDID} vanished')).detail)


if __name__ == '__main__':
    unittest.main()
