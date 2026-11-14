package pcdb;

import java.io.Serializable;


public class Column implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final DataType type;
    private final boolean primaryKey;
    private final String referencedTable;
    private final String referencedColumn;

    public Column(String name, DataType type, boolean primaryKey,
                  String referencedTable, String referencedColumn) {
        this.name = name.toLowerCase();
        this.type = type;
        this.primaryKey = primaryKey;
        this.referencedTable = referencedTable == null ? null : referencedTable.toLowerCase();
        this.referencedColumn = referencedColumn == null ? null : referencedColumn.toLowerCase();
    }
    public String getName() { return name; }
    public DataType getType() { return type; }
    public boolean isPrimaryKey() { return primaryKey; }
    public String getReferencedTable() { return referencedTable; }
    public String getReferencedColumn() { return referencedColumn; }
}
