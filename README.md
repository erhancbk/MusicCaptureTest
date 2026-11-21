# MusicCaptureTest / Ses Test v1.3

Samsung Galaxy Note 10+ (Android 12) üzerinde dahili oynatma sesi yakalama denemesi.

## Ne yapar?
- AudioPlaybackCaptureConfiguration ile mikrofon yerine uygulamaların **izin verdiği** oynatma sesini yakalar.
- Her oturumda Android'in resmi MediaProjection iznini ister.
- Başka uygulamaya geçilse bile görünür bildirimle ses kaydını sürdürür.
- Durdur / Kaydet sonrasında Music/MusicCaptureTest klasöründe gerçek 192 kb/sn stereo MP3 oluşturur; MP3 dönüşüm hatasında kayıt kaybolmasın diye WAV yedeği kaydeder.
- Sessiz sinyal algılandıysa uygulama bunu açıkça belirtir.

## Sınırlar
- Spotify / YouTube Music gibi uygulamalar seslerinin yakalanmasına izin vermeyebilir.
- Koruma veya uygulama izinlerini aşmaya çalışmaz.
- Otomatik parça ayırma henüz yoktur. MP3 LAME Java kodlayıcısı ile kayıt bitiminde hazırlanır.
- Müzik haklarına uygun içeriklerle kullanın.

## APK
GitHub -> Actions -> Build APK -> en son başarılı run -> SesTest-debug-apk ZIP dosyasından APK'yi çıkarıp yükleyin. Kurulu önceki sürümün üzerine aynı applicationId ile yüklenebilir; imza değişirse önce eski test sürümünü kaldırmanız gerekebilir.

## Test
1. Ses Test uygulamasını açın ve Başlat seçin.
2. Android'in Ses kaydetme iznine (API gerekliliği; kaynak mikrofon değildir) ve ekran paylaşımı onayına izin verin.
3. Dahili ses yakalamaya izin veren bir uygulamada ses çalın.
4. Ses Test'e dönün; Durdur / Kaydet seçin.
5. Samsung Dosyalarım -> Dahili depolama -> Music -> MusicCaptureTest klasörünü kontrol edin.

Android 10+ zorunludur.

## v1.3 arayüz
- Başlat ve Durdur / Kaydet düğmeleri üst sırada, yan yana, yarım genişlikte ve 40 dp yüksekliğinde.
- Kaydın bitmesinin ardından MP3 dönüştürmesi işlem süresine göre biraz zaman alabilir.
- Android'in diğer uygulamaların sesi için tanımladığı paylaşım kısıtlamaları aynen geçerlidir.
- MP3 kodlayıcı: io.vacco.jlame:jlame:3.100.2 (GPL lisansı).
