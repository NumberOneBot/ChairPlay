# ChairPlay 1.0.11

This update adds two new styles for the GPS car's gauges, takes the pedals, the steering wheel, and the top speed from apps that drive the GPS car, and closes a running ChairPlay before an update installs.

## What's changed

- **Gauge styles.** **Settings > GPS > Gauge style** chooses German, as before, Italian, or British dials. Each has its own scale layout, needle color, and place for the coordinates, the wheel, and the pedals. [Compare the three styles](https://github.com/NumberOneBot/ChairPlay/blob/v1.0.11/docs/images/gauge-styles.png).
- **Gauges demo.** **Experiments > HUD demo > Gauges** runs the gauges up and down on the AltScreen in the style set. No iPhone or GPS is needed, and the GPS car stays where it is.
- **Integrations.**
  - An app driving the GPS car can send the throttle, the brake, the steering wheel's angle, and the car's top speed. The gauges show the pedals and the wheel, and the speedometer's scale runs to the top speed. Apps written for 1.0.9 work as before. [Integrations](https://github.com/NumberOneBot/ChairPlay/blob/v1.0.11/docs/INTEGRATIONS.md) describes the new fields.
  - The app's red line and top speed stay on the gauges after it stops sending, until the gamepad or keys drive the GPS car again.
- **GPS car.** Standing still, the car parks and stops its engine after 5 seconds instead of 10.
- **Updates.** Setup and the uninstaller close a running ChairPlay themselves, ending its CarPlay session as closing the window does, so the iPhone connects again right away.
- **Removed.** **Send satellites in view** and its almanac download are gone. The iPhone showed no difference with them.

## Install or update

Download **ChairPlay-1.0.11-setup.exe** from the [Releases page](https://github.com/NumberOneBot/ChairPlay/releases) and run it. Installation does not require administrator rights. Updates preserve settings, iPhone pairing, and MFi files. For the portable build, download **ChairPlay-1.0.11-portable.zip**: close ChairPlay, delete the **app** and **runtime** folders, and unpack the new zip over the old one.

For problems, use **About > Report a problem** to include the app version in a new issue.

[Full changelog](https://github.com/NumberOneBot/ChairPlay/blob/v1.0.11/CHANGELOG.md) · [Changes since v1.0.10](https://github.com/NumberOneBot/ChairPlay/compare/v1.0.10...v1.0.11)
