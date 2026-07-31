#!/usr/bin/env python3
"""
Generate a realistic EVSCT full-backup .zip for sharing with testers.

The output is byte-compatible with what the app's own "Settings -> Full backup
-> Save/Share" writes (schema v5): a single .zip containing

    backup.json        schema-versioned vehicles / trips / sessions / settings
    vehicles/<uuid>.jpg generated profile photos referenced by backup.json
    receipts/<uuid>.jpg generated receipt images referenced by sessions
    receipts/<uuid>.pdf a couple of PDF receipts (Pillow renders these)

Restoring it (Settings -> Full backup -> Restore from backup...) wipes the local
database and reinstalls this data inside one Room transaction, exactly as a
real device-to-device migration would.

The numbers are physically self-consistent so the in-app validation hints stay
quiet on every session:
  * odometer is monotonically increasing per vehicle (by session start),
  * battery end >= battery start,
  * effective $/kWh stays within 25% of the posted $/kWh (PER_KWH sessions),
  * effective $/min stays within 25% of the posted $/min (PER_MINUTE sessions),
  * effective average power never exceeds the posted station maximum,
  * duration, energy, and average power are derived from one another.

Run:  python3 generate_sample_backup.py
Out:  evsct-sample-backup.zip  (next to this script)
"""

from __future__ import annotations

import collections
import io
import json
import os
import random
import uuid
import zipfile
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from PIL import Image, ImageDraw, ImageFont

SCHEMA_VERSION = 5

# Stand-ins for the BuildConfig values a real export records. The app writes
# its versionName, its versionCode (the git commit count) and the short commit
# sha; these mark the file as generator-made instead.
SAMPLE_VERSION_NAME = "0.1.0"
SAMPLE_VERSION_CODE = 0
SAMPLE_GIT_SHA = "sample"
SEED = 20260628
EASTERN = timezone(timedelta(hours=-4))  # EDT; close enough for sample timestamps
OUT_ZIP = os.path.join(os.path.dirname(os.path.abspath(__file__)), "evsct-sample-backup.zip")

rng = random.Random(SEED)


def millis(dt: datetime) -> int:
    """Epoch millis for a naive local (Eastern) datetime."""
    return int(dt.replace(tzinfo=EASTERN).timestamp() * 1000)


def jitter(value: float, frac: float) -> float:
    return value * (1.0 + rng.uniform(-frac, frac))


# --------------------------------------------------------------------------- #
#  Image generation (vehicle profile photos + receipts)
# --------------------------------------------------------------------------- #

def _font(size: int):
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def _font_regular(size: int):
    path = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    if os.path.exists(path):
        return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def vehicle_photo_bytes(top_rgb, bottom_rgb, title: str, subtitle: str) -> bytes:
    """A clean vertical-gradient card with the vehicle name. Stands in for a
    real profile photo so the Vehicles screen isn't a sea of placeholders."""
    w, h = 1200, 800
    img = Image.new("RGB", (w, h))
    for y in range(h):
        t = y / (h - 1)
        r = int(top_rgb[0] + (bottom_rgb[0] - top_rgb[0]) * t)
        g = int(top_rgb[1] + (bottom_rgb[1] - top_rgb[1]) * t)
        b = int(top_rgb[2] + (bottom_rgb[2] - top_rgb[2]) * t)
        for x in range(w):
            img.putpixel((x, y), (r, g, b))
    draw = ImageDraw.Draw(img)
    # subtle EV "bolt" glyph
    draw.polygon(
        [(w - 250, 120), (w - 330, 360), (w - 250, 360), (w - 320, 600),
         (w - 150, 300), (w - 230, 300)],
        fill=(255, 255, 255, 40),
    )
    draw.text((70, h - 220), title, font=_font(96), fill=(255, 255, 255))
    draw.text((74, h - 110), subtitle, font=_font_regular(44), fill=(230, 240, 235))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=86)
    return buf.getvalue()


def receipt_image_bytes(lines: list[tuple[str, str]], header: str, sub: str) -> bytes:
    """A simple, legible 'photo of a receipt' so the receipt viewer and the
    'has attachment' row icon have something real to show."""
    w, h = 720, 1040
    img = Image.new("RGB", (w, h), (247, 247, 244))
    draw = ImageDraw.Draw(img)
    draw.rectangle([24, 24, w - 24, h - 24], outline=(210, 210, 205), width=2)
    draw.text((48, 56), header, font=_font(40), fill=(20, 60, 40))
    draw.text((48, 112), sub, font=_font_regular(26), fill=(90, 90, 90))
    draw.line([48, 170, w - 48, 170], fill=(200, 200, 195), width=2)
    y = 210
    for label, value in lines:
        draw.text((48, y), label, font=_font_regular(28), fill=(60, 60, 60))
        vfont = _font_regular(28)
        vw = draw.textlength(value, font=vfont)
        draw.text((w - 48 - vw, y), value, font=vfont, fill=(20, 20, 20))
        y += 52
    draw.line([48, y + 8, w - 48, y + 8], fill=(200, 200, 195), width=2)
    draw.text((48, y + 28), "Thank you — drive electric.", font=_font_regular(24),
              fill=(120, 120, 120))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=84)
    return buf.getvalue()


