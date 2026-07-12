#!/usr/bin/env python3
"""
Generate the EXPANDED sample backup: everything in evsct-sample-backup.zip
plus roughly double the trips and sessions, for exercising long lists,
scroll-position preservation, busier stats, and denser map pins.

This script does not duplicate the base generator — it imports
generate_sample_backup.py, rebuilds its exact dataset (same seed, so the
small zip's content is reproduced verbatim as the starting point), then
layers on five more trips and ~75 more sessions across the same three
vehicles before re-running the odometer / trip-window consistency passes
over the combined timeline. The result keeps every invariant the base
pack guarantees (monotonic odometers, battery end >= start, effective
rates within the app's hint tolerances) because the same machinery
computes them.

Run:  python3 generate_sample_backup_large.py
Out:  evsct-sample-backup-large.zip  (next to this script)

The small pack is untouched; regenerate it separately with
generate_sample_backup.py if needed.
"""

from __future__ import annotations

import collections
import json
import os
import random
import zipfile
from datetime import datetime, timedelta

import generate_sample_backup as base
from generate_sample_backup import Station

OUT_ZIP = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "evsct-sample-backup-large.zip"
)

# Independent stream so this script's randomness can't disturb the base
# dataset's deterministic rng consumption (and vice versa).
lrng = random.Random(20260712)


# --------------------------------------------------------------------------- #
#  Additional stations for the new trips
# --------------------------------------------------------------------------- #

BARRIE_EC = Station("Electrify Canada", "ONroute Barrie", "Barrie", "ON",
                    "Highway 400 ONroute, Barrie, ON", 44.3720, -79.7010)
OWENSOUND_FLO = Station("FLO", "Owen Sound Heritage Place", "Owen Sound", "ON",
                        "1350 16th St E, Owen Sound, ON", 44.5670, -80.9210)
TOBERMORY_L2 = Station("ChargePoint", "Tobermory Marina Lot", "Tobermory", "ON",
                       "7420 Highway 6, Tobermory, ON", 45.2530, -81.6650)
MONTREAL_EC = Station("Electric Circuit", "Centre Bell", "Montréal", "QC",
                      "1225 Rue Saint-Antoine O, Montréal, QC", 45.4960, -73.5710)
MONTREAL_HOTEL = Station("FLO", "Hôtel Bonaventure Garage", "Montréal", "QC",
                         "900 Rue de la Gauchetière O, Montréal, QC", 45.4990, -73.5650)
KINGSTON_FLO = Station("FLO", "Kingston Division St", "Kingston", "ON",
                       "645 Division St, Kingston, ON", 44.2580, -76.5020)
KINGSTON_FAMILY = Station("Family", "Grandma's driveway", "Kingston", "ON",
                          "Calvin Park, Kingston, ON", 44.2430, -76.5340)
CHELSEA_FLO = Station("FLO", "Chelsea Trailhead", "Chelsea", "QC",
                      "Chemin Old Chelsea, Chelsea, QC", 45.5000, -75.7900)
GATINEAU_EC = Station("Electric Circuit", "Casino du Lac-Leamy", "Gatineau", "QC",
                      "1 Boul du Casino, Gatineau, QC", 45.4430, -75.7160)


# --------------------------------------------------------------------------- #
#  Rebuild-then-extend plumbing
# --------------------------------------------------------------------------- #

def restore_drive_km(b: base.Builder) -> None:
    """base.build() already ran the odometer pass, which consumed each
    session's _driveKm. Invert the computed odometers back into per-session
    drive distances and clear the readings, so the pass can re-run cleanly
    over the combined base + new timeline."""
    byv = collections.defaultdict(list)
    for s in b.sessions:
        byv[s["vehicleId"]].append(s)
    for vid, lst in byv.items():
        lst.sort(key=lambda s: s["sessionStart"])
        prev = b._base_odo[vid]
        for s in lst:
            s["_driveKm"] = round(s["odometerKm"] - prev, 1)
            prev = s["odometerKm"]
            s["odometerKm"] = None


