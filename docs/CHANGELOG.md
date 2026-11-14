# Everis 0.2-beta

## Что нового

- **Расписание:** добавлен ДГТУ и поиск расписания преподавателей. Исправлены загрузка занятий и определение подгрупп. Для РИНХ доступны полные ФИО преподавателей, если их удалось однозначно определить.
- **Навигация:** компактный плавающий док с возможностью вернуть классический вид. Долгое нажатие на активную вкладку возвращает в начало раздела; жест назад последовательно закрывает вложенные экраны.
- **Домашние задания:** при выборе следующего занятия можно отфильтровать лекции, практики и лабораторные.
- **Интерфейс:** закреплены заголовки, стабилизирован поиск групп, улучшены переключение языка и анимации. Барабаны времени получили инерцию.
- **Быстродействие:** ускорено открытие дисциплин, устранены вспышки дока и чёрный экран при смене языка.

---

## What’s new

- **Timetables:** DSTU support, teacher timetable search, corrected subgroup handling and RSUE teacher name matching.
- **Navigation:** compact floating dock with a classic option, long-press shortcuts to each section’s main screen and hierarchical Back navigation.
- **Homework:** filter the next class by lecture, practice or lab.
- **Interface:** fixed headings, stable group suggestions, smoother language changes and inertial time wheels.
- **Performance:** faster discipline lists and fixes for dock flashes and language-change blackouts.


---

# Everis 0.1-beta

## Русский

Первый публичный выпуск Everis. Android 12+, только ARM64. Пакет `org.everis.app`, внутренний код версии 8.

- Публичное расписание РГЭУ (РИНХ) с сохранением для работы без интернета; дни, недели и месяцы, подгруппы и статистика занятий.
- Домашние задания к следующей паре, список задач по дисциплинам, локальные напоминания и проверка изменений расписания.
- Светлая, тёмная и чёрная темы, динамический акцент Monet и собственные цвета; пять языков интерфейса.
- Локальный экспорт/импорт расписания и резервные копии JSON. Нет сервера приложения, аккаунтов и телеметрии.
- Исправлены направления и прерывание переходов, быстрые переключения вкладок, анимации переключателей и отклик кнопок. Убрана история страниц для системного «Назад».
- Добавлены ссылки проекта, проверка обновлений через GitHub Releases и единый раздел «О приложении».

Опубликованный APK подписан постоянным Release-ключом. Другие вузы пока не подключены. Android может задерживать фоновые проверки в режиме сна. Это ранняя бета: отзывы и ошибки принимаются через Issues и контакты в README.

## English

First public Everis release. Android 12+, ARM64 only. Package `org.everis.app`, Android versionCode 8.

- Public RSUE/RINH timetables saved for offline use, day/week/month views, subgroup filtering and study totals.
- Homework for the next class, discipline task lists, local reminders and schedule change checks.
- Light, dark and black themes, Monet dynamic colors and custom accents; five interface languages.
- Offline schedule sharing and JSON backups. No application server, accounts or telemetry.
- Fixed transition direction and interruption, rapid tab switching, settings switches and full-button touch feedback. System Back no longer visits a tab/page history.
- Project links, GitHub release checks and a unified About page.

The APK uses the permanent Release signing identity. Other universities are not supported yet. Android may defer background checks while sleeping. This is an early beta; report feedback through Issues or the README contacts.
