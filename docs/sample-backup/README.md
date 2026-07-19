# Sample backup for testers

`evsct-sample-backup.zip` is a ready-to-restore EVSCT full backup you can hand
to testers so they land in a populated app instead of an empty one. It's the
same `.zip` shape the app writes from **Settings → Full backup → Save / Share**
(schema v5): a `backup.json` plus generated vehicle photos and receipt files.

There are three packs:

| Pack | Sessions | Trips | Use it for |
| --- | --- | --- | --- |
| `evsct-sample-backup.zip` | 87 | 5 | the standard tester pack |
| `evsct-sample-backup-large.zip` | 163 | 10 | long lists, scroll checks, denser map/stats |
| `evsct-sample-backup-roadtrip.zip` | 160 | 8 | continent-wide map, 3-year history, zero-home-charging stats |

The large pack is a strict superset: the same three vehicles and all of the
small pack's data, plus five more trips (Toronto Christmas run, Montréal long
weekend, Kingston family visit, Bruce Peninsula/Tobermory, Gatineau Park
weekend) and ~76 more sessions — a denser home/work charging cadence, winter
DC stops, three more receipts, and enough trips that **all ten map pin
colors** are in use. Same physical-consistency guarantees as the small pack
(see below); data runs Apr 2025 → Jul 2026.

The road-trip pack is a **standalone third dataset** (not a superset — restoring
it replaces the other packs' data). It covers three full years, **Aug 2023 →
Jul 2026**, with just two vehicles that both take big road trips across Canada
and the USA:

- **2023 Kia EV6 GT-Line AWD** (default) — a downtown-condo car that **never
  charges at home**: 80 sessions of curbside posts, workplace garage, flat-fee
  mall garages and DC fast, and not a single "Home" entry. Use it to check
  screens and stats when home kWh is exactly zero.
- **2022 Ford Mustang Mach-E Premium AWD ER** — suburban home L2 (sparsely
  logged, the way real users treat a boring flat overnight rate) plus every
  trip charge. 80 sessions.
- **8 long-haul trips**: Adirondacks (Oct 2023), Chicago via Michigan
  (Jun 2024), a 32-session **cross-Canada Ottawa→Banff** run on Petro-Canada's
  Electric Highway (Jul–Aug 2024), New England fall colours (Oct 2024), a
  Florida winter escape down I-81/I-95 with 20 USD sessions (Feb 2025), the
  Gaspésie loop (Jul 2025), Halifax & the Maritimes on NB Power eCharge
  (Aug 2025), and Nashville & the Smokies (Apr 2026). Map pins stretch from
  Lake Louise to Orlando.
- Flavour details: 43 sessions billed in **USD**, Tesla Supercharger stops via
  the **NACS adapter** (Mach-E from Feb 2025, EV6 from Apr 2026), every pricing
  model, 7 receipts (4 photos + 3 PDFs), and 2025 *and* 2026 year-recap data.
  Same physical-consistency guarantees as the other packs, enforced by an
  assertion pass inside the generator.

## What's inside

- **3 vehicles** — 2023 Tesla Model Y (default), 2022 Hyundai Ioniq 5, 2021
  Chevrolet Bolt EV — each with a profile photo, battery/range specs, VIN and
  notes.
- **87 charging sessions** (34 / 28 / 25 per vehicle) spanning **Apr 2025 →
  Jun 2026**, so the 12-month rolling charts, "this month" cards, and the
  2025 **and** 2026 year-recaps all have data.
- **5 trips** with map pin colors: Toronto long weekend, Québec City, Algonquin
  cottage week, Lake Placid NY (billed in **USD**), and a Mont-Tremblant ski
  weekend tagged `winter test`.
- A realistic charging mix: DC fast (Tesla Supercharger, Electrify
  Canada/America, Petro-Canada, Ivy, FLO, Electric Circuit), home AC L2, a few
  120 V L1 trickle charges, plus free workplace/destination L2. Every pricing
  model is represented (per-kWh, per-minute, flat, free, hybrid).
- Latitude/longitude on every session (Ontario / Québec / upstate NY) so the
  **Map**, heatmap, and trip-route polylines populate immediately.
- Free-form **tags** (`home`, `road trip`, `winter test`, `work charge`,
  `cottage`, `supercharger`, …), occasional **wait time**, `continuesPrevious`
  chains on road-trip legs, and **5 receipts** (3 photos + 2 PDFs, including a
  multi-receipt-capable layout) to exercise the receipt viewer.

The numbers are physically self-consistent — odometer increases monotonically
per vehicle, battery end ≥ start, and effective $/kWh, $/min and average power
stay within the app's tolerances — so **no validation hints fire** on any
session.

## How a tester loads it

> ⚠️ **Restore replaces everything.** Restoring wipes the device's existing
> EVSCT database (vehicles, trips, sessions, photos, receipts) and installs
> this sample in its place. Use a fresh install or a device with no data you
> care about. The app shows a confirmation dialog before it proceeds.

1. Get `evsct-sample-backup.zip` onto the phone (Drive, email, Messages, USB —
   anywhere the file picker can reach).
2. Open EVSCT once so the database is initialized.
3. **Settings → Full backup → Restore from backup…**, pick the zip, and confirm.

## Regenerating

The data is produced by `generate_sample_backup.py` (deterministic — fixed
random seed). It needs Python 3 and Pillow:

```bash
pip install Pillow
python3 generate_sample_backup.py           # rewrites evsct-sample-backup.zip
python3 generate_sample_backup_large.py     # rewrites evsct-sample-backup-large.zip
python3 generate_sample_backup_roadtrip.py  # rewrites evsct-sample-backup-roadtrip.zip
```

Tweak the vehicle list, station table, or per-vehicle session blocks in that
script to reshape the sample. The large generator imports the base one,
rebuilds its dataset verbatim as the starting point, and layers the extra
trips/sessions on top before re-running the shared odometer/trip-window
consistency passes — so edits to the base script flow into both packs. The
road-trip generator also imports the base script, but only for the shared
machinery (Builder, image/receipt rendering, consistency passes); its dataset
is authored from scratch and is unaffected by edits to the base data. Unlike
the other packs it uses real vehicle photos — `vehicle-photo-ev6.jpg` and
`vehicle-photo-mach-e.jpg` next to the script — and falls back to the
generated gradient cards if those files are missing.
