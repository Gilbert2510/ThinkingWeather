# Chinese administrative display names

`china-counties.tsv` is a derived three-column name table (province, prefecture,
county-level full name), containing 2,847 entries. It is used only to complete a
provider's shortened display name; it is not used to change a location ID,
coordinates or weather coverage.

Source: [uiwjs/province-city-china](https://github.com/uiwjs/province-city-china),
`gh-pages` revision `547ade93bc913de0c937200d4fa218d05c0ba97b`
(2026-10-11), files `province.json`, `city.json`, `area.json`.
The maintainers describe their administrative data as derived from China's
Ministry of Civil Affairs. This is a pinned upstream snapshot, not a claim of
live administrative boundary coverage. Unknown or ambiguous names remain the
provider's original text.

The upstream MIT license is preserved at
`../licenses/province-city-china-MIT.txt` (Copyright (c) 2019 wabg).
License source was pinned from the upstream master revision
`a708f0df0ebdb68f09f5fcf6345367f7d5e9a7dc`.

Input SHA-256:

- `province.json`: `7d7ee447e058bec7e2918ee78241be1341c4471ac8483e6b34eecfd2e154796e`
- `city.json`: `450f63fd503878c5a5fee36aa539dce0916a91e48200aa49034320eb4ff9185e`
- `area.json`: `c286dffaf5d8b28316f77a343d3d0bba01efbc0570780bc90ef81a71fc73dca1`

The generated table replaces direct-controlled municipality parent labels with
the province name and omits synthetic “direct-administered county” parents.
Updates should fetch a pinned revision, parse the JSON as data, regenerate these
three columns, retain the license and run `AdministrativeNamesTest`.
