package pcdb;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class Row implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Object> values = new LinkedHashMap<String, Object>();

    public Object get(String column) { return values.get(column.toLowerCase()); }
    public void set(String column, Object value) { values.put(column.toLowerCase(), value); }
    public boolean contains(String column) { return values.containsKey(column.toLowerCase()); }
    public Map<String, Object> getValues() { return values; }
}
