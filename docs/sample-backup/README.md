# Sample backup for testers

`evsct-sample-backup.zip` is a ready-to-restore EVSCT full backup you can hand
to testers so they land in a populated app instead of an empty one. It's the
same `.zip` shape the app writes from **Settings → Full backup → Save / Share**
(schema v5): a `backup.json` plus generated vehicle photos and receipt files.

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
python3 generate_sample_backup.py   # rewrites evsct-sample-backup.zip
```

Tweak the vehicle list, station table, or per-vehicle session blocks in that
script to reshape the sample.
