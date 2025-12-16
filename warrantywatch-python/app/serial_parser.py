import re
from typing import Optional, Tuple
from app.models import MinerType

# Java: \d{2,4}[/-]\d{1,2}[/-]\d{1,2}
DATE_PATTERN = re.compile(r'\d{2,4}[/-]\d{1,2}[/-]\d{1,2}')

# Java: [A-Z0-9]{17,33}
SERIAL_PATTERN = re.compile(r'[A-Z0-9]{17,33}')

# Java: [A-Z0-9]{6}[A-Z]{2,5}([2-3][0-9])([0-5][0-9])[0-9]{7,11}
ALPHA_SERIAL_PATTERN = re.compile(r'[A-Z0-9]{6}[A-Z]{2,5}([2-3][0-9])([0-5][0-9])[0-9]{7,11}')

def is_serial_number(text: str) -> bool:
    """Checks if a string matches the general serial number pattern."""
    if not text:
        return False
    return bool(SERIAL_PATTERN.fullmatch(text.strip()))

def is_alpha_miner_serial(text: str) -> bool:
    """Checks if a string matches the Alpha Miner serial number pattern."""
    if not text:
        return False
    return bool(ALPHA_SERIAL_PATTERN.fullmatch(text.strip()))

def extract_date(text: str) -> Optional[str]:
    """Extracts the first date found in a string."""
    if not text:
        return None
    match = DATE_PATTERN.search(text)
    if match:
        return match.group(0)
    return None

def normalize_canaan_date(date_str: str) -> str:
    """Replaces / with - as seen in Java code."""
    if not date_str:
        return ""
    return date_str.replace("/", "-")
