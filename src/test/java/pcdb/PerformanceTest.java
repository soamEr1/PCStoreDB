package pcdb;

import java.util.*;

/** Небольшой воспроизводимый тест: линейный поиск против поиска через индекс. */
public class PerformanceTest {
    public static void main(String[] args) {
        final int count = 100000;
        Table table = new Table("test", Arrays.asList(
                new Column("id", DataType.INT, true, null, null),
                new Column("category_id", DataType.INT, false, null, null),
                new Column("price", DataType.DOUBLE, false, null, null)));
        for (int i = 1; i <= count; i++) {
            Row row = new Row();
            row.set("id", i);
            row.set("category_id", i % 100);
            row.set("price", i * 1.5);
            table.insert(row);
        }
        // Прогрев JVM перед замерами.
        for (int i = 0; i < 3; i++) {
            scan(table, 37);
        }
        long scanStart = System.nanoTime();
        int scanFound = scan(table, 37);
        long scanTime = System.nanoTime() - scanStart;

        table.createIndex("idx_category", "category_id");
        long indexStart = System.nanoTime();
        int indexFound = table.findByIndex("category_id", 37).size();
        long indexTime = System.nanoTime() - indexStart;

        System.out.println("Записей: " + count);
        System.out.println("Найдено без индекса: " + scanFound);
        System.out.println("Линейный поиск, мс: " + scanTime / 1_000_000.0);
        System.out.println("Найдено с индексом: " + indexFound);
        System.out.println("Поиск по индексу, мс: " + indexTime / 1_000_000.0);
        if (scanFound != indexFound) throw new AssertionError("Результаты поиска не совпадают");
    }

    private static int scan(Table table, int categoryId) {
        int found = 0;
        for (Row row : table.getRows())
            if (Integer.valueOf(categoryId).equals(row.get("category_id"))) found++;
        return found;
    }
}
