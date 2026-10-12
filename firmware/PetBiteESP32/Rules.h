#pragma once
#include <cmath>
namespace petbite {
inline bool validPortion(int grams) { return grams >= 5 && grams <= 300; }
inline bool validTime(int h, int m) { return h >= 0 && h < 24 && m >= 0 && m < 60; }
inline bool due(bool enabled, int h, int m, int nowH, int nowM, int lastDay, int day) {
    return enabled && validTime(h, m) && h == nowH && m == nowM && lastDay < day;
}
inline bool mustClose(float gain, int target, float margin, unsigned long elapsed, unsigned long maxTime) {
    return !std::isfinite(gain) || gain < -5.0f || gain >= target - margin || elapsed >= maxTime;
}
}

namespace petbite {
// PETMIN NOTA: reglas puras compartidas por firmware y pruebas nativas.
inline float hopperLoss(float before, float after) { return before - after; }
inline bool sequenceAllowed(unsigned long long candidate, unsigned long long last) { return candidate > last && candidate > 0; }
inline bool weightInRange(float weight, float capacity) { return std::isfinite(weight) && weight >= -10 && weight <= capacity; }
inline bool withinTolerance(float measured, int requested, float tolerance) {
    return std::isfinite(measured) && measured >= 0 && std::fabs(measured - requested) <= tolerance;
}
}