def add_home_sessions(b, vehicle_id, station, dates, *, avg_kw, tags="home",
                      posted_kwh=0.103, posted_max_kw=11.5, note_every=4):
    for i, d in enumerate(dates):
        bs = lrng.randint(28, 54)
        be = lrng.choice([80, 85, 90, 90, 95, 100])
        b.add_session(
            vehicle_id, d, station, "AC_L2",
            drive_km=lrng.uniform(120, 340),
            batt_start=bs, batt_end=be, pricing="PER_KWH",
            posted_kwh=posted_kwh, posted_max_kw=posted_max_kw,
            avg_kw=avg_kw * lrng.uniform(0.97, 1.03),
            tags=tags, eff_noise=lrng.uniform(-0.03, 0.03),
            notes=("Off-peak overnight." if i % note_every == 0 else None),
        )


# --------------------------------------------------------------------------- #
#  The expansion dataset
# --------------------------------------------------------------------------- #

def extend(b: base.Builder) -> None:
    # ---- New trips (ids continue after the base pack's 1-5) -------------- #
    # Odometer windows are placeholders; _fill_trip_odometers recomputes
    # them from the member sessions after the combined odometer pass.
    b.add_trip(6, "Toronto — Christmas run",
               datetime(2025, 12, 24, 8, 0), datetime(2025, 12, 27, 18, 0),
               0.0, 0.0,
               "Family Christmas in Markham. Winter rates and a flat-fee "
               "garage overnight.", "CYAN")
    b.add_trip(7, "Montréal long weekend",
               datetime(2025, 9, 20, 8, 30), datetime(2025, 9, 22, 19, 0),
               0.0, 0.0,
               "Ioniq 5 down the 417. Habs pre-season game and old-port "
               "wandering.", "YELLOW")
    b.add_trip(8, "Kingston family visit",
               datetime(2025, 11, 8, 9, 0), datetime(2025, 11, 9, 18, 0),
               0.0, 0.0,
               "Bolt's slow DC ceiling means one long FLO stop each way — "
               "plus grandma's 120V overnight.", "BLUE")
    b.add_trip(9, "Bruce Peninsula / Tobermory",
               datetime(2026, 6, 19, 7, 30), datetime(2026, 6, 22, 20, 0),
               0.0, 0.0,
               "Grotto hike and the Big Tub lighthouse. Charging thins out "
               "past Owen Sound — topped up before the peninsula.", "MAGENTA")
    b.add_trip(10, "Gatineau Park spring weekend",
               datetime(2026, 5, 23, 9, 0), datetime(2026, 5, 24, 17, 0),
               0.0, 0.0,
               "Short hop across the river: trailhead L2 while hiking, casino "
               "DC before dinner.", "ROSE")

    # ===================================================================== #
    #  VEHICLE 1 — Tesla Model Y
    # ===================================================================== #
    v = 1
    add_home_sessions(b, v, base.HOME, [
        datetime(2025, 5, 22, 22, 10), datetime(2025, 5, 29, 21, 55),
        datetime(2025, 7, 15, 22, 40), datetime(2025, 7, 29, 22, 5),
        datetime(2025, 8, 12, 21, 35), datetime(2025, 8, 26, 22, 50),
        datetime(2025, 10, 7, 22, 15), datetime(2025, 10, 21, 21, 30),
        datetime(2025, 11, 18, 22, 45), datetime(2025, 12, 15, 22, 20),
        datetime(2026, 1, 20, 22, 35), datetime(2026, 2, 10, 21, 50),
        datetime(2026, 2, 24, 22, 25), datetime(2026, 3, 17, 22, 0),
        datetime(2026, 4, 23, 21, 40), datetime(2026, 5, 26, 22, 15),
        datetime(2026, 6, 30, 22, 45), datetime(2026, 7, 8, 21, 50),
    ], avg_kw=11.0)

    b.add_session(v, datetime(2025, 8, 20, 13, 15), base.OTT_FLO_DC, "DC_FAST",
                  drive_km=95, batt_start=31, batt_end=74, pricing="PER_KWH",
                  posted_kwh=0.55, posted_max_kw=100, avg_kw=92.0,
                  tags="errand", stall="DC-2",
                  notes="Train-station splash while picking up visitors.")
    b.add_session(v, datetime(2026, 4, 4, 11, 45), base.OTT_PETROCAN, "DC_FAST",
                  drive_km=105, batt_start=22, batt_end=80, pricing="PER_KWH",
                  posted_kwh=0.66, posted_max_kw=200, avg_kw=121.0,
                  tags="errand", stall="Stall 1", wait=4)
    b.add_session(v, datetime(2026, 5, 30, 13, 30), base.OTT_CHARGEPOINT, "AC_L2",
                  drive_km=60, batt_start=55, batt_end=79, pricing="FREE",
                  posted_max_kw=7.7, avg_kw=7.1, tags="free,errand",
                  notes="Farmers' market again — free L2 still the best deal in town.")

    # ---- Trip 6: Toronto Christmas (Model Y) ----
    rec_xmas = b.make_receipt(
        "jpg", "Tesla Supercharger", "Kingston, ON · Dec 24, 2025",
        [("Session", "DC Fast"), ("Energy", "52.6 kWh"),
         ("Rate", "$0.44 / kWh"), ("Duration", "29 min"), ("Total", "$23.14")],
        "tesla-kingston-2025-12-24.jpg")
    b.add_session(v, datetime(2025, 12, 24, 9, 10), base.KINGSTON_SC, "DC_FAST",
                  drive_km=192, batt_start=15, batt_end=82, pricing="PER_KWH",
                  posted_kwh=0.44, posted_max_kw=250, avg_kw=112.0, trip_id=6,
                  tags="road trip,supercharger,winter test", stall="Stall 3",
                  notes="-12°C — preconditioning made a visible difference.",
                  receipts=[rec_xmas])
    b.add_session(v, datetime(2025, 12, 24, 12, 5), base.PORTHOPE_IVY, "DC_FAST",
                  drive_km=168, batt_start=30, batt_end=75, pricing="PER_MINUTE",
                  posted_min=0.62, posted_max_kw=150, avg_kw=98.0, trip_id=6,
                  tags="road trip,winter test", stall="Charger 2", wait=8,
                  continues=True, notes="ONroute mobbed on Christmas Eve.")
    b.add_session(v, datetime(2025, 12, 25, 20, 30), base.MARKHAM_L2, "AC_L2",
                  drive_km=110, batt_start=34, batt_end=92, pricing="FLAT",
                  flat_cost=14.0, posted_max_kw=7.2, avg_kw=6.8, trip_id=6,
                  tags="road trip,destination",
                  notes="Civic-centre garage flat fee — parked overnight anyway.")
    b.add_session(v, datetime(2025, 12, 27, 12, 40), base.TRENTON_EC, "DC_FAST",
                  drive_km=150, batt_start=26, batt_end=78, pricing="PER_MINUTE",
                  posted_min=0.57, posted_max_kw=350, avg_kw=132.0, trip_id=6,
                  tags="road trip,winter test", stall="Charger 4",
                  notes="Boxing-day traffic crawl on the 401.")

    # ---- Trip 9: Bruce Peninsula (Model Y) ----
    rec_owen = b.make_receipt(
        "jpg", "FLO", "Owen Sound, ON · Jun 19, 2026",
        [("Session", "DC Fast"), ("Energy", "38.9 kWh"),
         ("Rate", "$0.55 / kWh"), ("Duration", "28 min"), ("Total", "$21.40")],
        "flo-owensound-2026-06-19.jpg")
    b.add_session(v, datetime(2026, 6, 19, 9, 40), BARRIE_EC, "DC_FAST",
                  drive_km=352, batt_start=19, batt_end=78, pricing="PER_MINUTE",
                  posted_min=0.57, posted_max_kw=350, avg_kw=138.0, trip_id=9,
                  tags="road trip", stall="Charger 1", wait=6,
                  notes="400-series construction the whole way to Barrie.")
    b.add_session(v, datetime(2026, 6, 19, 12, 50), OWENSOUND_FLO, "DC_FAST",
                  drive_km=122, batt_start=41, batt_end=82, pricing="PER_KWH",
                  posted_kwh=0.55, posted_max_kw=100, avg_kw=84.0, trip_id=9,
                  tags="road trip", stall="DC-1", continues=True,
                  notes="Last fast charger before the peninsula — topped high.",
                  receipts=[rec_owen])
    b.add_session(v, datetime(2026, 6, 20, 18, 20), TOBERMORY_L2, "AC_L2",
                  drive_km=118, batt_start=33, batt_end=94, pricing="PER_KWH",
                  posted_kwh=0.35, posted_max_kw=7.2, avg_kw=6.6, trip_id=9,
                  tags="road trip,destination",
                  notes="Marina lot L2 while we did the Big Tub lighthouse walk.")
    b.add_session(v, datetime(2026, 6, 22, 11, 30), OWENSOUND_FLO, "DC_FAST",
                  drive_km=126, batt_start=27, batt_end=76, pricing="PER_KWH",
                  posted_kwh=0.55, posted_max_kw=100, avg_kw=81.0, trip_id=9,
                  tags="road trip", stall="DC-2",
                  notes="Grotto parking was a zoo — glad we booked the timeslot.")
    b.add_session(v, datetime(2026, 6, 22, 14, 55), BARRIE_EC, "DC_FAST",
                  drive_km=121, batt_start=35, batt_end=74, pricing="PER_MINUTE",
                  posted_min=0.57, posted_max_kw=350, avg_kw=126.0, trip_id=9,
                  tags="road trip", stall="Charger 3", continues=True)

    # ===================================================================== #
    #  VEHICLE 2 — Hyundai Ioniq 5
    # ===================================================================== #
    v = 2
    add_home_sessions(b, v, base.HOME, [
        datetime(2025, 5, 6, 21, 30), datetime(2025, 6, 17, 22, 20),
        datetime(2025, 7, 22, 21, 45), datetime(2025, 8, 19, 22, 10),
        datetime(2025, 10, 14, 21, 55), datetime(2025, 12, 9, 22, 30),
        datetime(2026, 2, 3, 22, 15), datetime(2026, 4, 14, 21, 40),
        datetime(2026, 6, 16, 22, 25),
    ], avg_kw=10.6, tags="home,commute")
    # Workplace free L2 — Carolyn's commute cadence.
    for d in [
        datetime(2025, 5, 14, 9, 10), datetime(2025, 6, 5, 9, 5),
        datetime(2025, 7, 10, 9, 15), datetime(2025, 8, 21, 9, 0),
        datetime(2025, 9, 11, 9, 10), datetime(2025, 10, 23, 9, 5),
        datetime(2025, 11, 27, 9, 20), datetime(2026, 1, 15, 9, 10),
        datetime(2026, 3, 12, 9, 5), datetime(2026, 5, 7, 9, 15),
    ]:
        b.add_session(v, d, base.WORK, "AC_L2",
                      drive_km=lrng.uniform(40, 75),
                      batt_start=lrng.randint(38, 60),
                      batt_end=lrng.choice([78, 80, 85, 90]),
                      pricing="FREE", posted_max_kw=7.2,
                      avg_kw=6.9 * lrng.uniform(0.97, 1.03),
                      tags="work charge,free",
                      notes="Workplace charger — first-come.")
    b.add_session(v, datetime(2026, 1, 24, 12, 20), base.OTT_PETROCAN, "DC_FAST",
                  drive_km=98, batt_start=25, batt_end=79, pricing="PER_KWH",
                  posted_kwh=0.66, posted_max_kw=200, avg_kw=158.0,
                  tags="errand,winter test", stall="Stall 2", wait=3,
                  notes="800V + preconditioning: held 150 kW+ at -18°C.")

    # ---- Trip 7: Montréal long weekend (Ioniq 5) ----
    rec_mtl = b.make_receipt(
        "pdf", "Electric Circuit", "Montréal, QC · Sep 20, 2025",
        [("Session", "BRCC 100 kW"), ("Energy", "41.2 kWh"),
         ("Rate", "$0.31 / min"), ("Duration", "24 min"), ("Total", "$7.44")],
        "circuit-electrique-montreal-2025-09-20.pdf")
    b.add_session(v, datetime(2025, 9, 20, 9, 35), base.CASSELMAN_IVY, "DC_FAST",
                  drive_km=145, batt_start=32, batt_end=76, pricing="PER_MINUTE",
                  posted_min=0.45, posted_max_kw=150, avg_kw=118.0, trip_id=7,
                  tags="road trip", stall="Charger 2",
                  notes="Breakfast stop — Casselman as always.")
    b.add_session(v, datetime(2025, 9, 20, 11, 50), MONTREAL_EC, "DC_FAST",
                  drive_km=152, batt_start=34, batt_end=80, pricing="PER_MINUTE",
                  posted_min=0.31, posted_max_kw=100, avg_kw=88.0, trip_id=7,
                  tags="road trip", stall="BRCC 1", continues=True, wait=7,
                  notes="Downtown BRCC before checking in.",
                  receipts=[rec_mtl])
    b.add_session(v, datetime(2025, 9, 21, 21, 15), MONTREAL_HOTEL, "AC_L2",
                  drive_km=48, batt_start=44, batt_end=96, pricing="HYBRID",
                  flat_cost=2.0, posted_min=0.02, posted_max_kw=6.6, avg_kw=6.2,
                  trip_id=7, tags="road trip,destination",
                  notes="Hotel garage: $2 connection + per-minute. Overnight.")
    b.add_session(v, datetime(2025, 9, 22, 15, 40), base.BROSSARD_EC, "DC_FAST",
                  drive_km=30, batt_start=58, batt_end=88, pricing="PER_MINUTE",
                  posted_min=0.31, posted_max_kw=100, avg_kw=82.0, trip_id=7,
                  tags="road trip", stall="BRCC 2",
                  notes="Top-up at DIX30 before the drive home.")

    # ---- Trip 10: Gatineau Park spring weekend (Ioniq 5) ----
    b.add_session(v, datetime(2026, 5, 23, 10, 40), CHELSEA_FLO, "AC_L2",
                  drive_km=52, batt_start=52, batt_end=84, pricing="PER_KWH",
                  posted_kwh=0.35, posted_max_kw=7.2, avg_kw=6.8, trip_id=10,
                  tags="road trip,hike",
                  notes="Trailhead L2 during the Pink Lake loop.")
    b.add_session(v, datetime(2026, 5, 23, 18, 5), GATINEAU_EC, "DC_FAST",
                  drive_km=38, batt_start=47, batt_end=85, pricing="PER_MINUTE",
                  posted_min=0.31, posted_max_kw=100, avg_kw=86.0, trip_id=10,
                  tags="road trip", stall="BRCC 1",
                  notes="Casino BRCC before dinner across the river.")
    b.add_session(v, datetime(2026, 5, 24, 13, 30), CHELSEA_FLO, "AC_L2",
                  drive_km=44, batt_start=55, batt_end=83, pricing="PER_KWH",
                  posted_kwh=0.35, posted_max_kw=7.2, avg_kw=6.7, trip_id=10,
                  tags="road trip,hike", continues=True,
                  notes="Second hike day — Champlain Lookout.")

    # ===================================================================== #
    #  VEHICLE 3 — Chevrolet Bolt EV  (55 kW DC ceiling)
    # ===================================================================== #
    v = 3
    add_home_sessions(b, v, base.HOME, [
        datetime(2025, 5, 4, 22, 0), datetime(2025, 6, 8, 21, 40),
        datetime(2025, 8, 3, 22, 20), datetime(2025, 9, 7, 22, 5),
        datetime(2025, 10, 12, 21, 50), datetime(2025, 11, 23, 22, 30),
        datetime(2026, 1, 11, 22, 10), datetime(2026, 2, 22, 21, 45),
        datetime(2026, 3, 29, 22, 15), datetime(2026, 5, 3, 21, 55),
        datetime(2026, 6, 14, 22, 35), datetime(2026, 7, 5, 22, 0),
    ], avg_kw=7.2, posted_max_kw=7.4)

    b.add_session(v, datetime(2025, 12, 20, 11, 30), base.OTT_FLO_DC, "DC_FAST",
                  drive_km=88, batt_start=28, batt_end=72, pricing="PER_KWH",
                  posted_kwh=0.55, posted_max_kw=100, avg_kw=46.0,
                  tags="errand,winter test", stall="DC-1", wait=10,
                  notes="Bolt tops out ~46 kW — the wait was longer than the errand.")
    b.add_session(v, datetime(2026, 4, 18, 14, 10), base.OTT_CHARGEPOINT, "AC_L2",
                  drive_km=54, batt_start=50, batt_end=76, pricing="FREE",
                  posted_max_kw=7.7, avg_kw=6.9, tags="free,errand")

    # ---- Trip 8: Kingston family visit (Bolt) ----
    b.add_session(v, datetime(2025, 11, 8, 10, 40), KINGSTON_FLO, "DC_FAST",
                  drive_km=178, batt_start=21, batt_end=78, pricing="PER_KWH",
                  posted_kwh=0.55, posted_max_kw=100, avg_kw=45.0, trip_id=8,
                  tags="road trip,family", stall="DC-1", wait=12,
                  notes="Long lunch while the Bolt did its 46 kW thing.")
    b.add_session(v, datetime(2025, 11, 8, 22, 30), KINGSTON_FAMILY, "AC_L1",
                  drive_km=25, batt_start=62, batt_end=88, pricing="FREE",
                  posted_max_kw=1.4, avg_kw=1.3, trip_id=8,
                  tags="road trip,family,free", continues=True,
                  notes="Grandma's 120V outlet overnight — good enough.")
    b.add_session(v, datetime(2025, 11, 9, 15, 20), KINGSTON_FLO, "DC_FAST",
                  drive_km=32, batt_start=64, batt_end=92, pricing="PER_KWH",
                  posted_kwh=0.55, posted_max_kw=100, avg_kw=42.0, trip_id=8,
                  tags="road trip,family", stall="DC-2", continues=True,
                  notes="Buffer for the drive home into a headwind.")

    # A second cottage-style valley outing (untripped, echoes the base pack).
    b.add_session(v, datetime(2026, 6, 27, 12, 15), base.RENFREW_FLO, "DC_FAST",
                  drive_km=112, batt_start=30, batt_end=74, pricing="PER_KWH",
                  posted_kwh=0.55, posted_max_kw=50, avg_kw=44.0,
                  tags="errand", stall="DC",
                  notes="Valley swim day. 50 kW stall is fine when you ARE the ceiling.")
    b.add_session(v, datetime(2026, 6, 27, 19, 40), base.PEMBROKE_PC, "DC_FAST",
                  drive_km=68, batt_start=42, batt_end=76, pricing="PER_KWH",
                  posted_kwh=0.66, posted_max_kw=200, avg_kw=45.0,
                  tags="errand", continues=True,
                  notes="Ice cream stop on the way back.")


