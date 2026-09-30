## Download the Android app

### [⬇ Download E-paper USB 1.2.0 — APK](https://github.com/hersonrodrigues/e-paper-usb/releases/download/v1.2.0/Epaper-USB-1.2.0-debug.apk)

**Android 8.0+ · USB OTG · 25 language variants · Debug build for testing**

Open this link on your Android phone, download the APK, and open the downloaded file to install it. If Android asks, allow installation from the browser or file manager you used. No Android Studio or computer is needed to install this download.

[Release notes and downloads](https://github.com/hersonrodrigues/e-paper-usb/releases/tag/v1.2.0) · [Installation and USB guide](android/README.md#usar-no-celular) · [Build from source](#build-the-app)

The APK passed the recorded offline tests; a complete transfer and physical display refresh still need hardware validation.

---

<p align="center">
  <img src="docs/assets/readme-banner.svg" alt="E-paper USB — an illustration of a phone sending a four-color image to an e-paper display" width="1120">
</p>

# E-paper USB

**Turn a photo on your Android phone into an image for your e-paper display.** Choose a photo, adjust the preview, and send it directly over a USB OTG cable. Designed as an Android alternative to the Windows ImageToUSB workflow, with a separate local browser app for desktop use.

**[Get started](#get-started)** · **[Example hardware](#example-hardware)** · **[Build the app](#build-the-app)** · **[Language summaries](#language-summaries)** · **[Test results](docs/validation.md)**

## What it does

- **Prepare your photo:** select an image, rotate it, and choose a proportional crop or white borders.
- **Preview four colors:** convert to black, white, yellow and red at **800 × 480**, with optional dithering. The preview and transmitted data use the same final image.
- **Send by cable:** connect a CH340-based controller through USB OTG, grant USB access, and tap **Send**. Connecting alone never starts a transfer.
- **Keep photos local:** Android processes images on the phone and has no internet permission. Diagnostic logs can be exported locally.
- **Use your language:** the Android app follows device language settings, with **25 locale variants** and English fallback. Android 13+ also offers per-app language settings. Arabic, Persian and Urdu use right-to-left layouts.

| Android app | Desktop browser app |
| --- | --- |
| Android 8.0+ with USB host / OTG | Local app served with Node.js 20+ |
| Direct USB transfer from the phone | USB transfer in desktop Chrome or Edge |
| Photo picker, preview, four-color conversion | Image conversion, PNG/C export, serial upload |
| Multilingual interface | English interface |

## Example hardware

**[Good Display GDP075FU1 — view the manufacturer's product page](https://www.good-display.com/product/640.html)**

The app's target profile is based on the **GDP075FU1 USB model** and the ImageToUSB v4.0 protocol. Good Display lists this model with a **7.5-inch, 800 × 480 display**, **black / white / red / yellow** colors, and a **Micro USB connection**. The linked page also includes the GDP075FW1 and GDP075FW1B Wi-Fi models; this project's USB profile targets GDP075FU1. [Manufacturer specifications](https://www.good-display.com/product/640.html)

> **Compatibility status:** CH340 detection and serial-port opening have been verified on a Pixel 10. A complete image transfer and physical screen refresh still need hardware validation. Check the controller and panel model; a CH340 identifier alone does not establish compatibility. [See validation details](docs/validation.md).

## Get started

1. [Download and install the Android APK](#download-the-android-app), or [build it from source](#build-the-app).
2. Connect the display to your phone with a suitable **USB OTG adapter and data cable**.
3. Open **E-paper USB**, tap **Connect USB**, and allow access when Android asks.
4. Select a photo or **Color test**, adjust the preview, and tap **Send to display**. Keep the app open and the display connected.

**Send becomes available when both an image and an authorized USB connection are ready.** After a failed or interrupted transfer, power the display off and on before retrying. A completed send confirms bytes were written; check the physical display to confirm the result.

## Build the app

Open [`android/`](android/) in Android Studio and configure the SDK. On macOS, from the repository root:

```sh
android/tools/build-macos.sh
```

The script runs Android tests, Lint, and the debug build. Install the generated APK at `android/app/build/outputs/apk/debug/app-debug.apk`. Build dependencies require internet; the installed Android app works locally. See the [Android guide](android/README.md) for SDK requirements, installation, and the physical test procedure.

To run the separate **desktop browser app**:

```sh
npm start
```

Open **http://localhost:5173** in desktop Chrome or Edge. The browser implements the serial protocol directly in plain JavaScript; no npm dependencies, Python packages or external serial libraries are required. It keeps reading device replies and waits 25 seconds between completed uploads. See the [browser guide](docs/browser.md) for profiles, exports, serial upload and local diagnostic logs.

## Language summaries

The following summaries cover every language variant supported by the **Android app**. Expand a language to read a short overview and find the example product.

<details>
<summary><strong>English</strong></summary>

Select a photo on your Android phone, crop or rotate it, preview it in four colors, and send it to an e-paper display through USB OTG. Photos are processed locally, and the app follows your device language. Example hardware: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); physical image transfer still needs validation.

</details>

<details>
<summary><strong>简体中文 · Simplified Chinese</strong></summary>

在 Android 手机上选择照片，裁剪或旋转，预览四色效果，然后通过 USB OTG 将图像发送到电子纸屏幕。照片在本地处理，应用会跟随设备的语言设置。参考设备：[Good Display GDP075FU1（USB 版）](https://www.good-display.com/product/640.html)；图像传输仍需在实际设备上验证。

</details>

<details>
<summary><strong>繁體中文 · Traditional Chinese</strong></summary>

在 Android 手機上選擇相片，裁切或旋轉，預覽四色效果，再透過 USB OTG 將影像傳送到電子紙螢幕。相片在本機處理，應用程式會跟隨裝置的語言設定。參考裝置：[Good Display GDP075FU1（USB 版）](https://www.good-display.com/product/640.html)；影像傳輸仍須在實際裝置上驗證。

</details>

<details>
<summary><strong>Português (Brasil)</strong></summary>

Selecione uma foto no celular Android, ajuste o corte ou a rotação, confira a prévia em quatro cores e envie para uma tela e-paper por USB OTG. As fotos são processadas localmente, e o app segue o idioma do dispositivo. Produto de referência: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); o envio de imagens ainda precisa ser validado no equipamento físico.

</details>

<details>
<summary><strong>Português (Portugal)</strong></summary>

Escolha uma fotografia no telemóvel Android, ajuste o recorte ou a rotação, veja a pré-visualização em quatro cores e envie-a para um ecrã de papel eletrónico por USB OTG. As fotografias são processadas localmente, e a aplicação segue o idioma do dispositivo. Equipamento de referência: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); a transferência de imagens ainda requer validação no equipamento físico.

</details>

<details>
<summary><strong>Español</strong></summary>

Selecciona una foto en tu móvil Android, recórtala o gírala, comprueba la vista previa en cuatro colores y envíala a una pantalla de papel electrónico mediante USB OTG. Las fotos se procesan localmente y la aplicación sigue el idioma del dispositivo. Equipo de referencia: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); el envío de imágenes aún requiere validación en el equipo físico.

</details>

<details>
<summary><strong>Français</strong></summary>

Choisissez une photo sur votre téléphone Android, recadrez-la ou faites-la pivoter, vérifiez l’aperçu en quatre couleurs, puis envoyez-la à un écran à papier électronique via USB OTG. Les photos sont traitées localement et l’application suit la langue de l’appareil. Matériel de référence : [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html) ; le transfert d’images reste à valider sur le matériel réel.

</details>

<details>
<summary><strong>Italiano</strong></summary>

Scegli una foto sul tuo telefono Android, ritagliala o ruotala, controlla l’anteprima a quattro colori e inviala a un display e-paper tramite USB OTG. Le foto vengono elaborate localmente e l’app segue la lingua del dispositivo. Dispositivo di riferimento: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); il trasferimento delle immagini deve ancora essere verificato sull’hardware reale.

</details>

<details>
<summary><strong>Deutsch</strong></summary>

Wähle auf deinem Android-Smartphone ein Foto aus, schneide es zu oder drehe es, prüfe die Vorschau in vier Farben und übertrage es per USB OTG auf ein E-Paper-Display. Fotos werden lokal verarbeitet; die App übernimmt die Gerätesprache. Referenzgerät: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html). Die Bildübertragung muss noch am physischen Gerät überprüft werden.

</details>

<details>
<summary><strong>한국어 · Korean</strong></summary>

Android 휴대전화에서 사진을 선택하고, 자르거나 회전한 뒤 네 가지 색상의 미리보기를 확인하여 USB OTG로 전자종이 화면에 전송할 수 있습니다. 사진은 기기에서 처리되며 앱은 기기의 언어 설정을 따릅니다. 참고 기기: [Good Display GDP075FU1 (USB 모델)](https://www.good-display.com/product/640.html). 실제 기기에서의 이미지 전송은 아직 검증이 필요합니다.

</details>

<details>
<summary><strong>日本語 · Japanese</strong></summary>

Android スマートフォンで写真を選び、切り抜きや回転を調整し、4色のプレビューを確認してから USB OTG 経由で電子ペーパーディスプレイに送信できます。写真は端末内で処理され、アプリは端末の言語設定に従います。参考機種：[Good Display GDP075FU1（USB モデル）](https://www.good-display.com/product/640.html)。実機での画像転送は、引き続き検証が必要です。

</details>

<details>
<summary><strong>हिन्दी · Hindi</strong></summary>

अपने Android फ़ोन पर फ़ोटो चुनें, उसे काटें या घुमाएँ, चार रंगों में प्रीव्यू देखें और USB OTG से ई-पेपर स्क्रीन पर भेजें। फ़ोटो फ़ोन पर ही प्रोसेस होती हैं और ऐप डिवाइस की भाषा सेटिंग अपनाता है। संदर्भ डिवाइस: [Good Display GDP075FU1 (USB मॉडल)](https://www.good-display.com/product/640.html)। असली डिवाइस पर फ़ोटो भेजने की प्रक्रिया का सत्यापन अभी बाकी है।

</details>

<details>
<summary><strong>العربية · Arabic</strong></summary>

<div dir="rtl">

اختر صورة على هاتف Android، واقصصها أو دوّرها، ثم عاينها بأربعة ألوان وأرسلها إلى شاشة الورق الإلكتروني عبر USB OTG. تُعالج الصور على الهاتف، ويتبع التطبيق لغة الجهاز مع واجهة من اليمين إلى اليسار. الجهاز المرجعي: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html)؛ ما زال إرسال الصور يحتاج إلى التحقق على الجهاز الفعلي.

</div>

</details>

<details>
<summary><strong>বাংলা · Bengali</strong></summary>

Android ফোনে ছবি বাছুন, কাটুন বা ঘোরান, চার রঙে প্রিভিউ দেখুন এবং USB OTG দিয়ে ই-পেপার স্ক্রিনে পাঠান। ছবি ফোনেই প্রক্রিয়াকরণ হয় এবং অ্যাপ ডিভাইসের ভাষা অনুসরণ করে। উদাহরণ ডিভাইস: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); আসল ডিভাইসে ছবি পাঠানো এখনও যাচাই করা বাকি।

</details>

<details>
<summary><strong>Русский · Russian</strong></summary>

Выберите фото на Android, обрежьте или поверните его, проверьте предпросмотр в четырёх цветах и отправьте на экран электронной бумаги через USB OTG. Фото обрабатываются на телефоне, приложение использует язык устройства. Пример оборудования: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); передача изображений ещё требует проверки на физическом устройстве.

</details>

<details>
<summary><strong>Bahasa Indonesia · Indonesian</strong></summary>

Pilih foto di ponsel Android, pangkas atau putar, periksa pratinjau empat warna, lalu kirim ke layar e-paper melalui USB OTG. Foto diproses di ponsel dan aplikasi mengikuti bahasa perangkat. Contoh perangkat: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); pengiriman gambar masih perlu divalidasi pada perangkat fisik.

