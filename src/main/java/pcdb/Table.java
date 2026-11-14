package pcdb;

import java.io.Serializable;
import java.util.*;


public class Table implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final LinkedHashMap<String, Column> columns = new LinkedHashMap<String, Column>();
    private final LinkedHashMap<Integer, Row> rows = new LinkedHashMap<Integer, Row>();
    // Имя индекса -> столбец; столбец -> значение -> набор первичных ключей.
    private final LinkedHashMap<String, String> indexDefinitions = new LinkedHashMap<String, String>();
    private final Map<String, Map<String, Set<Integer>>> indexes = new HashMap<String, Map<String, Set<Integer>>>();
    private String primaryKey;

    public Table(String name, List<Column> definitions) {
        this.name = name.toLowerCase();
        for (Column column : definitions) {
            if (columns.containsKey(column.getName()))
                throw new IllegalArgumentException("Столбец уже объявлен: " + column.getName());
            columns.put(column.getName(), column);
            if (column.isPrimaryKey()) {
                if (primaryKey != null) throw new IllegalArgumentException("Разрешён только один PRIMARY KEY");
                if (column.getType() != DataType.INT)
                    throw new IllegalArgumentException("В этой учебной версии PRIMARY KEY должен иметь тип INT");
                primaryKey = column.getName();
            }
        }
        if (primaryKey == null) throw new IllegalArgumentException("Необходимо объявить PRIMARY KEY");
    }

    public String getName() { return name; }
    public String getPrimaryKey() { return primaryKey; }
    public Collection<Column> getColumns() { return Collections.unmodifiableCollection(columns.values()); }
    public Column getColumn(String name) { return columns.get(name.toLowerCase()); }
    public Collection<Row> getRows() { return Collections.unmodifiableCollection(rows.values()); }
    public Map<Integer, Row> getRowsById() { return Collections.unmodifiableMap(rows); }
    public Map<String, String> getIndexDefinitions() { return Collections.unmodifiableMap(indexDefinitions); }

    public void validateValue(String columnName, Object value) {
        Column column = columns.get(columnName.toLowerCase());
        if (column == null) throw new IllegalArgumentException("В таблице нет столбца: " + columnName);
        if (value == null) {
            if (column.isPrimaryKey()) throw new IllegalArgumentException("PRIMARY KEY не может быть NULL");
            return;
        }
        boolean valid;
        switch (column.getType()) {
            case INT: valid = value instanceof Integer; break;
            case DOUBLE: valid = value instanceof Double; break;
            case STRING: valid = value instanceof String; break;
            case DATE: valid = value instanceof java.time.LocalDate; break;
            case BOOLEAN: valid = value instanceof Boolean; break;
            default: valid = false;
        }
        if (!valid) throw new IllegalArgumentException("Неверный тип значения для столбца " + columnName);
    }

    public void insert(Row row) {
        for (String column : columns.keySet()) {
            if (!row.contains(column)) row.set(column, null);
            validateValue(column, row.get(column));
        }
        int id = (Integer) row.get(primaryKey);
        if (rows.containsKey(id)) throw new IllegalArgumentException("Первичный ключ " + id + " уже существует");
        rows.put(id, row);
        rebuildIndexes();
    }

    public void replaceRows(Map<Integer, Row> replacement) {
        rows.clear();
        rows.putAll(replacement);
        rebuildIndexes();
    }

    public void deleteById(int id) {
        rows.remove(id);
        rebuildIndexes();
    }

    public void createIndex(String indexName, String columnName) {
        String idxName = indexName.toLowerCase();
        String col = columnName.toLowerCase();
        if (!columns.containsKey(col)) throw new IllegalArgumentException("Нет столбца " + col);
        if (indexDefinitions.containsKey(idxName)) throw new IllegalArgumentException("Индекс уже существует: " + idxName);
        for (String existing : indexDefinitions.values())
            if (existing.equals(col)) throw new IllegalArgumentException("Для столбца уже создан индекс: " + col);
        indexDefinitions.put(idxName, col);
        rebuildIndexes();
    }

    public boolean hasIndexOn(String column) { return indexes.containsKey(column.toLowerCase()); }

    public Set<Integer> findByIndex(String column, Object value) {
        Map<String, Set<Integer>> index = indexes.get(column.toLowerCase());
        if (index == null) return null;
        Set<Integer> found = index.get(indexKey(value));
        return found == null ? Collections.<Integer>emptySet() : new LinkedHashSet<Integer>(found);
    }

    public void rebuildIndexes() {
        indexes.clear();
        for (String column : indexDefinitions.values()) indexes.put(column, new HashMap<String, Set<Integer>>());
        for (Map.Entry<Integer, Row> entry : rows.entrySet()) {
            for (Map.Entry<String, Map<String, Set<Integer>>> indexEntry : indexes.entrySet()) {
                String key = indexKey(entry.getValue().get(indexEntry.getKey()));
                Map<String, Set<Integer>> map = indexEntry.getValue();
                if (!map.containsKey(key)) map.put(key, new LinkedHashSet<Integer>());
                map.get(key).add(entry.getKey());
            }
        }
    }

    private String indexKey(Object value) {
        return value == null ? "<NULL>" : value.getClass().getName() + ":" + value.toString();
    }
}
