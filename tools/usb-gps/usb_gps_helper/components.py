"""Apple's USB components on this PC, by usbmux, installed packages and running processes (F-04).

Not one service's name: Apple Devices from the Microsoft Store lays out its parts differently from iTunes. usbmux
answering is "ready"; anything Apple installed or running while usbmux is silent is "not-running" (open Apple Devices,
then probe again), never "missing"; "missing" only when every check ran and found nothing.
"""
import os
import socket

USBMUX = ('127.0.0.1', 27015)
_PROCESSES = {'applemobiledeviceservice.exe', 'applemobiledeviceprocess.exe', 'appledevices.exe', 'itunes.exe',
              'ituneshelper.exe'}
_SERVICE_KEYS = (r'SYSTEM\CurrentControlSet\Services\Apple Mobile Device Service',
                 r'SOFTWARE\Apple Inc.\Apple Mobile Device Support')
_APPX_PACKAGES = r'Software\Classes\Local Settings\Software\Microsoft\Windows\CurrentVersion\AppModel\Repository\Packages'
_APPX_PREFIXES = ('appleinc.appledevices', 'appleinc.itunes')


def usbmux_answers():
    with socket.create_connection(USBMUX, timeout=1):
        return True


def installed():
    import winreg
    found = []
    for key in _SERVICE_KEYS:
        try:
            winreg.CloseKey(winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, key))
            found.append(key.rsplit('\\', 1)[-1])
        except OSError:
            pass
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _APPX_PACKAGES) as packages:
            index = 0
            while True:
                try:
                    name = winreg.EnumKey(packages, index)
                except OSError:
                    break
                index += 1
                if name.lower().startswith(_APPX_PREFIXES):
                    found.append(name.split('_', 1)[0])
    except OSError:
        pass
    return found


def running():
    import psutil
    names = []
    for process in psutil.process_iter(['name']):
        name = (process.info.get('name') or '').lower()
        if name in _PROCESSES:
            names.append(name)
    return names


def _try(check):
    try:
        return check(), True
    except Exception:
        return None, False


def detect(usbmux=usbmux_answers, installed=installed, running=running):
    answered, _ = _try(usbmux)
    if answered:
        return 'ready'
    if os.name != 'nt' and usbmux is usbmux_answers:
        return 'unknown'
    found_installed, installed_ran = _try(installed)
    found_running, running_ran = _try(running)
    if found_installed or found_running:
        return 'not-running'
    return 'missing' if installed_ran and running_ran else 'unknown'