def receipt_pdf_bytes(lines: list[tuple[str, str]], header: str, sub: str) -> bytes:
    """Pillow saves a single-page PDF wrapping the rendered receipt image —
    a valid PDF any system viewer opens, exercising the app's PDF receipt path."""
    w, h = 720, 1040
    img = Image.new("RGB", (w, h), "white")
    draw = ImageDraw.Draw(img)
    draw.text((48, 56), header, font=_font(40), fill=(20, 60, 40))
    draw.text((48, 112), sub, font=_font_regular(26), fill=(90, 90, 90))
    draw.line([48, 170, w - 48, 170], fill=(200, 200, 195), width=2)
    y = 210
    for label, value in lines:
        draw.text((48, y), label, font=_font_regular(28), fill=(60, 60, 60))
        vfont = _font_regular(28)
        vw = draw.textlength(value, font=vfont)
        draw.text((w - 48 - vw, y), value, font=vfont, fill=(20, 20, 20))
        y += 52
    buf = io.BytesIO()
    img.save(buf, format="PDF", resolution=150.0)
    return buf.getvalue()


# --------------------------------------------------------------------------- #
#  Static reference data
# --------------------------------------------------------------------------- #

@dataclass
class Station:
    brand: str
    station: str
    city: str
    prov: str
    address: str
    lat: float
    lon: float
    country: str = "CA"


HOME = Station("Home", "Home charger", "Ottawa", "ON",
               "Barrhaven, Ottawa, ON", 45.2733, -75.7459)
WORK = Station("FLO", "Kanata North Business Park", "Ottawa", "ON",
               "340 March Rd, Kanata, ON", 45.3414, -75.9100)
OTT_PETROCAN = Station("Petro-Canada EV Fast Charge", "Ottawa South", "Ottawa", "ON",
                       "2900 Bank St, Ottawa, ON", 45.3360, -75.6420)
OTT_FLO_DC = Station("FLO", "Ottawa Train Station", "Ottawa", "ON",
                     "200 Tremblay Rd, Ottawa, ON", 45.4166, -75.6526)
OTT_CHARGEPOINT = Station("ChargePoint", "Lansdowne Park", "Ottawa", "ON",
                          "1015 Bank St, Ottawa, ON", 45.3990, -75.6830)

KINGSTON_SC = Station("Tesla Supercharger", "Kingston — Gardiners Rd", "Kingston", "ON",
                      "945 Gardiners Rd, Kingston, ON", 44.2520, -76.5630)
TRENTON_EC = Station("Electrify Canada", "ONroute Trenton North", "Trenton", "ON",
                     "Highway 401 ONroute, Trenton, ON", 44.1450, -77.5160)
PORTHOPE_IVY = Station("Ivy Charging Network", "ONroute Port Hope", "Port Hope", "ON",
                       "Highway 401 ONroute, Port Hope, ON", 43.9870, -78.3490)
MARKHAM_L2 = Station("ChargePoint", "Markham Civic Centre", "Markham", "ON",
                     "101 Town Centre Blvd, Markham, ON", 43.8590, -79.3370)

CASSELMAN_IVY = Station("Ivy Charging Network", "ONroute Casselman", "Casselman", "ON",
                        "Highway 417 ONroute, Casselman, ON", 45.3060, -75.0790)
BROSSARD_EC = Station("Electric Circuit", "Quartier DIX30", "Brossard", "QC",
                      "9350 Boul Leduc, Brossard, QC", 45.4500, -73.4300)
DRUMMOND_EC = Station("Electric Circuit", "BRCC Drummondville", "Drummondville", "QC",
                      "Autoroute 20, Drummondville, QC", 45.8830, -72.4830)
QC_EC = Station("Electric Circuit", "Sainte-Foy", "Québec", "QC",
                "Place Ste-Foy, Québec, QC", 46.7820, -71.2900)
QC_HOTEL = Station("Tesla Destination", "Hôtel Château Laurier", "Québec", "QC",
                   "1220 Place George-V O, Québec, QC", 46.8100, -71.2150)

TREMBLANT_EC = Station("Electric Circuit", "Station Mont-Tremblant", "Mont-Tremblant", "QC",
                       "3005 Chemin de la Chapelle, Mont-Tremblant, QC", 46.2095, -74.5855)
TREMBLANT_FLO = Station("FLO", "Tremblant Village Garage", "Mont-Tremblant", "QC",
                        "1000 Chemin des Voyageurs, Mont-Tremblant, QC", 46.2090, -74.5870)

PEMBROKE_PC = Station("Petro-Canada EV Fast Charge", "Pembroke", "Pembroke", "ON",
                      "1100 Pembroke St W, Pembroke, ON", 45.8270, -77.1120)
RENFREW_FLO = Station("FLO", "Renfrew O'Brien Rd", "Renfrew", "ON",
                      "760 O'Brien Rd, Renfrew, ON", 45.4720, -76.6830)
COTTAGE_L1 = Station("Cottage", "Lake-side cabin", "Whitney", "ON",
                     "Galeairy Lake, Whitney, ON", 45.5060, -78.2390)

PLATTSBURGH_SC = Station("Tesla Supercharger", "Plattsburgh", "Plattsburgh", "NY",
                         "60 Smithfield Blvd, Plattsburgh, NY", 44.6995, -73.4670, "US")
LAKEGEORGE_EA = Station("Electrify America", "Lake George — Route 9", "Lake George", "NY",
                        "1454 US-9, Lake George, NY", 43.4250, -73.7100, "US")
LAKEPLACID_L2 = Station("ChargePoint", "Lake Placid Main St", "Lake Placid", "NY",
                        "2317 Main St, Lake Placid, NY", 44.2790, -73.9790, "US")
CIRCLEK_CORNWALL = Station("Circle K Recharge", "Cornwall", "Cornwall", "ON",
                           "1380 Brookdale Ave, Cornwall, ON", 45.0410, -74.7280)


