package home.horizon.tv;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * On-screen text in the stick's system language.
 * Supported: English (default), Arabic, Spanish, Portuguese, Indonesian, Mandarin (Simplified Chinese),
 * Russian, Turkish and Urdu. Any other system language shows English.
 * Keys are the English text itself; a missing translation simply shows the English.
 * Names from the IPTV provider (channels, films, categories) are never translated.
 */
final class T {
    private T() { }

    // Column order of every entry below.
    private static final String[] LANGS = {"ar", "es", "pt", "id", "zh", "ru", "tr", "ur"};
    private static final Map<String, String[]> TABLE = new HashMap<>();

    /** Language column for the current system language, or -1 for English. */
    private static int column() {
        String l = Locale.getDefault().getLanguage();
        if ("in".equals(l)) l = "id";               // Android reports Indonesian as the old code "in"
        for (int i = 0; i < LANGS.length; i++) if (LANGS[i].equals(l)) return i;
        return -1;
    }

    /** True for Arabic and Urdu: the layout runs right to left. */
    static boolean rtl() {
        String l = Locale.getDefault().getLanguage();
        return "ar".equals(l) || "ur".equals(l);
    }

    /** Translated text. */
    static String t(String en) {
        int c = column();
        if (c < 0) return en;
        String[] row = TABLE.get(en);
        return row == null || row[c] == null || row[c].isEmpty() ? en : row[c];
    }

    /** Translated text with values filled in (%s, %d, %1$s ...). */
    static String f(String en, Object... args) {
        return String.format(Locale.getDefault(), t(en), args);
    }

    private static void e(String en, String ar, String es, String pt, String id, String zh, String ru, String tr, String ur) {
        TABLE.put(en, new String[] {ar, es, pt, id, zh, ru, tr, ur});
    }

