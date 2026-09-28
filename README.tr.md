# Janus

[English](README.md) · **Türkçe**

Janus, WireGuard ve AmneziaWG için **uygulama ve site bazlı split tunnel** destekleyen bir Android
VPN istemcisidir. Hangi uygulama ve sitelerin VPN dışında kalacağını seçebilir ya da yalnızca
seçtiklerinizi VPN'den geçirebilirsiniz.

Bir VPN sağlayıcısından ya da kendi sunucunuzdan aldığınız her standart WireGuard yapılandırmasıyla
ve AmneziaWG sunucularıyla çalışır. Janus,
[amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android)'in forkudur.

## Özellikler

- **Uygulama ve site bazlı split tunnel.** Uygulamaları listeden seçin, siteleri alan adı
  (`example.com`) ya da IP / ağ (`1.2.3.0/24`) olarak ekleyin. Sitelerin IP adreslerini Janus
  kendisi bulur. Her liste iki moddan biriyle çalışır:
  - **VPN dışında tut:** seçilen uygulama ve siteler doğrudan gider, geri kalan her şey VPN'den.
  - **Sadece bunlar VPN'den:** yalnızca seçilen uygulama ve siteler VPN'i kullanır.
- **DPI koruması:** VPN trafiğini engelleyen ağlarda işe yarayabilecek junk paketleri ekler. Düz
  WireGuard sunucularıyla da çalışır.
- **Hızlı Ayarlar kutucuğu:** bildirim panelinden bağlanıp bağlantıyı kesebilirsiniz.
- **Otomatik yeniden bağlanma:** bağlantı takılırsa kendiliğinden yeniden bağlanır.
- **Dahili güncelleme:** yeni sürümler uygulamanın içinden gelir.
- İngilizce ve Türkçe arayüz.

## Gereksinimler

- Android 7.0 veya üstü. Site bazlı split tunnel için Android 13 veya üstü gerekir.
- Bir WireGuard ya da AmneziaWG yapılandırması.

## Kurulum

APK'yı [son sürüm](https://github.com/aykq/janus/releases/tag/debug-latest) sayfasından indirip
telefonunuzda açın.

## Başlarken

1. **Tünel ekleyin:** *Tünel ekle*'ye dokunun, ardından bir yapılandırma dosyası içe aktarın, QR
   kod tarayın ya da sıfırdan oluşturun. Bağlanmak için büyük düğmeye dokunun.
2. **Split tunnel:** ana ekrandaki *Split tunnel* kartına dokunun, uygulama ve siteleri seçip modu
   belirleyin. Bağlıysanız yeni ayarları hemen kullanmak için *Şimdi uygula*'ya dokunun.

## Gizlilik

Janus'ta hesap, analitik ya da reklam yok. VPN sunucunuz dışında yalnızca güncelleme kontrolü için
GitHub'a ve split tunnel listenizdeki sitelerin adreslerini bulmak için bir DNS-over-HTTPS
çözümleyicisine bağlanır.

## Bilinen sınırlar

- Site bazlı split tunnel IP adresleri üzerinden çalışır. Bağlıyken bir site yeni bir adrese
  geçerse, o trafik yeniden bağlanana kadar varsayılan yoldan gider.
- Daha güçlü gizleme ayarları (S1/S2, H1–H4) bunları destekleyen bir sunucu gerektirir.

## Kaynaktan derleme

```bash
git clone --recurse-submodules https://github.com/aykq/janus.git
cd janus
./gradlew assembleDebug
```

JDK 17, Android SDK, Android NDK ve Go gerekir.

## Lisans

Apache License 2.0, bkz. [COPYING](COPYING).
[amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android) ve
[wireguard-android](https://git.zx2c4.com/wireguard-android) temel alınmıştır.
WireGuard, Jason A. Donenfeld'in tescilli ticari markasıdır.
