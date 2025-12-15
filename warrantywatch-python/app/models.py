from enum import Enum
from dataclasses import dataclass
from typing import Optional

class MinerType(str, Enum):
    BITMAIN = "BITMAIN"
    WHATSMINER = "WHATSMINER"
    CANAAN = "CANAAN"
    ALPHAMINER = "ALPHAMINER"
    AUTO = "AUTO"

@dataclass
class WarrantyDetails:
    type: MinerType
    warranty_date: str
    serial: str