    static {
        // ---- Home screen ----
        e("Continue Watching", "متابعة المشاهدة", "Seguir viendo", "Continuar assistindo", "Lanjutkan menonton", "继续观看", "Продолжить просмотр", "İzlemeye devam et", "دیکھنا جاری رکھیں");
        e("Live TV", "البث المباشر", "TV en directo", "TV ao vivo", "TV Langsung", "直播电视", "Прямой эфир", "Canlı TV", "لائیو ٹی وی");
        e("Movies", "الأفلام", "Películas", "Filmes", "Film", "电影", "Фильмы", "Filmler", "فلمیں");
        e("Series", "المسلسلات", "Series", "Séries", "Serial", "剧集", "Сериалы", "Diziler", "سیریز");
        e("Add your IPTV playlist from Options, the sliders button at the top.",
                "أضف قائمة IPTV من الخيارات، وهو زر أشرطة التمرير في الأعلى.",
                "Añade tu lista IPTV desde Opciones, el botón de controles deslizantes de arriba.",
                "Adicione sua lista IPTV em Opções, o botão de controles deslizantes no topo.",
                "Tambahkan playlist IPTV Anda dari Opsi, tombol penggeser di atas.",
                "请在顶部的“选项”（滑块按钮）中添加 IPTV 播放列表。",
                "Добавьте IPTV-плейлист в «Параметрах» — кнопка с ползунками вверху.",
                "IPTV listenizi üstteki kaydırıcı düğmesi olan Seçenekler'den ekleyin.",
                "اوپر موجود سلائیڈر بٹن یعنی آپشنز سے اپنی IPTV پلے لسٹ شامل کریں۔");
        e("Nothing yet. Start something from Live TV, Movies or Series.",
                "لا شيء بعد. ابدأ المشاهدة من البث المباشر أو الأفلام أو المسلسلات.",
                "Nada todavía. Empieza algo desde TV en directo, Películas o Series.",
                "Nada ainda. Comece algo em TV ao vivo, Filmes ou Séries.",
                "Belum ada. Mulai tonton dari TV Langsung, Film, atau Serial.",
                "暂无内容。从直播电视、电影或剧集开始观看吧。",
                "Пока пусто. Начните просмотр в разделах «Прямой эфир», «Фильмы» или «Сериалы».",
                "Henüz bir şey yok. Canlı TV, Filmler veya Diziler'den bir şey başlatın.",
                "ابھی کچھ نہیں۔ لائیو ٹی وی، فلموں یا سیریز سے کچھ شروع کریں۔");
        e("LIVE", "مباشر", "EN DIRECTO", "AO VIVO", "LIVE", "直播", "ЭФИР", "CANLI", "لائیو");
        e("Live channel", "قناة مباشرة", "Canal en directo", "Canal ao vivo", "Saluran langsung", "直播频道", "Канал в эфире", "Canlı kanal", "لائیو چینل");
        e("Resume", "استئناف", "Reanudar", "Retomar", "Lanjutkan", "继续播放", "Продолжить", "Devam et", "دوبارہ شروع");
        e("Remove", "إزالة", "Quitar", "Remover", "Hapus", "移除", "Убрать", "Kaldır", "ہٹائیں");
        e("%s removed from Continue Watching", "تمت إزالة %s من متابعة المشاهدة", "%s se quitó de Seguir viendo", "%s foi removido de Continuar assistindo", "%s dihapus dari Lanjutkan menonton", "已将 %s 从“继续观看”中移除", "%s убрано из «Продолжить просмотр»", "%s, İzlemeye devam et listesinden kaldırıldı", "%s کو دیکھنا جاری رکھیں سے ہٹا دیا گیا");
        e("Remove from Continue Watching", "إزالة من متابعة المشاهدة", "Quitar de Seguir viendo", "Remover de Continuar assistindo", "Hapus dari Lanjutkan menonton", "从“继续观看”中移除", "Убрать из «Продолжить просмотр»", "İzlemeye devam et listesinden kaldır", "دیکھنا جاری رکھیں سے ہٹائیں");
        e("Move left", "نقل لليسار", "Mover a la izquierda", "Mover para a esquerda", "Pindah ke kiri", "左移", "Сдвинуть влево", "Sola taşı", "بائیں منتقل کریں");
        e("Move right", "نقل لليمين", "Mover a la derecha", "Mover para a direita", "Pindah ke kanan", "右移", "Сдвинуть вправо", "Sağa taşı", "دائیں منتقل کریں");
        e("Remove from the row", "إزالة من الصف", "Quitar de la fila", "Remover da linha", "Hapus dari baris", "从此行移除", "Убрать из ряда", "Satırdan kaldır", "قطار سے ہٹائیں");

        // ---- Weather city ----
        e("Weather city", "مدينة الطقس", "Ciudad del tiempo", "Cidade do clima", "Kota cuaca", "天气城市", "Город для погоды", "Hava durumu şehri", "موسم کا شہر");
        e("Weather city (%s)", "مدينة الطقس (%s)", "Ciudad del tiempo (%s)", "Cidade do clima (%s)", "Kota cuaca (%s)", "天气城市（%s）", "Город для погоды (%s)", "Hava durumu şehri (%s)", "موسم کا شہر (%s)");
        e("Automatic", "تلقائي", "Automático", "Automático", "Otomatis", "自动", "Автоматически", "Otomatik", "خودکار");
        e("Off", "متوقف", "Desactivado", "Desligado", "Mati", "关闭", "Выкл.", "Kapalı", "بند");
        e("Automatic (from the time zone)", "تلقائي (من المنطقة الزمنية)", "Automático (según la zona horaria)", "Automático (pelo fuso horário)", "Otomatis (dari zona waktu)", "自动（根据时区）", "Автоматически (по часовому поясу)", "Otomatik (saat dilimine göre)", "خودکار (ٹائم زون سے)");
        e("Choose a city...", "اختيار مدينة...", "Elegir una ciudad...", "Escolher uma cidade...", "Pilih kota...", "选择城市...", "Выбрать город...", "Şehir seç...", "شہر منتخب کریں...");
        e("Turn weather off", "إيقاف الطقس", "Desactivar el tiempo", "Desligar o clima", "Matikan cuaca", "关闭天气", "Отключить погоду", "Hava durumunu kapat", "موسم بند کریں");
        e("Type a city name", "اكتب اسم المدينة", "Escribe el nombre de una ciudad", "Digite o nome de uma cidade", "Ketik nama kota", "输入城市名称", "Введите название города", "Bir şehir adı yazın", "شہر کا نام لکھیں");
        e("Searching...", "جارٍ البحث...", "Buscando...", "Buscando...", "Mencari...", "正在搜索...", "Поиск...", "Aranıyor...", "تلاش جاری ہے...");
        e("No city found with that name.", "لم يتم العثور على مدينة بهذا الاسم.", "No se encontró ninguna ciudad con ese nombre.", "Nenhuma cidade encontrada com esse nome.", "Tidak ada kota dengan nama itu.", "找不到该名称的城市。", "Город с таким названием не найден.", "Bu adla bir şehir bulunamadı.", "اس نام کا کوئی شہر نہیں ملا۔");

        // ---- Options and wallpaper ----
        e("Options", "الخيارات", "Opciones", "Opções", "Opsi", "选项", "Параметры", "Seçenekler", "آپشنز");
        e("Wallpaper", "الخلفية", "Fondo de pantalla", "Papel de parede", "Wallpaper", "壁纸", "Обои", "Duvar kâğıdı", "وال پیپر");
        e("No wallpaper (plain dark)", "بدون خلفية (داكن)", "Sin fondo (oscuro liso)", "Sem papel de parede (escuro liso)", "Tanpa wallpaper (gelap polos)", "无壁纸（纯深色）", "Без обоев (тёмный фон)", "Duvar kâğıdı yok (düz koyu)", "کوئی وال پیپر نہیں (سادہ گہرا)");
        e("No photos found on the stick", "لم يتم العثور على صور على الجهاز", "No se encontraron fotos en el dispositivo", "Nenhuma foto encontrada no dispositivo", "Tidak ada foto di perangkat", "设备上没有找到照片", "На устройстве нет фотографий", "Cihazda fotoğraf bulunamadı", "ڈیوائس پر کوئی تصویر نہیں ملی");
        e("Clear Continue Watching", "مسح متابعة المشاهدة", "Vaciar Seguir viendo", "Limpar Continuar assistindo", "Kosongkan Lanjutkan menonton", "清空“继续观看”", "Очистить «Продолжить просмотр»", "İzlemeye devam et listesini temizle", "دیکھنا جاری رکھیں صاف کریں");
        e("Edit app row (opens All apps)", "تعديل صف التطبيقات (يفتح كل التطبيقات)", "Editar fila de apps (abre Todas las apps)", "Editar linha de apps (abre Todos os apps)", "Ubah baris aplikasi (membuka Semua aplikasi)", "编辑应用行（打开所有应用）", "Изменить ряд приложений (откроет «Все приложения»)", "Uygulama satırını düzenle (Tüm uygulamalar açılır)", "ایپ قطار میں ترمیم کریں (تمام ایپس کھلیں گی)");
        e("Clear cache (%.0f MB of saved posters and lists)", "مسح ذاكرة التخزين المؤقت (%.0f MB من الصور والقوائم المحفوظة)", "Borrar caché (%.0f MB de pósters y listas guardados)", "Limpar cache (%.0f MB de pôsteres e listas salvos)", "Hapus cache (%.0f MB poster dan daftar tersimpan)", "清除缓存（已保存的海报和列表 %.0f MB）", "Очистить кэш (%.0f МБ сохранённых постеров и списков)", "Önbelleği temizle (%.0f MB kayıtlı afiş ve liste)", "کیش صاف کریں (%.0f MB محفوظ پوسٹرز اور فہرستیں)");
        e("Cache cleared", "تم مسح ذاكرة التخزين المؤقت", "Caché borrada", "Cache limpo", "Cache dihapus", "缓存已清除", "Кэш очищен", "Önbellek temizlendi", "کیش صاف ہو گیا");
        e("Cancel", "إلغاء", "Cancelar", "Cancelar", "Batal", "取消", "Отмена", "İptal", "منسوخ کریں");

        // ---- Playlists ----
        e("IPTV playlists", "قوائم IPTV", "Listas IPTV", "Listas IPTV", "Playlist IPTV", "IPTV 播放列表", "IPTV-плейлисты", "IPTV listeleri", "IPTV پلے لسٹس");
        e("IPTV playlists (using: %s)", "قوائم IPTV (المستخدمة: %s)", "Listas IPTV (en uso: %s)", "Listas IPTV (em uso: %s)", "Playlist IPTV (dipakai: %s)", "IPTV 播放列表（当前：%s）", "IPTV-плейлисты (используется: %s)", "IPTV listeleri (kullanılan: %s)", "IPTV پلے لسٹس (زیر استعمال: %s)");
        e("Add an IPTV playlist", "إضافة قائمة IPTV", "Añadir una lista IPTV", "Adicionar uma lista IPTV", "Tambah playlist IPTV", "添加 IPTV 播放列表", "Добавить IPTV-плейлист", "IPTV listesi ekle", "IPTV پلے لسٹ شامل کریں");
        e("Add a playlist", "إضافة قائمة", "Añadir una lista", "Adicionar uma lista", "Tambah playlist", "添加播放列表", "Добавить плейлист", "Liste ekle", "پلے لسٹ شامل کریں");
        e("Use this playlist", "استخدام هذه القائمة", "Usar esta lista", "Usar esta lista", "Gunakan playlist ini", "使用此播放列表", "Использовать этот плейлист", "Bu listeyi kullan", "یہ پلے لسٹ استعمال کریں");
        e("Edit name or sign-in", "تعديل الاسم أو بيانات الدخول", "Editar nombre o inicio de sesión", "Editar nome ou login", "Ubah nama atau info masuk", "编辑名称或登录信息", "Изменить имя или данные входа", "Adı veya girişi düzenle", "نام یا سائن اِن میں ترمیم کریں");
        e("Remove this playlist", "إزالة هذه القائمة", "Quitar esta lista", "Remover esta lista", "Hapus playlist ini", "移除此播放列表", "Удалить этот плейлист", "Bu listeyi kaldır", "یہ پلے لسٹ ہٹائیں");
        e("%s (in use)", "%s (قيد الاستخدام)", "%s (en uso)", "%s (em uso)", "%s (sedang dipakai)", "%s（使用中）", "%s (используется)", "%s (kullanımda)", "%s (زیر استعمال)");
        e("Now using %s", "يتم الآن استخدام %s", "Ahora usando %s", "Agora usando %s", "Sekarang memakai %s", "现在使用 %s", "Теперь используется %s", "Şimdi %s kullanılıyor", "اب %s استعمال ہو رہی ہے");
        e("Remove %s?", "إزالة %s؟", "¿Quitar %s?", "Remover %s?", "Hapus %s?", "移除 %s？", "Удалить %s?", "%s kaldırılsın mı?", "%s ہٹائیں؟");
        e("Its sign-in, Continue Watching and favourites are deleted from this stick. Your account with the provider isn't affected.",
                "ستُحذف بيانات الدخول ومتابعة المشاهدة والمفضلة الخاصة بها من هذا الجهاز. لن يتأثر حسابك لدى المزوّد.",
                "Se eliminarán de este dispositivo su inicio de sesión, Seguir viendo y favoritos. Tu cuenta con el proveedor no se verá afectada.",
                "O login, Continuar assistindo e os favoritos dela serão apagados deste dispositivo. Sua conta no provedor não é afetada.",
                "Info masuk, Lanjutkan menonton, dan favoritnya akan dihapus dari perangkat ini. Akun Anda di penyedia tidak terpengaruh.",
                "它的登录信息、继续观看和收藏将从此设备中删除。你在服务商处的账户不受影响。",
                "Данные входа, «Продолжить просмотр» и избранное этого плейлиста будут удалены с устройства. Ваш аккаунт у провайдера не затрагивается.",
                "Giriş bilgileri, İzlemeye devam et listesi ve favorileri bu cihazdan silinir. Sağlayıcıdaki hesabınız etkilenmez.",
                "اس کی سائن اِن معلومات، دیکھنا جاری رکھیں اور پسندیدہ اس ڈیوائس سے حذف ہو جائیں گے۔ فراہم کنندہ کے پاس آپ کا اکاؤنٹ متاثر نہیں ہوگا۔");
        e("%s removed", "تمت إزالة %s", "Se quitó %s", "Removido: %s", "%s dihapus", "已移除 %s", "Удалено: %s", "%s kaldırıldı", "%s ہٹا دی گئی");

        // ---- Sign-in ----
        e("Edit %s", "تعديل %s", "Editar %s", "Editar %s", "Ubah %s", "编辑 %s", "Изменить %s", "%s düzenle", "%s میں ترمیم کریں");
        e("Xtream Codes details from your IPTV provider.", "بيانات Xtream Codes من مزوّد IPTV الخاص بك.", "Datos de Xtream Codes de tu proveedor IPTV.", "Dados do Xtream Codes do seu provedor IPTV.", "Detail Xtream Codes dari penyedia IPTV Anda.", "来自 IPTV 服务商的 Xtream Codes 信息。", "Данные Xtream Codes от вашего IPTV-провайдера.", "IPTV sağlayıcınızdan Xtream Codes bilgileri.", "آپ کے IPTV فراہم کنندہ کی Xtream Codes تفصیلات۔");
        e("Leave the password empty to keep the saved one.", "اترك كلمة المرور فارغة للإبقاء على المحفوظة.", "Deja la contraseña vacía para conservar la guardada.", "Deixe a senha em branco para manter a salva.", "Kosongkan kata sandi untuk memakai yang tersimpan.", "密码留空则保留已保存的密码。", "Оставьте пароль пустым, чтобы сохранить прежний.", "Kayıtlı şifreyi korumak için şifreyi boş bırakın.", "محفوظ پاس ورڈ رکھنے کے لیے پاس ورڈ خالی چھوڑ دیں۔");
        e("Name (optional, for example Home or Sports)", "الاسم (اختياري، مثل المنزل أو الرياضة)", "Nombre (opcional, por ejemplo Casa o Deportes)", "Nome (opcional, por exemplo Casa ou Esportes)", "Nama (opsional, misalnya Rumah atau Olahraga)", "名称（可选，例如“家庭”或“体育”）", "Название (необязательно, например «Дом» или «Спорт»)", "Ad (isteğe bağlı, örneğin Ev veya Spor)", "نام (اختیاری، مثلاً گھر یا اسپورٹس)");
        e("Server address (for example http://example.com:8080)", "عنوان الخادم (مثل http://example.com:8080)", "Dirección del servidor (por ejemplo http://example.com:8080)", "Endereço do servidor (por exemplo http://example.com:8080)", "Alamat server (misalnya http://example.com:8080)", "服务器地址（例如 http://example.com:8080）", "Адрес сервера (например http://example.com:8080)", "Sunucu adresi (örneğin http://example.com:8080)", "سرور کا پتہ (مثلاً http://example.com:8080)");
        e("Username", "اسم المستخدم", "Usuario", "Usuário", "Nama pengguna", "用户名", "Имя пользователя", "Kullanıcı adı", "صارف نام");
        e("Password", "كلمة المرور", "Contraseña", "Senha", "Kata sandi", "密码", "Пароль", "Şifre", "پاس ورڈ");
        e("Sign in and add", "تسجيل الدخول والإضافة", "Iniciar sesión y añadir", "Entrar e adicionar", "Masuk dan tambahkan", "登录并添加", "Войти и добавить", "Giriş yap ve ekle", "سائن اِن کر کے شامل کریں");
        e("Save", "حفظ", "Guardar", "Salvar", "Simpan", "保存", "Сохранить", "Kaydet", "محفوظ کریں");
        e("Please fill in the server, username and password.", "يرجى إدخال الخادم واسم المستخدم وكلمة المرور.", "Completa el servidor, el usuario y la contraseña.", "Preencha o servidor, o usuário e a senha.", "Isi server, nama pengguna, dan kata sandi.", "请填写服务器、用户名和密码。", "Заполните адрес сервера, имя пользователя и пароль.", "Lütfen sunucu, kullanıcı adı ve şifreyi girin.", "براہ کرم سرور، صارف نام اور پاس ورڈ درج کریں۔");
        e("Checking with your provider...", "جارٍ التحقق مع مزوّدك...", "Comprobando con tu proveedor...", "Verificando com seu provedor...", "Memeriksa ke penyedia Anda...", "正在向服务商验证...", "Проверяем у провайдера...", "Sağlayıcınızla kontrol ediliyor...", "فراہم کنندہ سے تصدیق ہو رہی ہے...");
        e("The provider rejected these details. Check them and try again.", "رفض المزوّد هذه البيانات. تحقق منها وحاول مرة أخرى.", "El proveedor rechazó estos datos. Revísalos y vuelve a intentarlo.", "O provedor recusou esses dados. Confira e tente de novo.", "Penyedia menolak detail ini. Periksa lalu coba lagi.", "服务商拒绝了这些信息。请检查后重试。", "Провайдер отклонил эти данные. Проверьте их и попробуйте снова.", "Sağlayıcı bu bilgileri reddetti. Kontrol edip tekrar deneyin.", "فراہم کنندہ نے یہ تفصیلات مسترد کر دیں۔ جانچ کر دوبارہ کوشش کریں۔");
        e("Signed in (%1$s%2$s).", "تم تسجيل الدخول (%1$s%2$s).", "Sesión iniciada (%1$s%2$s).", "Conectado (%1$s%2$s).", "Berhasil masuk (%1$s%2$s).", "已登录（%1$s%2$s）。", "Вход выполнен (%1$s%2$s).", "Giriş yapıldı (%1$s%2$s).", "سائن اِن ہو گیا (%1$s%2$s)۔");
        e(", valid until %s", "، صالح حتى %s", ", válido hasta %s", ", válido até %s", ", berlaku hingga %s", "，有效期至 %s", ", действует до %s", ", %s tarihine kadar geçerli", "، %s تک کارآمد");
        e("The provider didn't accept this username and password. They are case-sensitive - check them and try again.",
                "لم يقبل المزوّد اسم المستخدم وكلمة المرور هذين. إنهما حساسان لحالة الأحرف - تحقق منهما وحاول مرة أخرى.",
                "El proveedor no aceptó este usuario y contraseña. Distinguen mayúsculas y minúsculas: revísalos y vuelve a intentarlo.",
                "O provedor não aceitou este usuário e senha. Eles diferenciam maiúsculas de minúsculas - confira e tente de novo.",
                "Penyedia tidak menerima nama pengguna dan kata sandi ini. Huruf besar/kecil berpengaruh - periksa lalu coba lagi.",
                "服务商不接受此用户名和密码。它们区分大小写，请检查后重试。",
                "Провайдер не принял это имя пользователя и пароль. Регистр букв важен — проверьте и попробуйте снова.",
                "Sağlayıcı bu kullanıcı adı ve şifreyi kabul etmedi. Büyük/küçük harfe duyarlıdırlar - kontrol edip tekrar deneyin.",
                "فراہم کنندہ نے یہ صارف نام اور پاس ورڈ قبول نہیں کیا۔ بڑے اور چھوٹے حروف کا فرق اہم ہے - جانچ کر دوبارہ کوشش کریں۔");
        e("Could not reach the provider: %s", "تعذّر الوصول إلى المزوّد: %s", "No se pudo contactar con el proveedor: %s", "Não foi possível acessar o provedor: %s", "Tidak dapat menghubungi penyedia: %s", "无法连接服务商：%s", "Не удалось связаться с провайдером: %s", "Sağlayıcıya ulaşılamadı: %s", "فراہم کنندہ تک رسائی نہیں ہو سکی: %s");

        // ---- M3U playlists ----
        e("M3U link", "رابط M3U", "Enlace M3U", "Link M3U", "Tautan M3U", "M3U 链接", "Ссылка M3U", "M3U bağlantısı", "M3U لنک");
        e("Playlist link (for example http://example.com/playlist.m3u)", "رابط القائمة (مثل http://example.com/playlist.m3u)", "Enlace de la lista (por ejemplo http://example.com/playlist.m3u)", "Link da lista (por exemplo http://example.com/playlist.m3u)", "Tautan playlist (misalnya http://example.com/playlist.m3u)", "播放列表链接（例如 http://example.com/playlist.m3u）", "Ссылка на плейлист (например http://example.com/playlist.m3u)", "Liste bağlantısı (örneğin http://example.com/playlist.m3u)", "پلے لسٹ لنک (مثلاً http://example.com/playlist.m3u)");
        e("An M3U playlist link from your IPTV provider.", "رابط قائمة M3U من مزوّد IPTV الخاص بك.", "Un enlace de lista M3U de tu proveedor IPTV.", "Um link de lista M3U do seu provedor IPTV.", "Tautan playlist M3U dari penyedia IPTV Anda.", "来自 IPTV 服务商的 M3U 播放列表链接。", "Ссылка на M3U-плейлист от вашего IPTV-провайдера.", "IPTV sağlayıcınızdan bir M3U liste bağlantısı.", "آپ کے IPTV فراہم کنندہ کا M3U پلے لسٹ لنک۔");
        e("Please fill in the playlist link.", "يرجى إدخال رابط القائمة.", "Escribe el enlace de la lista.", "Preencha o link da lista.", "Isi tautan playlist.", "请填写播放列表链接。", "Укажите ссылку на плейлист.", "Lütfen liste bağlantısını girin.", "براہ کرم پلے لسٹ لنک درج کریں۔");
        e("Loading the playlist...", "جارٍ تحميل القائمة...", "Cargando la lista...", "Carregando a lista...", "Memuat playlist...", "正在加载播放列表...", "Загрузка плейлиста...", "Liste yükleniyor...", "پلے لسٹ لوڈ ہو رہی ہے...");
        e("This link didn't give a playlist with any channels or videos.", "هذا الرابط لم يُرجع قائمة تحتوي على قنوات أو فيديوهات.", "Este enlace no devolvió una lista con canales o vídeos.", "Este link não trouxe uma lista com canais ou vídeos.", "Tautan ini tidak berisi saluran atau video.", "此链接没有返回包含频道或视频的播放列表。", "По этой ссылке нет плейлиста с каналами или видео.", "Bu bağlantı kanal veya video içeren bir liste vermedi.", "اس لنک سے چینلز یا ویڈیوز والی کوئی پلے لسٹ نہیں ملی۔");
        e("Loaded: %d channels and videos", "تم التحميل: %d من القنوات والفيديوهات", "Cargados: %d canales y vídeos", "Carregados: %d canais e vídeos", "Dimuat: %d saluran dan video", "已加载：%d 个频道和视频", "Загружено: %d каналов и видео", "Yüklendi: %d kanal ve video", "لوڈ ہو گئے: %d چینلز اور ویڈیوز");

        // ---- Browse (Live TV / Movies / Series) ----
        e("Search", "بحث", "Buscar", "Buscar", "Cari", "搜索", "Поиск", "Ara", "تلاش");
        e("Favourites", "المفضلة", "Favoritos", "Favoritos", "Favorit", "收藏", "Избранное", "Favoriler", "پسندیدہ");
        e("Recently watched", "شوهد مؤخرًا", "Vistos recientemente", "Assistidos recentemente", "Baru ditonton", "最近观看", "Недавно просмотренные", "Son izlenenler", "حال ہی میں دیکھے گئے");
        e("CATEGORIES", "الفئات", "CATEGORÍAS", "CATEGORIAS", "KATEGORI", "分类", "КАТЕГОРИИ", "KATEGORİLER", "زمرے");
        e("SORT", "الترتيب", "ORDENAR", "ORDENAR", "URUTKAN", "排序", "СОРТИРОВКА", "SIRALA", "ترتیب");
        e("Default", "الافتراضي", "Predeterminado", "Padrão", "Default", "默认", "По умолчанию", "Varsayılan", "طے شدہ");
        e("A to Z", "أ إلى ي", "A a Z", "A a Z", "A ke Z", "A 到 Z", "А–Я", "A'dan Z'ye", "الف تا ی");
        e("Z to A", "ي إلى أ", "Z a A", "Z a A", "Z ke A", "Z 到 A", "Я–А", "Z'den A'ya", "ی تا الف");
        e("Recently added", "المضاف حديثًا", "Añadidos recientemente", "Adicionados recentemente", "Baru ditambahkan", "最近添加", "Недавно добавленные", "Son eklenenler", "حال ہی میں شامل");
        e("Newest", "الأحدث", "Más nuevos", "Mais novos", "Terbaru", "最新", "Новые", "En yeni", "تازہ ترین");
        e("Top rated", "الأعلى تقييمًا", "Mejor valorados", "Mais bem avaliados", "Rating tertinggi", "评分最高", "С высоким рейтингом", "En yüksek puanlı", "سب سے زیادہ ریٹنگ");
        e("channels", "القنوات", "canales", "canais", "saluran", "频道", "каналы", "kanallar", "چینلز");
        e("movies", "الأفلام", "películas", "filmes", "film", "电影", "фильмы", "filmler", "فلمیں");
        e("series", "المسلسلات", "series", "séries", "serial", "剧集", "сериалы", "diziler", "سیریز");
        e("Type to search %s", "اكتب للبحث في %s", "Escribe para buscar %s", "Digite para buscar %s", "Ketik untuk mencari %s", "输入以搜索%s", "Введите текст для поиска (%s)", "%s içinde aramak için yazın", "%s تلاش کرنے کے لیے ٹائپ کریں");
        e("Loading...", "جارٍ التحميل...", "Cargando...", "Carregando...", "Memuat...", "正在加载...", "Загрузка...", "Yükleniyor...", "لوڈ ہو رہا ہے...");
        e("Loading categories...", "جارٍ تحميل الفئات...", "Cargando categorías...", "Carregando categorias...", "Memuat kategori...", "正在加载分类...", "Загрузка категорий...", "Kategoriler yükleniyor...", "زمرے لوڈ ہو رہے ہیں...");
        e("Could not load categories: %s", "تعذّر تحميل الفئات: %s", "No se pudieron cargar las categorías: %s", "Não foi possível carregar as categorias: %s", "Gagal memuat kategori: %s", "无法加载分类：%s", "Не удалось загрузить категории: %s", "Kategoriler yüklenemedi: %s", "زمرے لوڈ نہیں ہو سکے: %s");
        e("Your provider has no categories here.", "لا توجد فئات لدى مزوّدك هنا.", "Tu proveedor no tiene categorías aquí.", "Seu provedor não tem categorias aqui.", "Penyedia Anda tidak memiliki kategori di sini.", "你的服务商在此没有分类。", "У вашего провайдера здесь нет категорий.", "Sağlayıcınızın burada kategorisi yok.", "آپ کے فراہم کنندہ کے پاس یہاں کوئی زمرہ نہیں۔");
        e("%s - loading...", "%s - جارٍ التحميل...", "%s - cargando...", "%s - carregando...", "%s - memuat...", "%s - 正在加载...", "%s - загрузка...", "%s - yükleniyor...", "%s - لوڈ ہو رہا ہے...");
        e("Could not load %1$s: %2$s", "تعذّر تحميل %1$s: %2$s", "No se pudo cargar %1$s: %2$s", "Não foi possível carregar %1$s: %2$s", "Gagal memuat %1$s: %2$s", "无法加载 %1$s：%2$s", "Не удалось загрузить %1$s: %2$s", "%1$s yüklenemedi: %2$s", "%1$s لوڈ نہیں ہو سکا: %2$s");
        e("%1$d %2$s ready to search.", "جاهز للبحث: %1$d من %2$s.", "Disponibles para buscar: %1$d %2$s.", "Disponíveis para busca: %1$d %2$s.", "Siap dicari: %1$d %2$s.", "可搜索的%2$s：%1$d 个。", "Доступно для поиска: %1$d (%2$s).", "Aramaya hazır: %1$d %2$s.", "تلاش کے لیے تیار: %1$d %2$s۔");
        e("Getting today's list of %1$s (once a day): %2$.1f MB so far - you can type now.",
                "جارٍ جلب قائمة %1$s لليوم (مرة يوميًا): %2$.1f MB حتى الآن - يمكنك الكتابة الآن.",
                "Obteniendo la lista de %1$s de hoy (una vez al día): %2$.1f MB hasta ahora - ya puedes escribir.",
                "Baixando a lista de %1$s de hoje (uma vez por dia): %2$.1f MB até agora - você já pode digitar.",
                "Mengambil daftar %1$s hari ini (sekali sehari): %2$.1f MB sejauh ini - Anda bisa mengetik sekarang.",
                "正在获取今天的%1$s列表（每天一次）：已下载 %2$.1f MB - 现在可以输入了。",
                "Загружаем сегодняшний список (%1$s, раз в день): уже %2$.1f МБ — можно начинать вводить.",
                "Bugünün %1$s listesi alınıyor (günde bir kez): şimdiye kadar %2$.1f MB - şimdi yazabilirsiniz.",
                "آج کی %1$s فہرست لی جا رہی ہے (دن میں ایک بار): اب تک %2$.1f MB - آپ ابھی ٹائپ کر سکتے ہیں۔");
        e("Could not get the list for searching: %s", "تعذّر جلب قائمة البحث: %s", "No se pudo obtener la lista para buscar: %s", "Não foi possível obter a lista para busca: %s", "Gagal mengambil daftar untuk pencarian: %s", "无法获取搜索列表：%s", "Не удалось получить список для поиска: %s", "Arama listesi alınamadı: %s", "تلاش کی فہرست نہیں مل سکی: %s");
        e("Search all %s by name.", "ابحث في كل %s بالاسم.", "Busca %s por nombre.", "Busque %s pelo nome.", "Cari semua %s berdasarkan nama.", "按名称搜索所有%s。", "Поиск по названию (%s).", "Tüm %s içinde ada göre arayın.", "تمام %s میں نام سے تلاش کریں۔");
        e("the list is too large for this device", "القائمة كبيرة جدًا على هذا الجهاز", "la lista es demasiado grande para este dispositivo", "a lista é grande demais para este dispositivo", "daftar terlalu besar untuk perangkat ini", "列表太大，此设备无法处理", "список слишком велик для этого устройства", "liste bu cihaz için çok büyük", "فہرست اس ڈیوائس کے لیے بہت بڑی ہے");
        e("Type at least 2 letters.", "اكتب حرفين على الأقل.", "Escribe al menos 2 letras.", "Digite pelo menos 2 letras.", "Ketik minimal 2 huruf.", "请至少输入 2 个字。", "Введите не менее 2 букв.", "En az 2 harf yazın.", "کم از کم 2 حروف لکھیں۔");
        e("Results for \"%s\"", "نتائج \"%s\"", "Resultados de \"%s\"", "Resultados para \"%s\"", "Hasil untuk \"%s\"", "“%s”的结果", "Результаты по запросу «%s»", "\"%s\" için sonuçlar", "\"%s\" کے نتائج");
        e("No favourites yet. Hold OK on anything (or use the Favourite button on a movie or series page).",
                "لا توجد مفضلة بعد. اضغط مطولًا على موافق فوق أي عنصر (أو استخدم زر المفضلة في صفحة الفيلم أو المسلسل).",
                "Aún no hay favoritos. Mantén pulsado OK sobre cualquier elemento (o usa el botón Favorito en la página de una película o serie).",
                "Ainda não há favoritos. Mantenha OK pressionado em qualquer item (ou use o botão Favorito na página de um filme ou série).",
                "Belum ada favorit. Tahan OK pada item apa pun (atau gunakan tombol Favorit di halaman film atau serial).",
                "还没有收藏。在任意项目上长按确定键（或在电影或剧集页面使用收藏按钮）。",
                "Избранного пока нет. Удерживайте OK на любом элементе (или нажмите «В избранное» на странице фильма или сериала).",
                "Henüz favori yok. Herhangi bir öğede OK'u basılı tutun (veya film ya da dizi sayfasındaki Favori düğmesini kullanın).",
                "ابھی کوئی پسندیدہ نہیں۔ کسی بھی چیز پر OK دبائے رکھیں (یا فلم یا سیریز کے صفحے پر پسندیدہ بٹن استعمال کریں)۔");
        e("Nothing watched here yet.", "لم تتم مشاهدة أي شيء هنا بعد.", "Aún no has visto nada aquí.", "Nada assistido aqui ainda.", "Belum ada yang ditonton di sini.", "这里还没有观看记录。", "Здесь пока ничего не просмотрено.", "Burada henüz bir şey izlenmedi.", "یہاں ابھی کچھ نہیں دیکھا گیا۔");
        e("(showing the first %d)", "(يُعرض أول %d)", "(se muestran los primeros %d)", "(mostrando os primeiros %d)", "(menampilkan %d pertama)", "（显示前 %d 个）", "(показаны первые %d)", "(ilk %d gösteriliyor)", "(پہلے %d دکھائے جا رہے ہیں)");
        e("hold OK: favourite", "اضغط مطولًا على موافق: مفضلة", "mantén OK: favorito", "segure OK: favorito", "tahan OK: favorit", "长按确定键：收藏", "удерживайте OK: в избранное", "OK'u basılı tut: favori", "OK دبائے رکھیں: پسندیدہ");
        e("Press OK on a channel to preview it", "اضغط موافق على قناة لمعاينتها", "Pulsa OK en un canal para verlo en vista previa", "Aperte OK em um canal para pré-visualizá-lo", "Tekan OK pada saluran untuk melihat pratinjaunya", "在频道上按确定键即可预览", "Нажмите OK на канале для предпросмотра", "Önizlemek için bir kanalda OK'a basın", "پیش نظارہ کے لیے کسی چینل پر OK دبائیں");
        e("Press OK again for full screen", "اضغط موافق مرة أخرى لملء الشاشة", "Pulsa OK otra vez para pantalla completa", "Aperte OK de novo para tela cheia", "Tekan OK lagi untuk layar penuh", "再按一次确定键全屏播放", "Нажмите OK ещё раз для полноэкранного режима", "Tam ekran için tekrar OK'a basın", "فل اسکرین کے لیے دوبارہ OK دبائیں");
        e("This channel won't play right now.", "لا يمكن تشغيل هذه القناة الآن.", "Este canal no se puede reproducir ahora.", "Este canal não pode ser reproduzido agora.", "Saluran ini tidak dapat diputar sekarang.", "此频道目前无法播放。", "Этот канал сейчас не воспроизводится.", "Bu kanal şu anda oynatılamıyor.", "یہ چینل ابھی نہیں چل سکتا۔");
        e("Live", "مباشر", "En directo", "Ao vivo", "Langsung", "直播", "Эфир", "Canlı", "لائیو");
        e("Watched", "تمت المشاهدة", "Visto", "Assistido", "Sudah ditonton", "已看完", "Просмотрено", "İzlendi", "دیکھ لیا");
        e("%s added to Favourites", "تمت إضافة %s إلى المفضلة", "%s añadido a Favoritos", "%s adicionado aos Favoritos", "%s ditambahkan ke Favorit", "已将 %s 添加到收藏", "%s добавлено в избранное", "%s favorilere eklendi", "%s پسندیدہ میں شامل ہو گیا");
        e("%s removed from Favourites", "تمت إزالة %s من المفضلة", "%s quitado de Favoritos", "%s removido dos Favoritos", "%s dihapus dari Favorit", "已将 %s 从收藏中移除", "%s удалено из избранного", "%s favorilerden kaldırıldı", "%s پسندیدہ سے ہٹا دیا گیا");

        // ---- Movie and series pages ----
        e("Play", "تشغيل", "Reproducir", "Reproduzir", "Putar", "播放", "Смотреть", "Oynat", "چلائیں");
        e("Start over", "البدء من جديد", "Empezar de nuevo", "Começar de novo", "Mulai dari awal", "从头播放", "Сначала", "Baştan başla", "شروع سے چلائیں");
        e("Resume (%s)", "استئناف (%s)", "Reanudar (%s)", "Retomar (%s)", "Lanjutkan (%s)", "继续播放（%s）", "Продолжить (%s)", "Devam et (%s)", "دوبارہ شروع (%s)");
        e("In Favourites", "في المفضلة", "En Favoritos", "Nos Favoritos", "Di Favorit", "已收藏", "В избранном", "Favorilerde", "پسندیدہ میں");
        e("Add to Favourites", "إضافة إلى المفضلة", "Añadir a Favoritos", "Adicionar aos Favoritos", "Tambah ke Favorit", "加入收藏", "В избранное", "Favorilere ekle", "پسندیدہ میں شامل کریں");
        e("Added to Favourites", "تمت الإضافة إلى المفضلة", "Añadido a Favoritos", "Adicionado aos Favoritos", "Ditambahkan ke Favorit", "已添加到收藏", "Добавлено в избранное", "Favorilere eklendi", "پسندیدہ میں شامل ہو گیا");
        e("Removed from Favourites", "تمت الإزالة من المفضلة", "Quitado de Favoritos", "Removido dos Favoritos", "Dihapus dari Favorit", "已从收藏中移除", "Удалено из избранного", "Favorilerden kaldırıldı", "پسندیدہ سے ہٹا دیا گیا");
        e("Rating %s", "التقييم %s", "Valoración %s", "Nota %s", "Rating %s", "评分 %s", "Рейтинг %s", "Puan %s", "ریٹنگ %s");
        e("Loading episodes...", "جارٍ تحميل الحلقات...", "Cargando episodios...", "Carregando episódios...", "Memuat episode...", "正在加载剧集...", "Загрузка серий...", "Bölümler yükleniyor...", "اقساط لوڈ ہو رہی ہیں...");
        e("Could not load this series", "تعذّر تحميل هذا المسلسل", "No se pudo cargar esta serie", "Não foi possível carregar esta série", "Gagal memuat serial ini", "无法加载此剧集", "Не удалось загрузить сериал", "Bu dizi yüklenemedi", "یہ سیریز لوڈ نہیں ہو سکی");
        e("Season %s", "الموسم %s", "Temporada %s", "Temporada %s", "Musim %s", "第 %s 季", "Сезон %s", "Sezon %s", "سیزن %s");
        e("%d episodes", "%d حلقة", "%d episodios", "%d episódios", "%d episode", "%d 集", "Серий: %d", "%d bölüm", "%d اقساط");
        e("%d min", "%d دقيقة", "%d min", "%d min", "%d mnt", "%d 分钟", "%d мин", "%d dk", "%d منٹ");
        e("%1$d hr %2$d min left", "متبقٍ %1$d ساعة و%2$d دقيقة", "Quedan %1$d h %2$d min", "Faltam %1$d h %2$d min", "Sisa %1$d j %2$d mnt", "剩余 %1$d 小时 %2$d 分钟", "Осталось %1$d ч %2$d мин", "%1$d sa %2$d dk kaldı", "%1$d گھنٹے %2$d منٹ باقی");
        e("%d min left", "متبقٍ %d دقيقة", "Quedan %d min", "Faltam %d min", "Sisa %d mnt", "剩余 %d 分钟", "Осталось %d мин", "%d dk kaldı", "%d منٹ باقی");

        // ---- Player ----
        e("Add your IPTV playlist first", "أضف قائمة IPTV أولًا", "Primero añade tu lista IPTV", "Primeiro adicione sua lista IPTV", "Tambahkan playlist IPTV dulu", "请先添加 IPTV 播放列表", "Сначала добавьте IPTV-плейлист", "Önce IPTV listenizi ekleyin", "پہلے اپنی IPTV پلے لسٹ شامل کریں");
        e("Buffering...", "جارٍ التخزين المؤقت...", "Almacenando en búfer...", "Carregando buffer...", "Buffering...", "正在缓冲...", "Буферизация...", "Arabelleğe alınıyor...", "بفر ہو رہا ہے...");
        e("Trying the other stream format...", "جارٍ تجربة صيغة البث الأخرى...", "Probando el otro formato de emisión...", "Tentando o outro formato de transmissão...", "Mencoba format streaming lain...", "正在尝试另一种流格式...", "Пробуем другой формат потока...", "Diğer yayın biçimi deneniyor...", "دوسرا اسٹریم فارمیٹ آزمایا جا رہا ہے...");
        e("This stream won't play on this device (%s).", "لا يمكن تشغيل هذا البث على هذا الجهاز (%s).", "Esta emisión no se puede reproducir en este dispositivo (%s).", "Esta transmissão não pode ser reproduzida neste dispositivo (%s).", "Streaming ini tidak dapat diputar di perangkat ini (%s).", "此流无法在本设备上播放（%s）。", "Этот поток не воспроизводится на устройстве (%s).", "Bu yayın bu cihazda oynatılamıyor (%s).", "یہ اسٹریم اس ڈیوائس پر نہیں چل سکتی (%s)۔");
        e("Reconnecting...", "جارٍ إعادة الاتصال...", "Reconectando...", "Reconectando...", "Menyambung ulang...", "正在重新连接...", "Переподключение...", "Yeniden bağlanılıyor...", "دوبارہ جڑ رہا ہے...");
        e("Channel %1$d of %2$d", "القناة %1$d من %2$d", "Canal %1$d de %2$d", "Canal %1$d de %2$d", "Saluran %1$d dari %2$d", "第 %1$d 个频道，共 %2$d 个", "Канал %1$d из %2$d", "Kanal %1$d / %2$d", "چینل %1$d از %2$d");
        e("Skip intro", "تخطي المقدمة", "Saltar intro", "Pular abertura", "Lewati intro", "跳过片头", "Пропустить заставку", "Girişi atla", "تعارف چھوڑیں");
        e("Skipped %s. Left/Right now fine-tunes it for this show.", "تم تخطي %s. استخدم يسار/يمين الآن لضبطها لهذا العرض.", "Se saltaron %s. Izquierda/Derecha lo ajusta ahora para esta serie.", "Pulou %s. Esquerda/Direita agora ajusta isso para esta série.", "Dilewati %s. Kiri/Kanan sekarang menyesuaikannya untuk acara ini.", "已跳过 %s。现在按左/右键可为此剧微调。", "Пропущено %s. Влево/вправо — подстроить для этого сериала.", "%s atlandı. Sol/Sağ şimdi bu dizi için ayarlar.", "%s چھوڑ دیا گیا۔ اب بائیں/دائیں سے اس شو کے لیے درست کریں۔");
        e("Brightness", "السطوع", "Brillo", "Brilho", "Kecerahan", "亮度", "Яркость", "Parlaklık", "چمک");
        e("Left / Right to change   |   OK or Back to close", "يسار / يمين للتغيير   |   موافق أو رجوع للإغلاق", "Izquierda / Derecha para cambiar   |   OK o Atrás para cerrar", "Esquerda / Direita para mudar   |   OK ou Voltar para fechar", "Kiri / Kanan untuk mengubah   |   OK atau Kembali untuk menutup", "左 / 右键调节   |   确定或返回键关闭", "Влево / вправо — изменить   |   OK или «Назад» — закрыть", "Değiştirmek için Sol / Sağ   |   Kapatmak için OK veya Geri", "بدلنے کے لیے بائیں / دائیں   |   بند کرنے کے لیے OK یا واپس");

        // ---- All apps and errors ----
        e("All apps", "كل التطبيقات", "Todas las apps", "Todos os apps", "Semua aplikasi", "所有应用", "Все приложения", "Tüm uygulamalar", "تمام ایپس");
        e("Hold OK (or press the menu button) to add an app to the home row, or take it off.",
                "اضغط مطولًا على موافق (أو زر القائمة) لإضافة تطبيق إلى صف الشاشة الرئيسية أو إزالته.",
                "Mantén pulsado OK (o pulsa el botón de menú) para añadir una app a la fila de inicio o quitarla.",
                "Mantenha OK pressionado (ou aperte o botão de menu) para adicionar um app à linha inicial ou removê-lo.",
                "Tahan OK (atau tekan tombol menu) untuk menambahkan aplikasi ke baris beranda, atau menghapusnya.",
                "长按确定键（或按菜单键）可将应用添加到主屏幕行，或将其移除。",
                "Удерживайте OK (или нажмите кнопку меню), чтобы добавить приложение на главный экран или убрать его.",
                "Bir uygulamayı ana satıra eklemek veya kaldırmak için OK tuşunu basılı tutun (veya menü tuşuna basın).",
                "کسی ایپ کو ہوم قطار میں شامل کرنے یا ہٹانے کے لیے OK دبائے رکھیں (یا مینو بٹن دبائیں)۔");
        e("ON HOME", "في الرئيسية", "EN INICIO", "NO INÍCIO", "DI BERANDA", "已在主屏幕", "НА ГЛАВНОМ", "ANA EKRANDA", "ہوم پر");
        e("%s taken off the home row", "تمت إزالة %s من صف الشاشة الرئيسية", "%s se quitó de la fila de inicio", "%s foi removido da linha inicial", "%s dihapus dari baris beranda", "已将 %s 从主屏幕行移除", "%s убрано с главного экрана", "%s ana satırdan kaldırıldı", "%s ہوم قطار سے ہٹا دی گئی");
        e("%s added to the home row", "تمت إضافة %s إلى صف الشاشة الرئيسية", "%s se añadió a la fila de inicio", "%s foi adicionado à linha inicial", "%s ditambahkan ke baris beranda", "已将 %s 添加到主屏幕行", "%s добавлено на главный экран", "%s ana satıra eklendi", "%s ہوم قطار میں شامل کر دی گئی");
        e("Could not open that", "تعذّر فتح ذلك", "No se pudo abrir", "Não foi possível abrir", "Tidak dapat membuka", "无法打开", "Не удалось открыть", "Açılamadı", "کھولا نہیں جا سکا");
        e("Could not open Settings", "تعذّر فتح الإعدادات", "No se pudo abrir Ajustes", "Não foi possível abrir as Configurações", "Tidak dapat membuka Setelan", "无法打开设置", "Не удалось открыть настройки", "Ayarlar açılamadı", "سیٹنگز نہیں کھل سکیں");
    }
}
