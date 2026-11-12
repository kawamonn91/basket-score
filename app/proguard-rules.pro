# Compose と AndroidX は、ライブラリ側の consumer rules で足りる。
# ここにはアプリ固有の指定だけを書く。

# クラッシュしたときのスタックトレースを読めるように、行番号を残す
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
