$version: "2"

namespace demo

/// A simple weather service used to verify the typelevel-site smithy-docgen integration.
service Weather {
    version: "2025-01-01"
    operations: [GetCurrentTemperature]
}

/// Get the current temperature for a given city.
operation GetCurrentTemperature {
    input := {
        @required
        city: String
    }
    output := {
        @required
        temperatureCelsius: Float
    }
}
