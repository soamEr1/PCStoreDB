package pcdb;

import java.time.LocalDate;


public class Condition {
    private final String column;
    private final String operator;
    private final Object expected;

    public Condition(String column, String operator, Object expected) {
        this.column = column.toLowerCase();
        this.operator = operator;
        this.expected = expected;
    }
    public String getColumn() { return column; }
    public String getOperator() { return operator; }
    public Object getExpected() { return expected; }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean matches(Row row) {
        Object actual = row.get(column);
        if ("=".equals(operator)) return actual == null ? expected == null : actual.equals(expected);

        if ("!=".equals(operator) || "<>".equals(operator))
            return actual == null ? expected != null : !actual.equals(expected);
        if (actual == null || expected == null) return false;

        int comparison;
        if (actual instanceof Number && expected instanceof Number) {
            comparison = Double.compare(((Number) actual).doubleValue(), ((Number) expected).doubleValue());
        } else if (actual instanceof LocalDate && expected instanceof LocalDate) {
            comparison = ((LocalDate) actual).compareTo((LocalDate) expected);
        } else if (actual instanceof Comparable && actual.getClass().isInstance(expected)) {
            comparison = ((Comparable) actual).compareTo(expected);
        } else {
            comparison = actual.toString().compareTo(expected.toString());
        }
        if (">".equals(operator)) return comparison > 0;
        if ("<".equals(operator)) return comparison < 0;
        if (">=".equals(operator)) return comparison >= 0;
        if ("<=".equals(operator)) return comparison <= 0;
        throw new IllegalArgumentException("Незвестный оператор: " + operator);
    }
}
