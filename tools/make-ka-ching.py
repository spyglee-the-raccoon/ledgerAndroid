"""Synthesizes the "ka-ching" played after adding a transaction (iOS plays the system payment sound).

Usage:  python tools/make-ka-ching.py   -> app/src/main/res/raw/ka_ching.wav

A short filtered noise burst for the register's "ka", then a bell-like "ching" made of slightly
inharmonic partials with exponential decay. Plain Python, no dependencies; the output is the same
on every run.
"""
import math
import random
import struct
import wave
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw" / "ka_ching.wav"
RATE = 44_100
LENGTH = 0.75  # seconds

KA_START, KA_LENGTH = 0.0, 0.045
CHING_START = 0.07
# (frequency Hz, relative level, decay time s): a bright bell, upper partials die out first.
PARTIALS = [(2093, 1.0, 0.30), (2637, 0.6, 0.24), (3136, 0.5, 0.20), (4186, 0.35, 0.14), (5274, 0.25, 0.10), (6644, 0.15, 0.07)]


def sample(t, noise, state):
    value = 0.0
    if KA_START <= t < KA_START + KA_LENGTH:
        # Noise through a one-pole high-pass, with a fast attack and decay: a mechanical click.
        x = noise.uniform(-1, 1)
        state["hp"] = 0.6 * (state["hp"] + x - state["last"])
        state["last"] = x
        local = t - KA_START
        envelope = min(1.0, local / 0.002) * math.exp(-local / 0.012)
        value += 0.55 * state["hp"] * envelope
    if t >= CHING_START:
        local = t - CHING_START
        attack = min(1.0, local / 0.003)
        for frequency, level, decay in PARTIALS:
            value += 0.22 * level * attack * math.exp(-local / decay) * math.sin(2 * math.pi * frequency * local)
    return value


def main():
    noise = random.Random(7)
    state = {"hp": 0.0, "last": 0.0}
    frames = [sample(i / RATE, noise, state) for i in range(int(RATE * LENGTH))]
    peak = max(abs(v) for v in frames)
    fade = int(RATE * 0.02)
    data = bytearray()
    for i, v in enumerate(frames):
        if i >= len(frames) - fade:
            v *= (len(frames) - i) / fade
        data += struct.pack("<h", int(v / peak * 0.8 * 32767))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(bytes(data))
    print("Wrote", OUT)


if __name__ == "__main__":
    main()
