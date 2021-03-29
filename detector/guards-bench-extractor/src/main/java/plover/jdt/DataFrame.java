package plover.jdt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DataFrame {

    private List<List<String>> data = new ArrayList<>();

    private List<String> columnNames;

    public static DataFrame newInstance(List<String> columnNames) throws Exception {
        if (columnNames == null || columnNames.size() == 0) {
            throw new Exception("Columns' names should not be null or empty");
        }
        if (hasDuplicate(columnNames)) {
            throw new Exception("Columns' names should be unique");
        }
        DataFrame df = new DataFrame();
        df.setColumnNames(columnNames);
        return df;
    }

    private static <T> boolean hasDuplicate(Iterable<T> all) {
        Set<T> set = new HashSet<T>();
        // Set#add returns false if the set does not change, which
        // indicates that a duplicate element has been added.
        for (T each: all) if (!set.add(each)) return true;
        return false;
    }

    public void addRow(List<String> values) throws Exception {
        if (values.size() != columnNames.size()) {
            throw new Exception("Size of values does not equal to size of columns");
        }
        this.data.add(values);
    }

    public List<String> getColumnNames() {
        return columnNames;
    }


    public void setColumnNames(List<String> columnNames) {
        this.columnNames = columnNames;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(columnNames.stream().collect(Collectors.joining("\t"))).append("\n");
        for (List<String> row : data) {
            sb.append(row.stream().collect(Collectors.joining("\t"))).append("\n");
        }
        return sb.toString();
    }

}