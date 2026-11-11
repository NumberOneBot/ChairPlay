/** The GPS car's city presets, kept off the GPS page for now. */

import { Row, Segmented } from '../shell/controls'
import type { Settings } from '../shell/useSettings'
import { CITIES, cityAt } from './cities'

/**
 * Puts the car on a busy road in one of these cities, facing along it (gps.start and gps.heading); the city the car
 * stands in, [place], is the one chosen.
 */
export function CityPresets({
  settings,
  place,
}: {
  settings: Settings
  place: { latitude: string; longitude: string } | null
}) {
  return (
    <Row label="City presets" title="Put the car on a busy road in one of these cities, facing along it">
      <Segmented
        label="City presets"
        className="rows cities"
        testId="city-preset"
        value={(place && cityAt(`${place.latitude}, ${place.longitude}`)?.code) ?? ''}
        options={CITIES.map((city) => [city.code, city.code, `${city.name}: ${city.road}`])}
        onChange={(code) => {
          const city = CITIES.find((each) => each.code === code)
          if (city)
            void settings.save({
              'gps.start': `${city.latitude}, ${city.longitude}`,
              'gps.heading': String(city.heading),
            })
        }}
      />
    </Row>
  )
}
