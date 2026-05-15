<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=F5732A,FF9A00&height=200&section=header&text=Tsukiji%20Maps&fontSize=60&fontColor=ffffff&fontAlignY=38&desc=Find%20your%20perfect%20business%20location&descAlignY=58&descSize=18" />

[![Made with Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Google Maps](https://img.shields.io/badge/Google%20Maps-4285F4?style=for-the-badge&logo=googlemaps&logoColor=white)](https://developers.google.com/maps)
[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-F5732A?style=for-the-badge)](LICENSE)

</div>

---

### What is Tsukiji?

Tsukiji Maps is an Android app that helps entrepreneurs and investors find the best spots to open a business. It uses live heatmaps to visualize commercial density, analyzes competition in any area, and returns ranked location suggestions with opportunity scores.

---

### Features

- **Live Heatmap** — Business density rendered dynamically, adapts radius and intensity per zoom level
- **Location Scoring** — Evaluates 200 candidates per search and surfaces the top 5 optimal placements
- **Category Filters** — Restaurants, Retail, Services, Entertainment
- **Save Locations** — Bookmark any suggested spot with its score and timestamp
- **Property Board** — List commercial spaces for rent or sale with photos and full address

---

### Tech Stack

<div align="center">

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white)
![Google Maps](https://img.shields.io/badge/Google%20Maps%20SDK-4285F4?style=flat-square&logo=googlemaps&logoColor=white)
![Coroutines](https://img.shields.io/badge/Coroutines-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Material](https://img.shields.io/badge/Material%203-757575?style=flat-square&logo=material-design&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white)

</div>

---

### Getting Started

**1. Clone**
```bash
git clone https://github.com/your-org/tsukiji-maps.git
cd tsukiji-maps
```

**2. Add your Maps API key** — create `secrets.properties` in the root:
```properties
MAPS_API_KEY=YOUR_KEY_HERE
```

**3. Run**
```bash
./gradlew :app:installDebug
```

---

### How the Scoring Works

```
Search area  →  200 random candidates
                        ↓
              Score = competitor distance (rewarded)
                    + foot traffic proximity
                    + business density bonus
                    - same-category saturation penalty
                        ↓
              Top 5 non-overlapping results  →  Markers on map
```

| Score | Meaning |
|---|---|
| > 70 | Excellent opportunity |
| 50 – 70 | Good balance |
| 30 – 50 | Emerging zone |
| < 30 | High competition |

---

### Project Structure

```
tsukiji-maps/
├── app/                     # Tsukiji application
│   ├── MainActivity.kt      # Map, heatmap, scoring logic
│   ├── GuardadosFragment    # Saved locations
│   └── PropiedadesFragment  # Property listings
├── maps-ktx/                # Kotlin extensions for Maps SDK
└── maps-utils-ktx/          # Kotlin extensions for Maps Utils
```

---

<div align="center">

*Built on top of [android-maps-ktx](https://github.com/googlemaps/android-maps-ktx) · Apache 2.0 License*

<img src="https://capsule-render.vercel.app/api?type=waving&color=F5732A,FF9A00&height=100&section=footer" />

</div>
