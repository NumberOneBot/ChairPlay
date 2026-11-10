import unittest

from usb_gps_helper.components import detect


def fails():
    raise OSError('access denied')


class ComponentsTests(unittest.TestCase):
    def test_usbmux_answering_is_ready_whatever_else_is_found(self):
        self.assertEqual('ready', detect(usbmux=lambda: True, installed=lambda: [], running=lambda: []))

    def test_installed_but_silent_is_not_running_not_missing(self):
        self.assertEqual('not-running', detect(usbmux=lambda: False, installed=lambda: ['apple-devices'],
                                               running=lambda: []))
        self.assertEqual('not-running', detect(usbmux=lambda: False, installed=lambda: [],
                                               running=lambda: ['AppleDevices.exe']))

    def test_nothing_found_is_missing(self):
        self.assertEqual('missing', detect(usbmux=lambda: False, installed=lambda: [], running=lambda: []))

    def test_checks_that_fail_leave_it_unknown(self):
        self.assertEqual('unknown', detect(usbmux=fails, installed=fails, running=fails))
        self.assertEqual('not-running', detect(usbmux=fails, installed=lambda: ['itunes'], running=fails))


if __name__ == '__main__':
    unittest.main()
