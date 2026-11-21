# MusicCaptureTest / Ses Test v1.5

Samsung Galaxy Note 10+ (Android 12): dahili ses yakalama, şarkı adına göre 320 kb/sn MP3, **kalıcı kayıt geçmişi ve tekrar uyarısı**.

## Kaydetme ve tekrar uyarısı

1. **Başlat** ile Android'in medya yakalama iznini verin.
2. Müziği başlatın; **Şarkı adını etkinleştir (Bildirim erişimi)** daha önce açık olmalıdır. Bu yetki medya oturumundaki başlık/sanatçı bilgisini okumaya yarar.
3. Şarkı adı bulunduğunda uygulama mevcut kayıt geçmişini sorgular. Aynı **sanatçı + şarkı adı** daha önce başarıyla kaydedildiyse **"Bu şarkı daha önce kaydedildi"** bildirimi ve ekranda bir uyarı gösterilir. **Kayıt kesilmez veya durdurulmaz.**
4. **Durdur / Kaydet** ile 320 kb/sn stereo MP3 dosyası kaydedilir.
5. Yeni kaydedilen MP3 geçmişe tarih, sanatçı, başlık, dosya adı ve tekrar işaretiyle kaydedilir. MP3 başarısız olursa WAV yedeği de geçmişe kaydedilmeye çalışılır.
6. Uygulamada **Kayıt geçmişi (N)** düğmesiyle son 20 dosyayı ve toplam dosya sayısını görüntüleyin.

Dosyalar: **Dosyalarım → Dahili depolama → Music → MusicCaptureTest**

## Önceki sürümlerdeki dosyalar

Uygulama Music/MusicCaptureTest içindeki kayıtları otomatik taramayı dener. Daha önceki debug sürümünü kaldırıp yeni APK'yı yüklemek gerekirse Android eski ses dosyalarına erişimi sınırlayabilir. Böyle bir durumda uygulamadaki **Eski kayıtları tara** düğmesiyle yalnızca isterseniz müzik/dosya okuma izni verin. Eski v1.4 kayıtları Artist - Title_YYYYMMDD_HHMMSS.mp3 biçimindeyse başlık ve sanatçı dosya adından geri yüklenebilir. Eski dosyanın gerçek başlığı anlaşılamıyorsa yalnızca dosya adı geçmişe eklenir; yanlış şarkı eşleşmesi yapılmaz. Geçmişin veritabanı uygulamanın özel alanında saklanır; APK kaldırılırsa bu veritabanı silinir, mevcut MP3'lerden yeniden oluşturulabilir.

## Sınırlar

- Bu özellik ses tanıma / audio fingerprinting değildir. Android medya oturumunun paylaştığı başlık/sanatçı bilgilerini karşılaştırır.
- Büyük-küçük harf, boşluk, noktalama ve aksan farklılıkları normalize edilir. Eserin farklı isimle yayımlanmış sürümleri aynı şarkı olarak algılanmayabilir.
- Başlık/sanatçı yoksa kesin tekrar kontrolü yapılamaz.
- Çoklu şarkı kaydı hâlâ tek MP3 olur; çoklu dosya otomatik ayırma bu sürümde yoktur.
- Bir şarkı tekrar olsa bile kullanıcı isteğine uygun olarak kayıt durmaz ve yeni dosya kaydedilir.
- Android 10+ gerektirir; yalnızca paylaşımına izin verilen uygulamaların sesi yakalanır.
- Ses, şarkı bilgisi ve geçmiş **yalnızca cihaz üzerinde** saklanır.

## Derleme

GitHub Actions -> Build APK -> en yeni başarılı run -> SesTest-debug-apk ZIP dosyasından APK'yi çıkarın.
