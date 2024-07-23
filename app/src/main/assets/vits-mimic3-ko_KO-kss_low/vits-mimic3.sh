#!/usr/bin/env bash

set -ex

pip install iso639-lang

echo "LANG: $LANG"
echo "NAME: $NAME"

wget -q https://github.com/MycroftAI/mimic3-voices/raw/master/voices/$LANG/$NAME/generator.onnx
wget -q https://raw.githubusercontent.com/MycroftAI/mimic3-voices/master/voices/$LANG/$NAME/config.json
wget -q https://raw.githubusercontent.com/MycroftAI/mimic3-voices/master/voices/$LANG/$NAME/phonemes.txt

mv generator.onnx $LANG-$NAME.onnx
mv config.json $LANG-$NAME.onnx.json

cat >README.md <<EOF
# Introduction
#
This model is converted from the following repository.
https://github.com/MycroftAI/mimic3-voices/tree/master/voices/$LANG/$NAME
EOF

wget -qq https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2
tar xf espeak-ng-data.tar.bz2
rm espeak-ng-data.tar.bz2

pip install piper-phonemize onnx onnxruntime==1.16.0

python3 ./vits-mimic3.py