</details>

<details>
<summary><strong>Türkçe · Turkish</strong></summary>

Android telefonunuzda bir fotoğraf seçin, kırpın veya döndürün, dört renkli önizlemeyi kontrol edin ve USB OTG ile e-kâğıt ekrana gönderin. Fotoğraflar telefonda işlenir ve uygulama cihazın dilini kullanır. Örnek cihaz: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); görüntü aktarımının fiziksel cihazda doğrulanması hâlâ gereklidir.

</details>

<details>
<summary><strong>Tiếng Việt · Vietnamese</strong></summary>

Chọn ảnh trên điện thoại Android, cắt hoặc xoay, kiểm tra bản xem trước bốn màu rồi gửi đến màn hình giấy điện tử qua USB OTG. Ảnh được xử lý trên điện thoại và ứng dụng dùng ngôn ngữ của thiết bị. Thiết bị tham khảo: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); việc truyền ảnh vẫn cần được kiểm chứng trên thiết bị thật.

</details>

<details>
<summary><strong>ภาษาไทย · Thai</strong></summary>

เลือกภาพบนโทรศัพท์ Android ตัดหรือหมุนภาพ ดูตัวอย่างสี่สี แล้วส่งไปยังจออีเปเปอร์ผ่าน USB OTG ภาพประมวลผลบนโทรศัพท์ และแอปใช้ภาษาตามการตั้งค่าของอุปกรณ์ อุปกรณ์ตัวอย่าง: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html) การส่งภาพยังต้องตรวจสอบกับอุปกรณ์จริง

