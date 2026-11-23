# Translations to review

The 190 strings shared with iOS are generated from the iOS catalog (`tools/sync-ios-strings.ps1`) and
were translated by the iOS team. The strings below exist only on Android (iOS gets them from the
system) and were translated during the port, without a native speaker. Please have them checked;
Polish was added with iOS `8a76b5d`.

They live in `app/src/main/res/values*/strings_android.xml`. `%1$s` is replaced by a value (for
`zoom_level`, e.g. "2×").

| Key | English | Croatian | Ukrainian | Polish |
|---|---|---|---|---|
| `back` | Back | Natrag | Назад | Wstecz |
| `cancel` | Cancel | Odustani | Скасувати | Anuluj |
| `remove_from_collection` | Remove from collection | Ukloni iz kolekcije | Вилучити з колекції | Usuń z kolekcji |
| `change_sign` | Change sign | Promijeni predznak | Змінити знак | Zmień znak |
| `change_date` | Change date | Promijeni datum | Змінити дату | Zmień datę |
| `selected` | Selected | Odabrano | Вибрано | Wybrano |
| `reminder_channel` | Daily reminder | Dnevni podsjetnik | Щоденне нагадування | Codzienne przypomnienie |
| `lock_unavailable` | Set up a screen lock on this device to lock the app. | Za zaključavanje aplikacije postavite zaključavanje zaslona na uređaju. | Щоб блокувати застосунок, налаштуйте блокування екрана на пристрої. | Ustaw blokadę ekranu na tym urządzeniu, aby zablokować aplikację. |
| `notifications_denied` | Notifications are off for this app. Turn them on in system settings. | Obavijesti su isključene za ovu aplikaciju. Uključite ih u postavkama sustava. | Сповіщення для цього застосунку вимкнено. Увімкніть їх у налаштуваннях системи. | Powiadomienia tej aplikacji są wyłączone. Włącz je w ustawieniach systemu. |
| `open_settings` | Settings | Postavke | Налаштування | Ustawienia |
| `take_photo` | Take photo | Snimi fotografiju | Зробити фото | Zrób zdjęcie |
| `retake_photo` | Retake | Ponovi | Перезняти | Powtórz |
| `use_photo` | Use photo | Koristi fotografiju | Використати фото | Użyj zdjęcia |
| `flash` | Flash | Bljeskalica | Спалах | Lampa błyskowa |
| `close` | Close | Zatvori | Закрити | Zamknij |
| `camera_denied` | Camera access is off for this app. Turn it on in system settings to scan receipts. | Pristup kameri isključen je za ovu aplikaciju. Uključite ga u postavkama sustava da biste skenirali račune. | Доступ до камери для цього застосунку вимкнено. Увімкніть його в налаштуваннях системи, щоб сканувати чеки. | Dostęp do aparatu jest wyłączony dla tej aplikacji. Włącz go w ustawieniach systemu, aby skanować paragony. |
| `zoom_level` | Zoom %1$s | Povećanje %1$s | Масштаб %1$s | Powiększenie %1$s |
| `previous_month` | Previous month | Prethodni mjesec | Попередній місяць | Poprzedni miesiąc |
| `next_month` | Next month | Sljedeći mjesec | Наступний місяць | Następny miesiąc |
| `increase` | Increase | Povećaj | Збільшити | Zwiększ |
| `decrease` | Decrease | Smanji | Зменшити | Zmniejsz |
| `exact_alarms_title` | Exact timing | Točno vrijeme | Точний час | Dokładny czas |
| `exact_alarms_message` | Allow Ledger to use alarms, so planned transactions are recorded and reminders arrive right on time. Without them, they can come a few minutes late. | Dopustite Ledgeru korištenje alarma kako bi se planirane transakcije bilježile, a podsjetnici stizali točno na vrijeme. Bez toga mogu kasniti nekoliko minuta. | Дозвольте Ledger використовувати будильники, щоб заплановані транзакції записувалися, а нагадування надходили вчасно. Без цього вони можуть запізнюватися на кілька хвилин. | Pozwól aplikacji Ledger używać alarmów, aby zaplanowane transakcje były zapisywane, a przypomnienia przychodziły punktualnie. Bez tego mogą się spóźniać o kilka minut. |
| `exact_alarms_allow` | Allow | Dopusti | Дозволити | Zezwól |
| `not_now` | Not now | Ne sada | Не зараз | Nie teraz |
| `allow_exact_alarms` | Allow exact timing | Dopusti točno vrijeme | Дозволити точний час | Zezwól na dokładny czas |
| `widget_locked` | Hidden while the app lock is on | Skriveno dok je uključeno zaključavanje aplikacije | Приховано, поки ввімкнено блокування застосунку | Ukryte, gdy blokada aplikacji jest włączona |
| `pro_price_per_month` | %1$s / month | %1$s mjesečno | %1$s на місяць | %1$s miesięcznie |
| `pro_price_per_year` | %1$s / year | %1$s godišnje | %1$s на рік | %1$s rocznie |
| `pro_subscribe` | Subscribe | Pretplati se | Підписатися | Subskrybuj |
| `pro_renewal_note` | Renews automatically until you cancel it in Google Play. | Obnavlja se automatski dok je ne otkažete u Google Playu. | Поновлюється автоматично, доки ви не скасуєте підписку в Google Play. | Odnawia się automatycznie, dopóki nie anulujesz jej w Google Play. |
| `pro_restore` | Restore purchases | Vrati kupnje | Відновити покупки | Przywróć zakupy |
| `pro_restore_none` | No subscription was found for this Google account. | Za ovaj Google račun nije pronađena pretplata. | Для цього облікового запису Google підписку не знайдено. | Nie znaleziono subskrypcji dla tego konta Google. |
| `pro_terms` | Terms of Service | Uvjeti korištenja | Умови використання | Warunki korzystania |
| `pro_privacy` | Privacy Policy | Pravila privatnosti | Політика конфіденційності | Polityka prywatności |
| `pro_unavailable` | Subscriptions aren't available right now. Check that Google Play is installed and you're signed in. | Pretplate trenutačno nisu dostupne. Provjerite je li Google Play instaliran i jeste li prijavljeni. | Підписки зараз недоступні. Перевірте, чи встановлено Google Play і чи ви ввійшли в обліковий запис. | Subskrypcje są teraz niedostępne. Sprawdź, czy Google Play jest zainstalowany i czy jesteś zalogowany. |
| `pro_try_again` | Try again | Pokušaj ponovno | Спробувати ще раз | Spróbuj ponownie |
| `pro_pending` | Your payment is being processed. Statistics unlock once Google Play confirms it. | Plaćanje se obrađuje. Statistika se otključava kad ga Google Play potvrdi. | Платіж обробляється. Статистика відкриється, щойно Google Play його підтвердить. | Płatność jest przetwarzana. Statystyki odblokują się, gdy Google Play ją potwierdzi. |
| `pro_failed` | The purchase didn't go through. Please try again. | Kupnja nije uspjela. Pokušajte ponovno. | Не вдалося здійснити покупку. Спробуйте ще раз. | Zakup się nie powiódł. Spróbuj ponownie. |
| `pro_manage` | Manage subscription | Upravljanje pretplatom | Керувати підпискою | Zarządzaj subskrypcją |
| `pro_free_trial_days` | one: %d day free / other: %d days free | one: %d dan besplatno / few: %d dana besplatno / other: %d dana besplatno | one: %d день безкоштовно / few: %d дні безкоштовно / many: %d днів безкоштовно / other: %d дня безкоштовно | one: %d dzień za darmo / few: %d dni za darmo / many: %d dni za darmo / other: %d dnia za darmo |
| `pro_free_trial_weeks` | one: %d week free / other: %d weeks free | one: %d tjedan besplatno / few: %d tjedna besplatno / other: %d tjedana besplatno | one: %d тиждень безкоштовно / few: %d тижні безкоштовно / many: %d тижнів безкоштовно / other: %d тижня безкоштовно | one: %d tydzień za darmo / few: %d tygodnie za darmo / many: %d tygodni za darmo / other: %d tygodnia za darmo |
| `pro_free_trial_months` | one: %d month free / other: %d months free | one: %d mjesec besplatno / few: %d mjeseca besplatno / other: %d mjeseci besplatno | one: %d місяць безкоштовно / few: %d місяці безкоштовно / many: %d місяців безкоштовно / other: %d місяця безкоштовно | one: %d miesiąc za darmo / few: %d miesiące za darmo / many: %d miesięcy za darmo / other: %d miesiąca za darmo |

The `pro_` strings are for Ledger Pro (decision D1): the paywall and "Manage subscription". `%1$s` in `pro_price_per_month` and `pro_price_per_year` is Google Play's price, such as "€2.99"; the trial strings are plurals, shown as quantity: text.
