# Binary assets (base64 parts)

The GitHub push API corrupts raw binaries, so the app's binary
assets are stored here as base64 split parts. Reassemble each
asset from the repo root with the command shown, then verify
its SHA-256. The live build keeps the real binaries in place
under `platform/app/src/main/res/`.

## platform/app/src/main/res/drawable-nodpi/product_sony_wi_c100.webp

```sh
cat platform/app/binary-assets/res/drawable-nodpi/product_sony_wi_c100.webp.b64.part* | tr -d '\n' | base64 -d > platform/app/src/main/res/drawable-nodpi/product_sony_wi_c100.webp
sha256sum platform/app/src/main/res/drawable-nodpi/product_sony_wi_c100.webp  # expect 47fdbeb34e2b92d26f7922da19da121e603a9513620da0ad1814689d6d04d132
```

## platform/app/src/main/res/mipmap-hdpi/ic_launcher.png

```sh
cat platform/app/binary-assets/res/mipmap-hdpi/ic_launcher.png.b64.part* | tr -d '\n' | base64 -d > platform/app/src/main/res/mipmap-hdpi/ic_launcher.png
sha256sum platform/app/src/main/res/mipmap-hdpi/ic_launcher.png  # expect 7fc9207060f8ff81c9c389b8a8220edde22562fbfd9af8674877bc8fafb19468
```

## platform/app/src/main/res/mipmap-mdpi/ic_launcher.png

```sh
cat platform/app/binary-assets/res/mipmap-mdpi/ic_launcher.png.b64.part* | tr -d '\n' | base64 -d > platform/app/src/main/res/mipmap-mdpi/ic_launcher.png
sha256sum platform/app/src/main/res/mipmap-mdpi/ic_launcher.png  # expect 27bbdcefe4837c4ee1e3f8b0c7f89b3624694f14fbf28579734cd1a3ebde193e
```

## platform/app/src/main/res/mipmap-xhdpi/ic_launcher.png

```sh
cat platform/app/binary-assets/res/mipmap-xhdpi/ic_launcher.png.b64.part* | tr -d '\n' | base64 -d > platform/app/src/main/res/mipmap-xhdpi/ic_launcher.png
sha256sum platform/app/src/main/res/mipmap-xhdpi/ic_launcher.png  # expect 27025f6e4c707abe3f200bf11f1d65d305af188481b8071438d2737b4a1e1ff6
```

## platform/app/src/main/res/mipmap-xxhdpi/ic_launcher.png

```sh
cat platform/app/binary-assets/res/mipmap-xxhdpi/ic_launcher.png.b64.part* | tr -d '\n' | base64 -d > platform/app/src/main/res/mipmap-xxhdpi/ic_launcher.png
sha256sum platform/app/src/main/res/mipmap-xxhdpi/ic_launcher.png  # expect 3e3215e4824ffa6cdc075435a9d0fc87f3d51cb5dd6d0fadb5d446c08a51cf35
```

## platform/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png

```sh
cat platform/app/binary-assets/res/mipmap-xxxhdpi/ic_launcher.png.b64.part* | tr -d '\n' | base64 -d > platform/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
sha256sum platform/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png  # expect 8ded5e1bf0392e73a4e42e52b9113a5b8b7cc3f63314dfdd56b8dce1511fa79b
```
