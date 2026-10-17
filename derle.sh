#!/usr/bin/env bash
# =============================================================================
#  DÜŞÜK RAM DERLEME YARDIMCISI  —  8 GB RAM
# =============================================================================
#  Bu betik, gradle.properties içindeki bellek sınırlarını kullanarak derler.
#  Ek olarak derleme bittikten sonra arka planda RAM tutan daemon'ları kapatmayı
#  ÖNERİR — Gradle + Kotlin daemon, boşta beklerken ~1.5 GB RAM'i elinde tutar
#  ve bu, 8 GB'lık makinede tarayıcı/IDE açınca donma sebebidir.
#
#  Kullanım:
#     ./derle.sh              -> Debug APK derle
#     ./derle.sh release      -> Release (R8 minify'li) APK derle
#     ./derle.sh temiz        -> Önce temizle, sonra Debug APK derle
#     ./derle.sh serbest      -> Tüm daemon'ları kapatıp RAM'i geri ver
#     ./derle.sh test         -> Birim testleri çalıştır
# =============================================================================
set -euo pipefail

cd "$(dirname "$0")"

GODLANDIR="$PWD"
# Birden fazla Gradle hedefi oldugunda dizi olarak tutulur; tek string olarak
# gecilirse Gradle "clean :app:assembleDebug" diye TEK bir proje adi saniyor.
HEDEFLER=()

kullanim() {
    cat <<'EOF'
Kullanim: ./derle.sh [debug|release|temiz|test|serbest]

  debug    (varsayilan) Debug APK derler
  release  (varsayilan) Release APK derler - R8 KUCULTME KAPALI (8 GB icin guvenli)
  release-full  Tam R8 kucultmeli Release APK. Cok bellek ister; once Android
           Studio / emulator / tarayiciyi KAPATIN.
  temiz    Tam temizlikten sonra Debug APK derler (en cok sure, en cok RAM)
  test     Birim testlerini calistirir
  serbest  Arka plandaki Gradle/Kotlin daemon'larini kapatir (~1.5 GB RAM geri gelir)
EOF
}

# Kullanicidan oncesi rami biraz rahatlat: tarayici ve IDE yerine baslayacaktir.
on_rami_uyari() {
    local kullanilabilir
    kullanilabilir=$(free -m | awk 'NR==2{print $7}')
    if [ "${kullanilabilir:-0}" -lt 2500 ]; then
        echo ""
        echo "  UYARI: Yalnizca ~${kullanilabilir} MB RAM kullanilabilir."
        echo "         Kasma olmasi icin Android Studio / emulatoru kapatin."
        echo "         Gerekirse once './derle.sh serbest' calistirin."
        echo ""
    fi
}

case "${1:-debug}" in
    debug)   HEDEFLER=(":app:assembleDebug") ;;
    release)
        # VARSAYILAN: R8'siz release. 8 GB'da R8, Gradle daemon'ini OOM ile
        # olduruyordu ("build daemon disappeared", tepe ~6.7 GB + swap).
        HEDEFLER=(":app:assembleRelease")
        EXTRA=("-PlowRam=true")
        ;;
    release-full)
        # Gercek R8 kucultmesi. Yalnizca Android Studio / emulator / tarayici
        # KAPALIYKEN calistir; aksi halde makine doner.
        HEDEFLER=(":app:assembleRelease")
        JVM_ASIM="2048m"
        JVM_META="768m"
        echo ""
        echo "  DIKKAT: Tam R8 kucultmesi 8 GB'ta 2 GB+ ek bellek ister."
        echo "         Android Studio, emulator ve tarayiciyi KAPATIN."
        echo ""
        ;;
    temiz)   HEDEFLER=("clean" ":app:assembleDebug") ;;
    test)    HEDEFLER=(":app:testDebugUnitTest") ;;
    serbest)
        echo "Arka plandaki derleme surecleri kapatiliyor..."
        ./gradlew --stop || true
        pkill -f 'KotlinCompileDaemon' 2>/dev/null || true
        sleep 1
        echo "Tamam. Geri kazanilan bellek:"
        free -h
        exit 0
        ;;
    -h|--help|help) kullanim; exit 0 ;;
    *) echo "Bilinmeyen arguman: $1"; echo; kullanim; exit 1 ;;
esac

JVM_ASIM="${JVM_ASIM:-1024m}"
JVM_META="${JVM_META:-512m}"
EXTRA=(${EXTRA[@]+"${EXTRA[@]}"})
HEDEF_CIKTI="${HEDEFLER[*]}"

echo "=========================================================="
echo " Soru Defteri - Dusuk RAM derlemesi"
echo " Hedef : $HEDEF_CIKTI"
echo " Gradle: Xmx${JVM_ASIM} / Metaspace ${JVM_META} | tek is parcacigi"
echo "=========================================================="
on_rami_uyari

# -Dorg.gradle.jvmargs sadece BU gradle calistirmasini etkiler;
# gradle.properties kalici olarak degismez.
./gradlew "${HEDEFLER[@]}" ${EXTRA[@]+"${EXTRA[@]}"} \
    -Dorg.gradle.jvmargs="-Xmx${JVM_ASIM} -XX:MaxMetaspaceSize=${JVM_META} -XX:+UseSerialGC -Dfile.encoding=UTF-8" \
    --console=plain

APK_KLASORU="app/build/outputs/apk"
if [ -d "$APK_KLASORU" ]; then
    echo ""
    echo "Uretilen APK dosyalari:"
    ls -lh "$APK_KLASORU"/*/*.apk 2>/dev/null || echo "  (henuz APK bulunamadi)"
fi

echo ""
echo "Tamamlandi. Arka planda ~1.5 GB RAM tutan daemon'lari kapatmak icin:"
echo "    ./derle.sh serbest"
