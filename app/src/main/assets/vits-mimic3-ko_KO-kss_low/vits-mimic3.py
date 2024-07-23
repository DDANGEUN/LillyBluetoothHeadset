#!/usr/bin/env python3

import json
import os
from typing import Any, Dict

import onnx
from iso639 import Lang


def add_meta_data(filename: str, meta_data: Dict[str, Any]):
    """Add meta data to an ONNX model. It is changed in-place.

    Args:
      filename:
        Filename of the ONNX model to be changed.
      meta_data:
        Key-value pairs.
    """
    model = onnx.load(filename)
    for key, value in meta_data.items():
        meta = model.metadata_props.add()
        meta.key = key
        meta.value = str(value)

    onnx.save(model, filename)


def generate_tokens():
    token2id = dict()
    with open("phonemes.txt") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            idx, token = line.split()
            if len(token) > 1:
                print(f"skip token {token}")
                continue
            token2id[token] = int(idx)
    token2id[" "] = 0

    with open("tokens.txt", "w", encoding="utf-8") as f:
        for s, i in token2id.items():
            f.write(f"{s} {i}\n")
    print("Generated tokens.txt")


def load_config(filename):
    with open(filename, "r", encoding="utf-8") as file:
        config = json.load(file)
    return config


def main():
    print("generate tokens")
    generate_tokens()

    lang = os.environ.get("LANG")
    name = os.environ.get("NAME")

    print(lang, name)
    config = load_config(f"{lang}-{name}.onnx.json")

    lang_iso = Lang(lang.split("_")[0])

    print("add model metadata")
    meta_data = {
        "model_type": "vits",
        "comment": "piper",  # must be piper for models from piper or mimic3
        "language": lang_iso.name,
        "voice": lang_iso.pt1,
        "has_espeak": 1,
        "n_speakers": config["model"]["n_speakers"],
        "sample_rate": config["audio"]["sample_rate"],
    }
    print(meta_data)
    m = f"{lang}-{name}.onnx"
    add_meta_data(m, meta_data)


main()
