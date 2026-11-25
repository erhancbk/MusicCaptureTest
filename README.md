# MusicCaptureTest / Ses Test v1.6

Samsung Galaxy Note 10+ (Android 12) için dahili ses yakalama ve 320 kb/sn MP3 kayıt.

## Yeni: Akıllı bitiş (şarkı sonunda otomatik durdur ve kaydet)

- Ana ekrandaki **Şarkı bitince otomatik durdur ve kaydet** kutusu varsayılan olarak açık. İstenirse kapatılabilir.
- Şarkı başladıktan sonra Android medya oturumunun sunduğu şarkı başlığını, **süreyi (duration)** ve **geçerli oynatma konumunu (position)** izler.
- Android bir süre ve konum veriyorsa ekran üzerinde **geçen/toplam/kalan süre** gösterilir; süre sonuna yaklaşıldığında ses kaydı sonlanır, MP3'e dönüştürülür ve geçmişe eklenir.
- Medya oturumundaki şarkı başlığı farklı bir şarkı/reklam başlığına değişirse kayıt sonlandırılır. Yeni başlık kaydedilen MP3'ün adına yazılmaz; önceki şarkının adı korunur.
- Tekrar şarkı uyarısı ve geçmiş özellikleri korunur.
- **Durdur / Kaydet** düğmesi her zaman çalışır. Kutuyu kapatırsanız önceki sürümlerdeki gibi manuel kayıt yapılır.
- MP3 stereo 44.1 kHz, **320 kb/sn**. Dosyalar: **Music/MusicCaptureTest**.

## Kullanım

1. Android ayarlarında **Ses Test - Şarkı Adı** bildirim erişiminin açık olduğundan emin olun. Bu erişim, Android medya oturumlarının bilgisini okumaya yarar.
2. Ses Test'te **Şarkı bitince otomatik durdur ve kaydet** seçili olsun.
3. **Başlat** düğmesine basın ve Android'in ekran/ses paylaşım onayını verin.
4. YouTube veya başka bir uygulamada kaydetmek istediğiniz şarkıyı çalın.
5. Uygulama ekranda şarkının süresini izler ve varsa bitişi tespit ettiğinde **otomatik durur ve MP3 kaydeder**. Manuel durdurma da mümkündür.
6. Kaydı **Kayıt geçmişi** bölümünde ve cihazın Music/MusicCaptureTest klasöründe kontrol edin.

## Gerçek sınırlar / reklamlar

Bu özellik reklam engelleyici değildir; YouTube uygulamasının reklamlarını değiştirmez veya atlamaz.
Android'in medya oturumu API'sini kullanır. YouTube sürümü, video/reklam türü ve ayarları nedeniyle:
- Şarkı süresi veya oynatma konumu bildirilmeyebilir.
- Reklam ile şarkı arasındaki geçiş ayrı bir medya başlığı olarak bildirilmeyebilir.
- Reklam şarkı biter bitmez başladığında yakalama ve metadata güncellemesi arasında küçük bir gecikme olabilir; kayda reklamın kısa bir parçası girebilir.
- Şarkı başladıktan sonra kayda geçilirse şarkının başı dosyada olmaz.
- Şarkının ortasında reklam başlarsa başlık değişimini şarkı sonu gibi algılayıp kayıt erken bitebilir.

Süre/konum eksikse otomatik **süreyle durma garanti edilemez**; medya başlığı değişimi yine algılanabilir. Otomatik bitişi YouTube'da ilk olarak **tek bir şarkıyla test etmelisiniz.** MP3 dönüştürmesi başarısız olursa WAV yedeği bırakılır.

## Kayıt geçmişi

SQLite yerel kayıt günlüğü, duplicate-song uyarısı, sanatçı/başlık adlandırması, geçmiş arama (son 20 kayıt), ve eski sürümlerden kayıt içe aktarma özellikleri v1.5 ile aynıdır. Android'in ses yakalama izni ve kayıt uygulamalarıyla ilgili sınırlamaları geçerlidir. Kullanımınızda içerik haklarına uyun.

## GitHub Actions derlemesi

Actions -> Build APK -> son başarılı run -> **SesTest-debug-apk** ZIP dosyası.
