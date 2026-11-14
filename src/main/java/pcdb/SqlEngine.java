package pcdb;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SqlEngine {
    private final Database database;

    public SqlEngine(Database database) { this.database = database; }

    public String execute(String command) {
        String sql = command.trim();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).trim();
        if (sql.isEmpty()) return "";
        String upper = sql.toUpperCase(Locale.ROOT);
        if (upper.startsWith("CREATE TABLE ")) return createTable(sql);
        if (upper.startsWith("DROP TABLE ")) {
            database.dropTable(sql.substring(11).trim());
            return "Таблица удалена.";
        }
        if (upper.startsWith("CREATE INDEX ")) return createIndex(sql);
        if (upper.startsWith("INSERT INTO ")) return insert(sql);
        if (upper.startsWith("SELECT ")) return select(sql);
        if (upper.startsWith("UPDATE ")) return update(sql);
        if (upper.startsWith("DELETE FROM ")) return delete(sql);
        throw new IllegalArgumentException("Неизвестная или неподдерживаемая SQL-команда.");
    }

    private String createTable(String sql) {
        Matcher matcher = Pattern.compile("(?is)^CREATE TABLE\\s+(\\w+)\\s*\\((.*)\\)$").matcher(sql);
        if (!matcher.matches()) throw new IllegalArgumentException("Неверный формат CREATE TABLE");
        List<String> definitions = splitComma(matcher.group(2));
        List<Column> columns = new ArrayList<Column>();
        for (String definition : definitions) {
            Matcher foreign = Pattern.compile("(?i)^FOREIGN\\s+KEY\\s*\\((\\w+)\\)\\s+REFERENCES\\s+(\\w+)\\s*\\((\\w+)\\)$").matcher(definition.trim());
            if (foreign.matches()) {
                String columnName = foreign.group(1).toLowerCase();
                int position = -1;
                for (int i = 0; i < columns.size(); i++)
                    if (columns.get(i).getName().equals(columnName)) position = i;
                if (position < 0) throw new IllegalArgumentException("Сначала объявите столбец внешнего ключа");
                Column old = columns.get(position);
                columns.set(position, new Column(old.getName(), old.getType(), old.isPrimaryKey(),
                        foreign.group(2), foreign.group(3)));
                continue;
            }
            Matcher column = Pattern.compile("(?i)^(\\w+)\\s+(INT|INTEGER|DOUBLE|FLOAT|REAL|STRING|TEXT|VARCHAR(?:\\s*\\(\\s*\\d+\\s*\\))?|DATE|BOOLEAN|BOOL)(?:\\s+(PRIMARY\\s+KEY))?$").matcher(definition.trim());
            if (!column.matches()) throw new IllegalArgumentException("Не удалось разобрать столбец: " + definition);
            columns.add(new Column(column.group(1), DataType.fromSql(column.group(2)),
                    column.group(3) != null, null, null));
        }
        database.createTable(matcher.group(1), columns);
        return "Таблица " + matcher.group(1) + " создана.";
    }

    private String createIndex(String sql) {
        Matcher matcher = Pattern.compile("(?i)^CREATE INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\((\\w+)\\)$").matcher(sql);
        if (!matcher.matches()) throw new IllegalArgumentException("Неверный формат CREATE INDEX");
        database.getTable(matcher.group(2)).createIndex(matcher.group(1), matcher.group(3));
        database.save();
        return "Индекс " + matcher.group(1) + " создан и сохранён.";
    }

    private String insert(String sql) {
        Matcher matcher = Pattern.compile("(?is)^INSERT INTO\\s+(\\w+)\\s*(?:\\((.*?)\\))?\\s*VALUES\\s*(.+)$").matcher(sql);
        if (!matcher.matches()) throw new IllegalArgumentException("Неверный формат INSERT");
        Table table = database.getTable(matcher.group(1));
        List<String> columns = matcher.group(2) == null
                ? columnNames(table) : splitComma(matcher.group(2));
        List<List<String>> records = parseValueGroups(matcher.group(3));
        int inserted = 0;
        for (List<String> values : records) {
            if (columns.size() != values.size())
                throw new IllegalArgumentException("Количество столбцов и значений не совпадает");
            Row row = new Row();
            for (int i = 0; i < columns.size(); i++) {
                String name = columns.get(i).trim().toLowerCase();
                Column column = table.getColumn(name);
                if (column == null) throw new IllegalArgumentException("Нет столбца: " + name);
                Object value = column.getType().parse(values.get(i));
                table.validateValue(name, value);
                row.set(name, value);
            }
            for (Column column : table.getColumns()) {
                if (!row.contains(column.getName())) row.set(column.getName(), null);
                table.validateValue(column.getName(), row.get(column.getName()));
            }
            database.validateForeignKeys(table, row);
            table.insert(row);
            inserted++;
        }
        database.save();
        return "Добавлено записей: " + inserted;
    }

    private String select(String sql) {
        Matcher matcher = Pattern.compile("(?is)^SELECT\\s+(.*?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?$").matcher(sql);
        if (!matcher.matches()) throw new IllegalArgumentException("Неверный формат SELECT");
        Table table = database.getTable(matcher.group(2));
        List<String> projection = matcher.group(1).trim().equals("*")
                ? columnNames(table) : splitComma(matcher.group(1));
        for (int i = 0; i < projection.size(); i++) {
            projection.set(i, projection.get(i).trim().toLowerCase());
            if (table.getColumn(projection.get(i)) == null)
                throw new IllegalArgumentException("Нет столбца: " + projection.get(i));
        }
        Condition condition = matcher.group(3) == null ? null : parseCondition(table, matcher.group(3));
        Collection<Row> candidates;
        boolean indexUsed = false;
        if (condition != null && "=".equals(condition.getOperator()) && table.hasIndexOn(condition.getColumn())) {
            Set<Integer> ids = table.findByIndex(condition.getColumn(), condition.getExpected());
            List<Row> indexedRows = new ArrayList<Row>();
            for (Integer id : ids) {
                Row row = table.getRowsById().get(id);
                if (row != null) indexedRows.add(row);
            }
            candidates = indexedRows;
            indexUsed = true;
        } else {
            candidates = table.getRows();
        }
        List<Row> result = new ArrayList<Row>();
        for (Row row : candidates)
            if (condition == null || condition.matches(row)) result.add(row);
        return formatTable(projection, result, indexUsed);
    }

    private String update(String sql) {
        Matcher matcher = Pattern.compile("(?is)^UPDATE\\s+(\\w+)\\s+SET\\s+(\\w+)\\s*=\\s*(.*?)\\s+WHERE\\s+(.+)$").matcher(sql);
        if (!matcher.matches()) throw new IllegalArgumentException("Неверный формат UPDATE: требуется WHERE");
        Table table = database.getTable(matcher.group(1));
        String columnName = matcher.group(2).toLowerCase();
        Column column = table.getColumn(columnName);
        if (column == null) throw new IllegalArgumentException("Нет столбца: " + columnName);
        Object newValue = column.getType().parse(matcher.group(3));
        table.validateValue(columnName, newValue);
        Condition condition = parseCondition(table, matcher.group(4));

        List<Row> selected = new ArrayList<Row>();
        for (Row row : table.getRows()) if (condition.matches(row)) selected.add(row);

        Map<Integer, Row> replacement = new LinkedHashMap<Integer, Row>();
        for (Map.Entry<Integer, Row> entry : table.getRowsById().entrySet()) {
            Row row = entry.getValue();
            if (selected.contains(row)) {
                Object oldValue = row.get(columnName);
                row.set(columnName, newValue);
                try {
                    database.validateForeignKeys(table, row);
                } catch (RuntimeException exception) {
                    row.set(columnName, oldValue);
                    throw exception;
                }
            }
            int id = (Integer) row.get(table.getPrimaryKey());
            if (replacement.containsKey(id) && replacement.get(id) != row)
                throw new IllegalArgumentException("Обновление нарушает уникальность PRIMARY KEY");
            replacement.put(id, row);
        }
        table.replaceRows(replacement);
        database.save();
        return "Обновлено записей: " + selected.size();
    }

    private String delete(String sql) {
        Matcher matcher = Pattern.compile("(?is)^DELETE FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?$").matcher(sql);
        if (!matcher.matches()) throw new IllegalArgumentException("Неверный формат DELETE");
        Table table = database.getTable(matcher.group(1));
        Condition condition = matcher.group(2) == null ? null : parseCondition(table, matcher.group(2));
        List<Integer> ids = new ArrayList<Integer>();
        for (Map.Entry<Integer, Row> entry : table.getRowsById().entrySet())
            if (condition == null || condition.matches(entry.getValue())) ids.add(entry.getKey());

        // Не разрешаем удалить родительскую запись, на которую ещё ссылаются.
        for (Table child : database.getTables()) {
            if (child == table) continue;
            for (Column column : child.getColumns()) {
                if (table.getName().equals(column.getReferencedTable())) {
                    for (Row childRow : child.getRows()) {
                        for (Integer id : ids) {
                            if (Objects.equals(childRow.get(column.getName()), id))
                                throw new IllegalArgumentException("Нельзя удалить запись: на неё ссылается таблица " + child.getName());
                        }
                    }
                }
            }
        }
        Map<Integer, Row> remaining = new LinkedHashMap<Integer, Row>(table.getRowsById());
        for (Integer id : ids) remaining.remove(id);
        table.replaceRows(remaining);
        database.save();
        return "Удалено записей: " + ids.size();
    }

    private Condition parseCondition(Table table, String text) {
        Matcher matcher = Pattern.compile("(?is)^\\s*(\\w+)\\s*(>=|<=|<>|!=|=|>|<)\\s*(.*?)\\s*$").matcher(text);
        if (!matcher.matches()) throw new IllegalArgumentException("WHERE поддерживает одно сравнение: =, !=, >, <, >=, <=");
        String name = matcher.group(1).toLowerCase();
        Column column = table.getColumn(name);
        if (column == null) throw new IllegalArgumentException("Нет столбца: " + name);
        return new Condition(name, matcher.group(2), column.getType().parse(matcher.group(3)));
    }

    private List<String> columnNames(Table table) {
        List<String> names = new ArrayList<String>();
        for (Column column : table.getColumns()) names.add(column.getName());
        return names;
    }

    private String formatTable(List<String> columns, List<Row> rows, boolean indexUsed) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) output.append(" | ");
            output.append(columns.get(i));
        }
        output.append('\n');
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) output.append("-+-");
            output.append("----------------");
        }
        output.append('\n');
        for (Row row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) output.append(" | ");
                output.append(String.valueOf(row.get(columns.get(i))));
            }
            output.append('\n');
        }
        output.append("Строк: ").append(rows.size());
        if (indexUsed) output.append(" (использован индекс)");
        return output.toString();
    }

    /** Разбивает строку по запятым, не затрагивая запятые внутри строк в одинарных кавычках. */
    static List<String> splitComma(String text) {
        List<String> result = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\'') {
                current.append(ch);
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    current.append(text.charAt(++i));
                    continue;
                }
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (quoted) throw new IllegalArgumentException("Строковое значение не закрыто кавычкой");
        if (current.length() > 0 || text.trim().isEmpty()) result.add(current.toString().trim());
        return result;
    }

    private List<List<String>> parseValueGroups(String text) {
        List<List<String>> groups = new ArrayList<List<String>>();
        int position = 0;
        while (position < text.length()) {
            while (position < text.length() && (Character.isWhitespace(text.charAt(position)) || text.charAt(position) == ',')) position++;
            if (position >= text.length()) break;
            if (text.charAt(position) != '(') throw new IllegalArgumentException("Ожидалась открывающая скобка VALUES");
            int start = ++position;
            boolean quoted = false;
            while (position < text.length()) {
                char ch = text.charAt(position);
                if (ch == '\'') {
                    if (quoted && position + 1 < text.length() && text.charAt(position + 1) == '\'') {
                        position += 2;
                        continue;
                    }
                    quoted = !quoted;
                } else if (ch == ')' && !quoted) {
                    groups.add(splitComma(text.substring(start, position)));
                    position++;
                    break;
                }
                position++;
            }
            if (position >= text.length() && (text.isEmpty() || text.charAt(text.length() - 1) != ')'))
                throw new IllegalArgumentException("Не закрыта скобка VALUES");
        }
        if (groups.isEmpty()) throw new IllegalArgumentException("Нет значений для INSERT");
        return groups;
    }
}
