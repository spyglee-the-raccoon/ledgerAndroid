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