# --------------------------------------------------------------------------- #
#  Builders that produce backup.json records
# --------------------------------------------------------------------------- #

class Builder:
    def __init__(self):
        self.vehicles: list[dict] = []
        self.trips: list[dict] = []
        self.sessions: list[dict] = []
        self.media: dict[str, bytes] = {}   # zip path -> bytes
        self._sid = 0
        self._base_odo: dict[int, float] = {}

    # --- vehicles ---------------------------------------------------------- #
    def add_vehicle(self, vid, name, year, make, model, trim, cap, rng_km, vin,
                    notes, photo_bytes, is_default, created, base_odo):
        img_name = f"{uuid.uuid4()}.jpg"
        self.media[f"vehicles/{img_name}"] = photo_bytes
        self.vehicles.append({
            "id": vid, "name": name, "year": year, "make": make, "model": model,
            "trim": trim, "batteryCapacityKwh": cap, "nominalRangeKm": rng_km,
            "vin": vin, "notes": notes, "imageFile": img_name,
            "isDefault": is_default,
            "createdAt": millis(created), "updatedAt": millis(created),
        })
        self._base_odo[vid] = base_odo

    # --- trips ------------------------------------------------------------- #
    def add_trip(self, tid, name, start, end, start_odo, end_odo, notes, color):
        self.trips.append({
            "id": tid, "name": name,
            "startDate": millis(start), "endDate": millis(end),
            "startOdometerKm": start_odo, "endOdometerKm": end_odo,
            "notes": notes, "pinColor": color, "createdAt": millis(start),
        })

    # --- sessions ---------------------------------------------------------- #
    def add_session(self, vehicle_id, when, station: Station, ctype, drive_km,
                    batt_start, batt_end, *, pricing="PER_KWH", posted_kwh=None,
                    posted_min=None, posted_max_kw=None, avg_kw=None, currency="CAD",
                    trip_id=None, tags=None, notes=None, wait=None, stall=None,
                    continues=False, eff_noise=0.0, free=False, flat_cost=None,
                    receipts=None, with_geo=True):
        self._sid += 1
        sid = self._sid

        cap = next(v["batteryCapacityKwh"] for v in self.vehicles if v["id"] == vehicle_id)
        # Odometer is filled in chronologically by _compute_odometers() once
        # every session exists — sessions are added out of date order, so
        # accumulating here would make the odometer jump around by start time.

        delta = max(0, batt_end - batt_start)
        eff = 0.95 if ctype == "DC_FAST" else (0.88 if ctype == "AC_L2" else 0.85)
        energy = round((delta / 100.0) * cap / eff, 2)

        # derive duration from energy + average power so eff power == avg_kw
        if avg_kw is None:
            avg_kw = {"DC_FAST": 70.0, "AC_L2": 7.4, "AC_L1": 1.4}[ctype]
        duration_s = int(round(energy / avg_kw * 3600.0)) if energy > 0 else None
        duration_min = (duration_s / 60.0) if duration_s else 0.0

        # cost by pricing model
        if free or pricing == "FREE":
            cost = 0.0
        elif pricing == "PER_KWH":
            base = posted_kwh if posted_kwh is not None else 0.40
            cost = round(energy * base * (1.0 + eff_noise), 2)
        elif pricing == "PER_MINUTE":
            base = posted_min if posted_min is not None else 0.30
            cost = round(duration_min * base * (1.0 + eff_noise), 2)
        elif pricing == "FLAT":
            cost = round(flat_cost if flat_cost is not None else 12.0, 2)
        elif pricing == "HYBRID":
            # connection fee + per-minute; posted rates left null so the
            # posted-vs-effective hint can't false-positive on the blended rate
            cost = round((flat_cost or 1.0) + duration_min * (posted_min or 0.30), 2)
        else:
            cost = round(energy * 0.40, 2)

        sess = {
            "id": sid,
            "sessionStart": millis(when),
            "durationSeconds": duration_s,
            "waitTimeMinutes": wait,
            "odometerKm": None,          # set by _compute_odometers()
            "_driveKm": round(drive_km, 1),
            "energyKwh": energy if energy > 0 else None,
            "totalCost": cost,
            "currency": currency,
            "postedEnergyPricePerKwh": posted_kwh if pricing == "PER_KWH" else None,
            "postedTimeRatePerMin": posted_min if pricing == "PER_MINUTE" else None,
            "postedMaxPowerKw": posted_max_kw,
            "batteryStartPct": batt_start,
            "batteryEndPct": batt_end,
            "chargingType": ctype,
            "pricingModel": pricing,
            "brand": station.brand,
            "locationCity": station.city,
            "locationProvince": station.prov,
            "locationAddress": station.address,
            "stationName": station.station,
            "stallName": stall,
            "tripId": trip_id,
            "vehicleId": vehicle_id,
            "notes": notes,
            "tags": tags,
            "receipts": receipts or [],
            "receiptFile": (receipts[0]["file"] if receipts else None),
            "latitude": round(jitter(station.lat, 0.0002), 6) if with_geo else None,
            "longitude": round(jitter(station.lon, 0.0002), 6) if with_geo else None,
            "continuesPrevious": continues,
            "createdAt": millis(when + timedelta(minutes=(duration_min or 30) + 5)),
            "updatedAt": millis(when + timedelta(minutes=(duration_min or 30) + 5)),
        }
        self.sessions.append(sess)
        return sess

    # --- receipts helper --------------------------------------------------- #
    def make_receipt(self, kind, header, sub, lines, original_name):
        name = f"{uuid.uuid4()}.{kind}"
        if kind == "pdf":
            self.media[f"receipts/{name}"] = receipt_pdf_bytes(lines, header, sub)
        else:
            self.media[f"receipts/{name}"] = receipt_image_bytes(lines, header, sub)
        return {"file": name, "originalName": original_name}

    # --- output ------------------------------------------------------------ #
    def backup_json(self) -> dict:
        return {
            "schemaVersion": SCHEMA_VERSION,
            "exportedAt": millis(datetime(2026, 6, 28, 9, 15)),
            # Build provenance, matching what BackupIo now stamps on every
            # export. Deliberately synthetic rather than a plausible-looking
            # sha: anyone debugging a restore should be able to tell at a
            # glance that this file came from the generator, not a device.
            "appVersionName": SAMPLE_VERSION_NAME,
            "appVersionCode": SAMPLE_VERSION_CODE,
            "gitSha": SAMPLE_GIT_SHA,
            "settings": {},
            "vehicles": self.vehicles,
            "trips": self.trips,
            "sessions": self.sessions,
        }