</details>

<details>
<summary><strong>اردو · Urdu</strong></summary>

<div dir="rtl">

Android فون پر تصویر منتخب کریں، اسے تراشیں یا گھمائیں، چار رنگوں میں پیش منظر دیکھیں اور USB OTG سے ای پیپر اسکرین پر بھیجیں۔ تصاویر فون پر ہی تیار ہوتی ہیں اور ایپ دائیں سے بائیں انٹرفیس کے ساتھ ڈیوائس کی زبان اپناتی ہے۔ مثال: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html)؛ اصل ڈیوائس پر تصاویر کی منتقلی کی تصدیق ابھی باقی ہے۔

</div>

</details>

<details>
<summary><strong>فارسی · Persian</strong></summary>

<div dir="rtl">

در گوشی Android عکس انتخاب کنید، آن را برش دهید یا بچرخانید، پیش‌نمایش چهاررنگ را ببینید و از طریق USB OTG به نمایشگر کاغذ الکترونیکی بفرستید. عکس‌ها روی گوشی پردازش می‌شوند و برنامه با رابط راست‌به‌چپ از زبان دستگاه پیروی می‌کند. دستگاه نمونه: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html)؛ انتقال تصویر هنوز به بررسی روی دستگاه واقعی نیاز دارد.

</div>

