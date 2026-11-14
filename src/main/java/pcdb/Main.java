package pcdb;

import java.util.Scanner;

/** Консольный интерфейс MiniDB. */
public class Main {
    public static void main(String[] args) {
        Database database = new Database("data/pcstore.db");
        SqlEngine engine = new SqlEngine(database);
        System.out.println("PC Store MiniDB ведите HELP для примеров, EXIT для выхода.");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("pcdb> ");
            if (!scanner.hasNextLine()) break;
            String command = scanner.nextLine().trim();
            if (command.equalsIgnoreCase("EXIT") || command.equalsIgnoreCase("QUIT")) break;
            if (command.equalsIgnoreCase("HELP")) {
                printHelp();
                continue;
            }
            if (command.isEmpty()) continue;
            try {
                System.out.println(engine.execute(command));
            } catch (Exception exception) {
                System.out.println("Ошибка: " + exception.getMessage());
            }
        }
        scanner.close();
        System.out.println("Работа завершена. Данные сохранены.");
    }

    private static void printHelp() {
        System.out.println(
            "CREATE TABLE categories (id INT PRIMARY KEY, name STRING, description STRING);\\n" +
            "CREATE TABLE components (id INT PRIMARY KEY, name STRING, category_id INT, price DOUBLE, release_date DATE, in_stock BOOLEAN, FOREIGN KEY (category_id) REFERENCES categories(id));\\n" +
            "INSERT INTO categories VALUES (1, 'Процессоры', 'CPU');\\n" +
            "INSERT INTO components VALUES (1, 'AMD Ryzen 7', 1, 28999.0, '2025-01-15', TRUE);\\n" +
            "INSERT INTO components (id, name, category_id) VALUES (2, 'Intel Core i5', 1);\\n" +
            "SELECT * FROM components WHERE price > 20000.0;\\n" +
            "CREATE INDEX idx_components_category ON components (category_id);\\n" +
            "UPDATE components SET price = 30000.0 WHERE id = 1;\\n" +
            "DELETE FROM components WHERE id = 2;\\n" +
            "DROP TABLE components;\\n" +
            "Один запрос вводится одной строкой. WHERE поддерживает одно простое условие.");
    }
}
