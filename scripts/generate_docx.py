import os
import docx
from docx import Document
from docx.shared import Inches, Pt, Mm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

def create_contract_docx():
    doc = Document()
    
    # 1. Page Margins (СТП / ГОСТ: Top 20mm, Bottom 20mm, Left 30mm, Right 15mm)
    for section in doc.sections:
        section.top_margin = Mm(20)
        section.bottom_margin = Mm(20)
        section.left_margin = Mm(30)
        section.right_margin = Mm(15)
        section.page_width = Mm(210)
        section.page_height = Mm(297)

    # 2. Base Normal Style
    normal_style = doc.styles['Normal']
    font = normal_style.font
    font.name = 'Times New Roman'
    font.size = Pt(13)
    font.color.rgb = RGBColor(0, 0, 0)
    normal_style.paragraph_format.line_spacing = 1.15
    normal_style.paragraph_format.space_after = Pt(4)
    normal_style.paragraph_format.space_before = Pt(0)

    def add_p(text="", align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=12.5, bold=False, italic=False, size=13, space_before=0, space_after=4):
        p = doc.add_paragraph()
        p.alignment = align
        p.paragraph_format.space_before = Pt(space_before)
        p.paragraph_format.space_after = Pt(space_after)
        p.paragraph_format.line_spacing = 1.15
        if indent > 0:
            p.paragraph_format.first_line_indent = Mm(indent)
        else:
            p.paragraph_format.first_line_indent = Mm(0)
            
        if text:
            run = p.add_run(text)
            run.font.name = 'Times New Roman'
            run.font.size = Pt(size)
            run.bold = bold
            run.italic = italic
        return p

    def add_title(title, subtitle=None):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(6)
        p.paragraph_format.space_after = Pt(2)
        p.paragraph_format.first_line_indent = Mm(0)
        run = p.add_run(title)
        run.font.name = 'Times New Roman'
        run.font.size = Pt(14)
        run.bold = True
        
        if subtitle:
            p2 = doc.add_paragraph()
            p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p2.paragraph_format.space_before = Pt(0)
            p2.paragraph_format.space_after = Pt(12)
            p2.paragraph_format.first_line_indent = Mm(0)
            run2 = p2.add_run(subtitle)
            run2.font.name = 'Times New Roman'
            run2.font.size = Pt(13)
            run2.bold = True

    def add_section_h(text):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.space_before = Pt(10)
        p.paragraph_format.space_after = Pt(4)
        p.paragraph_format.first_line_indent = Mm(0)
        run = p.add_run(text)
        run.font.name = 'Times New Roman'
        run.font.size = Pt(13)
        run.bold = True
        return p

    def set_cell_margins(cell, top=100, bottom=100, left=150, right=150):
        tcPr = cell._tc.get_or_add_tcPr()
        tcMar = OxmlElement('w:tcMar')
        for m, val in [('w:top', top), ('w:bottom', bottom), ('w:left', left), ('w:right', right)]:
            node = OxmlElement(m)
            node.set(qn('w:w'), str(val))
            node.set(qn('w:type'), 'dxa')
            tcMar.append(node)
        tcPr.append(tcMar)

    def set_cell_shading(cell, color_hex):
        shading = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{color_hex}"/>')
        cell._tc.get_or_add_tcPr().append(shading)

    # ----------------- MAIN CONTRACT -----------------
    add_title("ДОГОВОР № 2026/11-MH", "на разработку, внедрение и передачу прав на программный комплекс «Mentor Hub»")

    # City and Date Table
    meta_table = doc.add_table(rows=1, cols=2)
    meta_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    meta_table.autofit = False
    meta_table.rows[0].cells[0].width = Mm(82.5)
    meta_table.rows[0].cells[1].width = Mm(82.5)
    
    p_city = meta_table.rows[0].cells[0].paragraphs[0]
    p_city.paragraph_format.first_line_indent = Mm(0)
    p_city.paragraph_format.space_after = Pt(12)
    r_city = p_city.add_run("г. Ереван / г. Минск")
    r_city.font.name = "Times New Roman"
    r_city.font.size = Pt(13)
    r_city.bold = True
    
    p_date = meta_table.rows[0].cells[1].paragraphs[0]
    p_date.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p_date.paragraph_format.first_line_indent = Mm(0)
    p_date.paragraph_format.space_after = Pt(12)
    r_date = p_date.add_run("«10» ноября 2026 г.")
    r_date.font.name = "Times New Roman"
    r_date.font.size = Pt(13)
    r_date.bold = True

    # Preambles
    p = add_p()
    r = p.add_run("Индивидуальный предприниматель Писарик Мирослав Игоревич ")
    r.bold = True
    p.add_run("(ԱՁ Պիսարիկ Միրոսլավ Իգորևիչ), действующий на основании свидетельства о государственной регистрации физического лица в качестве индивидуального предпринимателя № 264.1284953 от 14 февраля 2024 г., выданного Агентством государственного регистра юридических лиц Министерства юстиции Республики Армения, учетный номер налогоплательщика (TIN / ՀՎՀՀ): 74981624, именуемый в дальнейшем ")
    r2 = p.add_run("«Исполнитель»")
    r2.bold = True
    p.add_run(", с одной стороны,")

    p = add_p()
    p.add_run("и ")
    r = p.add_run("гражданка Республики Беларусь Сидоренко Ксения Сергеевна")
    r.bold = True
    p.add_run(", паспорт серии МР № 2459812, выдан Советским РУВД г. Минска 22.04.2021 г., идентификационный номер: 4210595A012PB4, проживающая по адресу: Республика Беларусь, г. Минск, пр-т Победителей, д. 38, кв. 74, именуемая в дальнейшем ")
    r2 = p.add_run("«Заказчик»")
    r2.bold = True
    p.add_run(", с другой стороны,")

    p = add_p()
    p.add_run("вместе именуемые ")
    p.add_run("«Стороны»").bold = True
    p.add_run(", а по отдельности — ")
    p.add_run("«Сторона»").bold = True
    p.add_run(", заключили настоящий Договор (далее — «Договор») о нижеследующем:")

    # 1. Предмет
    add_section_h("1. ПРЕДМЕТ ДОГОВОРА")
    p = add_p()
    p.add_run("1.1. По настоящему Договору Исполнитель обязуется по заданию Заказчика выполнить комплекс научно-технических и практических работ по проектированию, разработке, конфигурированию, тестированию и развертыванию программного комплекса ")
    p.add_run("«Mentor Hub»").bold = True
    p.add_run(" (далее — «Программный комплекс» / «ПО»), предназначенного для автоматизации сбора ежедневных отчетов, контроля дисциплины подопечных и формирования аналитических кейсов динамики дохода («Точка А ➡️ Точка Б»), а также передать Заказчику исключительные имущественные права на созданное ПО в полном объеме, а Заказчик обязуется принять результаты выполненных работ и оплатить их на условиях, определенных настоящим Договором.")

    add_p("1.2. Функциональные, технические, программные и эксплуатационные требования к Программному комплексу, этапы выполнения работ, состав передаваемой документации и исходного кода определяются Техническим заданием (Приложение № 1 к Договору) и Календарным планом (Приложение № 2 к Договору), являющимися неотъемлемыми частями настоящего Договора.")

    add_p("1.3. В состав создаваемого Программного комплекса входят следующие ключевые компоненты:")
    
    p = add_p(indent=18)
    p.add_run("• ").bold = True
    p.add_run("Фоновый шлюз интеграции с Telegram (Telegram Ingestion Gateway): ").bold = True
    p.add_run("модуль взаимодействия на базе Telegram Bot API / Telegram Business API для автоматического сбора отчетов, валидации медиа-ссылок Instagram Reels с защитой от повторной сдачи (SHA-256 хеширование) и отправки системных напоминаний;")

    p = add_p(indent=18)
    p.add_run("• ").bold = True
    p.add_run("Интеллектуальный модуль фильтрации и анализа (AI-Pipeline): ").bold = True
    p.add_run("модуль автоматической транскрибации голосовых аудиосообщений (.oga -> текст) и извлечения сущностей (Reels URL, суммы продаж в рублях, профессиональные вопросы к наставнику) с отсечением нерелевантного бытового флуда;")

    p = add_p(indent=18)
    p.add_run("• ").bold = True
    p.add_run("Клиентский аналитический веб-интерфейс наставника (React SPA): ").bold = True
    p.add_run("оптимизированный для Apple iPad и десктопных браузеров пользовательский интерфейс с функционалом «Inbox Zero», интерактивным недельным календарем дисциплины (Пн–Вс) и генератором готовых продающих текстов кейсов для сторис;")

    p = add_p(indent=18)
    p.add_run("• ").bold = True
    p.add_run("Серверный бэкенд и база данных: ").bold = True
    p.add_run("серверный REST API на базе стека Java 21 LTS / Spring Boot и реляционная СУБД PostgreSQL 16+.")

    # 2. Стоимость
    add_section_h("2. СТОИМОСТЬ УСЛУГ И ПОРЯДОК РАСЧЕТОВ")
    p = add_p()
    p.add_run("2.1. Общая стоимость работ по разработке и передаче прав на Программный комплекс по настоящему Договору (тариф «Полный вариант PRO») составляет ")
    p.add_run("475,00 (четыреста семьдесят пять белорусских рублей 00 копеек) BYN").bold = True
    p.add_run(" (без НДС в связи с применением Исполнителем специального налогового режима для индивидуальных предпринимателей в сфере информационных технологий в соответствии с законодательством Республики Армения).")

    add_p("2.2. Оплата по настоящему Договору производится Заказчиком в безналичном порядке путем перечисления денежных средств на банковский расчетный счет Исполнителя в следующем порядке:")
    
    p = add_p(indent=18)
    p.add_run("2.2.1. Авансовый платеж (предоплата): ").bold = True
    p.add_run("в размере 50% (пятидесяти процентов) от общей стоимости Договора, что составляет ")
    p.add_run("237,50 (двести тридцать семь белорусских рублей 50 копеек) BYN").bold = True
    p.add_run(", выплачивается Заказчиком в течение 3 (трех) банковских дней с момента подписания настоящего Договора.")

    p = add_p(indent=18)
    p.add_run("2.2.2. Окончательный расчет: ").bold = True
    p.add_run("в размере оставшихся 50% (пятидесяти процентов) стоимости Договора, что составляет ")
    p.add_run("237,50 (двести тридцать семь белорусских рублей 50 копеек) BYN").bold = True
    p.add_run(", выплачивается Заказчиком в течение 3 (трех) банковских дней со дня подписания двустороннего Итогового акта сдачи-приемки оказанных услуг.")

    add_p("2.3. В случае осуществления расчетов в иной валюте (драмы РА — AMD, доллары США — USD или российские рубли — RUB), сумма платежа рассчитывается по официальному обменному курсу Центрального банка на дату выставления соответствующего счета (инвойса) Исполнителем.")
    add_p("2.4. Обязательство Заказчика по оплате считается исполненным с момента списания денежных средств с расчетного счета Заказчика либо зачисления средств на корреспондентский счет банка Исполнителя.")

    # 3. Сроки
    add_section_h("3. СРОКИ И ЭТАПЫ ВЫПОЛНЕНИЯ РАБОТ")
    p = add_p()
    p.add_run("3.1. Сроки выполнения работ по настоящему Договору:")
    
    p = add_p(indent=18)
    p.add_run("• Дата начала выполнения работ: ").bold = True
    p.add_run("«10» ноября 2026 г.")
    
    p = add_p(indent=18)
    p.add_run("• Дата окончания работ и сдачи в опытную эксплуатацию: ").bold = True
    p.add_run("«24» ноября 2026 г. (общий срок — 14 календарных дней).")

    add_p("3.2. Работы выполняются в соответствии с этапами, зафиксированными в Календарном плане (Приложение № 2): анализ требований и проектирование БД (2 дня); разработка ядра Telegram Ingestion (3 дня); разработка клиентского SPA веб-трекера (4 дня); реализация AI-пайплайна обработки отчетов (3 дня); интеграционное тестирование и развертывание (2 дня).")
    add_p("3.3. Исполнитель вправе завершить выполнение работ или отдельных этапов досрочно, уведомив об этом Заказчика. Заказчик обязан принять и оплатить досрочно выполненные работы в установленном порядке.")

    # 4. Приемка
    add_section_h("4. ПОРЯДОК СДАЧИ И ПРИЕМКИ РАБОТ")
    add_p("4.1. По завершении работ Исполнитель предоставляет Заказчику доступ к Git-репозиторию с полным исходным кодом ПО, рабочий экземпляр Программного комплекса, развернутый на тестовом сервере Заказчика, техническую документацию и 2 (два) подписанных экземпляра Акта сдачи-приемки оказанных услуг (Приложение № 3).")
    add_p("4.2. Заказчик в течение 3 (трех) рабочих дней со дня получения комплекта материалов производит проверку соответствия Программного комплекса требованиям Технического задания (Приложение № 1) и направляет Исполнителю подписанный Акт сдачи-приемки либо мотивированный письменный отказ с перечнем выявленных несоответствий ТЗ.")
    add_p("4.3. В случае мотивированного отказа Заказчика Стороны составляют протокол доработок с указанием перечня исправлений и сроков их устранения Исполнителем (не превышающих 5 рабочих дней). Устранение несоответствий ТЗ осуществляется Исполнителем безвозмездно.")
    add_p("4.4. Если в течение 3 (трех) рабочих дней Заказчик не подпишет Акт и не предоставит мотивированный отказ, работы считаются принятыми в полном объеме и надлежащего качества на основании одностороннего Акта, составленного Исполнителем.")

    # 5. Права и обязанности
    add_section_h("5. ПРАВА И ОБЯЗАННОСТИ СТОРОН")
    add_p("5.1. Исполнитель обязуется выполнить работы качественно и в срок в строгом соответствии с ТЗ, соблюдать стандарты чистого кода и информационной безопасности, безвозмездно устранять выявленные скрытые дефекты и обеспечивать конфиденциальность данных Заказчика.")
    add_p("5.2. Исполнитель имеет право запрашивать у Заказчика необходимые разъяснения и приостанавливать работы при задержке предоставления критических доступов (API-токены Telegram Bot API, хостинг).")
    add_p("5.3. Заказчик обязуется предоставить Исполнителю в течение 2 (двух) календарных дней необходимые доступы и тестовые данные отчетов, принимать оперативные решения по согласованию макетов и своевременно осуществлять оплату.")

    # 6. Интеллектуальные права
    add_section_h("6. ИНТЕЛЛЕКТУАЛЬНЫЕ ПРАВА")
    add_p("6.1. Все исключительные имущественные права на результаты интеллектуальной деятельности (включая исходный код ПО «Mentor Hub», архитектуру БД, скрипты развертывания и компоненты веб-интерфейса), созданные Исполнителем по настоящему Договору, передаются Заказчику в полном объеме с момента проведения окончательного расчета.")
    add_p("6.2. Исполнитель гарантирует лицензионную чистоту используемых библиотек и открытых фреймворков (Spring Boot, React, Tailwind CSS, PostgreSQL Driver, распространяемых по свободным лицензиям MIT, Apache 2.0).")

    # 7. Гарантия
    add_section_h("7. ГАРАНТИЙНЫЕ ОБЯЗАТЕЛЬСТВА И ПОДДЕРЖКА")
    p = add_p()
    p.add_run("7.1. Исполнитель устанавливает гарантийный срок на разработанный Программный комплекс продолжительностью ")
    p.add_run("6 (шесть) календарных месяцев").bold = True
    p.add_run(" со дня подписания Итогового акта сдачи-приемки оказанных услуг.")
    add_p("7.2. В течение гарантийного периода Исполнитель обязуется безвозмездно устранять любые выявленные скрытые программные ошибки («баги») в срок не более 48 часов с момента поступления запроса от Заказчика.")

    # 8. Конфиденциальность, ответственность, споры
    add_section_h("8. КОНФИДЕНЦИАЛЬНОСТЬ (NDA) И ОТВЕТСТВЕННОСТЬ СТОРОН")
    add_p("8.1. Стороны обязуются сохранять строгую конфиденциальность в отношении персональных данных учениц Заказчика, сумм продаж, финансовых метрик и ключей доступа к API мессенджера Telegram.")
    add_p("8.2. За нарушение сроков сдачи работ либо сроков оплаты виновная Сторона уплачивает неустойку (пеню) в размере 0,1% от суммы просроченного обязательства за каждый день просрочки, но не более 10% от общей стоимости Договора.")
    add_p("8.3. Все споры и разногласия разрешаются Сторонами путем переговоров с направлением мотивированной претензии (срок ответа — 10 рабочих дней), а при недостижении согласия — в судебном порядке по месту нахождения Исполнителя.")

    # 9. Заключительные положения
    add_section_h("9. ЗАКЛЮЧИТЕЛЬНЫЕ ПОЛОЖЕНИЯ")
    add_p("9.1. Настоящий Договор вступает в силу с момента его подписания и действует до полного исполнения Сторонами своих обязательств.")
    add_p("9.2. Стороны признают полную юридическую силу документов, согласованных и направленных в электронном виде посредством мессенджера Telegram и электронной почты.")
    add_p("9.3. Настоящий Договор составлен на русском языке в двух подлинных экземплярах, имеющих равную юридическую силу, по одному экземпляру для каждой из Сторон.")

    # 10. Реквизиты и подписи сторон
    add_section_h("10. АДРЕСА, РЕКВИЗИТЫ И ПОДПИСИ СТОРОН")
    
    table = doc.add_table(rows=1, cols=2)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    
    c1, c2 = table.rows[0].cells
    c1.width = Mm(82.5)
    c2.width = Mm(82.5)
    set_cell_margins(c1, 120, 120, 100, 100)
    set_cell_margins(c2, 120, 120, 100, 100)

    # Исполнитель
    p = c1.paragraphs[0]
    p.paragraph_format.first_line_indent = Mm(0)
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run("ИСПОЛНИТЕЛЬ:\n")
    r.bold = True
    r2 = p.add_run("ИП Писарик Мирослав Игоревич\n")
    r2.bold = True
    p.add_run("(ԱՁ Պիսարիկ Միրոսլավ Իգորևիչ)\n"
              "Свид. о рег. № 264.1284953 от 14.02.2024 г.\n"
              "Агентство гос. регистра МЮ РА\n"
              "TIN / ՀՎՀՀ: 74981624\n"
              "Юр. адрес: Республика Армения, г. Ереван, 0010, р-н Кентрон, ул. Амиряна, д. 4/3, кв. 18\n"
              "Банк: ЗАО «АКБА Банк» (г. Ереван, РА)\n"
              "IBAN (AMD): AM35 0151 0001 2345 6789\n"
              "IBAN (USD/BYN): AM92 0151 0002 9876 5432\n"
              "SWIFT: ARCBAM22\n"
              "Email: pisarik.miroslav@gmail.com\n\n\n"
              "____________________ / М. И. Писарик /\n"
              "М.П. (Печать ИП)")

    # Заказчик
    p = c2.paragraphs[0]
    p.paragraph_format.first_line_indent = Mm(0)
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run("ЗАКАЗЧИК:\n")
    r.bold = True
    r2 = p.add_run("Сидоренко Ксения Сергеевна\n")
    r2.bold = True
    p.add_run("Гражданство: Республика Беларусь\n"
              "Паспорт: серия МР № 2459812\n"
              "выдан Советским РУВД г. Минска 22.04.2021 г.\n"
              "Идентификационный №: 4210595A012PB4\n"
              "Адрес регистрации: Республика Беларусь,\n"
              "220035, г. Минск, пр-т Победителей, д. 38, кв. 74\n"
              "Телефон: +375 (29) 714-88-29\n"
              "Telegram: @ksenia_sidorenko\n"
              "Email: ksenia.mentoring@gmail.com\n\n\n"
              "____________________ / К. С. Сидоренко /\n")


    # ----------------- ПРИЛОЖЕНИЕ № 1 (ТЗ) -----------------
    doc.add_page_break()

    p_app = doc.add_paragraph()
    p_app.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p_app.paragraph_format.first_line_indent = Mm(0)
    p_app.paragraph_format.space_after = Pt(8)
    r_app = p_app.add_run("Приложение № 1\nк Договору № 2026/11-MH\nот «10» ноября 2026 г.")
    r_app.bold = True

    add_title("ТЕХНИЧЕСКОЕ ЗАДАНИЕ (ТЗ)", "на разработку программного комплекса «Mentor Hub»")

    add_section_h("1. ОБЩИЕ СВЕДЕНИЯ И НАЗНАЧЕНИЕ СИСТЕМЫ")
    add_p("1.1. Полное наименование: Программный комплекс сбора отчетов, трекинга дисциплины и автоматизированного формирования аналитических кейсов «Mentor Hub».")
    add_p("1.2. Заказчик: Сидоренко Ксения Сергеевна (наставник экспертов и предпринимателей).")
    add_p("1.3. Исполнитель: Индивидуальный предприниматель Писарик Мирослав Игоревич.")
    add_p("1.4. Назначение системы: Освобождение 1.5–2 часов ежедневного рабочего времени наставника за счет ликвидации ручной рутины в разрозненных личных диалогах и чатах Telegram, защита от фрода (сдачи старых или повторных Reels) и автоматический расчет динамики учениц («Точка А ➡️ Точка Б») для блога.")

    add_section_h("2. ТРЕБОВАНИЯ К ФУНКЦИОНАЛЬНЫМ ПОДСИСТЕМАМ")
    add_p("2.1. Подсистема сбора отчетов Telegram (Telegram Ingestion Gateway):")
    p = add_p(indent=18)
    p.add_run("• Интеграция с Telegram Business API (для личных диалогов с ученицами) и Telegram Bot API (для мини-групп);\n"
              "• Фильтрация входящих сообщений по обязательным хэштегам: #рилс, #продажи, #рассылки, #вопрос;\n"
              "• Валидация гиперссылок Instagram Reels с проверкой формата и HTTP-проверкой доступности ресурса;\n"
              "• Алгоритм защиты от дубликатов: расчет цифрового отпечатка SHA-256 URL и проверка по базе отчетов с выставлением флага DUPLICATE_SUSPECT;\n"
              "• Автоматический триггер вечерних напоминаний (cron в 20:00) ученицам, не сдавшим отчет.")

    add_p("2.2. Подсистема искусственного интеллекта (AI-Pipeline):")
    p = add_p(indent=18)
    p.add_run("• Распознавание речи: автоматическая транскрибация аудиосообщений формата .oga в текст;\n"
              "• Интеллектуальный фильтр флуда: вычленение из текста отчета ключевых сущностей (ссылка на Reels, сумма продаж в рублях, вопрос наставнику) с отсечением бытовых эмоциональных жалоб;\n"
              "• Автогенератор кейсов для Instagram Stories: сопоставление Точки А и текущей Точки Б, расчет кратного множителя роста (х2, х5) и генерация продающего текста в 1 клик.")

    add_p("2.3. Пользовательский веб-интерфейс наставника (Mentor Dashboard SPA):")
    p = add_p(indent=18)
    p.add_run("• Оптимизация под экраны Apple iPad и десктопные мониторы;\n"
              "• Вкладка «На проверку (Inbox Zero)» с карточками отчетов за день, валидацией Reels, быстрыми комментариями и кнопкой перехода «В Telegram»;\n"
              "• Вкладка «Ученицы & Календарь» с недельной сеткой (Пн–Вс) и цветовой индикацией дисциплины;\n"
              "• Вкладка «Аналитика & Кейсы» с кнопкой копирования готового текста прогрева в буфер обмена.")

    add_section_h("3. ТЕХНИЧЕСКИЙ СТЕК И АРХИТЕКТУРА")
    add_p("3.1. Бэкенд: Java 21 LTS (Virtual Threads), фреймворк Spring Boot 3.4+ / Spring AI, TelegramBots SDK 10.3, СУБД PostgreSQL 16+.")
    add_p("3.2. Фронтенд: React 19, сборщик Vite, Tailwind CSS, Lucide Icons.")
    add_p("3.3. Развертывание: Docker, Docker Compose, переменные окружения (.env).")

    # Подписи к ТЗ
    table_tz = doc.add_table(rows=1, cols=2)
    table_tz.alignment = WD_TABLE_ALIGNMENT.CENTER
    table_tz.autofit = False
    c1, c2 = table_tz.rows[0].cells
    c1.width = Mm(82.5)
    c2.width = Mm(82.5)
    set_cell_margins(c1, 150, 100, 100, 100)
    set_cell_margins(c2, 150, 100, 100, 100)
    
    p = c1.paragraphs[0]
    p.paragraph_format.first_line_indent = Mm(0)
    p.add_run("Исполнитель:\n\n_________________ / М. И. Писарик /\nМ.П.").bold = True
    
    p = c2.paragraphs[0]
    p.paragraph_format.first_line_indent = Mm(0)
    p.add_run("Заказчик:\n\n_________________ / К. С. Сидоренко /").bold = True


    # ----------------- ПРИЛОЖЕНИЕ № 2 (КАЛЕНДАРНЫЙ ПЛАН) -----------------
    doc.add_page_break()

    p_app = doc.add_paragraph()
    p_app.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p_app.paragraph_format.first_line_indent = Mm(0)
    p_app.paragraph_format.space_after = Pt(8)
    r_app = p_app.add_run("Приложение № 2\nк Договору № 2026/11-MH\nот «10» ноября 2026 г.")
    r_app.bold = True

    add_title("КАЛЕНДАРНЫЙ ПЛАН И СМЕТА РАБОТ", "по разработке программного комплекса «Mentor Hub»")

    plan_table = doc.add_table(rows=1, cols=5)
    plan_table.style = 'Table Grid'
    plan_table.alignment = WD_TABLE_ALIGNMENT.CENTER
    plan_table.autofit = False

    headers = ["№", "Наименование этапа работ", "Срок", "Дата сдачи", "Стоимость"]
    col_widths = [Mm(10), Mm(80), Mm(20), Mm(25), Mm(30)]

    hdr_cells = plan_table.rows[0].cells
    for i, title in enumerate(headers):
        hdr_cells[i].width = col_widths[i]
        p = hdr_cells[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.first_line_indent = Mm(0)
        p.paragraph_format.space_before = Pt(4)
        p.paragraph_format.space_after = Pt(4)
        run = p.add_run(title)
        run.bold = True
        run.font.name = 'Times New Roman'
        run.font.size = Pt(11)
        set_cell_margins(hdr_cells[i], 80, 80, 80, 80)
        set_cell_shading(hdr_cells[i], "E2E8F0")

    stages = [
        ("1", "Анализ требований, проектирование реляционной схемы БД PostgreSQL и спецификаций REST API", "2 дня", "12.11.2026", "75,00 BYN"),
        ("2", "Разработка Telegram Bot Ingestion Gateway, валидация Reels, алгоритм SHA-256 дедупликатора", "3 дня", "15.11.2026", "120,00 BYN"),
        ("3", "Разработка пользовательского интерфейса React 19 SPA («Inbox Zero», календарь, адаптация iPad)", "4 дня", "19.11.2026", "130,00 BYN"),
        ("4", "Интеграция AI-пайплайна транскрибации, парсинга отчетов и генератора сторис-кейсов", "3 дня", "22.11.2026", "100,00 BYN"),
        ("5", "Интеграционное тестирование, развертывание в Docker и сдача в опытную эксплуатацию", "2 дня", "24.11.2026", "50,00 BYN")
    ]

    for item in stages:
        row = plan_table.add_row()
        for idx, text in enumerate(item):
            cell = row.cells[idx]
            cell.width = col_widths[idx]
            set_cell_margins(cell, 80, 80, 80, 80)
            p = cell.paragraphs[0]
            p.paragraph_format.first_line_indent = Mm(0)
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(2)
            if idx in (0, 2, 3):
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            elif idx == 4:
                p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
            else:
                p.alignment = WD_ALIGN_PARAGRAPH.LEFT
            run = p.add_run(text)
            run.font.name = 'Times New Roman'
            run.font.size = Pt(11)

    # Итоговая строка
    total_row = plan_table.add_row()
    c_tot_label = total_row.cells[0]
    c_tot_val = total_row.cells[4]
    
    # merge 0..3
    c_tot_label.merge(total_row.cells[1]).merge(total_row.cells[2]).merge(total_row.cells[3])
    set_cell_margins(c_tot_label, 100, 100, 100, 100)
    set_cell_margins(c_tot_val, 100, 100, 100, 100)
    set_cell_shading(c_tot_label, "F1F5F9")
    set_cell_shading(c_tot_val, "F1F5F9")
    
    p = c_tot_label.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p.paragraph_format.first_line_indent = Mm(0)
    r = p.add_run("ИТОГО ПО ДОГОВОРУ (14 календарных дней):")
    r.bold = True
    r.font.name = 'Times New Roman'
    r.font.size = Pt(11)

    p_val = c_tot_val.paragraphs[0]
    p_val.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p_val.paragraph_format.first_line_indent = Mm(0)
    r_val = p_val.add_run("475,00 BYN")
    r_val.bold = True
    r_val.font.name = 'Times New Roman'
    r_val.font.size = Pt(11)

    # Подписи к Плану
    table_plan_sign = doc.add_table(rows=1, cols=2)
    table_plan_sign.alignment = WD_TABLE_ALIGNMENT.CENTER
    table_plan_sign.autofit = False
    c1, c2 = table_plan_sign.rows[0].cells
    c1.width = Mm(82.5)
    c2.width = Mm(82.5)
    set_cell_margins(c1, 150, 100, 100, 100)
    set_cell_margins(c2, 150, 100, 100, 100)
    
    p = c1.paragraphs[0]
    p.paragraph_format.first_line_indent = Mm(0)
    p.add_run("Исполнитель:\n\n_________________ / М. И. Писарик /\nМ.П.").bold = True
    
    p = c2.paragraphs[0]
    p.paragraph_format.first_line_indent = Mm(0)
    p.add_run("Заказчик:\n\n_________________ / К. С. Сидоренко /").bold = True


    # ----------------- ПРИЛОЖЕНИЕ № 3 (АКТ СДАЧИ-ПРИЕМКИ) -----------------
    doc.add_page_break()

    p_app = doc.add_paragraph()
    p_app.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p_app.paragraph_format.first_line_indent = Mm(0)
    p_app.paragraph_format.space_after = Pt(8)
    r_app = p_app.add_run("Приложение № 3\nк Договору № 2026/11-MH\nот «10» ноября 2026 г.")
    r_app.bold = True

    add_title("АКТ СДАЧИ-ПРИЕМКИ ОКАЗАННЫХ УСЛУГ", "по разработке программного комплекса «Mentor Hub»")

    meta_act = doc.add_table(rows=1, cols=2)
    meta_act.alignment = WD_TABLE_ALIGNMENT.CENTER
    meta_act.autofit = False
    meta_act.rows[0].cells[0].width = Mm(82.5)
    meta_act.rows[0].cells[1].width = Mm(82.5)
    
    p_c = meta_act.rows[0].cells[0].paragraphs[0]
    p_c.paragraph_format.first_line_indent = Mm(0)
    p_c.paragraph_format.space_after = Pt(12)
    p_c.add_run("г. Ереван / г. Минск").bold = True
    
    p_d = meta_act.rows[0].cells[1].paragraphs[0]
    p_d.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p_d.paragraph_format.first_line_indent = Mm(0)
    p_d.paragraph_format.space_after = Pt(12)
    p_d.add_run("«24» ноября 2026 г.").bold = True

    p = add_p()
    p.add_run("Мы, нижеподписавшиеся: ")
    p.add_run("Индивидуальный предприниматель Писарик Мирослав Игоревич ").bold = True
    p.add_run("(ԱՁ Պիսարիկ Միրոսլավ Իգորևիչ), действовавший на основании свидетельства № 264.1284953, TIN: 74981624, именуемый в дальнейшем «Исполнитель», с одной стороны, и гражданка Республики Беларусь ")
    p.add_run("Сидоренко Ксения Сергеевна").bold = True
    p.add_run(", паспорт серии МР № 2459812, именуемая в дальнейшем «Заказчик», с другой стороны, составили настоящий Акт о нижеследующем:")

    add_p("1. В соответствии с Договором № 2026/11-MH от «10» ноября 2026 г. Исполнитель в полном объеме и с надлежащим качеством выполнил работы по проектированию, разработке, конфигурированию, тестированию, развертыванию Программного комплекса «Mentor Hub» (включая шлюз Telegram Ingestion Gateway, модуль AI-аналитики отчетов и транскрибации, клиентский веб-трекер наставника), а также передал Заказчику исключительные имущественные права на разработанное программное обеспечение.")

    add_p("2. Работы выполнены в строгом соответствии с Техническим заданием (Приложение № 1) и Календарным планом (Приложение № 2). Претензий по объему, качеству и срокам выполнения работ со стороны Заказчика не имеется.")

    p = add_p()
    p.add_run("3. Общая стоимость выполненных и принятых работ составляет ")
    p.add_run("475,00 (четыреста семьдесят пять белорусских рублей 00 копеек) BYN").bold = True
    p.add_run(" (без НДС).")
    
    p = add_p(indent=18)
    p.add_run("• Ранее перечисленный Заказчиком аванс: ").bold = True
    p.add_run("237,50 (двести тридцать семь белорусских рублей 50 копеек) BYN.")

    p = add_p(indent=18)
    p.add_run("• Сумма, подлежащая окончательной оплате по настоящему Акту: ").bold = True
    p.add_run("237,50 (двести тридцать семь белорусских рублей 50 копеек) BYN.").bold = True

    add_p("4. Настоящий Акт составлен в двух экземплярах, имеющих равную юридическую силу, по одному для каждой из Сторон.")

    # Реквизиты и подписи к Акту
    table_act_sign = doc.add_table(rows=1, cols=2)
    table_act_sign.alignment = WD_TABLE_ALIGNMENT.CENTER
    table_act_sign.autofit = False
    c1, c2 = table_act_sign.rows[0].cells
    c1.width = Mm(82.5)
    c2.width = Mm(82.5)
    set_cell_margins(c1, 150, 100, 100, 100)
    set_cell_margins(c2, 150, 100, 100, 100)
    
    p = c1.paragraphs[0]
    p.paragraph_format.first_line_indent = Mm(0)
    p.add_run("Исполнитель:\n"
              "ИП Писарик М. И.\n\n\n"
              "_________________ / М. И. Писарик /\n"
              "М.П. (Печать ИП)").bold = True
    
    p = c2.paragraphs[0]
    p.paragraph_format.first_line_indent = Mm(0)
    p.add_run("Заказчик:\n"
              "Сидоренко К. С.\n\n\n"
              "_________________ / К. С. Сидоренко /").bold = True

    # Save documents
    out_dir = r"c:\Users\Honor\Desktop\miranda\docs"
    os.makedirs(out_dir, exist_ok=True)
    out_path_1 = os.path.join(out_dir, "ДОГОВОР_ПИСАРИК_СИДОРЕНКО.docx")
    out_path_2 = os.path.join(out_dir, "CONTRACT_PISARIK_SIDORENKO.docx")
    
    doc.save(out_path_1)
    doc.save(out_path_2)
    print(f"Successfully generated:\n- {out_path_1}\n- {out_path_2}")

if __name__ == "__main__":
    create_contract_docx()