# --------------------------------------------------------------------------- #
#  Write the zip
# --------------------------------------------------------------------------- #

def main():
    b = base.build()
    base_sessions, base_trips = len(b.sessions), len(b.trips)
    restore_drive_km(b)
    extend(b)
    base._compute_odometers(b)
    base._fill_trip_odometers(b)

    payload = b.backup_json()
    payload["exportedAt"] = base.millis(datetime(2026, 7, 12, 9, 0))
    js = json.dumps(payload, indent=2, ensure_ascii=False)

    with zipfile.ZipFile(OUT_ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("backup.json", js)
        for path, data in b.media.items():
            z.writestr(path, data)

    per_vehicle = collections.Counter(s["vehicleId"] for s in b.sessions)
    print(f"Wrote {OUT_ZIP}")
    print(f"  vehicles : {len(b.vehicles)}")
    print(f"  trips    : {len(b.trips)}  (base {base_trips} + new {len(b.trips) - base_trips})")
    print(f"  sessions : {len(b.sessions)}  (base {base_sessions} + new "
          f"{len(b.sessions) - base_sessions})  per-vehicle={dict(per_vehicle)}")
    print(f"  media    : {len(b.media)} files "
          f"({sum(1 for k in b.media if k.startswith('vehicles/'))} photos, "
          f"{sum(1 for k in b.media if k.startswith('receipts/'))} receipts)")
    print(f"  zip size : {os.path.getsize(OUT_ZIP):,} bytes")


if __name__ == "__main__":
    main()
