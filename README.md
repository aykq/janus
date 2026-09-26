# Switchyard

Adı demiryolundaki makas sahasından (switchyard): trenler orada farklı raylara ayrılır,
Switchyard da trafiği VPN ile doğrudan bağlantı arasında ayırır.

Kişisel Android VPN istemcisi. [amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android)
(Apache-2.0) forku; orijinal README: [README.upstream.md](README.upstream.md).

Hedef: Proton'un (veya herhangi bir sağlayıcının) WireGuard config'iyle, Amnezia VPN
uygulamasına ihtiyaç duymadan:

- **Uygulama bazlı split tunnel**: upstream'den gelir (tünel editörü → Excluded applications).
- **Site/IP bazlı split tunnel**: ana menüde **Siteler**. Site adı veya IP/CIDR yazılır, listedekiler
  VPN dışında kalır. Domain'ler bağlanırken DoH ve sistem DNS'i ile paralel çözülür, iki cevabın
  birleşimi tünel dışına alınır (CDN'ler resolver'a göre farklı IP döndüğü için). Çözülen IP'ler
  önbelleğe birikir; yavaş/başarısız sorguda önbellek kullanılır. `ornek.com` girilince
  `www.ornek.com` da çözülür. Android 13+ (`VpnService.Builder.excludeRoute`).
- **Private DNS bypass** (varsayılan kapalı, son çare): VPN açıkken "Private DNS sunucusuna
  erişilemiyor" hatasının kök nedeni çözülemezse. Private DNS hostname'i çözülür, IP'leri tünel
  dışına alınır; sorgular DoT ile şifreli kalır ama VPN IP'si yerine gerçek IP'den gider.
- **DPI koruması**: tünel editörü → menü → *DPI koruması*. Jc/Jmin/Jmax junk paketlerini ekler.
  Bunlar el sıkışmadan önce gönderilir ve düz WireGuard sunucularınca yok sayılır, Proton'la
  uyumludur. S1/S2 ve H1-H4 sunucu desteği ister, Proton'da **dokunma**.
- **Quick Settings kısayolu**: upstream'den gelir (bildirim panelinde tile ekle).

## Değişen dosyalar

| Dosya | Ne |
| --- | --- |
| `tunnel/.../backend/GoBackend.java` | `ExcludedRoutesProvider` kancası, `excludeRoute` uygulaması |
| `ui/.../split/DohResolver.kt` | RFC 8484 DoH istemcisi (A/AAAA) |
| `ui/.../split/SiteStore.kt` | Site listesi, DoH adresi, Private DNS ayarları, IP önbelleği (DataStore) |
| `ui/.../split/SiteRouteProvider.kt` | Listeyi rotaya çevirir |
| `ui/.../split/PrivateDns.kt` | Alttaki ağın Private DNS hostname'ini bulur |
| `ui/.../activity/SitesActivity.kt` + `activity_sites.xml` | Siteler ekranı |
| `ui/.../fragment/TunnelEditorFragment.kt` | DPI preset menüsü |
| `ui/src/main/res/values/strings_switchyard.xml` | Türkçe metinler |

Kod namespace'i `org.amnezia.awg` olarak kaldı (upstream'den güncelleme çekmek kolay olsun);
applicationId `tr.aykq.switchyard`, yani Amnezia uygulamalarıyla yan yana kurulur.

## Derleme

```
git clone --recurse-submodules <repo>
cd switchyard
./gradlew assembleDebug        # ui/build/outputs/apk/debug/ui-debug.apk
```

JDK 17, Android SDK 36, NDK 26.1.10909125 ve Go gerekir (tunnel modülü amneziawg-go'yu derler).
`master`'a her push'ta GitHub Actions debug APK üretir (Actions → artifact `debug-apk`).

## Bilinen sınırlar

- Site split rota tabanlı: IP'si bağlantı sırasında bilinmeyen adresler (yeni CDN IP'si) tünelden
  gider. Ağ değişince tünel yeniden kurulur ve siteler yeniden çözülür; elle yenilemek için
  Siteler → *Kaydet ve tüneli yeniden bağla*.
- Private DNS bypass'ın `excludeRoute` ile sistem DoT trafiğine uygulandığı cihazda test edilmeli.
