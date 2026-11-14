package pcdb;

import java.time.LocalDate;


public enum DataType {
    INT, DOUBLE, STRING, DATE, BOOLEAN;

    public static DataType fromSql(String text) {
        String type = text.trim().toUpperCase();
        if (type.equals("INT") || type.equals("INTEGER")) return INT;
        if (type.equals("DOUBLE") || type.equals("FLOAT") || type.equals("REAL")) return DOUBLE;
        if (type.equals("STRING") || type.equals("TEXT") || type.startsWith("VARCHAR")) return STRING;
        if (type.equals("DATE")) return DATE;
        if (type.equals("BOOLEAN") || type.equals("BOOL")) return BOOLEAN;
        throw new IllegalArgumentException("Неизвестный тип данных: " + text);
    }

    public Object parse(String text) {
        String value = text.trim();
        if (value.equalsIgnoreCase("NULL")) return null;
        switch (this) {
            case INT: return Integer.valueOf(value);
            case DOUBLE: return Double.valueOf(value);
            case STRING:
                if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'"))
                    return value.substring(1, value.length() - 1).replace("''", "'");
                return value;
            case DATE:
                return LocalDate.parse(unquote(value));
            case BOOLEAN:
                if (value.equalsIgnoreCase("TRUE")) return Boolean.TRUE;
                if (value.equalsIgnoreCase("FALSE")) return Boolean.FALSE;
                throw new IllegalArgumentException("Для булевых используйте TRUE или FALSE");
            default: throw new IllegalStateException("Неизвестный тип");
        }
    }

    private String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'"))
            return value.substring(1, value.length() - 1);
        return value;
    }
}
