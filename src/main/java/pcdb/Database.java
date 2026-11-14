package pcdb;

import java.io.*;
import java.nio.file.*;
import java.util.*;


public class Database {
    private final Map<String, Table> tables = new LinkedHashMap<String, Table>();
    private final Path storageFile;

    public Database(String fileName) {
        storageFile = Paths.get(fileName);
        load();
    }

    public Collection<Table> getTables() { return Collections.unmodifiableCollection(tables.values()); }
    public Table getTable(String name) {
        Table table = tables.get(name.toLowerCase());
        if (table == null) throw new IllegalArgumentException("Таблица не найдена: " + name);
        return table;
    }

    public void createTable(String name, List<Column> columns) {
        String key = name.toLowerCase();
        if (tables.containsKey(key)) throw new IllegalArgumentException("Таблиnца уже существует: " + name);
        // Проверяем ссылки до добавления таблицы.
        for (Column column : columns) {
            if (column.getReferencedTable() != null) {
                Table parent = tables.get(column.getReferencedTable());
                if (parent == null) throw new IllegalArgumentException("Таблица для KEY не найдена: " + column.getReferencedTable());
                Column referenced = parent.getColumn(column.getReferencedColumn());
                if (referenced == null || !referenced.isPrimaryKey())
                    throw new IllegalArgumentException("Внешний ключ должен ссылаться на PRIMARY KEY");
                if (referenced.getType() != column.getType())
                    throw new IllegalArgumentException("Типы внешнего и первичного ключей должны совпадать");
            }
        }
        tables.put(key, new Table(key, columns));
        save();
    }

    public void dropTable(String name) {
        String key = name.toLowerCase();
        if (!tables.containsKey(key)) throw new IllegalArgumentException("таблица не надена: " + name);
        for (Table table : tables.values()) {
            if (table.getName().equals(key)) continue;
            for (Column column : table.getColumns()) {
                if (key.equals(column.getReferencedTable()))
                    throw new IllegalArgumentException("Нельзя удалить таблицу: на неё ссылается " + table.getName());
            }
        }
        tables.remove(key);
        save();
    }

    public void validateForeignKeys(Table table, Row row) {
        for (Column column : table.getColumns()) {
            if (column.getReferencedTable() == null) continue;
            Object value = row.get(column.getName());
            if (value == null) continue;
            Table parent = getTable(column.getReferencedTable());
            boolean found = false;
            for (Row parentRow : parent.getRows()) {
                if (Objects.equals(parentRow.get(column.getReferencedColumn()), value)) {
                    found = true;
                    break;
                }
            }
            if (!found) throw new IllegalArgumentException("Нарушен FOREIGN KEY: значение " + value +
                    " отсутствует в " + column.getReferencedTable() + "." + column.getReferencedColumn());
        }
    }

    public void save() {
        try {
            Path parent = storageFile.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = storageFile.resolveSibling(storageFile.getFileName().toString() + ".tmp");
            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(temp))) {
                output.writeObject(tables);
                output.flush();
            }
            try {
                Files.move(temp, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, storageFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new RuntimeException("Не удалось сохранить базу данных: " + exception.getMessage(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(storageFile)) return;
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(storageFile))) {
            Object object = input.readObject();
            if (!(object instanceof Map)) throw new IOException("Неврный формат файла базы данных");
            tables.putAll((Map<String, Table>) object);
            for (Table table : tables.values()) table.rebuildIndexes();
        } catch (Exception exception) {
            Path damaged = storageFile.resolveSibling(storageFile.getFileName().toString() + ".corrupt");
            try { Files.move(storageFile, damaged, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException ignored) { }
            System.err.println("файл базы данных повреждён. Он сохранён как " + damaged +
                    ". Программа запущена с пустой базой.");
        }
    }
}