# --------------------------------------------------------------------------- #
#  Compose the dataset
# --------------------------------------------------------------------------- #

def money(x):
    return f"${x:,.2f}"


def build() -> Builder:
    b = Builder()

    # ---- Vehicles -------------------------------------------------------- #
    b.add_vehicle(
        1, "Model Y", 2023, "Tesla", "Model Y", "Long Range AWD",
        75.0, 500, "5YJYGDEE9PF000142",
        "Daily driver. 20\" induction wheels, tow hitch. Heat pump handles "
        "Ottawa winters fine but range drops ~30% below -15°C.",
        vehicle_photo_bytes((26, 41, 38), (12, 84, 64), "Model Y", "2023 Tesla · Long Range AWD"),
        True, datetime(2025, 3, 28, 18, 0), base_odo=17850.0,
    )
    b.add_vehicle(
        2, "Ioniq 5", 2022, "Hyundai", "IONIQ 5", "Preferred LR AWD",
        74.0, 414, "KM8KRDDF1NU012783",
        "800V architecture — peaks ~180 kW on a 350 kW stall when preconditioned. "
        "Second household car, mostly Carolyn's commute.",
        vehicle_photo_bytes((30, 40, 60), (40, 120, 150), "Ioniq 5", "2022 Hyundai · Preferred LR AWD"),
        False, datetime(2025, 3, 28, 18, 5), base_odo=31480.0,
    )
    b.add_vehicle(
        3, "Bolt", 2021, "Chevrolet", "Bolt EV", "Premier",
        65.0, 417, "1G1FZ6S00M4100337",
        "55 kW DC ceiling, so road trips need patience. Great around-town runabout "
        "and the cottage car.",
        vehicle_photo_bytes((50, 30, 30), (150, 90, 40), "Bolt EV", "2021 Chevrolet · Premier"),
        False, datetime(2025, 3, 28, 18, 10), base_odo=52390.0,
    )

    # ---- Trips ----------------------------------------------------------- #
    # (odometer windows are filled in after sessions are generated where it
    #  matters; here we set plausible spans up front.)
    b.add_trip(1, "Toronto — May long weekend",
               datetime(2025, 5, 17, 7, 0), datetime(2025, 5, 19, 20, 0),
               18250.0, 19180.0,
               "Victoria Day weekend down the 401. Two Supercharger stops each way.",
               "RED")
    b.add_trip(2, "Québec City road trip",
               datetime(2025, 8, 8, 8, 0), datetime(2025, 8, 12, 19, 0),
               12880.0, 13920.0,
               "Ioniq 5 through Montréal to Québec City. Electric Circuit the whole way.",
               "AZURE")
    b.add_trip(3, "Algonquin cottage week",
               datetime(2025, 7, 19, 9, 0), datetime(2025, 7, 26, 17, 0),
               21450.0, 22060.0,
               "Bolt up to Galeairy Lake. Trickle charge at the cabin, FLO/Petro-Can on the way.",
               "GREEN")
    b.add_trip(4, "Lake Placid (NY)",
               datetime(2025, 10, 11, 7, 30), datetime(2025, 10, 13, 19, 0),
               24010.0, 24560.0,
               "Cross-border fall colours. Sessions billed in USD.",
               "ORANGE")
    b.add_trip(5, "Mont-Tremblant ski weekend",
               datetime(2026, 2, 13, 7, 0), datetime(2026, 2, 15, 18, 0),
               31180.0, 31720.0,
               "Cold-weather test — watch the kWh-per-km climb below -20°C.",
               "VIOLET")

    # ===================================================================== #
    #  VEHICLE 1 — Tesla Model Y  (default daily driver)
    # ===================================================================== #
    v = 1
    # Regular home charging cadence + town errands, March-onward 2025.
    home_dates = [
        datetime(2025, 4, 2, 22, 30), datetime(2025, 4, 9, 21, 45),
        datetime(2025, 4, 16, 23, 0), datetime(2025, 4, 24, 22, 0),
        datetime(2025, 5, 1, 21, 30), datetime(2025, 5, 8, 22, 15),
        datetime(2025, 6, 3, 22, 0), datetime(2025, 6, 12, 23, 10),
        datetime(2025, 6, 24, 21, 50), datetime(2025, 7, 6, 22, 30),
        datetime(2025, 9, 2, 22, 0), datetime(2025, 9, 15, 21, 40),
        datetime(2025, 11, 4, 22, 20), datetime(2025, 12, 1, 23, 0),
        datetime(2026, 1, 7, 22, 30), datetime(2026, 3, 3, 22, 10),
        datetime(2026, 4, 9, 22, 0), datetime(2026, 5, 12, 21, 45),
        datetime(2026, 6, 9, 22, 30), datetime(2026, 6, 22, 23, 0),
    ]
    for i, d in enumerate(home_dates):
        bs = rng.randint(30, 52)
        be = rng.choice([80, 85, 90, 90, 100])
        b.add_session(
            v, d, HOME, "AC_L2", drive_km=rng.uniform(140, 360),
            batt_start=bs, batt_end=be, pricing="PER_KWH",
            posted_kwh=0.103, posted_max_kw=11.5, avg_kw=jitter(11.0, 0.03),
            tags="home", eff_noise=rng.uniform(-0.03, 0.03),
            notes=("Off-peak overnight." if i % 4 else None),
        )

    # A couple of around-town public top-ups
    b.add_session(v, datetime(2025, 6, 18, 12, 30), OTT_PETROCAN, "DC_FAST",
                  drive_km=120, batt_start=24, batt_end=78, pricing="PER_KWH",
                  posted_kwh=0.66, posted_max_kw=200, avg_kw=118.0,
                  tags="errand", stall="Stall 2", wait=5,
                  notes="Quick splash before the airport run.")
    b.add_session(v, datetime(2025, 9, 27, 14, 0), OTT_CHARGEPOINT, "AC_L2",
                  drive_km=70, batt_start=58, batt_end=82, pricing="FREE",
                  posted_max_kw=7.7, avg_kw=7.2, tags="free,errand",
                  notes="Free L2 while at the Lansdowne farmers' market.")

    # ---- Trip 1: Toronto (Model Y) ----
    rec_kingston = b.make_receipt(
        "jpg", "Tesla Supercharger", "Kingston, ON · May 17, 2025",
        [("Session", "DC Fast"), ("Energy", "47.8 kWh"),
         ("Rate", "$0.42 / kWh"), ("Duration", "27 min"), ("Total", "$20.08")],
        "tesla-kingston-2025-05-17.jpg")
    b.add_session(v, datetime(2025, 5, 17, 9, 20), KINGSTON_SC, "DC_FAST",
                  drive_km=195, batt_start=18, batt_end=80, pricing="PER_KWH",
                  posted_kwh=0.42, posted_max_kw=250, avg_kw=108.0, trip_id=1,
                  tags="road trip,supercharger", stall="Stall 6",
                  notes="Preconditioned on the way in — ramped to 190 kW.",
                  receipts=[rec_kingston])
    b.add_session(v, datetime(2025, 5, 17, 11, 35), PORTHOPE_IVY, "DC_FAST",
                  drive_km=175, batt_start=28, batt_end=72, pricing="PER_MINUTE",
                  posted_min=0.33, posted_max_kw=150, avg_kw=92.0, trip_id=1,
                  tags="road trip", stall="Charger 3", wait=10, continues=True,
                  notes="One of two stalls ICE'd — short wait for the cable.")
    b.add_session(v, datetime(2025, 5, 17, 14, 10), MARKHAM_L2, "AC_L2",
                  drive_km=70, batt_start=55, batt_end=90, pricing="PER_KWH",
                  posted_kwh=0.18, posted_max_kw=7.7, avg_kw=7.0, trip_id=1,
                  tags="road trip,destination",
                  notes="Topped up at the hotel while we walked Main St Unionville.")
    rec_kingston_pdf = b.make_receipt(
        "pdf", "Tesla Supercharger", "Kingston, ON · May 19, 2025",
        [("Session", "DC Fast"), ("Energy", "44.1 kWh"),
         ("Rate", "$0.44 / kWh"), ("Duration", "25 min"), ("Total", "$19.40")],
        "tesla-kingston-return.pdf")
    b.add_session(v, datetime(2025, 5, 19, 16, 40), KINGSTON_SC, "DC_FAST",
                  drive_km=315, batt_start=22, batt_end=78, pricing="PER_KWH",
                  posted_kwh=0.44, posted_max_kw=250, avg_kw=106.0, trip_id=1,
                  tags="road trip,supercharger", stall="Stall 9",
                  notes="Return leg — peak rate window.", receipts=[rec_kingston_pdf])

    # ---- Trip 4: Lake Placid (USD) (Model Y) ----
    rec_plattsburgh = b.make_receipt(
        "jpg", "Tesla Supercharger", "Plattsburgh, NY · Oct 11, 2025",
        [("Session", "DC Fast"), ("Energy", "41.0 kWh"),
         ("Rate", "US$0.36 / kWh"), ("Duration", "23 min"), ("Total", "US$14.76")],
        "tesla-plattsburgh.jpg")
    b.add_session(v, datetime(2025, 10, 11, 10, 15), PLATTSBURGH_SC, "DC_FAST",
                  drive_km=215, batt_start=20, batt_end=75, pricing="PER_KWH",
                  posted_kwh=0.36, posted_max_kw=250, avg_kw=104.0, currency="USD",
                  trip_id=4, tags="road trip,usa,supercharger", stall="Stall 4",
                  notes="First US Supercharger of the trip. Border was quick.",
                  receipts=[rec_plattsburgh])
    b.add_session(v, datetime(2025, 10, 11, 13, 5), LAKEGEORGE_EA, "DC_FAST",
                  drive_km=120, batt_start=34, batt_end=80, pricing="PER_KWH",
                  posted_kwh=0.48, posted_max_kw=350, avg_kw=96.0, currency="USD",
                  trip_id=4, tags="road trip,usa", stall="Charger 2", wait=5,
                  notes="EA on the NUSA adapter — handshake took two tries.")
    b.add_session(v, datetime(2025, 10, 12, 9, 30), LAKEPLACID_L2, "AC_L2",
                  drive_km=85, batt_start=52, batt_end=95, pricing="FLAT",
                  posted_max_kw=7.7, avg_kw=6.8, currency="USD", flat_cost=5.00,
                  trip_id=4, tags="road trip,usa,destination",
                  notes="Flat $5 municipal lot L2 overnight by the Olympic oval.")
    b.add_session(v, datetime(2025, 10, 13, 15, 20), PLATTSBURGH_SC, "DC_FAST",
                  drive_km=130, batt_start=26, batt_end=82, pricing="PER_KWH",
                  posted_kwh=0.37, posted_max_kw=250, avg_kw=101.0, currency="USD",
                  trip_id=4, tags="road trip,usa,supercharger", stall="Stall 1",
                  continues=True, notes="Charged to 82% to clear customs and reach home.")

    # ---- Trip 5: Tremblant ski (winter test) (Model Y) ----
    b.add_session(v, datetime(2026, 2, 13, 9, 0), CASSELMAN_IVY, "DC_FAST",
                  drive_km=95, batt_start=30, batt_end=72, pricing="PER_MINUTE",
                  posted_min=0.33, posted_max_kw=150, avg_kw=70.0, trip_id=5,
                  tags="road trip,winter test", stall="Charger 1",
                  notes="-22°C. Battery cold despite preconditioning — slow ramp.")
    b.add_session(v, datetime(2026, 2, 13, 12, 30), TREMBLANT_EC, "DC_FAST",
                  drive_km=190, batt_start=22, batt_end=80, pricing="PER_MINUTE",
                  posted_min=0.32, posted_max_kw=100, avg_kw=58.0, trip_id=5,
                  tags="road trip,winter test", stall="BRCC",
                  notes="Consumption hit 28 kWh/100km on the climb up.")
    b.add_session(v, datetime(2026, 2, 14, 8, 45), TREMBLANT_FLO, "AC_L2",
                  drive_km=20, batt_start=48, batt_end=90, pricing="PER_KWH",
                  posted_kwh=0.21, posted_max_kw=7.7, avg_kw=6.9, trip_id=5,
                  tags="road trip,winter test,destination",
                  notes="Heated garage L2 overnight — battery warm for morning runs.")
    b.add_session(v, datetime(2026, 2, 15, 15, 0), CASSELMAN_IVY, "DC_FAST",
                  drive_km=300, batt_start=24, batt_end=78, pricing="PER_MINUTE",
                  posted_min=0.33, posted_max_kw=150, avg_kw=74.0, trip_id=5,
                  tags="road trip,winter test", stall="Charger 2", continues=True,
                  notes="Return leg warmed up nicely — better ramp than Friday.")

    # ===================================================================== #
    #  VEHICLE 2 — Hyundai Ioniq 5  (commuter + Québec trip)
    # ===================================================================== #
    v = 2
    home2 = [
        datetime(2025, 4, 4, 21, 0), datetime(2025, 4, 11, 22, 30),
        datetime(2025, 4, 22, 21, 15), datetime(2025, 5, 6, 22, 0),
        datetime(2025, 5, 14, 21, 30), datetime(2025, 6, 5, 22, 45),
        datetime(2025, 6, 20, 21, 0), datetime(2025, 7, 3, 22, 30),
        datetime(2025, 9, 9, 22, 0), datetime(2025, 9, 22, 21, 20),
        datetime(2025, 10, 28, 22, 0), datetime(2025, 11, 18, 22, 30),
        datetime(2025, 12, 16, 23, 0), datetime(2026, 1, 20, 22, 15),
        datetime(2026, 3, 17, 21, 45), datetime(2026, 4, 21, 22, 0),
        datetime(2026, 5, 19, 22, 30), datetime(2026, 6, 16, 21, 50),
    ]
    for i, d in enumerate(home2):
        bs = rng.randint(28, 50)
        be = rng.choice([80, 85, 90, 100])
        b.add_session(
            v, d, HOME, "AC_L2", drive_km=rng.uniform(120, 300),
            batt_start=bs, batt_end=be, pricing="PER_KWH",
            posted_kwh=0.103, posted_max_kw=10.9, avg_kw=jitter(9.6, 0.03),
            tags="home,commute", eff_noise=rng.uniform(-0.03, 0.03),
        )

    # Workplace free L2 a few times
    for d in (datetime(2025, 5, 21, 9, 30), datetime(2025, 10, 2, 9, 15),
              datetime(2026, 3, 25, 9, 20)):
        b.add_session(v, d, WORK, "AC_L2", drive_km=rng.uniform(40, 70),
                      batt_start=rng.randint(45, 60), batt_end=rng.choice([78, 82, 85]),
                      pricing="FREE", posted_max_kw=7.7, avg_kw=6.9,
                      tags="work charge,free", notes="Workplace charger — first-come.")

    # Around-town DC top-up + one HYBRID demo on the way to Cornwall
    b.add_session(v, datetime(2025, 7, 30, 13, 0), OTT_FLO_DC, "DC_FAST",
                  drive_km=160, batt_start=22, batt_end=80, pricing="PER_KWH",
                  posted_kwh=0.40, posted_max_kw=100, avg_kw=88.0,
                  tags="errand", stall="Charger A", wait=0,
                  notes="Quick FLO top-up downtown.")
    b.add_session(v, datetime(2025, 11, 8, 11, 30), CIRCLEK_CORNWALL, "DC_FAST",
                  drive_km=115, batt_start=26, batt_end=74, pricing="HYBRID",
                  posted_min=0.30, posted_max_kw=180, avg_kw=120.0, flat_cost=1.00,
                  tags="errand", stall="DC-1",
                  notes="Circle K Recharge: $1 connection fee + $0.30/min blended.")

    # ---- Trip 2: Québec City (Ioniq 5) ----
    rec_brossard = b.make_receipt(
        "pdf", "Electric Circuit", "Brossard, QC · Aug 8, 2025",
        [("Session", "DC Fast"), ("Duration", "31 min"),
         ("Rate", "$0.32 / min"), ("Energy", "44.6 kWh"), ("Total", "$9.92")],
        "electric-circuit-brossard.pdf")
    b.add_session(v, datetime(2025, 8, 8, 10, 30), BROSSARD_EC, "DC_FAST",
                  drive_km=200, batt_start=20, batt_end=78, pricing="PER_MINUTE",
                  posted_min=0.32, posted_max_kw=100, avg_kw=85.0, trip_id=2,
                  tags="road trip", stall="BRCC 2",
                  notes="DIX30 mall stop — lunch while it charged.",
                  receipts=[rec_brossard])
    b.add_session(v, datetime(2025, 8, 8, 14, 0), DRUMMOND_EC, "DC_FAST",
                  drive_km=145, batt_start=30, batt_end=82, pricing="PER_MINUTE",
                  posted_min=0.32, posted_max_kw=100, avg_kw=80.0, trip_id=2,
                  tags="road trip", stall="BRCC 1", continues=True,
                  notes="Autoroute 20 halfway point.")
    b.add_session(v, datetime(2025, 8, 8, 17, 30), QC_HOTEL, "AC_L2",
                  drive_km=160, batt_start=40, batt_end=100, pricing="FREE",
                  posted_max_kw=10.9, avg_kw=9.4, trip_id=2,
                  tags="road trip,destination,free",
                  notes="Hotel Tesla Destination L2 — free for guests, charged to 100%.")
    b.add_session(v, datetime(2025, 8, 11, 11, 0), QC_EC, "DC_FAST",
                  drive_km=60, batt_start=46, batt_end=85, pricing="PER_MINUTE",
                  posted_min=0.32, posted_max_kw=100, avg_kw=78.0, trip_id=2,
                  tags="road trip", stall="BRCC 3",
                  notes="Topped up at Place Ste-Foy before heading back west.")
    b.add_session(v, datetime(2025, 8, 11, 15, 30), DRUMMOND_EC, "DC_FAST",
                  drive_km=240, batt_start=22, batt_end=80, pricing="PER_MINUTE",
                  posted_min=0.32, posted_max_kw=100, avg_kw=82.0, trip_id=2,
                  tags="road trip", stall="BRCC 2", continues=True,
                  notes="Return leg — busy but no wait.")

    # ===================================================================== #
    #  VEHICLE 3 — Chevrolet Bolt EV  (runabout + cottage)
    # ===================================================================== #
    v = 3
    home3 = [
        datetime(2025, 4, 6, 20, 0), datetime(2025, 4, 13, 21, 0),
        datetime(2025, 4, 27, 20, 30), datetime(2025, 5, 11, 21, 0),
        datetime(2025, 6, 1, 20, 45), datetime(2025, 6, 15, 21, 30),
        datetime(2025, 6, 29, 20, 0), datetime(2025, 8, 3, 21, 0),
        datetime(2025, 9, 7, 20, 30), datetime(2025, 9, 28, 21, 0),
        datetime(2025, 10, 19, 20, 45), datetime(2025, 11, 23, 21, 0),
        datetime(2025, 12, 21, 21, 30), datetime(2026, 1, 25, 20, 30),
        datetime(2026, 2, 22, 21, 0), datetime(2026, 4, 5, 20, 45),
        datetime(2026, 5, 24, 21, 0), datetime(2026, 6, 14, 20, 30),
    ]
    for i, d in enumerate(home3):
        bs = rng.randint(30, 55)
        be = rng.choice([80, 85, 90, 100])
        # Bolt at home: mostly 7.7 kW L2, occasionally a slow 1.4 kW L1 on the
        # 120V garage outlet when the L2 is in use by the other car.
        if i % 5 == 4:
            b.add_session(v, d, HOME, "AC_L1", drive_km=rng.uniform(30, 90),
                          batt_start=bs, batt_end=min(be, bs + 20), pricing="PER_KWH",
                          posted_kwh=0.103, posted_max_kw=1.4, avg_kw=1.35,
                          tags="home", notes="120V trickle overnight — L2 was taken.")
        else:
            b.add_session(v, d, HOME, "AC_L2", drive_km=rng.uniform(90, 220),
                          batt_start=bs, batt_end=be, pricing="PER_KWH",
                          posted_kwh=0.103, posted_max_kw=7.7, avg_kw=jitter(7.4, 0.03),
                          tags="home", eff_noise=rng.uniform(-0.03, 0.03))

    # Town DC + free L2
    b.add_session(v, datetime(2025, 5, 28, 13, 0), OTT_PETROCAN, "DC_FAST",
                  drive_km=130, batt_start=18, batt_end=72, pricing="PER_KWH",
                  posted_kwh=0.66, posted_max_kw=100, avg_kw=48.0,
                  tags="errand", stall="Stall 1", wait=8,
                  notes="Bolt caps ~52 kW so this took a while.")
    b.add_session(v, datetime(2025, 9, 13, 10, 30), OTT_CHARGEPOINT, "AC_L2",
                  drive_km=60, batt_start=50, batt_end=88, pricing="FREE",
                  posted_max_kw=7.7, avg_kw=6.9, tags="free,errand",
                  notes="Free municipal L2 during the Saturday market.")

    # ---- Trip 3: Algonquin cottage (Bolt) ----
    rec_renfrew = b.make_receipt(
        "jpg", "FLO", "Renfrew, ON · Jul 19, 2025",
        [("Session", "DC Fast"), ("Energy", "34.2 kWh"),
         ("Rate", "$0.40 / kWh"), ("Duration", "42 min"), ("Total", "$13.68")],
        "flo-renfrew.jpg")
    b.add_session(v, datetime(2025, 7, 19, 10, 0), RENFREW_FLO, "DC_FAST",
                  drive_km=100, batt_start=24, batt_end=78, pricing="PER_KWH",
                  posted_kwh=0.40, posted_max_kw=50, avg_kw=46.0, trip_id=3,
                  tags="road trip,cottage", stall="DC",
                  notes="Last fast charger before the park — filled up.",
                  receipts=[rec_renfrew])
    b.add_session(v, datetime(2025, 7, 19, 13, 30), COTTAGE_L1, "AC_L1",
                  drive_km=120, batt_start=40, batt_end=62, pricing="FREE",
                  posted_max_kw=1.4, avg_kw=1.3, trip_id=3,
                  tags="road trip,cottage,free", continues=True,
                  notes="120V outlet on the boathouse — ~8 km/h, but a week is a week.")
    b.add_session(v, datetime(2025, 7, 26, 11, 0), PEMBROKE_PC, "DC_FAST",
                  drive_km=95, batt_start=28, batt_end=80, pricing="PER_KWH",
                  posted_kwh=0.66, posted_max_kw=100, avg_kw=49.0, trip_id=3,
                  tags="road trip,cottage", stall="Stall 2",
                  notes="Petro-Canada in Pembroke on the way home.")

    # A few more Bolt town sessions to round out 'a couple dozen'
    b.add_session(v, datetime(2025, 11, 30, 14, 0), OTT_FLO_DC, "DC_FAST",
                  drive_km=110, batt_start=22, batt_end=70, pricing="PER_KWH",
                  posted_kwh=0.40, posted_max_kw=50, avg_kw=44.0,
                  tags="errand", stall="Charger B",
                  notes="Cold day — DC ramp was sluggish.")
    b.add_session(v, datetime(2026, 5, 2, 12, 30), RENFREW_FLO, "DC_FAST",
                  drive_km=150, batt_start=26, batt_end=76, pricing="PER_KWH",
                  posted_kwh=0.40, posted_max_kw=50, avg_kw=47.0,
                  tags="errand", stall="DC", notes="Day trip to the valley.")

    # Resolve odometers chronologically, then derive trip odometer windows
    # from the sessions actually assigned to each trip.
    _compute_odometers(b)
    _fill_trip_odometers(b)
    return b