</details>

<details>
<summary><strong>Polski · Polish</strong></summary>

Wybierz zdjęcie na telefonie z Androidem, przytnij je lub obróć, sprawdź podgląd w czterech kolorach i wyślij na ekran e-paper przez USB OTG. Zdjęcia są przetwarzane na telefonie, a aplikacja używa języka urządzenia. Przykładowy sprzęt: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); przesyłanie obrazów wymaga jeszcze weryfikacji na fizycznym urządzeniu.

</details>

<details>
<summary><strong>Nederlands · Dutch</strong></summary>

Kies een foto op je Android-telefoon, snijd deze bij of draai hem, controleer het voorbeeld in vier kleuren en stuur hem via USB OTG naar een e-paperscherm. Foto’s worden op de telefoon verwerkt en de app volgt de apparaattaal. Voorbeeldapparaat: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); beeldoverdracht moet nog op het fysieke apparaat worden gevalideerd.

</details>

<details>
<summary><strong>Українська · Ukrainian</strong></summary>

Виберіть фото на Android, обріжте або поверніть його, перевірте попередній перегляд у чотирьох кольорах і надішліть на екран електронного паперу через USB OTG. Фото обробляються на телефоні, застосунок використовує мову пристрою. Приклад обладнання: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html); передача зображень ще потребує перевірки на фізичному пристрої.

</details>

<details>
<summary><strong>தமிழ் · Tamil</strong></summary>

Android தொலைபேசியில் படத்தைத் தேர்ந்தெடுத்து, வெட்டி அல்லது சுழற்றி, நான்கு வண்ண முன்னோட்டத்தைப் பார்த்து USB OTG மூலம் மின்னணுக் காகிதத் திரைக்கு அனுப்பலாம். படங்கள் தொலைபேசியிலேயே செயலாக்கப்படும்; செயலி சாதனத்தின் மொழியைப் பின்பற்றும். எடுத்துக்காட்டு சாதனம்: [Good Display GDP075FU1 (USB)](https://www.good-display.com/product/640.html). உண்மையான சாதனத்தில் படப் பரிமாற்றம் இன்னும் சரிபார்க்கப்பட வேண்டும்.

</details>

## Documentation and project status

**Android version 1.2.0:** 33 Android tests passed. The updated browser passes 33 JavaScript tests; 7 Python tests passed in the previous validation of the unchanged diagnostic code. Android Lint completed with 0 errors and 6 warnings. A browser handshake has been observed, but complete device-side image receipt and physical refresh remain unverified. [Recorded validation results](docs/validation.md)

| Explore | Contents |
| --- | --- |
| [Android guide](android/README.md) | Build, installation, USB connection and physical testing |
| [Browser guide](docs/browser.md) | Desktop workflow, exports and supported profiles |
| [Protocol notes](docs/imagetousb40-protocol.md) | ImageToUSB v4.0 framing, color encoding and source evidence |
| [Translation catalogs](android/tools/locales/) | All 25 Android locale variants |
| [Original dossier](android/evidence/) | Preserved engineering evidence |

Source, tests and build scripts are included. APK downloads are published in [GitHub Releases](https://github.com/hersonrodrigues/e-paper-usb/releases). Local SDK paths, signing keys, build caches, APK binaries and device logs are excluded from Git history.
