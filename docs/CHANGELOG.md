# Everis 0.3-beta

## English

### What’s new since 0.2-beta

- Added group timetables for SFedU ICTIS, RGUPS and Southern University (IMBL). ICTIS also supports teacher timetables. For full-time RGUPS groups, Everis shows the current and following week based on the university’s two-week cycle. All three sources support offline viewing after the first download.
- On compact screens or with large text, you can collapse the timetable summary to leave more room for the calendar.
- Universities are listed alphabetically during group selection. Language, appearance, haptics and performance settings are now together under **App → Interface**.
- The update dialog shows release notes in the app language, with English as the fallback.

### Fixes

- The week arrows now jump directly to Monday in the selected week. The date and daily totals update together, fixing the flicker when switching days.
- The day selector adapts to screen width and font size. When tab labels cannot fit, the floating dock shows icons instead of cutting off names.
- The subgroup filter and notification switches now respond as soon as you tap them. The dock and switches follow theme changes without a color delay.

---

## Русский

### Что нового по сравнению с 0.2-beta

- Добавлены расписания групп ИКТИБ ЮФУ, РГУПС и Южного университета (ИУБиП). Для ИКТИБ доступно и расписание преподавателей. Для очных групп РГУПС Everis показывает текущую и следующую недели по двухнедельному циклу вуза. После первой загрузки расписания всех трёх вузов доступны без интернета.
- На небольшом экране или при крупном шрифте сводку занятий можно свернуть, чтобы освободить место для календаря.
- При выборе группы вузы перечислены по алфавиту. Язык, оформление, тактильная отдача и режим повышенной производительности собраны в разделе **Приложение → Интерфейс**.
- В окне обновления список изменений отображается на языке приложения. Если перевода нет, показывается английский текст.

### Исправления

- Стрелки недели сразу открывают понедельник выбранной недели. При смене дня дата и сводка обновляются вместе, поэтому заголовок больше не мелькает.
- Панель дней учитывает ширину экрана и размер шрифта. Если подписи вкладок не помещаются, на плавающей панели остаются иконки без обрезанных названий.
- Переключатели фильтра подгруппы и уведомлений реагируют сразу при нажатии. При смене темы нижняя панель и переключатели меняют цвет вместе с остальным интерфейсом.

---

# Everis 0.2-beta

## English

### What’s new

- **Timetables:** DSTU support, teacher timetable search, full teacher names for both RSUE and DSTU, and corrected subgroup handling.
- **Navigation:** compact floating dock with a classic option, long-press shortcuts to each section’s main screen and hierarchical Back navigation.
- **Homework:** filter the next class by lecture, practice or lab.
- **Interface:** fixed headings, stable group suggestions, smoother language changes and inertial time wheels.
- **Performance:** faster discipline lists and fixes for dock flashes and language-change blackouts.

---

## Русский

### Что нового

- **Расписание:** добавлены ДГТУ, поиск расписания преподавателей и полные ФИО для РИНХ и ДГТУ. Исправлены загрузка занятий и определение подгрупп.
- **Навигация:** компактный плавающий док с возможностью вернуть классический вид. Долгое нажатие на активную вкладку возвращает в начало раздела; жест назад последовательно закрывает вложенные экраны.
- **Домашние задания:** при выборе следующего занятия можно отфильтровать лекции, практики и лабораторные.
- **Интерфейс:** закреплены заголовки, стабилизирован поиск групп, улучшены переключение языка и анимации. Барабаны времени получили инерцию.
- **Быстродействие:** ускорено открытие дисциплин, устранены вспышки дока и чёрный экран при смене языка.

---

# Everis 0.1-beta

## English

### What’s new

First Everis release, migrated from the previous repository. Android 12+, ARM64 only. Package `org.everis.app`, Android versionCode 8.

- Public RSUE/RINH timetables saved for offline use, day/week/month views, subgroup filtering and study totals.
- Homework for the next class, discipline task lists, local reminders and schedule change checks.
- Light, dark and black themes, Monet dynamic colors and custom accents; five interface languages.
- Offline schedule sharing and JSON backups. No application server, accounts or telemetry.
- Fixed transition direction and interruption, rapid tab switching, settings switches and full-button touch feedback. System Back no longer visits a tab/page history.
- Project links, GitHub release checks and a unified About page.

The APK uses the permanent Release signing identity. Other universities are not supported yet. Android may defer background checks while sleeping. This is an early beta; report feedback through Issues or the README contacts.

---

## Русский

### Что нового

Первый выпуск Everis, перенесённый из прежнего репозитория. Android 12+, только ARM64. Пакет `org.everis.app`, внутренний код версии 8.

- Публичное расписание РГЭУ (РИНХ) с сохранением для работы без интернета; дни, недели и месяцы, подгруппы и статистика занятий.
- Домашние задания к следующей паре, список задач по дисциплинам, локальные напоминания и проверка изменений расписания.
- Светлая, тёмная и чёрная темы, динамический акцент Monet и собственные цвета; пять языков интерфейса.
- Локальный экспорт/импорт расписания и резервные копии JSON. Нет сервера приложения, аккаунтов и телеметрии.
- Исправлены направления и прерывание переходов, быстрые переключения вкладок, анимации переключателей и отклик кнопок. Убрана история страниц для системного «Назад».
- Добавлены ссылки проекта, проверка обновлений через GitHub Releases и единый раздел «О приложении».

Опубликованный APK подписан постоянным Release-ключом. Другие вузы пока не подключены. Android может задерживать фоновые проверки в режиме сна. Это ранняя бета: отзывы и ошибки принимаются через Issues и контакты в README.
