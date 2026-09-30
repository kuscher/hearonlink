#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")"
C=$HOME/.cache/android
L=libs/hiddenapibypass-6.1.jar
[[ -f $L ]] || { mkdir -p libs; curl -sfL -o /tmp/hab.aar https://repo1.maven.org/maven2/org/lsposed/hiddenapibypass/hiddenapibypass/6.1/hiddenapibypass-6.1.aar && unzip -p /tmp/hab.aar classes.jar > $L; }
OUT=build; rm -rf $OUT; mkdir -p $OUT/gen $OUT/classes $OUT/dex
aapt2 compile --dir res -o $OUT/res.zip
aapt2 link -o $OUT/app.apk -I "$C/android.jar" --manifest AndroidManifest.xml --java $OUT/gen $OUT/res.zip
javac --release 17 -Xlint:all,-options -encoding UTF-8 -cp "$C/android-37.jar:libs/hiddenapibypass-6.1.jar" -d $OUT/classes $(find src $OUT/gen -name '*.java')
java -cp "$C/r8.jar" com.android.tools.r8.D8 --release --min-api 34 --lib "$C/android-37.jar" --output $OUT/dex $(find $OUT/classes -name '*.class') libs/hiddenapibypass-6.1.jar
python3 -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1],'a',zipfile.ZIP_DEFLATED); z.write(sys.argv[2],'classes.dex')" $OUT/app.apk $OUT/dex/classes.dex
zipalign -f 4 $OUT/app.apk $OUT/aligned.apk
[[ -f podprobe.jks ]] || keytool -genkeypair -keystore podprobe.jks -storepass podprobe -keypass podprobe -alias key -keyalg RSA -keysize 2048 -validity 365 -dname "CN=PodLink probe" 2>/dev/null
apksigner sign --ks podprobe.jks --ks-pass pass:podprobe --key-pass pass:podprobe --out podprobe.apk $OUT/aligned.apk
rm -f podprobe.apk.idsig; ls -la podprobe.apk