def _compute_odometers(b: Builder):
    """Assign a monotonically-increasing odometer per vehicle in session-start
    order, starting from each vehicle's base reading and adding the per-session
    distance-since-last-charge. Keeps the app's 'odometer went backward' hint
    quiet no matter what order sessions were appended in."""
    byv = collections.defaultdict(list)
    for s in b.sessions:
        byv[s["vehicleId"]].append(s)
    for vid, lst in byv.items():
        lst.sort(key=lambda s: s["sessionStart"])
        odo = b._base_odo.get(vid, 0.0)
        for s in lst:
            odo += s.pop("_driveKm", 0.0)
            s["odometerKm"] = round(odo, 1)


def _fill_trip_odometers(b: Builder):
    for trip in b.trips:
        tid = trip["id"]
        odos = sorted(s["odometerKm"] for s in b.sessions
                      if s["tripId"] == tid and s["odometerKm"] is not None)
        if len(odos) >= 2:
            # start a touch before the first charge, end a touch after the last
            trip["startOdometerKm"] = round(odos[0] - rng.uniform(20, 60), 1)
            trip["endOdometerKm"] = round(odos[-1] + rng.uniform(40, 120), 1)


# --------------------------------------------------------------------------- #
#  Write the zip
# --------------------------------------------------------------------------- #

def main():
    b = build()
    payload = b.backup_json()
    js = json.dumps(payload, indent=2, ensure_ascii=False)

    with zipfile.ZipFile(OUT_ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("backup.json", js)
        for path, data in b.media.items():
            z.writestr(path, data)

    # quick summary
    n_sessions = len(b.sessions)
    per_vehicle = {}
    for s in b.sessions:
        per_vehicle[s["vehicleId"]] = per_vehicle.get(s["vehicleId"], 0) + 1
    print(f"Wrote {OUT_ZIP}")
    print(f"  vehicles : {len(b.vehicles)}")
    print(f"  trips    : {len(b.trips)}")
    print(f"  sessions : {n_sessions}  per-vehicle={per_vehicle}")
    print(f"  media    : {len(b.media)} files "
          f"({sum(1 for k in b.media if k.startswith('vehicles/'))} photos, "
          f"{sum(1 for k in b.media if k.startswith('receipts/'))} receipts)")
    print(f"  zip size : {os.path.getsize(OUT_ZIP):,} bytes")


if __name__ == "__main__":
    main()
