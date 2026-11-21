# MusicCaptureTest / Ses Test v1.4

Samsung Galaxy Note 10+ (Android 12) — dahili ses yakalama ve şarkı adına göre 320 kb/sn MP3 kayıt.

## Kullanım
1. Uygulamayı açın. Şarkı adına göre kayıt için **Şarkı adını etkinleştir (Bildirim erişimi)** düğmesine basın.
2. Android ayarlarında **Ses Test - Şarkı Adı** için bildirim erişimini etkinleştirin. Uygulama sadece Android medya oturumlarının başlık/sanatçı bilgisini okumak için erişimi kullanır; bildirimleri kaydetmez veya yüklemez.
3. **Başlat** düğmesine basıp Android ekran/ses yakalama iznini onaylayın.
4. İzin veren bir uygulamada bir şarkı çalın.
5. Uygulamaya dönüp **Durdur / Kaydet** düğmesine basın.
6. Samsung **Dosyalarım → Dahili depolama → Music → MusicCaptureTest** dizinine gidin.

## MP3 adı ve kalite
- Stereo 44.1 kHz, **320 kb/sn CBR MP3** (Java LAME codec).
- Android MediaSession metadata mevcutsa örn. `Sanatçı - Şarkı_20261121_221000.mp3` olarak kaydeder.
- Şarkı başlığı paylaşılmıyorsa `MusicCapture_<tarih_saat>.mp3` olur.
- Tek kayıt esnasında birden fazla şarkı algılanırsa yanlış adlandırmayı önlemek için `MusicCapture_MultiTrack_<tarih_saat>.mp3` olur. **Henüz otomatik olarak parçalara bölmez.**
- Sesi 320 kb/sn kodlamak önceki düşük kaliteli/sıkıştırılmış ses kaynağını iyileştirmez. Sadece MP3 yeniden kodlama kaybını azaltabilir.
- MP3 dönüştürmesi hata verirse kayıt kaybolmaması için WAV yedeği oluşturulur.
- Kayıtlar yalnızca telefona yerel kaydedilir; ses veya metadata çevrimiçi bir servise gönderilmez.

## Teknik sınırlar
- Android 10+ AudioPlaybackCaptureConfiguration kullanır, mikrofon kaydetmez.
- Kaynak uygulamanın Android dahili ses kaydına izin vermesi gerekir. Korumayı aşmaz.
- Başlık tanıma çevrimiçi ses tanıma değildir, yalnızca uygulamanın MediaSession başlığıdır. Bazı uygulamalar bu bilgiyi sunmayabilir.
- Bildirim erişimi kapsamlı bir Android iznidir; isteğe bağlıdır ve Android ayarlarından geri alınabilir.
- Kullanımınızda müzik haklarına uyun.

## Derleme
GitHub Actions -> Build APK -> başarılı workflow -> SesTest-debug-apk artifact.
