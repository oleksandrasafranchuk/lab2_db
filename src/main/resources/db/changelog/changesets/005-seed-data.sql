-- liquibase formatted sql

--changeset oleksandra:005-seed-reference
INSERT INTO halls (name)
VALUES ('Головний зал'),
       ('Літня тераса');

INSERT INTO restaurant_tables (hall_id, number, seats)
VALUES (1, 1, 2),
       (1, 2, 4),
       (1, 3, 6),
       (2, 1, 4);

INSERT INTO menu_categories (name)
VALUES ('Супи'),
       ('Основні страви'),
       ('Салати'),
       ('Напої');

INSERT INTO dishes (category_id, name, price)
VALUES (1, 'Борщ український', 145.00),
       (1, 'Крем-суп грибний', 135.00),
       (2, 'Котлета по-київськи', 265.00),
       (2, 'Стейк Рібай', 690.00),
       (2, 'Вареники з картоплею', 155.00),
       (3, 'Салат Цезар', 210.00),
       (3, 'Салат Грецький', 185.00),
       (4, 'Americano', 55.00);

INSERT INTO employees (full_name, job_title)
VALUES ('Марія Коваль', 'Waiter'),
       ('Андрій Шевчук', 'Waiter'),
       ('Ігор Лисенко', 'Manager');

INSERT INTO customers (full_name, phone, discount_pct)
VALUES ('Петро Іваненко', '+380501110011', 0),
       ('Оксана Гриценко', '+380501110012', 5),
       ('Дмитро Савченко', '+380501110013', 10),
       ('Юлія Романюк', '+380501110014', 15),
       ('Назар Поліщук', '+380501110015', 0);
