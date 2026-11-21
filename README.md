# MusicCaptureTest / Ses Test v1.2

Samsung Galaxy Note 10+ (Android 12) üzerinde dahili oynatma sesi yakalama denemesi.

## Ne yapar?
- AudioPlaybackCaptureConfiguration ile mikrofon yerine uygulamaların **izin verdiği** oynatma sesini yakalar.
- Her oturumda Android'in resmi MediaProjection iznini ister.
- Başka uygulamaya geçilse bile görünür bildirimle ses kaydını sürdürür.
- DURDUR VE KAYDET sonrasında Music/MusicCaptureTest klasöründe WAV dosyası oluşturur.
- Sessiz sinyal algılandıysa uygulama bunu açıkça belirtir.

## Sınırlar
- Spotify / YouTube Music gibi uygulamalar seslerinin yakalanmasına izin vermeyebilir.
- Koruma veya uygulama izinlerini aşmaya çalışmaz.
- Şu an **WAV testidir**; otomatik parça ayırma / MP3 henüz yoktur.
- Müzik haklarına uygun içeriklerle kullanın.

## APK
GitHub -> Actions -> Build APK -> en son başarılı run -> SesTest-debug-apk ZIP dosyasından APK'yi çıkarıp yükleyin. Kurulu önceki sürümün üzerine aynı applicationId ile yüklenebilir; imza değişirse önce eski test sürümünü kaldırmanız gerekebilir.

## Test
1. Ses Test uygulamasını açın ve YAKALAMAYI BAŞLAT seçin.
2. Android'in Ses kaydetme iznine (API gerekliliği; kaynak mikrofon değildir) ve ekran paylaşımı onayına izin verin.
3. Dahili ses yakalamaya izin veren bir uygulamada ses çalın.
4. Ses Test'e dönün; DURDUR VE KAYDET seçin.
5. Samsung Dosyalarım -> Dahili depolama -> Music -> MusicCaptureTest klasörünü kontrol edin.

Android 10+ zorunludur.
