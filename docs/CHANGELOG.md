# Everis 0.3-beta

## English

### What’s new

- Added offline timetables for SFedU ICTIS, RGUPS and Southern University (IMBL). ICTIS also supports teacher search. For full-time RGUPS groups, Everis calculates the current and following week from the university’s two-week cycle. IMBL shows the dated weeks published for your group.
- Week arrows now jump straight to Monday. The day selector, calendar and navigation labels fit smaller screens and larger system fonts. Daily totals resize smoothly when the text wraps.
- Universities are listed alphabetically. Group suggestions stay below the field while you type or switch to the symbol keyboard. The list shrinks smoothly as the search narrows.
- The homework editor fades in at the top of the day view. Its fields and dropdowns now use the same colors as the “Change group” button.
- Interface settings bring language, appearance, haptics and performance together. Switches respond immediately, and the dock follows theme changes. The smaller E fits more launcher icon shapes.
- In-app release notes follow the app language and fall back to English when a translation is unavailable.

---

## Русский

### Что нового

- Добавили офлайн-расписания ИКТИБ ЮФУ, РГУПС и Южного университета (ИУБиП). В ИКТИБ доступен также поиск преподавателей. Для очных групп РГУПС Everis рассчитывает текущую и следующую недели по двухнедельному циклу вуза. В ИУБиП видны все недели, для которых вуз опубликовал даты занятий группы.
- Стрелки недели сразу открывают понедельник. Панель дней, календарь и подписи вкладок лучше помещаются на небольших экранах и при крупном системном шрифте. Высота сводки дня плавно меняется при переносе текста.
- Вузы отсортированы по алфавиту. При вводе группы и переключении клавиатуры на символы подсказки остаются под полем. Когда вариантов становится меньше, список плавно сжимается.
- Редактор ДЗ плавно появляется вверху экрана дня. Поля ДЗ и выпадающие списки теперь оформлены в цветах кнопки «Изменить группу».
- В разделе «Интерфейс» собраны язык, оформление, тактильная отдача и режим повышенной производительности. Переключатели сразу реагируют на нажатие, а док меняет цвет вместе с темой. Буква E на значке стала чуть меньше и лучше вписывается в системные маски.
- При обновлении приложение показывает список изменений на выбранном языке. Если перевода нет, открывается английский текст.

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
